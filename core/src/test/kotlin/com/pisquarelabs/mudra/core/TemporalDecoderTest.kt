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

    @Test
    fun `an interruption resets the stability run so it must start over`() {
        var now = 0L
        val decoder = TemporalDecoder(stabilityFrameCount = 3, pauseMs = 500L, clock = { now })

        // NEED, NEED interrupted by HELP: the two leading NEEDs must not count towards stability.
        decoder.onCandidates(candidates("NEED"))
        decoder.onCandidates(candidates("NEED"))
        decoder.onCandidates(candidates("HELP"))
        decoder.onCandidates(candidates("NEED"))
        decoder.onCandidates(candidates("NEED"))
        now += 600L
        val event = decoder.onNoCandidates()

        // Only two NEEDs followed the interruption, short of the stabilityFrameCount of 3.
        assertEquals(TemporalDecoder.Event.None, event)
    }

    @Test
    fun `forceFlush emits whatever phrase has accumulated so far`() {
        var now = 0L
        val decoder = TemporalDecoder(stabilityFrameCount = 2, pauseMs = 500L, clock = { now })

        repeat(2) { decoder.onCandidates(candidates("HELP")) }
        val event = decoder.forceFlush()

        assertTrue(event is TemporalDecoder.Event.PhraseReady)
        assertEquals(listOf("HELP"), (event as TemporalDecoder.Event.PhraseReady).sequence.frames.map { it.candidates.first().gloss })
    }

    @Test
    fun `forceFlush on an empty phrase buffer is a no-op`() {
        val decoder = TemporalDecoder()
        assertEquals(TemporalDecoder.Event.None, decoder.forceFlush())
    }

    @Test
    fun `after a phrase is flushed the decoder starts a fresh phrase cleanly`() {
        var now = 0L
        val decoder = TemporalDecoder(stabilityFrameCount = 2, pauseMs = 500L, clock = { now })

        repeat(2) { decoder.onCandidates(candidates("HELP")) }
        now += 600L
        decoder.onNoCandidates() // flush #1

        now += 100L
        repeat(2) { decoder.onCandidates(candidates("YES")) }
        now += 600L
        val secondEvent = decoder.onNoCandidates()

        val sequence = (secondEvent as TemporalDecoder.Event.PhraseReady).sequence
        assertEquals(listOf("YES"), sequence.frames.map { it.candidates.first().gloss })
    }
}
