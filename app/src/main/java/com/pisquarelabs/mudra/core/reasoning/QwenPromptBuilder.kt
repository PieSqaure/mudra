package com.pisquarelabs.mudra.core.reasoning

import com.pisquarelabs.mudra.core.model.ConversationContext
import com.pisquarelabs.mudra.core.model.SignSequence
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

private val prettyJson = Json { prettyPrint = true }

/**
 * Builds the exact, tightly-constrained prompt from the architecture doc: Qwen receives only
 * structured evidence (glosses + confidences + prior context), never raw frames or images,
 * and is told explicitly not to invent facts and to return JSON only.
 */
object QwenPromptBuilder {

    private const val SYSTEM_INSTRUCTIONS = """
You are the language reconstruction engine
for an accessibility communication device.

You receive uncertain sign-language tokens
from a separate vision model.

Your job is to reconstruct the most likely
short utterance.

Rules:
- Do not invent information.
- Do not introduce names, locations, events,
  or facts absent from the input.
- Use previous context only to resolve ambiguity.
- Preserve uncertainty.
- Prefer short natural sentences.
- Never execute actions.
- Return JSON only.
""".trimIndent()

    fun build(sequence: SignSequence, context: ConversationContext): String {
        val inputJson = buildJsonObject {
            put("sign_sequence", buildJsonArray {
                sequence.frames.forEach { frame ->
                    add(buildJsonObject {
                        put("candidates", buildJsonArray {
                            frame.candidates.forEach { candidate ->
                                add(JsonArray(listOf(JsonPrimitive(candidate.gloss), JsonPrimitive(candidate.confidence))))
                            }
                        })
                    })
                }
            })
            put("previous_context", JsonPrimitive(context.previousText))
        }

        return buildString {
            appendLine(SYSTEM_INSTRUCTIONS)
            appendLine()
            appendLine("Input:")
            appendLine(prettyJson.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), inputJson))
            appendLine()
            appendLine("Return:")
            appendLine(
                """
                {
                  "text": "...",
                  "intent": "...",
                  "confidence": 0.0,
                  "needs_confirmation": true
                }
                """.trimIndent()
            )
        }
    }
}
