package com.pisquarelabs.mudra.core

import com.pisquarelabs.mudra.core.model.SignCandidate
import com.pisquarelabs.mudra.core.model.SignFrameCandidates
import com.pisquarelabs.mudra.core.temporal.TemporalDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporalDecoderTest {

    private fun candidates(gloss: String, confidence: Float = 0.9f) =
        SignFrameCandidates(listOf(SignCandidate(gloss, confidence)))

    @Test
    fun `stable run of five identical glosses commits exactly one phrase frame`() {
        var now = 0L
        val decoder = TemporalDecoder(stabilityFrameCount = 5, pauseMs = 700L, clock = { now })

        repeat(5) { decoder.onCandidates(candidates("NEED")) }
        // A pause after the stable run should flush a single-frame phrase.
        now += 800L
        val event = decoder.onNoCandidates()

        assertTrue(event is TemporalDecoder.Event.PhraseReady)
        val sequence = (event as TemporalDecoder.Event.PhraseReady).sequence
        assertEquals(1, sequence.frames.size)
        assertEquals("NEED", sequence.frames.first().candidates.first().gloss)
    }

    @Test
    fun `switching stable glosses produces a multi-frame phrase in order`() {
        var now = 0L
        val decoder = TemporalDecoder(stabilityFrameCount = 3, pauseMs = 500L, clock = { now })

        repeat(3) { decoder.onCandidates(candidates("NEED")) }
        repeat(3) { decoder.onCandidates(candidates("HELP")) }
        repeat(3) { decoder.onCandidates(candidates("DOCTOR")) }
        now += 600L
        val event = decoder.onNoCandidates()

        val sequence = (event as TemporalDecoder.Event.PhraseReady).sequence
        assertEquals(listOf("NEED", "HELP", "DOCTOR"), sequence.frames.map { it.candidates.first().gloss })
    }

    @Test
    fun `low confidence candidates never become stable`() {
        var now = 0L
        val decoder = TemporalDecoder(stabilityFrameCount = 3, minConfidence = 0.6f, pauseMs = 500L, clock = { now })

        repeat(5) { decoder.onCandidates(candidates("NEED", confidence = 0.3f)) }
        now += 600L
        val event = decoder.onNoCandidates()

        assertEquals(TemporalDecoder.Event.None, event)
    }

    @Test
    fun `no phrase emitted before the pause elapses`() {
        var now = 0L
        val decoder = TemporalDecoder(stabilityFrameCount = 3, pauseMs = 500L, clock = { now })

        repeat(3) { decoder.onCandidates(candidates("HELP")) }
        now += 100L
        val event = decoder.onNoCandidates()

        assertEquals(TemporalDecoder.Event.None, event)
    }
}
