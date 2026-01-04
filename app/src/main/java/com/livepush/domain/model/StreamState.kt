package com.livepush.domain.model

sealed class StreamState {
    data object Idle : StreamState()
    data object Preparing : StreamState()
    data object Previewing : StreamState()
    data object Connecting : StreamState()
    data class Streaming(val startTime: Long = System.currentTimeMillis()) : StreamState()
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : StreamState()
    data class Error(val error: StreamError) : StreamState()
}

sealed class StreamError : Exception() {
    data class ConnectionFailed(override val message: String) : StreamError()
    data class ConnectionTimeout(val timeout: Long) : StreamError()
    data class ConnectionLost(val reason: String) : StreamError()
    data class EncoderNotSupported(val codec: String) : StreamError()
    data class EncoderConfigFailed(override val message: String) : StreamError()
    data object CameraNotAvailable : StreamError()
    data object MicrophoneNotAvailable : StreamError()
    data class PermissionDenied(val permission: String) : StreamError()
    data object NetworkUnavailable : StreamError()
    data class ServerError(val code: Int, override val message: String) : StreamError()
    data class SignalingFailed(override val message: String) : StreamError()
    data class IceFailed(val reason: String) : StreamError()
}

data class StreamStats(
    val videoBitrate: Long = 0,
    val audioBitrate: Long = 0,
    val fps: Float = 0f,
    val droppedFrames: Int = 0,
    val rtt: Long = 0,
    val duration: Long = 0,
    val bytesSent: Long = 0
)
