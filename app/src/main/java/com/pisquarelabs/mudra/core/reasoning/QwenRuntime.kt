package com.pisquarelabs.mudra.core.reasoning

/**
 * Raw text-in/text-out inference backend for Qwen. Kept separate from [LanguageReasoner] so the
 * prompt building, JSON validation and fallback behavior in [QwenLanguageReasoner] never has to
 * change when the underlying runtime does (llama.cpp today, Qualcomm QNN/AI Hub tomorrow).
 */
interface QwenRuntime {
    /** Runs one full generation for [prompt] and returns the model's raw text output. */
    suspend fun generate(prompt: String): String
}
