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
    val streamConfirmationEnabled by viewModel.streamConfirmationEnabled.collectAsStateWithLifecycle()

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
    var showLicensesDialog by remember { mutableStateOf(false) }

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

    // Open source licenses dialog
    if (showLicensesDialog) {
        LicensesDialog(
            onDismiss = { showLicensesDialog = false }
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
                    onClick = { showKeyframeIntervalDialog = true }
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
                    onClick = { showLicensesDialog = true }
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