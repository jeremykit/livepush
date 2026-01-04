package com.livepush.streaming.encoder

import android.media.MediaCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages MediaCodec buffer lifecycle to prevent memory leaks and 25-40 minute crashes.
 *
 * This class ensures:
 * - Immediate buffer release after data copying
 * - Proper PTS (Presentation Timestamp) calculation
 * - Exception-safe buffer handling with try-finally
 * - Monotonically increasing timestamps
 *
 * Critical for long session stability (4+ hours).
 */
@Singleton
class BufferReleaseManager @Inject constructor() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // PTS tracking for monotonically increasing timestamps
    private var lastPresentationTimeUs: Long = 0L
    private var baseTimestampUs: Long = 0L
    private var accumulatedDurationUs: Long = 0L

    // Buffer statistics
    private val _bufferStats = MutableStateFlow(BufferStats())
    val bufferStats: StateFlow<BufferStats> = _bufferStats.asStateFlow()

    /**
     * Process a MediaCodec output buffer, copy its data, and release immediately.
     *
     * @param encoder The MediaCodec encoder instance
     * @param outputBufferId The buffer index returned by dequeueOutputBuffer
     * @param bufferInfo The BufferInfo associated with this buffer
     * @param config Audio configuration for PTS calculation
     * @return ByteArray copy of the buffer data, or null if processing failed
     */
    fun processAndReleaseBuffer(
        encoder: MediaCodec,
        outputBufferId: Int,
        bufferInfo: MediaCodec.BufferInfo,
        config: AudioEncoderConfig
    ): ByteArray? {
        if (outputBufferId < 0) {
            Timber.w("Invalid buffer ID: $outputBufferId")
            return null
        }

        var bufferData: ByteArray? = null

        try {
            // Get the output buffer
            val outputBuffer = encoder.getOutputBuffer(outputBufferId)
            if (outputBuffer == null) {
                Timber.e("Failed to get output buffer for index: $outputBufferId")
                return null
            }

            // Copy data to temporary buffer for async processing
            // CRITICAL: Must copy before releasing to prevent data corruption
            bufferData = copyBufferData(outputBuffer, bufferInfo)

            // Calculate and validate PTS
            val calculatedPts = calculatePresentationTimeUs(bufferInfo.size, config)
            bufferInfo.presentationTimeUs = calculatedPts

            // Update statistics
            _bufferStats.value = _bufferStats.value.copy(
                totalBuffersProcessed = _bufferStats.value.totalBuffersProcessed + 1,
                totalBytesProcessed = _bufferStats.value.totalBytesProcessed + bufferInfo.size,
                lastPresentationTimeUs = calculatedPts
            )

            Timber.v(
                "Buffer processed: id=$outputBufferId, size=${bufferInfo.size}, " +
                "pts=${calculatedPts}us, flags=${bufferInfo.flags}"
            )

        } catch (e: Exception) {
            Timber.e(e, "Error processing buffer: $outputBufferId")
            _bufferStats.value = _bufferStats.value.copy(
                bufferErrors = _bufferStats.value.bufferErrors + 1
            )
        } finally {
            // CRITICAL: Always release buffer even on error
            // This prevents the 25-40 minute crash pattern
            try {
                encoder.releaseOutputBuffer(outputBufferId, false)
                Timber.v("Buffer released: id=$outputBufferId")
            } catch (e: Exception) {
                Timber.e(e, "Failed to release buffer: $outputBufferId")
                _bufferStats.value = _bufferStats.value.copy(
                    releaseErrors = _bufferStats.value.releaseErrors + 1
                )
            }
        }

        return bufferData
    }

    /**
     * Copy buffer data to a new ByteArray.
     * Required to prevent holding buffer references during async processing.
     */
    private fun copyBufferData(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo): ByteArray {
        val data = ByteArray(bufferInfo.size)

        // Position the buffer correctly
        buffer.position(bufferInfo.offset)
        buffer.limit(bufferInfo.offset + bufferInfo.size)

        // Copy data
        buffer.get(data)

        return data
    }

    /**
     * Calculate presentation timestamp for audio buffer.
     * Ensures monotonically increasing PTS to prevent audio/video drift.
     *
     * Formula: PTS = baseTimestamp + accumulatedDuration
     * Duration = (bufferSize * 1000000) / (sampleRate * channels * bytesPerSample)
     *
     * @param bufferSize Size of the buffer in bytes
     * @param config Audio configuration
     * @return Presentation time in microseconds
     */
    fun calculatePresentationTimeUs(bufferSize: Int, config: AudioEncoderConfig): Long {
        // Calculate buffer duration in microseconds
        val bytesPerSample = config.bitsPerSample / 8
        val totalBytesPerSample = config.channelCount * bytesPerSample
        val sampleCount = bufferSize / totalBytesPerSample
        val bufferDurationUs = (sampleCount * 1_000_000L) / config.sampleRate

        // Accumulate duration
        val presentationTimeUs = baseTimestampUs + accumulatedDurationUs
        accumulatedDurationUs += bufferDurationUs

        // Ensure monotonically increasing PTS
        val currentPts = maxOf(presentationTimeUs, lastPresentationTimeUs + 1)
        lastPresentationTimeUs = currentPts

        return currentPts
    }

    /**
     * Reset PTS calculation state for a new encoding session.
     * Call this when starting a new stream.
     */
    fun reset() {
        baseTimestampUs = System.nanoTime() / 1000
        lastPresentationTimeUs = 0L
        accumulatedDurationUs = 0L

        _bufferStats.value = BufferStats()

        Timber.d("BufferReleaseManager reset, base timestamp: ${baseTimestampUs}us")
    }

    /**
     * Release all resources and cancel ongoing operations.
     */
    fun release() {
        scope.cancel()
        reset()
        Timber.d("BufferReleaseManager released")
    }

    /**
     * Statistics for buffer processing monitoring.
     */
    data class BufferStats(
        val totalBuffersProcessed: Long = 0L,
        val totalBytesProcessed: Long = 0L,
        val bufferErrors: Long = 0L,
        val releaseErrors: Long = 0L,
        val lastPresentationTimeUs: Long = 0L
    ) {
        val averageBufferSize: Long
            get() = if (totalBuffersProcessed > 0) {
                totalBytesProcessed / totalBuffersProcessed
            } else {
                0L
            }

        val errorRate: Double
            get() = if (totalBuffersProcessed > 0) {
                (bufferErrors + releaseErrors).toDouble() / totalBuffersProcessed
            } else {
                0.0
            }

        val hasErrors: Boolean
            get() = bufferErrors > 0 || releaseErrors > 0
    }
}
