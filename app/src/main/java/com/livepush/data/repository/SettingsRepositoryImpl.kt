package com.livepush.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.livepush.domain.model.AudioCodec
import com.livepush.domain.model.AudioConfig
import com.livepush.domain.model.ReconnectionConfig
import com.livepush.domain.model.StreamConfig
import com.livepush.domain.model.StreamProtocol
import com.livepush.domain.model.VideoCodec
import com.livepush.domain.model.VideoConfig
import com.livepush.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    companion object {
        // Video Settings
        val VIDEO_WIDTH = intPreferencesKey("video_width")
        val VIDEO_HEIGHT = intPreferencesKey("video_height")
        val VIDEO_FPS = intPreferencesKey("video_fps")
        val VIDEO_BITRATE = intPreferencesKey("video_bitrate")
        val VIDEO_CODEC = stringPreferencesKey("video_codec")
        val KEYFRAME_INTERVAL = intPreferencesKey("keyframe_interval")

        // Audio Settings
        val AUDIO_SAMPLE_RATE = intPreferencesKey("audio_sample_rate")
        val AUDIO_CHANNEL_COUNT = intPreferencesKey("audio_channel_count")
        val AUDIO_BITRATE = intPreferencesKey("audio_bitrate")
        val AUDIO_CODEC = stringPreferencesKey("audio_codec")

        // Protocol
        val STREAM_PROTOCOL = stringPreferencesKey("stream_protocol")

        // Reconnection Settings
        val RECONNECTION_MAX_RETRIES = intPreferencesKey("reconnection_max_retries")
        val RECONNECTION_INITIAL_DELAY_MS = intPreferencesKey("reconnection_initial_delay_ms")

        // Last URL
        val LAST_STREAM_URL = stringPreferencesKey("last_stream_url")
    }

    override fun getStreamConfig(): Flow<StreamConfig> {
        return dataStore.data.map { prefs ->
            StreamConfig(
                videoConfig = VideoConfig(
                    width = prefs[VIDEO_WIDTH] ?: 1280,
                    height = prefs[VIDEO_HEIGHT] ?: 720,
                    fps = prefs[VIDEO_FPS] ?: 30,
                    bitrate = prefs[VIDEO_BITRATE] ?: 2_000_000,
                    codec = prefs[VIDEO_CODEC]?.let { VideoCodec.valueOf(it) } ?: VideoCodec.H264,
                    keyFrameInterval = prefs[KEYFRAME_INTERVAL] ?: 2
                ),
                audioConfig = AudioConfig(
                    sampleRate = prefs[AUDIO_SAMPLE_RATE] ?: 44100,
                    channelCount = prefs[AUDIO_CHANNEL_COUNT] ?: 2,
                    bitrate = prefs[AUDIO_BITRATE] ?: 128_000,
                    codec = prefs[AUDIO_CODEC]?.let { AudioCodec.valueOf(it) } ?: AudioCodec.AAC
                ),
                protocol = prefs[STREAM_PROTOCOL]?.let { StreamProtocol.valueOf(it) } ?: StreamProtocol.RTMP
            )
        }
    }

    override suspend fun updateStreamConfig(config: StreamConfig) {
        dataStore.edit { prefs ->
            // Video
            prefs[VIDEO_WIDTH] = config.videoConfig.width
            prefs[VIDEO_HEIGHT] = config.videoConfig.height
            prefs[VIDEO_FPS] = config.videoConfig.fps
            prefs[VIDEO_BITRATE] = config.videoConfig.bitrate
            prefs[VIDEO_CODEC] = config.videoConfig.codec.name
            prefs[KEYFRAME_INTERVAL] = config.videoConfig.keyFrameInterval

            // Audio
            prefs[AUDIO_SAMPLE_RATE] = config.audioConfig.sampleRate
            prefs[AUDIO_CHANNEL_COUNT] = config.audioConfig.channelCount
            prefs[AUDIO_BITRATE] = config.audioConfig.bitrate
            prefs[AUDIO_CODEC] = config.audioConfig.codec.name

            // Protocol
            prefs[STREAM_PROTOCOL] = config.protocol.name
        }
    }

    override suspend fun getLastStreamUrl(): String? {
        return dataStore.data.first()[LAST_STREAM_URL]
    }

    override suspend fun setLastStreamUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[LAST_STREAM_URL] = url
        }
    }
}
