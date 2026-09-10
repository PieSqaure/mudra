package com.pisquarelabs.mudra.core.reasoning

import com.pisquarelabs.mudra.core.model.ConversationContext
import com.pisquarelabs.mudra.core.model.SignCandidate
import com.pisquarelabs.mudra.core.model.SignFrameCandidates
import com.pisquarelabs.mudra.core.model.SignSequence
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QwenLanguageReasonerTest {

    private val sampleSequence = SignSequence(
        frames = listOf(
            SignFrameCandidates(listOf(SignCandidate("HELP", 0.9f))),
            SignFrameCandidates(listOf(SignCandidate("DOCTOR", 0.85f)))
        )
    )

    private class FakeQwenRuntime(private val response: (String) -> String) : QwenRuntime {
        var lastPrompt: String? = null
        override suspend fun generate(prompt: String): String {
            lastPrompt = prompt
            return response(prompt)
        }
    }

    private val silentLogger = ReasoningLogger { _, _ -> }

    @Test
    fun `valid JSON response is parsed into a ResolvedUtterance`() = runBlocking {
        val runtime = FakeQwenRuntime {
            """{"text":"I need help. I need a doctor.","intent":"MEDICAL_HELP","confidence":0.91,"needs_confirmation":false}"""
        }
        val resolved = QwenLanguageReasoner(runtime, silentLogger).resolve(sampleSequence, ConversationContext())

        assertEquals("I need help. I need a doctor.", resolved.text)
        assertEquals("MEDICAL_HELP", resolved.intent)
        assertEquals(0.91f, resolved.confidence, 0.001f)
        assertFalse(resolved.needsConfirmation)
    }

    @Test
    fun `JSON wrapped in prose or code fences is still extracted`() = runBlocking {
        val runtime = FakeQwenRuntime {
            "Sure, here you go:\n```json\n{\"text\":\"Hello.\",\"intent\":\"GREETING\",\"confidence\":0.8,\"needs_confirmation\":false}\n```"
        }
        val resolved = QwenLanguageReasoner(runtime, silentLogger).resolve(sampleSequence, ConversationContext())

        assertEquals("Hello.", resolved.text)
        assertEquals("GREETING", resolved.intent)
    }

    @Test
    fun `low confidence forces needsConfirmation even if the model said false`() = runBlocking {
        val runtime = FakeQwenRuntime {
            """{"text":"Maybe help?","intent":"REQUEST_HELP","confidence":0.3,"needs_confirmation":false}"""
        }
        val resolved = QwenLanguageReasoner(runtime, silentLogger).resolve(sampleSequence, ConversationContext())

        assertTrue(resolved.needsConfirmation)
    }

    @Test
    fun `invalid JSON falls back to raw glosses and flags for confirmation`() = runBlocking {
        val runtime = FakeQwenRuntime { "not json at all" }
        val resolved = QwenLanguageReasoner(runtime, silentLogger).resolve(sampleSequence, ConversationContext())

        assertEquals("HELP DOCTOR", resolved.text)
        assertEquals("UNKNOWN", resolved.intent)
        assertEquals(0f, resolved.confidence, 0.001f)
        assertTrue(resolved.needsConfirmation)
    }

    @Test
    fun `runtime failure falls back to raw glosses instead of crashing`() = runBlocking {
        val runtime = object : QwenRuntime {
            override suspend fun generate(prompt: String): String = error("model unavailable")
        }
        val resolved = QwenLanguageReasoner(runtime, silentLogger).resolve(sampleSequence, ConversationContext())

        assertEquals("HELP DOCTOR", resolved.text)
        assertTrue(resolved.needsConfirmation)
    }

    @Test
    fun `prompt sent to the runtime includes the candidate glosses, confidences and prior context`() = runBlocking {
        val runtime = FakeQwenRuntime {
            """{"text":"x","intent":"y","confidence":0.9,"needs_confirmation":false}"""
        }
        val reasoner = QwenLanguageReasoner(runtime, silentLogger)

        reasoner.resolve(sampleSequence, ConversationContext(previousText = "earlier turn"))

        val prompt = requireNotNull(runtime.lastPrompt)
        assertTrue(prompt.contains("HELP"))
        assertTrue(prompt.contains("DOCTOR"))
        assertTrue(prompt.contains("earlier turn"))
        assertTrue(prompt.contains("Return JSON only."))
    }
}
