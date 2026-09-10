package com.pisquarelabs.mudra.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "mudra_settings")

enum class QwenModelVariant(val label: String, val paramCount: String) {
    QWEN3_0_6B("Qwen3 0.6B", "0.6B"),
    QWEN3_1_7B("Qwen3 1.7B", "1.7B")
}

data class MudraSettings(
    val qwenModelVariant: QwenModelVariant = QwenModelVariant.QWEN3_0_6B,
    val useOnDeviceQwen: Boolean = false, // false = MockLanguageReasoner until a GGUF model is wired in
    val minSignConfidence: Float = 0.6f,
    val ttsEnabled: Boolean = true
)

/** DataStore-backed app settings: which Qwen variant to target, thresholds, TTS on/off. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val QWEN_VARIANT = stringPreferencesKey("qwen_variant")
        val USE_ON_DEVICE_QWEN = booleanPreferencesKey("use_on_device_qwen")
        val MIN_SIGN_CONFIDENCE = floatPreferencesKey("min_sign_confidence")
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
    }

    val settings: Flow<MudraSettings> = context.dataStore.data.map { prefs ->
        MudraSettings(
            qwenModelVariant = prefs[Keys.QWEN_VARIANT]
                ?.let { name -> QwenModelVariant.entries.find { it.name == name } }
                ?: QwenModelVariant.QWEN3_0_6B,
            useOnDeviceQwen = prefs[Keys.USE_ON_DEVICE_QWEN] ?: false,
            minSignConfidence = prefs[Keys.MIN_SIGN_CONFIDENCE] ?: 0.6f,
            ttsEnabled = prefs[Keys.TTS_ENABLED] ?: true
        )
    }

    suspend fun setQwenVariant(variant: QwenModelVariant) {
        context.dataStore.edit { it[Keys.QWEN_VARIANT] = variant.name }
    }

    suspend fun setUseOnDeviceQwen(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USE_ON_DEVICE_QWEN] = enabled }
    }

    suspend fun setMinSignConfidence(value: Float) {
        context.dataStore.edit { it[Keys.MIN_SIGN_CONFIDENCE] = value }
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TTS_ENABLED] = enabled }
    }
}
