package com.livepush.presentation.ui.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livepush.R
import com.livepush.domain.model.StreamState
import com.livepush.presentation.ui.components.CameraPermissions
import com.livepush.presentation.ui.components.RequirePermissions
import com.livepush.presentation.ui.components.StreamCameraPreview
import com.livepush.presentation.ui.theme.LiveRed
import com.livepush.presentation.viewmodel.StreamViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamScreen(
    streamUrl: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StreamViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val streamState by viewModel.streamState.collectAsStateWithLifecycle()
    val streamStats by viewModel.streamStats.collectAsStateWithLifecycle()

    var showStopDialog by remember { mutableStateOf(false) }

    val isStreaming = streamState is StreamState.Streaming
    val isConnecting = streamState is StreamState.Connecting

    // 设置推流地址
    LaunchedEffect(streamUrl) {
        viewModel.setStreamUrl(streamUrl)
    }

    // 停止确认对话框
    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text(stringResource(R.string.confirm)) },
            text = { Text(stringResource(R.string.stop_stream_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.stopStream()
                        showStopDialog = false
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(uiState.formattedDuration)
                        if (isStreaming) {
                            Surface(
                                color = LiveRed,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = stringResource(R.string.live),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isStreaming) {
                            showStopDialog = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        RequirePermissions(
            permissions = CameraPermissions,
            rationaleTitle = stringResource(R.string.permission_stream_title),
            rationaleMessage = stringResource(R.string.permission_stream_message)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(paddingValues)
            ) {
                // 相机预览
                StreamCameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onSurfaceReady = { viewModel.onSurfaceReady(it) },
                    onSurfaceDestroyed = { viewModel.onSurfaceDestroyed() }
                )

                // 统计信息栏
                if (isStreaming) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 100.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(
                                label = stringResource(R.string.bitrate),
                                value = formatBitrate(streamStats.videoBitrate)
                            )
                            StatItem(
                                label = stringResource(R.string.fps),
                                value = String.format("%.1f", streamStats.fps)
                            )
                            StatItem(
                                label = stringResource(R.string.latency),
                                value = "${streamStats.rtt}ms"
                            )
                        }
                    }
                }

                // 控制栏
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ControlButton(
                            icon = Icons.Default.Cameraswitch,
                            label = stringResource(R.string.switch_camera),
                            onClick = { viewModel.switchCamera() }
                        )
                        ControlButton(
                            icon = if (uiState.isFlashOn) Icons.Default.FlashOff else Icons.Default.FlashOn,
                            label = stringResource(R.string.flash),
                            isActive = uiState.isFlashOn,
                            onClick = { viewModel.toggleFlash() }
                        )
                        ControlButton(
                            icon = if (uiState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            label = stringResource(R.string.mute),
                            isActive = uiState.isMuted,
                            onClick = { viewModel.toggleMute() }
                        )
                        ControlButton(
                            icon = Icons.Default.AutoAwesome,
                            label = stringResource(R.string.beauty),
                            isActive = uiState.showBeautyPanel,
                            onClick = { viewModel.toggleBeautyPanel() }
                        )
                        FilledIconButton(
                            onClick = {
                                if (isStreaming) {
                                    showStopDialog = true
                                } else {
                                    viewModel.startStream()
                                }
                            },
                            modifier = Modifier.size(56.dp),
                            enabled = !isConnecting,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (isStreaming) LiveRed else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = if (isStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.stop),
                                tint = Color.White
                            )
                        }
                    }
                }

                // 错误状态显示
                if (streamState is StreamState.Error) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                            .padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = (streamState as StreamState.Error).error.message ?: stringResource(R.string.stream_error),
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

private fun formatBitrate(bitrate: Long): String {
    return when {
        bitrate >= 1_000_000 -> String.format("%.1f Mbps", bitrate / 1_000_000.0)
        bitrate >= 1_000 -> String.format("%.0f Kbps", bitrate / 1_000.0)
        else -> "$bitrate bps"
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = if (isActive) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
