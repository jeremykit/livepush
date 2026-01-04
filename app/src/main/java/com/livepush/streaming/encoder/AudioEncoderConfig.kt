package com.livepush.streaming.encoder

import com.livepush.domain.model.AudioCodec

/**
 * Audio encoder configuration with buffer size calculation
 */
data class AudioEncoderConfig(
    val sampleRate: Int = 44100,
    val channelCount: Int = 2,
    val bitrate: Int = 128_000,
    val codec: AudioCodec = AudioCodec.AAC,
    val bitsPerSample: Int = 16,
    val bufferDurationMs: Int = 20
) {
    /**
     * Calculate buffer size in bytes based on audio configuration
     * Formula: (sampleRate * channelCount * bitsPerSample * bufferDurationMs) / (1000 * 8)
     */
    fun calculateBufferSize(): Int {
        val bytesPerSample = bitsPerSample / 8
        val samplesPerBuffer = (sampleRate * bufferDurationMs) / 1000
        return samplesPerBuffer * channelCount * bytesPerSample
    }

    /**
     * Calculate minimum buffer size for the encoder
     * Typically 2-3x the calculated buffer size for stability
     */
    fun calculateMinBufferSize(): Int {
        return calculateBufferSize() * 2
    }

    /**
     * Calculate frame size in bytes for one audio frame
     */
    fun calculateFrameSize(): Int {
        return (sampleRate / 1000) * channelCount * (bitsPerSample / 8)
    }

    /**
     * Validate configuration parameters
     */
    fun isValid(): Boolean {
        return sampleRate > 0 &&
                channelCount in 1..2 &&
                bitrate > 0 &&
                bitsPerSample in listOf(8, 16, 24, 32) &&
                bufferDurationMs > 0
    }

    companion object {
        // Standard sample rates
        const val SAMPLE_RATE_8000 = 8000
        const val SAMPLE_RATE_16000 = 16000
        const val SAMPLE_RATE_22050 = 22050
        const val SAMPLE_RATE_44100 = 44100
        const val SAMPLE_RATE_48000 = 48000

        // Standard buffer durations in milliseconds
        const val BUFFER_DURATION_10MS = 10
        const val BUFFER_DURATION_20MS = 20
        const val BUFFER_DURATION_40MS = 40
        const val BUFFER_DURATION_60MS = 60

        // Bitrate presets
        const val BITRATE_LOW = 64_000
        const val BITRATE_MEDIUM = 128_000
        const val BITRATE_HIGH = 192_000
        const val BITRATE_VERY_HIGH = 320_000
    }
}
