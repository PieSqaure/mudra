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

class MockLanguageReasonerTest {

    private val reasoner = MockLanguageReasoner()

    private fun sequenceOf(vararg glossConfidence: Pair<String, Float>) = SignSequence(
        frames = glossConfidence.map { (gloss, confidence) -> SignFrameCandidates(listOf(SignCandidate(gloss, confidence))) }
    )

    @Test
    fun `single HELP frame resolves to a help sentence with REQUEST_HELP intent`() = runBlocking {
        val resolved = reasoner.resolve(sequenceOf("HELP" to 0.9f), ConversationContext())

        assertEquals("I need help.", resolved.text)
        assertEquals("REQUEST_HELP", resolved.intent)
        assertFalse(resolved.needsConfirmation)
    }

    @Test
    fun `HELP then DOCTOR reconstructs both sentences in order`() = runBlocking {
        val resolved = reasoner.resolve(sequenceOf("HELP" to 0.9f, "DOCTOR" to 0.85f), ConversationContext())

        assertEquals("I need help. I need a doctor.", resolved.text)
    }

    @Test
    fun `empty sequence resolves to an unresolved, needs-confirmation result`() = runBlocking {
        val resolved = reasoner.resolve(SignSequence(emptyList()), ConversationContext())

        assertEquals("", resolved.text)
        assertEquals("UNKNOWN", resolved.intent)
        assertEquals(0f, resolved.confidence, 0.0001f)
        assertTrue(resolved.needsConfirmation)
    }

    @Test
    fun `unmapped gloss falls back to the raw gloss instead of inventing words`() = runBlocking {
        val resolved = reasoner.resolve(sequenceOf("UNSEEN_GLOSS" to 0.9f), ConversationContext())

        assertEquals("UNSEEN_GLOSS", resolved.text)
        assertEquals("UNKNOWN", resolved.intent)
    }

    @Test
    fun `low average confidence forces needsConfirmation`() = runBlocking {
        val resolved = reasoner.resolve(sequenceOf("HELP" to 0.4f), ConversationContext())

        assertTrue(resolved.confidence < 0.6f)
        assertTrue(resolved.needsConfirmation)
    }

    @Test
    fun `picks the top candidate per frame, ignoring lower-confidence alternatives`() = runBlocking {
        val sequence = SignSequence(
            frames = listOf(SignFrameCandidates(listOf(SignCandidate("HELP", 0.9f), SignCandidate("YES", 0.1f))))
        )

        val resolved = reasoner.resolve(sequence, ConversationContext())

        assertEquals("I need help.", resolved.text)
    }
}
