package com.pisquarelabs.mudra.core.reasoning

import com.pisquarelabs.mudra.core.model.ConversationContext
import com.pisquarelabs.mudra.core.model.ResolvedUtterance
import com.pisquarelabs.mudra.core.model.SignCandidate
import com.pisquarelabs.mudra.core.model.SignSequence

/**
 * Deterministic, on-device, zero-dependency stand-in for [QwenLanguageReasoner] so the full
 * pipeline (camera -> landmarks -> classifier -> temporal decoder -> reasoning -> TTS) is
 * demoable without a Qwen model file on the device. It follows the same prompt rules Qwen is
 * given - never invent facts, only rearrange the glosses it was actually handed - just with a
 * lookup table instead of an LLM. This is the default [LanguageReasoner] until a Qwen GGUF
 * model + runtime (see [LlamaCppQwenRuntime]) is wired in from Settings.
 */
class MockLanguageReasoner : LanguageReasoner {

    private val phraseTemplates: Map<String, String> = mapOf(
        "HELLO" to "Hello.",
        "HELP" to "I need help.",
        "YES" to "Yes.",
        "YOU" to "You.",
        "PLEASE" to "Please.",
        "GOOD" to "That's good.",
        "DOCTOR" to "I need a doctor."
    )

    private val intents: Map<String, String> = mapOf(
        "HELP" to "REQUEST_HELP",
        "DOCTOR" to "MEDICAL_HELP",
        "HELLO" to "GREETING",
        "YES" to "AFFIRM",
        "GOOD" to "AFFIRM",
        "PLEASE" to "REQUEST_HELP",
        "YOU" to "REFERENCE"
    )

    override suspend fun resolve(sequence: SignSequence, context: ConversationContext): ResolvedUtterance {
        val topPerFrame: List<SignCandidate> = sequence.frames.mapNotNull { frame ->
            frame.candidates.maxByOrNull { it.confidence }
        }

        if (topPerFrame.isEmpty()) {
            return ResolvedUtterance(text = "", intent = "UNKNOWN", confidence = 0f, needsConfirmation = true)
        }

        val sentence = topPerFrame.joinToString(" ") { phraseTemplates[it.gloss] ?: it.gloss }
        val intent = topPerFrame.firstNotNullOfOrNull { intents[it.gloss] } ?: "UNKNOWN"
        val confidence = topPerFrame.map { it.confidence }.average().toFloat()

        return ResolvedUtterance(
            text = sentence,
            intent = intent,
            confidence = confidence,
            needsConfirmation = confidence < 0.6f
        )
    }
}
