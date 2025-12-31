package com.livepush.domain.model

data class StreamConfig(
    val videoConfig: VideoConfig = VideoConfig(),
    val audioConfig: AudioConfig = AudioConfig(),
    val protocol: StreamProtocol = StreamProtocol.RTMP
)

data class VideoConfig(
    val width: Int = 1280,
    val height: Int = 720,
    val fps: Int = 30,
    val bitrate: Int = 2_000_000,
    val codec: String = "H.264",
    val keyFrameInterval: Int = 2
)

data class AudioConfig(
    val sampleRate: Int = 44100,
    val channelCount: Int = 2,
    val bitrate: Int = 128_000,
    val codec: String = "AAC"
)

enum class StreamProtocol {
    RTMP,
    WEBRTC
}

enum class VideoCodec(val displayName: String) {
    H264("H.264"),
    H265("H.265")
}

enum class AudioCodec(val displayName: String) {
    AAC("AAC"),
    OPUS("Opus")
}
