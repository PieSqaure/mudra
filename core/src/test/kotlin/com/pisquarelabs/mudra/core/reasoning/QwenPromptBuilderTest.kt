package com.pisquarelabs.mudra.core.reasoning

import com.pisquarelabs.mudra.core.model.ConversationContext
import com.pisquarelabs.mudra.core.model.SignCandidate
import com.pisquarelabs.mudra.core.model.SignFrameCandidates
import com.pisquarelabs.mudra.core.model.SignSequence
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QwenPromptBuilderTest {

    private fun extractInputJson(prompt: String): JsonObject {
        val jsonText = prompt.substringAfter("Input:\n").substringBefore("\n\nReturn:")
        return Json.parseToJsonElement(jsonText).jsonObject
    }

    @Test
    fun `prompt states the core rules Qwen must follow`() {
        val prompt = QwenPromptBuilder.build(SignSequence(emptyList()), ConversationContext())

        assertTrue(prompt.contains("Return JSON only."))
        assertTrue(prompt.contains("Do not invent information."))
        assertTrue(prompt.contains("Never execute actions."))
    }

    @Test
    fun `prompt embeds previous context verbatim`() {
        val prompt = QwenPromptBuilder.build(SignSequence(emptyList()), ConversationContext(previousText = "I am hungry"))

        val input = extractInputJson(prompt)
        assertEquals("I am hungry", input["previous_context"]?.jsonPrimitive?.content)
    }

    @Test
    fun `prompt serializes every frame's candidates as gloss-confidence pairs in order`() {
        val sequence = SignSequence(
            frames = listOf(
                SignFrameCandidates(listOf(SignCandidate("NEED", 0.82f), SignCandidate("WANT", 0.12f))),
                SignFrameCandidates(listOf(SignCandidate("HELP", 0.94f)))
            )
        )

        val prompt = QwenPromptBuilder.build(sequence, ConversationContext())
        val frames = extractInputJson(prompt)["sign_sequence"]!!.jsonArray
        assertEquals(2, frames.size)

        val firstFrameCandidates = frames[0].jsonObject["candidates"]!!.jsonArray
        assertEquals("NEED", firstFrameCandidates[0].jsonArray[0].jsonPrimitive.content)
        assertEquals(0.82f, firstFrameCandidates[0].jsonArray[1].jsonPrimitive.content.toFloat(), 0.001f)
        assertEquals("WANT", firstFrameCandidates[1].jsonArray[0].jsonPrimitive.content)

        val secondFrameCandidates = frames[1].jsonObject["candidates"]!!.jsonArray
        assertEquals(1, secondFrameCandidates.size)
        assertEquals("HELP", secondFrameCandidates[0].jsonArray[0].jsonPrimitive.content)
    }

    @Test
    fun `empty sequence still produces a valid empty sign_sequence array`() {
        val prompt = QwenPromptBuilder.build(SignSequence(emptyList()), ConversationContext())

        val input = extractInputJson(prompt)
        assertTrue(input["sign_sequence"]!!.jsonArray.isEmpty())
    }
}
