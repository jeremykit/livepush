package com.livepush.domain.usecase

import com.livepush.domain.model.StreamConfig
import com.livepush.domain.model.StreamState
import com.livepush.domain.model.StreamStats
import kotlinx.coroutines.flow.StateFlow

interface StreamManager {
    val streamState: StateFlow<StreamState>
    val streamStats: StateFlow<StreamStats>

    fun startPreview(
        surfaceView: android.view.SurfaceView,
        config: StreamConfig
    )

    fun stopPreview()

    fun startStream(url: String)

    fun stopStream()

    fun switchCamera()

    fun enableTorch(enabled: Boolean)

    fun setMuted(muted: Boolean)

    fun release()

    val isFrontCamera: Boolean

    val isTorchEnabled: Boolean
}
