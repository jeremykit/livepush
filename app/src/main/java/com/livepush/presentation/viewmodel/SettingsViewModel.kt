package com.livepush.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livepush.domain.model.AudioConfig
import com.livepush.domain.model.StreamConfig
import com.livepush.domain.model.VideoCodec
import com.livepush.domain.model.VideoConfig
import com.livepush.domain.repository.SettingsRepository
import com.livepush.domain.repository.StreamHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val autoReconnect: Boolean = true,
    val maxReconnectAttempts: Int = 5,
    val connectionTimeout: Int = 10,
    val hardwareEncoder: Boolean = true,
    val noiseReduction: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val historyRepository: StreamHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val streamConfig: StateFlow<StreamConfig> = settingsRepository
        .getStreamConfig()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StreamConfig()
        )

    init {
        // Load network settings from DataStore
        viewModelScope.launch {
            val maxAttempts = settingsRepository.getMaxReconnectAttempts()
            val timeout = settingsRepository.getConnectionTimeout()
            _uiState.update {
                it.copy(
                    maxReconnectAttempts = maxAttempts,
                    connectionTimeout = timeout
                )
            }
        }
    }

    fun updateVideoConfig(videoConfig: VideoConfig) {
        viewModelScope.launch {
            val currentConfig = streamConfig.value
            settingsRepository.updateStreamConfig(
                currentConfig.copy(videoConfig = videoConfig)
            )
        }
    }

    fun updateAudioConfig(audioConfig: AudioConfig) {
        viewModelScope.launch {
            val currentConfig = streamConfig.value
            settingsRepository.updateStreamConfig(
                currentConfig.copy(audioConfig = audioConfig)
            )
        }
    }

    fun updateResolution(width: Int, height: Int) {
        viewModelScope.launch {
            val currentConfig = streamConfig.value
            settingsRepository.updateStreamConfig(
                currentConfig.copy(
                    videoConfig = currentConfig.videoConfig.copy(
                        width = width,
                        height = height
                    )
                )
            )
        }
    }

    fun updateFps(fps: Int) {
        viewModelScope.launch {
            val currentConfig = streamConfig.value
            settingsRepository.updateStreamConfig(
                currentConfig.copy(
                    videoConfig = currentConfig.videoConfig.copy(fps = fps)
                )
            )
        }
    }

    fun updateBitrate(bitrate: Int) {
        viewModelScope.launch {
            val currentConfig = streamConfig.value
            settingsRepository.updateStreamConfig(
                currentConfig.copy(
                    videoConfig = currentConfig.videoConfig.copy(bitrate = bitrate)
                )
            )
        }
    }

    fun updateCodec(codec: VideoCodec) {
        viewModelScope.launch {
            val currentConfig = streamConfig.value
            settingsRepository.updateStreamConfig(
                currentConfig.copy(
                    videoConfig = currentConfig.videoConfig.copy(codec = codec)
                )
            )
        }
    }

    fun updateKeyFrameInterval(interval: Int) {
        viewModelScope.launch {
            val currentConfig = streamConfig.value
            settingsRepository.updateStreamConfig(
                currentConfig.copy(
                    videoConfig = currentConfig.videoConfig.copy(keyFrameInterval = interval)
                )
            )
        }
    }

    fun updateSampleRate(sampleRate: Int) {
        viewModelScope.launch {
            val currentConfig = streamConfig.value
            settingsRepository.updateStreamConfig(
                currentConfig.copy(
                    audioConfig = currentConfig.audioConfig.copy(sampleRate = sampleRate)
                )
            )
        }
    }

    fun updateAudioBitrate(bitrate: Int) {
        viewModelScope.launch {
            val currentConfig = streamConfig.value
            settingsRepository.updateStreamConfig(
                currentConfig.copy(
                    audioConfig = currentConfig.audioConfig.copy(bitrate = bitrate)
                )
            )
        }
    }

    fun updateChannels(channels: Int) {
        viewModelScope.launch {
            val currentConfig = streamConfig.value
            settingsRepository.updateStreamConfig(
                currentConfig.copy(
                    audioConfig = currentConfig.audioConfig.copy(channelCount = channels)
                )
            )
        }
    }

    fun updateAutoReconnect(enabled: Boolean) {
        _uiState.update { it.copy(autoReconnect = enabled) }
    }

    fun updateMaxReconnectAttempts(attempts: Int) {
        viewModelScope.launch {
            settingsRepository.setMaxReconnectAttempts(attempts)
            _uiState.update { it.copy(maxReconnectAttempts = attempts) }
        }
    }

    fun updateConnectionTimeout(timeout: Int) {
        viewModelScope.launch {
            settingsRepository.setConnectionTimeout(timeout)
            _uiState.update { it.copy(connectionTimeout = timeout) }
        }
    }

    fun updateHardwareEncoder(enabled: Boolean) {
        _uiState.update { it.copy(hardwareEncoder = enabled) }
    }

    fun updateNoiseReduction(enabled: Boolean) {
        _uiState.update { it.copy(noiseReduction = enabled) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.deleteAllHistory()
        }
    }
}
