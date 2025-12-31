package com.livepush.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livepush.domain.model.StreamHistory
import com.livepush.domain.model.StreamProtocol
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

data class HomeUiState(
    val streamUrl: String = "",
    val selectedProtocol: StreamProtocol = StreamProtocol.RTMP,
    val isUrlValid: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val historyRepository: StreamHistoryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val recentHistory: StateFlow<List<StreamHistory>> = historyRepository
        .getRecentHistory(10)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        loadLastUrl()
    }

    private fun loadLastUrl() {
        viewModelScope.launch {
            val lastUrl = settingsRepository.getLastStreamUrl()
            if (!lastUrl.isNullOrBlank()) {
                updateStreamUrl(lastUrl)
            }
        }
    }

    fun updateStreamUrl(url: String) {
        _uiState.update { state ->
            state.copy(
                streamUrl = url,
                isUrlValid = validateUrl(url),
                errorMessage = null
            )
        }
    }

    fun updateProtocol(protocol: StreamProtocol) {
        _uiState.update { state ->
            state.copy(
                selectedProtocol = protocol,
                isUrlValid = validateUrl(state.streamUrl)
            )
        }
    }

    fun onHistorySelected(history: StreamHistory) {
        _uiState.update { state ->
            state.copy(
                streamUrl = history.url,
                selectedProtocol = history.protocol,
                isUrlValid = true,
                errorMessage = null
            )
        }
    }

    fun onScanResult(result: String) {
        val detectedProtocol = detectProtocol(result)
        _uiState.update { state ->
            state.copy(
                streamUrl = result,
                selectedProtocol = detectedProtocol,
                isUrlValid = validateUrl(result),
                errorMessage = null
            )
        }
    }

    fun deleteHistory(id: Long) {
        viewModelScope.launch {
            historyRepository.deleteHistory(id)
        }
    }

    suspend fun saveAndNavigate(): Boolean {
        val state = _uiState.value
        if (!state.isUrlValid) {
            _uiState.update { it.copy(errorMessage = "请输入有效的推流地址") }
            return false
        }

        settingsRepository.setLastStreamUrl(state.streamUrl)
        historyRepository.addOrUpdateHistory(state.streamUrl, state.selectedProtocol)
        return true
    }

    private fun validateUrl(url: String): Boolean {
        if (url.isBlank()) return false

        val protocol = _uiState.value.selectedProtocol
        return when (protocol) {
            StreamProtocol.RTMP -> {
                url.startsWith("rtmp://") || url.startsWith("rtmps://")
            }
            StreamProtocol.WEBRTC -> {
                url.startsWith("ws://") || url.startsWith("wss://") || url.startsWith("http")
            }
        }
    }

    private fun detectProtocol(url: String): StreamProtocol {
        return when {
            url.startsWith("rtmp://") || url.startsWith("rtmps://") -> StreamProtocol.RTMP
            url.startsWith("ws://") || url.startsWith("wss://") -> StreamProtocol.WEBRTC
            else -> _uiState.value.selectedProtocol
        }
    }
}
