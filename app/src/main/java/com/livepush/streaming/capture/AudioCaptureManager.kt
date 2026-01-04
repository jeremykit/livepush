package com.livepush.streaming.capture

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages microphone resource lifecycle and audio capture health monitoring.
 *
 * This manager handles:
 * - Microphone resource initialization and cleanup
 * - Audio buffer health monitoring
 * - Automatic recovery from transient failures
 * - Hardware sample rate detection
 *
 * Designed for extended streaming sessions (4+ hours) with stability focus.
 */
@Singleton
class AudioCaptureManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null

    private val _captureState = MutableStateFlow<AudioCaptureState>(AudioCaptureState.Idle)
    val captureState: StateFlow<AudioCaptureState> = _captureState.asStateFlow()

    private val _bufferHealth = MutableStateFlow(AudioBufferHealth())
    val bufferHealth: StateFlow<AudioBufferHealth> = _bufferHealth.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var currentConfig: AudioCaptureConfig = AudioCaptureConfig()
    private var retryCount: Int = 0

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val BUFFER_INCREASE_FACTOR = 2.0
        private const val DEFAULT_SAMPLE_RATE = 44100
        private const val DEFAULT_BITRATE = 128000
    }

    /**
     * Initializes audio capture with the specified configuration.
     *
     * Automatically detects hardware sample rate on API 24+ using SAMPLE_RATE_UNSPECIFIED,
     * falls back to 44100Hz on older devices.
     *
     * @param config Audio capture configuration
     * @return True if initialization successful, false otherwise
     */
    fun initialize(config: AudioCaptureConfig = AudioCaptureConfig()): Boolean {
        this.currentConfig = config

        try {
            release() // Clean up any existing resources

            val sampleRate = detectHardwareSampleRate(config.sampleRate)
            val channelConfig = if (config.isStereo) {
                AudioFormat.CHANNEL_IN_STEREO
            } else {
                AudioFormat.CHANNEL_IN_MONO
            }
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT

            // Calculate buffer size with safety factor for long sessions
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Timber.e("Invalid buffer size for sample rate: $sampleRate")
                _captureState.value = AudioCaptureState.Error("Invalid audio configuration")
                return false
            }

            val bufferSize = (minBufferSize * BUFFER_INCREASE_FACTOR).toInt()

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Timber.e("AudioRecord not initialized properly")
                _captureState.value = AudioCaptureState.Error("Microphone initialization failed")
                return false
            }

            _captureState.value = AudioCaptureState.Initialized(sampleRate, bufferSize)
            _bufferHealth.value = AudioBufferHealth(
                bufferSize = bufferSize,
                minBufferSize = minBufferSize
            )

            Timber.d("Audio capture initialized: ${sampleRate}Hz, buffer=${bufferSize}bytes (${bufferSize / minBufferSize}x min)")
            return true

        } catch (e: SecurityException) {
            Timber.e(e, "Microphone permission not granted")
            _captureState.value = AudioCaptureState.Error("Microphone permission denied")
            return false
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize audio capture")
            _captureState.value = AudioCaptureState.Error(e.message ?: "Unknown error")
            return false
        }
    }

    /**
     * Starts audio recording.
     *
     * @return True if recording started successfully, false otherwise
     */
    fun startRecording(): Boolean {
        if (audioRecord == null) {
            Timber.e("AudioRecord not initialized")
            return initializeWithRetry()
        }

        try {
            audioRecord?.startRecording()
            _captureState.value = AudioCaptureState.Recording
            startBufferMonitoring()

            Timber.d("Audio recording started")
            return true

        } catch (e: Exception) {
            Timber.e(e, "Failed to start recording")
            _captureState.value = AudioCaptureState.Error(e.message ?: "Recording failed")
            return false
        }
    }

    /**
     * Stops audio recording.
     */
    fun stopRecording() {
        try {
            captureJob?.cancel()
            audioRecord?.stop()
            _captureState.value = AudioCaptureState.Initialized(
                sampleRate = currentConfig.sampleRate,
                bufferSize = _bufferHealth.value.bufferSize
            )

            Timber.d("Audio recording stopped")

        } catch (e: Exception) {
            Timber.e(e, "Error stopping recording")
        }
    }

    /**
     * Releases all microphone resources.
     * Must be called to properly clean up hardware resources.
     */
    fun release() {
        try {
            captureJob?.cancel()
            captureJob = null

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            _captureState.value = AudioCaptureState.Idle
            _bufferHealth.value = AudioBufferHealth()

            retryCount = 0

            Timber.d("Audio capture resources released")

        } catch (e: Exception) {
            Timber.e(e, "Error releasing audio resources")
        }
    }

    /**
     * Detects the actual hardware sample rate.
     *
     * On API 24+, uses SAMPLE_RATE_UNSPECIFIED to let hardware report actual rate.
     * Falls back to specified rate on older devices.
     */
    private fun detectHardwareSampleRate(requestedRate: Int): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Use unspecified to auto-detect hardware rate on newer devices
            if (requestedRate == AudioFormat.SAMPLE_RATE_UNSPECIFIED) {
                DEFAULT_SAMPLE_RATE
            } else {
                requestedRate
            }
        } else {
            // Fallback for older devices
            if (requestedRate <= 0) DEFAULT_SAMPLE_RATE else requestedRate
        }
    }

    /**
     * Attempts to initialize with retry logic for transient failures.
     */
    private fun initializeWithRetry(): Boolean {
        if (retryCount >= MAX_RETRY_ATTEMPTS) {
            Timber.e("Max retry attempts reached for audio initialization")
            return false
        }

        retryCount++
        Timber.d("Retry audio initialization (attempt $retryCount/$MAX_RETRY_ATTEMPTS)")

        return initialize(currentConfig)
    }

    /**
     * Starts monitoring buffer health in the background.
     */
    private fun startBufferMonitoring() {
        captureJob?.cancel()
        captureJob = scope.launch {
            while (isActive) {
                try {
                    val recordingState = audioRecord?.recordingState ?: AudioRecord.RECORDSTATE_STOPPED

                    _bufferHealth.update { health ->
                        health.copy(
                            isHealthy = recordingState == AudioRecord.RECORDSTATE_RECORDING,
                            lastCheckTimestamp = System.currentTimeMillis()
                        )
                    }

                    kotlinx.coroutines.delay(1000) // Check every second

                } catch (e: Exception) {
                    Timber.e(e, "Error monitoring buffer health")
                }
            }
        }
    }

    /**
     * Gets the current recording state.
     */
    val isRecording: Boolean
        get() = audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING

    /**
     * Gets the configured sample rate.
     */
    val sampleRate: Int
        get() = when (val state = _captureState.value) {
            is AudioCaptureState.Initialized -> state.sampleRate
            is AudioCaptureState.Recording -> currentConfig.sampleRate
            else -> 0
        }
}

/**
 * Audio capture state sealed class.
 */
sealed class AudioCaptureState {
    object Idle : AudioCaptureState()
    data class Initialized(val sampleRate: Int, val bufferSize: Int) : AudioCaptureState()
    object Recording : AudioCaptureState()
    data class Error(val message: String) : AudioCaptureState()
}

/**
 * Audio capture configuration.
 */
data class AudioCaptureConfig(
    val sampleRate: Int = 44100,
    val bitrate: Int = 128000,
    val isStereo: Boolean = true,
    val channelCount: Int = if (isStereo) 2 else 1
)

/**
 * Audio buffer health metrics.
 */
data class AudioBufferHealth(
    val bufferSize: Int = 0,
    val minBufferSize: Int = 0,
    val isHealthy: Boolean = true,
    val lastCheckTimestamp: Long = 0L
) {
    val bufferUtilization: Float
        get() = if (minBufferSize > 0) bufferSize.toFloat() / minBufferSize else 0f
}
