package com.livepush.streaming

import android.content.Context
import android.view.SurfaceView
import com.livepush.domain.model.StreamConfig
import com.livepush.domain.model.StreamError
import com.livepush.domain.model.StreamState
import com.livepush.domain.model.StreamStats
import com.livepush.domain.usecase.StreamManager
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.rtmp.RtmpCamera1
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RtmpStreamManager @Inject constructor(
    @ApplicationContext private val context: Context
) : StreamManager, ConnectChecker {

    private var rtmpCamera: RtmpCamera1? = null
    private var surfaceView: SurfaceView? = null
    private var currentConfig: StreamConfig = StreamConfig()

    private val _streamState = MutableStateFlow<StreamState>(StreamState.Idle)
    override val streamState: StateFlow<StreamState> = _streamState.asStateFlow()

    private val _streamStats = MutableStateFlow(StreamStats())
    override val streamStats: StateFlow<StreamStats> = _streamStats.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var statsJob: Job? = null
    private var reconnectionJob: Job? = null
    private var streamStartTime: Long = 0L
    private var streamUrl: String? = null
    private var reconnectionAttempt: Int = 0

    override val isFrontCamera: Boolean
        get() = rtmpCamera?.cameraFacing == CameraHelper.Facing.FRONT

    override val isTorchEnabled: Boolean
        get() = rtmpCamera?.isLanternEnabled == true

    override fun startPreview(surfaceView: SurfaceView, config: StreamConfig) {
        this.surfaceView = surfaceView
        this.currentConfig = config

        try {
            rtmpCamera?.stopPreview()
            rtmpCamera = RtmpCamera1(surfaceView, this)

            val videoConfig = config.videoConfig
            val audioConfig = config.audioConfig

            rtmpCamera?.prepareVideo(
                videoConfig.width,
                videoConfig.height,
                videoConfig.fps,
                videoConfig.bitrate,
                CameraHelper.getCameraOrientation(context)
            )

            rtmpCamera?.prepareAudio(
                audioConfig.bitrate,
                audioConfig.sampleRate,
                audioConfig.channelCount == 2
            )

            rtmpCamera?.startPreview()
            _streamState.value = StreamState.Previewing

            Timber.d("Preview started: ${videoConfig.width}x${videoConfig.height}@${videoConfig.fps}fps")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start preview")
            _streamState.value = StreamState.Error(
                StreamError.CameraNotAvailable
            )
        }
    }

    override fun stopPreview() {
        rtmpCamera?.stopPreview()
        _streamState.value = StreamState.Idle
    }

    override fun startStream(url: String) {
        if (rtmpCamera == null) {
            Timber.e("Camera not initialized")
            return
        }

        streamUrl = url
        _streamState.value = StreamState.Connecting

        try {
            rtmpCamera?.startStream(url)
            Timber.d("Starting stream to: $url")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start stream")
            _streamState.value = StreamState.Error(
                StreamError.ConnectionFailed(e.message ?: "Unknown error")
            )
        }
    }

    override fun stopStream() {
        reconnectionJob?.cancel()
        statsJob?.cancel()
        rtmpCamera?.stopStream()
        _streamState.value = StreamState.Previewing
        _streamStats.value = StreamStats()
        streamUrl = null
        reconnectionAttempt = 0
    }

    override fun switchCamera() {
        rtmpCamera?.switchCamera()
    }

    override fun enableTorch(enabled: Boolean) {
        try {
            if (enabled) {
                rtmpCamera?.enableLantern()
            } else {
                rtmpCamera?.disableLantern()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to toggle torch")
        }
    }

    override fun setMuted(muted: Boolean) {
        if (muted) {
            rtmpCamera?.disableAudio()
        } else {
            rtmpCamera?.enableAudio()
        }
    }

    override fun release() {
        reconnectionJob?.cancel()
        statsJob?.cancel()
        rtmpCamera?.stopStream()
        rtmpCamera?.stopPreview()
        rtmpCamera = null
        surfaceView = null
        streamUrl = null
        reconnectionAttempt = 0
    }

    // ConnectChecker callbacks
    override fun onConnectionStarted(url: String) {
        Timber.d("Connection started: $url")
    }

    override fun onConnectionSuccess() {
        Timber.d("Connection success")
        reconnectionJob?.cancel()
        reconnectionAttempt = 0
        streamStartTime = System.currentTimeMillis()
        _streamState.value = StreamState.Streaming(streamStartTime)
        startStatsCollection()
    }

    override fun onConnectionFailed(reason: String) {
        Timber.e("Connection failed: $reason")
        _streamState.value = StreamState.Error(
            StreamError.ConnectionFailed(reason)
        )
    }

    override fun onNewBitrate(bitrate: Long) {
        _streamStats.update { it.copy(videoBitrate = bitrate) }
    }

    override fun onDisconnect() {
        Timber.d("Disconnected")
        statsJob?.cancel()
        if (_streamState.value is StreamState.Streaming) {
            _streamState.value = StreamState.Error(
                StreamError.ConnectionLost("Connection lost")
            )
            startReconnection()
        }
    }

    override fun onAuthError() {
        Timber.e("Auth error")
        _streamState.value = StreamState.Error(
            StreamError.ConnectionFailed("Authentication failed")
        )
    }

    override fun onAuthSuccess() {
        Timber.d("Auth success")
    }

    private fun startStatsCollection() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                val duration = System.currentTimeMillis() - streamStartTime
                _streamStats.update {
                    it.copy(
                        duration = duration,
                        fps = 30f, // Default FPS
                        droppedFrames = 0
                    )
                }
                delay(1000)
            }
        }
    }

    private fun startReconnection() {
        val url = streamUrl
        if (url == null) {
            Timber.e("Cannot reconnect: stream URL is null")
            return
        }

        reconnectionJob?.cancel()
        reconnectionJob = scope.launch {
            while (isActive && reconnectionAttempt < MAX_RECONNECTION_ATTEMPTS) {
                reconnectionAttempt++
                val delaySeconds = calculateBackoffDelay(reconnectionAttempt)

                Timber.d("Reconnection attempt $reconnectionAttempt/${MAX_RECONNECTION_ATTEMPTS} in ${delaySeconds}s")
                _streamState.value = StreamState.Reconnecting(reconnectionAttempt, delaySeconds)

                delay(delaySeconds * 1000L)

                if (!isActive) break

                Timber.d("Attempting to reconnect to: $url")
                _streamState.value = StreamState.Connecting

                try {
                    rtmpCamera?.stopStream()
                    delay(500) // Brief pause before reconnecting
                    rtmpCamera?.startStream(url)
                } catch (e: Exception) {
                    Timber.e(e, "Reconnection attempt failed")
                    if (reconnectionAttempt >= MAX_RECONNECTION_ATTEMPTS) {
                        _streamState.value = StreamState.Error(
                            StreamError.ConnectionFailed("Max reconnection attempts reached")
                        )
                    }
                }
            }
        }
    }

    private fun calculateBackoffDelay(attempt: Int): Int {
        // Exponential backoff: 2s, 4s, 8s, 16s, 30s (max)
        val delay = minOf(BASE_RECONNECTION_DELAY * (1 shl (attempt - 1)), MAX_RECONNECTION_DELAY)
        return delay
    }

    companion object {
        private const val BASE_RECONNECTION_DELAY = 2 // seconds
        private const val MAX_RECONNECTION_DELAY = 30 // seconds
        private const val MAX_RECONNECTION_ATTEMPTS = 10
    }
}
