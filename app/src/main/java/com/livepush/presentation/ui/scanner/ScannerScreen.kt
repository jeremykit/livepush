package com.livepush.presentation.ui.scanner

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livepush.R
import com.livepush.presentation.ui.components.QrScannerPreview
import com.livepush.presentation.ui.components.RequirePermissions
import com.livepush.presentation.ui.components.ScannerPermissions
import com.livepush.presentation.viewmodel.ScannerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onNavigateBack: () -> Unit,
    onScanResult: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 扫描成功震动反馈并返回
    LaunchedEffect(uiState.scanResult) {
        uiState.scanResult?.let { result ->
            // 震动反馈
            context.getSystemService<Vibrator>()?.let { vibrator ->
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator.vibrate(50)
                }
            }
            // 返回结果
            onScanResult(result)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_qr_code)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        RequirePermissions(
            permissions = ScannerPermissions,
            rationaleTitle = stringResource(R.string.permission_scanner_title),
            rationaleMessage = stringResource(R.string.permission_scanner_message)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(paddingValues)
            ) {
                // 相机预览
                QrScannerPreview(
                    modifier = Modifier.fillMaxSize(),
                    isFlashOn = uiState.isFlashOn,
                    onBarcodeDetected = { viewModel.onBarcodeDetected(it) }
                )

                // 扫描框覆盖层
                ScanOverlay(
                    modifier = Modifier.fillMaxSize()
                )

                // 底部控制
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.scan_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 闪光灯按钮
                    OutlinedButton(
                        onClick = { viewModel.toggleFlash() },
                        modifier = Modifier.fillMaxWidth(0.6f)
                    ) {
                        Icon(
                            imageVector = if (uiState.isFlashOn) Icons.Default.FlashOff else Icons.Default.FlashOn,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = if (uiState.isFlashOn) stringResource(R.string.flash_off)
                            else stringResource(R.string.flash_on)
                        )
                    }

                    // 从相册选择
                    TextButton(
                        onClick = { viewModel.onImageSelected("") },
                        modifier = Modifier.fillMaxWidth(0.6f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(stringResource(R.string.pick_from_gallery))
                    }
                }

                // 错误提示
                uiState.errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 100.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scanLinePosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanLine"
    )

    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // 扫描框大小
        val boxSize = minOf(canvasWidth, canvasHeight) * 0.7f
        val left = (canvasWidth - boxSize) / 2
        val top = (canvasHeight - boxSize) / 2

        // 半透明遮罩
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset.Zero,
            size = Size(canvasWidth, top)
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset(0f, top + boxSize),
            size = Size(canvasWidth, canvasHeight - top - boxSize)
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset(0f, top),
            size = Size(left, boxSize)
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset(left + boxSize, top),
            size = Size(canvasWidth - left - boxSize, boxSize)
        )

        // 扫描框边框
        val cornerLength = 40f
        val strokeWidth = 4f

        // 四个角
        // 左上
        drawLine(primaryColor, Offset(left, top), Offset(left + cornerLength, top), strokeWidth)
        drawLine(primaryColor, Offset(left, top), Offset(left, top + cornerLength), strokeWidth)
        // 右上
        drawLine(primaryColor, Offset(left + boxSize - cornerLength, top), Offset(left + boxSize, top), strokeWidth)
        drawLine(primaryColor, Offset(left + boxSize, top), Offset(left + boxSize, top + cornerLength), strokeWidth)
        // 左下
        drawLine(primaryColor, Offset(left, top + boxSize - cornerLength), Offset(left, top + boxSize), strokeWidth)
        drawLine(primaryColor, Offset(left, top + boxSize), Offset(left + cornerLength, top + boxSize), strokeWidth)
        // 右下
        drawLine(primaryColor, Offset(left + boxSize, top + boxSize - cornerLength), Offset(left + boxSize, top + boxSize), strokeWidth)
        drawLine(primaryColor, Offset(left + boxSize - cornerLength, top + boxSize), Offset(left + boxSize, top + boxSize), strokeWidth)

        // 扫描线
        val lineY = top + boxSize * scanLinePosition
        drawLine(
            color = primaryColor.copy(alpha = 0.8f),
            start = Offset(left + 10, lineY),
            end = Offset(left + boxSize - 10, lineY),
            strokeWidth = 2f
        )
    }
}
