package com.pisquarelabs.mudra.core.reasoning

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device Qwen3 (0.6B/1.7B, quantized GGUF) via a llama.cpp JNI bridge, as sketched in the
 * architecture doc ("Qualcomm path if viable; otherwise llama.cpp").
 *
 * NOT WIRED IN YET. This class defines the intended shape of the runtime and the native
 * function signatures, but the native library (`libmudra_llama_bridge.so`) is not built by
 * this project - see `native/README.md` for the steps to vendor llama.cpp, wire up
 * `externalNativeBuild` in `app/build.gradle.kts`, and place a Qwen GGUF model file on-device.
 * Until then, use [MockLanguageReasoner] (the default) or plug in a Qualcomm AI Hub/QNN runtime
 * behind the same [QwenRuntime] interface.
 */
class LlamaCppQwenRuntime(private val modelPath: String, private val maxTokens: Int = 200) : QwenRuntime {

    private var nativeHandle: Long = 0L

    init {
        System.loadLibrary("mudra_llama_bridge")
        nativeHandle = nativeLoadModel(modelPath)
        check(nativeHandle != 0L) { "Failed to load Qwen model from $modelPath" }
    }

    override suspend fun generate(prompt: String): String = withContext(Dispatchers.Default) {
        nativeGenerate(nativeHandle, prompt, maxTokens)
    }

    fun release() {
        if (nativeHandle != 0L) {
            nativeFreeModel(nativeHandle)
            nativeHandle = 0L
        }
    }

    private external fun nativeLoadModel(modelPath: String): Long
    private external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int): String
    private external fun nativeFreeModel(handle: Long)
}
