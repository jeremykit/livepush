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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livepush.R
import com.livepush.domain.model.VideoCodec
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

    var showResolutionDialog by remember { mutableStateOf(false) }
    var showFrameRateDialog by remember { mutableStateOf(false) }
    var showBitrateDialog by remember { mutableStateOf(false) }
    var showEncoderDialog by remember { mutableStateOf(false) }

    // Resolution selection dialog
    if (showResolutionDialog) {
        ResolutionDialog(
            currentWidth = streamConfig.videoConfig.width,
            currentHeight = streamConfig.videoConfig.height,
            onDismiss = { showResolutionDialog = false },
            onConfirm = { width, height ->
                viewModel.updateResolution(width, height)
                showResolutionDialog = false
            }
        )
    }

    // Frame rate selection dialog
    if (showFrameRateDialog) {
        FrameRateDialog(
            currentFps = streamConfig.videoConfig.fps,
            onDismiss = { showFrameRateDialog = false },
            onConfirm = { fps ->
                viewModel.updateFps(fps)
                showFrameRateDialog = false
            }
        )
    }

    // Bitrate selection dialog
    if (showBitrateDialog) {
        BitrateDialog(
            currentBitrate = streamConfig.videoConfig.bitrate,
            onDismiss = { showBitrateDialog = false },
            onConfirm = { bitrate ->
                viewModel.updateBitrate(bitrate)
                showBitrateDialog = false
            }
        )
    }

    // Encoder selection dialog
    if (showEncoderDialog) {
        EncoderDialog(
            currentCodec = streamConfig.videoConfig.codec,
            onDismiss = { showEncoderDialog = false },
            onConfirm = { codec ->
                viewModel.updateCodec(codec)
                showEncoderDialog = false
            }
        )
    }

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
                    onClick = { showResolutionDialog = true }
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.frame_rate),
                    value = "${streamConfig.videoConfig.fps} fps",
                    onClick = { showFrameRateDialog = true }
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.video_bitrate),
                    value = formatBitrate(streamConfig.videoConfig.bitrate),
                    onClick = { showBitrateDialog = true }
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.encoder),
                    value = streamConfig.videoConfig.codec.displayName,
                    onClick = { showEncoderDialog = true }
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

@Composable
private fun ResolutionDialog(
    currentWidth: Int,
    currentHeight: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val resolutions = listOf(
        Pair(640, 360) to "640 × 360 (360p)",
        Pair(854, 480) to "854 × 480 (480p)",
        Pair(1280, 720) to "1280 × 720 (720p)",
        Pair(1920, 1080) to "1920 × 1080 (1080p)"
    )

    var selectedResolution by remember {
        mutableStateOf(Pair(currentWidth, currentHeight))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_resolution)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                resolutions.forEach { (resolution, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (resolution == selectedResolution),
                                onClick = { selectedResolution = resolution },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (resolution == selectedResolution),
                            onClick = null
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(selectedResolution.first, selectedResolution.second)
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun FrameRateDialog(
    currentFps: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val frameRates = listOf(
        15 to "15 fps",
        24 to "24 fps",
        30 to "30 fps",
        60 to "60 fps"
    )

    var selectedFps by remember { mutableStateOf(currentFps) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_frame_rate)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                frameRates.forEach { (fps, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (fps == selectedFps),
                                onClick = { selectedFps = fps },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (fps == selectedFps),
                            onClick = null
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedFps) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun BitrateDialog(
    currentBitrate: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val bitrates = listOf(
        500_000 to "500 Kbps",
        1_000_000 to "1 Mbps",
        2_000_000 to "2 Mbps",
        4_000_000 to "4 Mbps",
        6_000_000 to "6 Mbps",
        8_000_000 to "8 Mbps"
    )

    var selectedBitrate by remember { mutableStateOf(currentBitrate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_bitrate)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                bitrates.forEach { (bitrate, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (bitrate == selectedBitrate),
                                onClick = { selectedBitrate = bitrate },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (bitrate == selectedBitrate),
                            onClick = null
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedBitrate) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun EncoderDialog(
    currentCodec: VideoCodec,
    onDismiss: () -> Unit,
    onConfirm: (VideoCodec) -> Unit
) {
    val codecs = VideoCodec.entries

    var selectedCodec by remember { mutableStateOf(currentCodec) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_encoder)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                codecs.forEach { codec ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (codec == selectedCodec),
                                onClick = { selectedCodec = codec },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (codec == selectedCodec),
                            onClick = null
                        )
                        Text(
                            text = codec.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedCodec) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
