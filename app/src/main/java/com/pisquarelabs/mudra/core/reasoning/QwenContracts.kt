package com.pisquarelabs.mudra.core.reasoning

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Qwen's required JSON-only response contract (see the prompt rules in [QwenPromptBuilder]). */
@Serializable
data class QwenOutput(
    val text: String,
    val intent: String,
    val confidence: Float,
    @SerialName("needs_confirmation") val needsConfirmation: Boolean
)
