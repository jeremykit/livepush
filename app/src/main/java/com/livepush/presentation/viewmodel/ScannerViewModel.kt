package com.livepush.presentation.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ScannerUiState(
    val isFlashOn: Boolean = false,
    val isScanning: Boolean = true,
    val scanResult: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ScannerViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    fun toggleFlash() {
        _uiState.update { it.copy(isFlashOn = !it.isFlashOn) }
    }

    fun onBarcodeDetected(barcode: String) {
        if (_uiState.value.scanResult != null) return // 防止重复扫描

        if (isValidStreamUrl(barcode)) {
            _uiState.update {
                it.copy(
                    isScanning = false,
                    scanResult = barcode,
                    errorMessage = null
                )
            }
        } else {
            _uiState.update {
                it.copy(errorMessage = "无效的推流地址")
            }
        }
    }

    fun onImageSelected(uri: String) {
        // TODO: 从图片中解析二维码
    }

    fun resetScan() {
        _uiState.update {
            ScannerUiState()
        }
    }

    private fun isValidStreamUrl(url: String): Boolean {
        return url.startsWith("rtmp://") ||
                url.startsWith("rtmps://") ||
                url.startsWith("ws://") ||
                url.startsWith("wss://") ||
                url.startsWith("http://") ||
                url.startsWith("https://")
    }
}
