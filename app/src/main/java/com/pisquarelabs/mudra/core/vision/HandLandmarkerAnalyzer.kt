package com.pisquarelabs.mudra.core.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.pisquarelabs.mudra.core.model.HandFrame
import com.pisquarelabs.mudra.core.model.HandLandmark
import java.io.ByteArrayOutputStream

private const val TAG = "HandLandmarkerAnalyzer"
private const val MODEL_ASSET_PATH = "hand_landmarker.task"

/**
 * Wraps MediaPipe's HandLandmarker (LIVE_STREAM mode) behind a CameraX [ImageAnalysis.Analyzer].
 *
 * This is the first stage of the pipeline described in the architecture doc:
 *   Camera -> MediaPipe Hand Landmarker -> Hand landmarks -> Custom sign model -> ...
 *
 * It never touches language reasoning; it only produces raw per-hand landmark coordinates.
 */
class HandLandmarkerAnalyzer(
    context: Context,
    private val onResult: (frames: List<HandFrame>, inputWidth: Int, inputHeight: Int) -> Unit,
    private val onError: (Throwable) -> Unit = { Log.e(TAG, "HandLandmarker error", it) }
) : ImageAnalysis.Analyzer {

    private var lastInputWidth = 0
    private var lastInputHeight = 0

    private val handLandmarker: HandLandmarker = HandLandmarker.createFromOptions(
        context,
        HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET_PATH)
                    .build()
            )
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener(::handleResult)
            .setErrorListener(onError)
            .build()
    )

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val bitmap = imageProxy.toBitmap().rotate(rotationDegrees)
            lastInputWidth = bitmap.width
            lastInputHeight = bitmap.height
            val mpImage = BitmapImageBuilder(bitmap).build()
            handLandmarker.detectAsync(mpImage, imageProxy.imageInfo.timestamp)
        } catch (t: Throwable) {
            onError(t)
        } finally {
            imageProxy.close()
        }
    }

    private fun handleResult(result: HandLandmarkerResult, @Suppress("UNUSED_PARAMETER") input: com.google.mediapipe.framework.image.MPImage) {
        val frames = result.landmarks().mapIndexed { handIndex, landmarks ->
            HandFrame(
                landmarks = landmarks.map { HandLandmark(it.x(), it.y(), it.z()) },
                handedness = result.handedness().getOrNull(handIndex)?.firstOrNull()?.categoryName() ?: "Unknown",
                timestampMs = System.currentTimeMillis()
            )
        }
        onResult(frames, lastInputWidth, lastInputHeight)
    }

    fun close() {
        handLandmarker.close()
    }
}

private fun ImageProxy.toBitmap(): Bitmap {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
    val bytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

private fun Bitmap.rotate(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
