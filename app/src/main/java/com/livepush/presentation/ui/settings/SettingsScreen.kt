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
    var showSampleRateDialog by remember { mutableStateOf(false) }
    var showAudioBitrateDialog by remember { mutableStateOf(false) }
    var showChannelsDialog by remember { mutableStateOf(false) }
    var showReconnectAttemptsDialog by remember { mutableStateOf(false) }
    var showConnectionTimeoutDialog by remember { mutableStateOf(false) }
    var showKeyframeIntervalDialog by remember { mutableStateOf(false) }

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

    // Sample rate selection dialog
    if (showSampleRateDialog) {
        SampleRateDialog(
            currentSampleRate = streamConfig.audioConfig.sampleRate,
            onDismiss = { showSampleRateDialog = false },
            onConfirm = { sampleRate ->
                viewModel.updateSampleRate(sampleRate)
                showSampleRateDialog = false
            }
        )
    }

    // Audio bitrate selection dialog
    if (showAudioBitrateDialog) {
        AudioBitrateDialog(
            currentBitrate = streamConfig.audioConfig.bitrate,
            onDismiss = { showAudioBitrateDialog = false },
            onConfirm = { bitrate ->
                viewModel.updateAudioBitrate(bitrate)
                showAudioBitrateDialog = false
            }
        )
    }

    // Channels selection dialog
    if (showChannelsDialog) {
        ChannelsDialog(
            currentChannelCount = streamConfig.audioConfig.channelCount,
            onDismiss = { showChannelsDialog = false },
            onConfirm = { channels ->
                viewModel.updateChannels(channels)
                showChannelsDialog = false
            }
        )
    }

    // Reconnect attempts selection dialog
    if (showReconnectAttemptsDialog) {
        ReconnectAttemptsDialog(
            currentAttempts = uiState.maxReconnectAttempts,
            onDismiss = { showReconnectAttemptsDialog = false },
            onConfirm = { attempts ->
                viewModel.updateMaxReconnectAttempts(attempts)
                showReconnectAttemptsDialog = false
            }
        )
    }

    // Connection timeout selection dialog
    if (showConnectionTimeoutDialog) {
        ConnectionTimeoutDialog(
            currentTimeout = uiState.connectionTimeout,
            onDismiss = { showConnectionTimeoutDialog = false },
            onConfirm = { timeout ->
                viewModel.updateConnectionTimeout(timeout)
                showConnectionTimeoutDialog = false
            }
        )
    }

    // Keyframe interval selection dialog
    if (showKeyframeIntervalDialog) {
        KeyframeIntervalDialog(
            currentInterval = streamConfig.videoConfig.keyFrameInterval,
            onDismiss = { showKeyframeIntervalDialog = false },
            onConfirm = { interval ->
                viewModel.updateKeyFrameInterval(interval)
                showKeyframeIntervalDialog = false
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
                    onClick = { showSampleRateDialog = true }
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.audio_bitrate),
                    value = formatBitrate(streamConfig.audioConfig.bitrate),
                    onClick = { showAudioBitrateDialog = true }
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.channels),
                    value = if (streamConfig.audioConfig.channelCount == 2)
                        stringResource(R.string.stereo) else stringResource(R.string.mono),
                    onClick = { showChannelsDialog = true }
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
                    onClick = { showReconnectAttemptsDialog = true }
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.connection_timeout),
                    value = "${uiState.connectionTimeout}s",
                    onClick = { showConnectionTimeoutDialog = true }
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

@Composable
private fun SampleRateDialog(
    currentSampleRate: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val sampleRates = listOf(
        22050 to "22050 Hz",
        44100 to "44100 Hz",
        48000 to "48000 Hz"
    )

    var selectedSampleRate by remember { mutableStateOf(currentSampleRate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_sample_rate)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                sampleRates.forEach { (sampleRate, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (sampleRate == selectedSampleRate),
                                onClick = { selectedSampleRate = sampleRate },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (sampleRate == selectedSampleRate),
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
                onClick = { onConfirm(selectedSampleRate) }
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
private fun AudioBitrateDialog(
    currentBitrate: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val bitrates = listOf(
        64_000 to "64 Kbps",
        96_000 to "96 Kbps",
        128_000 to "128 Kbps",
        192_000 to "192 Kbps",
        256_000 to "256 Kbps",
        320_000 to "320 Kbps"
    )

    var selectedBitrate by remember { mutableStateOf(currentBitrate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_audio_bitrate)) },
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
private fun ChannelsDialog(
    currentChannelCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val channels = listOf(
        1 to stringResource(R.string.mono),
        2 to stringResource(R.string.stereo)
    )

    var selectedChannelCount by remember { mutableStateOf(currentChannelCount) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_channels)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                channels.forEach { (count, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (count == selectedChannelCount),
                                onClick = { selectedChannelCount = count },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (count == selectedChannelCount),
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
                onClick = { onConfirm(selectedChannelCount) }
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
private fun ReconnectAttemptsDialog(
    currentAttempts: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val attemptOptions = listOf(
        1 to "1",
        3 to "3",
        5 to "5",
        10 to "10",
        -1 to stringResource(R.string.unlimited)
    )

    var selectedAttempts by remember { mutableStateOf(currentAttempts) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_reconnect_attempts)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                attemptOptions.forEach { (attempts, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (attempts == selectedAttempts),
                                onClick = { selectedAttempts = attempts },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (attempts == selectedAttempts),
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
                onClick = { onConfirm(selectedAttempts) }
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
private fun ConnectionTimeoutDialog(
    currentTimeout: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val timeoutOptions = listOf(
        5 to "5s",
        10 to "10s",
        15 to "15s",
        30 to "30s",
        60 to "60s"
    )

    var selectedTimeout by remember { mutableStateOf(currentTimeout) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_connection_timeout)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                timeoutOptions.forEach { (timeout, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (timeout == selectedTimeout),
                                onClick = { selectedTimeout = timeout },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (timeout == selectedTimeout),
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
                onClick = { onConfirm(selectedTimeout) }
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
