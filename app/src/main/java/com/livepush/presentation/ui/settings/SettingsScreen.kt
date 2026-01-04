package com.livepush.presentation.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livepush.R
import com.livepush.presentation.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val streamConfig by viewModel.streamConfig.collectAsStateWithLifecycle()
    val streamConfirmationEnabled by viewModel.streamConfirmationEnabled.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Video Settings
            item {
                SettingsGroupHeader(title = stringResource(R.string.video_settings))
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.resolution),
                    value = "${streamConfig.videoConfig.width} × ${streamConfig.videoConfig.height}",
                    onClick = { /* TODO: 显示分辨率选择对话框 */ }
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.frame_rate),
                    value = "${streamConfig.videoConfig.fps} fps",
                    onClick = { /* TODO: 显示帧率选择对话框 */ }
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.video_bitrate),
                    value = formatBitrate(streamConfig.videoConfig.bitrate),
                    onClick = { /* TODO: 显示码率选择对话框 */ }
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.encoder),
                    value = streamConfig.videoConfig.codec.displayName,
                    onClick = { /* TODO: 显示编码器选择对话框 */ }
                )
            }

            // Audio Settings
            item {
                SettingsGroupHeader(title = stringResource(R.string.audio_settings))
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.sample_rate),
                    value = "${streamConfig.audioConfig.sampleRate} Hz",
                    onClick = { /* TODO: 显示采样率选择对话框 */ }
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.audio_bitrate),
                    value = formatBitrate(streamConfig.audioConfig.bitrate),
                    onClick = { /* TODO: 显示码率选择对话框 */ }
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.channels),
                    value = if (streamConfig.audioConfig.channelCount == 2)
                        stringResource(R.string.stereo) else stringResource(R.string.mono),
                    onClick = { /* TODO: 显示声道选择对话框 */ }
                )
            }

            // Network Settings
            item {
                SettingsGroupHeader(title = stringResource(R.string.network_settings))
            }
            item {
                SettingsSwitchItem(
                    title = stringResource(R.string.auto_reconnect),
                    checked = uiState.autoReconnect,
                    onCheckedChange = { viewModel.updateAutoReconnect(it) }
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.max_reconnect_attempts),
                    value = "${uiState.maxReconnectAttempts}",
                    onClick = { /* TODO: 显示重连次数选择对话框 */ }
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.connection_timeout),
                    value = "${uiState.connectionTimeout}s",
                    onClick = { /* TODO: 显示超时设置对话框 */ }
                )
            }

            // Advanced Settings
            item {
                SettingsGroupHeader(title = stringResource(R.string.advanced_settings))
            }
            item {
                SettingsSwitchItem(
                    title = stringResource(R.string.hardware_encoder),
                    checked = uiState.hardwareEncoder,
                    onCheckedChange = { viewModel.updateHardwareEncoder(it) }
                )
            }
            item {
                SettingsSwitchItem(
                    title = stringResource(R.string.noise_reduction),
                    checked = uiState.noiseReduction,
                    onCheckedChange = { viewModel.updateNoiseReduction(it) }
                )
            }
            item {
                SettingsSwitchItem(
                    title = stringResource(R.string.show_confirmations),
                    checked = streamConfirmationEnabled,
                    onCheckedChange = { viewModel.updateStreamConfirmationEnabled(it) }
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.keyframe_interval),
                    value = "${streamConfig.videoConfig.keyFrameInterval}s",
                    onClick = { /* TODO: 显示关键帧间隔设置对话框 */ }
                )
            }

            // About
            item {
                SettingsGroupHeader(title = stringResource(R.string.about))
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.version),
                    value = "1.0.0",
                    onClick = { }
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.open_source_licenses),
                    onClick = { /* TODO: 显示开源许可 */ }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun formatBitrate(bitrate: Int): String {
    return when {
        bitrate >= 1_000_000 -> String.format("%.1f Mbps", bitrate / 1_000_000.0)
        bitrate >= 1_000 -> String.format("%.0f Kbps", bitrate / 1_000.0)
        else -> "$bitrate bps"
    }
}

@Composable
private fun SettingsGroupHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SettingsItem(
    title: String,
    value: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Row {
                if (value != null) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        modifier = modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        modifier = modifier
    )
}
