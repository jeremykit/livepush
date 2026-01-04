package com.livepush.presentation.viewmodel

import android.view.SurfaceView
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livepush.domain.model.StreamConfig
import com.livepush.domain.model.StreamState
import com.livepush.domain.model.StreamStats
import com.livepush.domain.repository.SettingsRepository
import com.livepush.domain.usecase.StreamManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StreamUiState(
    val isMuted: Boolean = false,
    val isFlashOn: Boolean = false,
    val isFrontCamera: Boolean = false,
    val showBeautyPanel: Boolean = false,
    val duration: Long = 0L,
    val formattedDuration: String = "00:00:00",
    val streamUrl: String = ""
)

@HiltViewModel
class StreamViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val streamManager: StreamManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(StreamUiState())
    val uiState: StateFlow<StreamUiState> = _uiState.asStateFlow()

    val streamState: StateFlow<StreamState> = streamManager.streamState

    val streamStats: StateFlow<StreamStats> = streamManager.streamStats

    val streamConfig: StateFlow<StreamConfig> = settingsRepository
        .getStreamConfig()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StreamConfig()
        )

    private var durationJob: Job? = null
    private var streamStartTime: Long = 0L
    private var currentSurfaceView: SurfaceView? = null

    init {
        // 监听推流状态变化
        viewModelScope.launch {
            streamManager.streamState.collect { state ->
                when (state) {
                    is StreamState.Streaming -> {
                        streamStartTime = state.startTime
                        startDurationTimer()
                    }
                    is StreamState.Previewing, is StreamState.Idle -> {
                        durationJob?.cancel()
                        _uiState.update { it.copy(duration = 0L, formattedDuration = "00:00:00") }
                    }
                    else -> {}
                }
            }
        }
    }

    fun setStreamUrl(url: String) {
        _uiState.update { it.copy(streamUrl = url) }
    }

    fun onSurfaceReady(surfaceView: SurfaceView) {
        currentSurfaceView = surfaceView
        startPreview()
    }

    fun onSurfaceDestroyed() {
        streamManager.stopPreview()
        currentSurfaceView = null
    }

    fun startPreview() {
        currentSurfaceView?.let { surface ->
            streamManager.startPreview(surface, streamConfig.value)
        }
    }

    fun startStream() {
        val url = _uiState.value.streamUrl
        if (url.isNotBlank()) {
            streamManager.startStream(url)
        }
    }

    fun stopStream() {
        durationJob?.cancel()
        streamManager.stopStream()
    }

    fun cancelReconnection() {
        streamManager.cancelReconnection()
    }

    fun toggleMute() {
        val newMuted = !_uiState.value.isMuted
        _uiState.update { it.copy(isMuted = newMuted) }
        streamManager.setMuted(newMuted)
    }

    fun toggleFlash() {
        val newFlash = !_uiState.value.isFlashOn
        _uiState.update { it.copy(isFlashOn = newFlash) }
        streamManager.enableTorch(newFlash)
    }

    fun switchCamera() {
        streamManager.switchCamera()
        _uiState.update { it.copy(isFrontCamera = streamManager.isFrontCamera) }
    }

    fun toggleBeautyPanel() {
        _uiState.update { it.copy(showBeautyPanel = !it.showBeautyPanel) }
    }

    private fun startDurationTimer() {
        durationJob?.cancel()
        durationJob = viewModelScope.launch {
            while (isActive) {
                val duration = System.currentTimeMillis() - streamStartTime
                _uiState.update {
                    it.copy(
                        duration = duration,
                        formattedDuration = formatDuration(duration)
                    )
                }
                delay(1000)
            }
        }
    }

    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = millis / (1000 * 60 * 60)
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun onCleared() {
        super.onCleared()
        durationJob?.cancel()
        streamManager.release()
    }
}
