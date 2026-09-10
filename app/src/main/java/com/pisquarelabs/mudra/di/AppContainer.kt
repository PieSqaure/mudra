package com.pisquarelabs.mudra.di

import android.content.Context
import android.util.Log
import com.pisquarelabs.mudra.core.reasoning.LanguageReasoner
import com.pisquarelabs.mudra.core.reasoning.LlamaCppQwenRuntime
import com.pisquarelabs.mudra.core.reasoning.MockLanguageReasoner
import com.pisquarelabs.mudra.core.reasoning.QwenLanguageReasoner
import com.pisquarelabs.mudra.core.sign.SignClassifier
import com.pisquarelabs.mudra.core.sign.TfLiteSignClassifier
import com.pisquarelabs.mudra.data.db.MudraDatabase
import com.pisquarelabs.mudra.data.settings.SettingsRepository
import com.pisquarelabs.mudra.tts.TextToSpeechManager

private const val TAG = "AppContainer"

/** Manual, Hilt-free dependency graph. Small enough to not need a DI framework. */
class AppContainer(private val appContext: Context) {

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val database: MudraDatabase by lazy { MudraDatabase.getInstance(appContext) }

    val ttsManager: TextToSpeechManager by lazy { TextToSpeechManager(appContext) }

    val signClassifier: SignClassifier by lazy { TfLiteSignClassifier(appContext) }

    fun createHandLandmarkerAnalyzer(
        onResult: (frames: List<com.pisquarelabs.mudra.core.model.HandFrame>, width: Int, height: Int) -> Unit
    ) = com.pisquarelabs.mudra.core.vision.HandLandmarkerAnalyzer(appContext, onResult)

    /**
     * Selects the language reasoning backend. On-device Qwen requires a GGUF model file and
     * the native bridge from `native/README.md`; until that's wired in, [LlamaCppQwenRuntime]
     * fails to load its native library and this falls back to [MockLanguageReasoner] so the
     * app keeps working end-to-end.
     */
    fun languageReasoner(useOnDeviceQwen: Boolean, qwenModelPath: String): LanguageReasoner {
        if (!useOnDeviceQwen) return MockLanguageReasoner()
        return runCatching {
            QwenLanguageReasoner(LlamaCppQwenRuntime(qwenModelPath))
        }.getOrElse {
            Log.w(TAG, "On-device Qwen runtime unavailable, falling back to MockLanguageReasoner", it)
            MockLanguageReasoner()
        }
    }
}
