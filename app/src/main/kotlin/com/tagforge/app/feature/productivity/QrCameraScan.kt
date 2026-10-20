package com.tagforge.app.feature.productivity

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tagforge.app.R
import java.util.concurrent.Executors

private const val ANALYSIS_WIDTH = 1280
private const val ANALYSIS_HEIGHT = 720

/**
 * The live camera with a square viewfinder, a torch button and a one-line instruction. The camera runs only while this is on
 * screen and is released when it leaves; [onScanned] receives the first QR text found.
 */
@Composable
fun QrCameraScan(onScanned: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(onScanned)
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }

    LaunchedEffect(previewView) {
        try {
            val cameraProvider = ProcessCameraProvider.awaitInstance(context)
            provider = cameraProvider
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            // 720p gives a small or distant code enough pixels; the default 480p often loses dense codes.
            val resolution = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(android.util.Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build()
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    val analyzer = QrFrameAnalyzer { text -> ContextCompat.getMainExecutor(context).execute { latest(text) } }
                    analysis.setAnalyzer(analysisExecutor, analyzer)
                }
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        } catch (_: Exception) {
            cameraError = true
        }
    }
    DisposableEffect(previewView) {
        onDispose {
            analysisExecutor.shutdown()
            provider?.unbindAll()
        }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Viewfinder(Modifier.align(Alignment.Center))
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(if (cameraError) R.string.qr_camera_error else R.string.qr_camera_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                FilledTonalIconButton(
                    onClick = {
                        torchOn = !torchOn
                        camera?.cameraControl?.enableTorch(torchOn)
                    },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        if (torchOn) Icons.Outlined.FlashOff else Icons.Outlined.FlashOn,
                        contentDescription = stringResource(if (torchOn) R.string.qr_torch_off else R.string.qr_torch_on),
                    )
                }
            }
        }
    }
}

@Composable
private fun Viewfinder(modifier: Modifier = Modifier) {
    Canvas(modifier.size(260.dp)) {
        drawRoundRect(
            color = Color.White,
            topLeft = Offset.Zero,
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(24.dp.toPx()),
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}
