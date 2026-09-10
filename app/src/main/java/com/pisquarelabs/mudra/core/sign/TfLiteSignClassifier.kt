package com.pisquarelabs.mudra.core.sign

import android.content.Context
import android.util.Log
import com.pisquarelabs.mudra.core.model.HandFrame
import com.pisquarelabs.mudra.core.model.SignFrameCandidates
import java.io.File

private const val TAG = "TfLiteSignClassifier"
private const val TRAINED_MODEL_ASSET = "sign_model.tflite"

/**
 * Placeholder for the real "custom sign model" the architecture doc calls for: a small
 * temporal network (e.g. landmarks -> LSTM/1D-conv -> softmax over a sign vocabulary),
 * exported to TFLite and optionally accelerated via the Qualcomm QNN delegate on Snapdragon.
 *
 * No trained model or vocabulary exists yet, so this intentionally does not pretend to run
 * inference: it logs once and falls back to [HeuristicSignClassifier] so the rest of the
 * pipeline keeps working end-to-end. Once `sign_model.tflite` (+ a label map) is trained and
 * dropped into `app/src/main/assets/`, replace the body of [classify] with:
 *   1. flatten landmarks into the model's input tensor layout,
 *   2. run `Interpreter.run(input, output)`,
 *   3. map output indices to glosses via the label map,
 *   4. return the top-K as [SignFrameCandidates].
 */
class TfLiteSignClassifier(context: Context) : SignClassifier {

    private val fallback = HeuristicSignClassifier()
    private val hasTrainedModel: Boolean = runCatching {
        context.assets.list("")?.contains(TRAINED_MODEL_ASSET) == true
    }.getOrDefault(false)

    init {
        if (!hasTrainedModel) {
            Log.w(TAG, "$TRAINED_MODEL_ASSET not found in assets; using ${HeuristicSignClassifier::class.simpleName} instead.")
        }
    }

    override fun classify(hands: List<HandFrame>): SignFrameCandidates? {
        if (!hasTrainedModel) return fallback.classify(hands)
        // TODO: load with org.tensorflow.lite.Interpreter and run real inference.
        throw NotImplementedError(
            "sign_model.tflite present but inference is not implemented yet. " +
                "See the TODO in ${File("core/sign/TfLiteSignClassifier.kt").path}."
        )
    }
}
