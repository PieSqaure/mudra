package com.pisquarelabs.mudra.core.reasoning

import com.pisquarelabs.mudra.core.model.ConversationContext
import com.pisquarelabs.mudra.core.model.ResolvedUtterance
import com.pisquarelabs.mudra.core.model.SignSequence
import kotlinx.serialization.json.Json

/** Pluggable so the Android app can route warnings to Logcat; defaults to stderr for plain JVM use (tests, tooling). */
fun interface ReasoningLogger {
    fun warn(message: String, throwable: Throwable?)
}

private val defaultReasoningLogger = ReasoningLogger { message, throwable ->
    System.err.println("QwenLanguageReasoner: $message${throwable?.let { " (${it.message})" } ?: ""}")
}

/**
 * The real [LanguageReasoner]: builds the constrained prompt, runs it through a [QwenRuntime],
 * and validates the model's JSON output before trusting it. Qwen only ever receives structured
 * sign-candidate evidence (never images/frames) and is never given any tool or action surface -
 * its entire contract is "return this JSON shape, or the app ignores you."
 */
class QwenLanguageReasoner(
    private val runtime: QwenRuntime,
    private val logger: ReasoningLogger = defaultReasoningLogger
) : LanguageReasoner {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun resolve(sequence: SignSequence, context: ConversationContext): ResolvedUtterance {
        val prompt = QwenPromptBuilder.build(sequence, context)
        val rawOutput = runCatching { runtime.generate(prompt) }
            .onFailure { logger.warn("Qwen runtime failed", it) }
            .getOrNull()
            ?: return fallback(sequence)

        val parsed = runCatching { json.decodeFromString(QwenOutput.serializer(), extractJson(rawOutput)) }
            .onFailure { logger.warn("Qwen returned invalid JSON: $rawOutput", it) }
            .getOrNull()
            ?: return fallback(sequence)

        return ResolvedUtterance(
            text = parsed.text,
            intent = parsed.intent,
            confidence = parsed.confidence.coerceIn(0f, 1f),
            needsConfirmation = parsed.needsConfirmation || parsed.confidence < 0.5f
        )
    }

    /** Models sometimes wrap JSON in prose or code fences despite instructions; grab the outermost object. */
    private fun extractJson(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start == -1 || end == -1 || end < start) return raw
        return raw.substring(start, end + 1)
    }

    /** Never let a broken/unavailable model produce a confident false utterance - degrade to "ask the user". */
    private fun fallback(sequence: SignSequence): ResolvedUtterance {
        val glosses = sequence.frames.mapNotNull { it.candidates.maxByOrNull { c -> c.confidence }?.gloss }
        return ResolvedUtterance(
            text = glosses.joinToString(" "),
            intent = "UNKNOWN",
            confidence = 0f,
            needsConfirmation = true
        )
    }
}
