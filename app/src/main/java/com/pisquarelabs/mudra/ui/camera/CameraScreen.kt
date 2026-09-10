package com.pisquarelabs.mudra.ui.camera

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.pisquarelabs.mudra.core.model.HandFrame
import com.pisquarelabs.mudra.core.vision.HandLandmarkerAnalyzer
import java.util.concurrent.Executors

/**
 * Camera preview + the first pipeline stage (MediaPipe hand landmarks) running live on
 * every analyzed frame. Emits raw [HandFrame]s upward via [onHandFrames]; has no idea what
 * happens to them next.
 */
@Composable
fun CameraScreen(
    modifier: Modifier = Modifier,
    createAnalyzer: (onResult: (List<HandFrame>, Int, Int) -> Unit) -> HandLandmarkerAnalyzer,
    onHandFrames: (List<HandFrame>) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val analyzerRef = remember { mutableStateOf<HandLandmarkerAnalyzer?>(null) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analyzer = createAnalyzer { frames, _, _ -> onHandFrames(frames) }
                analyzerRef.value = analyzer
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(480, 640))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, analyzer) }

                runCatching {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        imageAnalysis
                    )
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            analyzerRef.value?.close()
            analysisExecutor.shutdown()
        }
    }
}
