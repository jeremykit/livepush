package com.livepush.presentation.ui.components

import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun StreamCameraPreview(
    modifier: Modifier = Modifier,
    onSurfaceReady: (SurfaceView) -> Unit,
    onSurfaceDestroyed: () -> Unit = {}
) {
    val context = LocalContext.current

    val surfaceView = remember {
        SurfaceView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    DisposableEffect(surfaceView) {
        onSurfaceReady(surfaceView)
        onDispose {
            onSurfaceDestroyed()
        }
    }

    AndroidView(
        factory = { surfaceView },
        modifier = modifier
    )
}
