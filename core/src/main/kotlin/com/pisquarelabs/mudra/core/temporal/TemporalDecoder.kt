package com.pisquarelabs.mudra.core.temporal

import com.pisquarelabs.mudra.core.model.SignFrameCandidates
import com.pisquarelabs.mudra.core.model.SignSequence

/**
 * Implements the "temporal buffer -> phrase boundary detection" stage of the architecture doc.
 *
 * The sign classifier runs on every analyzed camera frame and is noisy frame-to-frame. This
 * decoder only commits a gloss to the current phrase once its top candidate has been the
 * winner for [stabilityFrameCount] consecutive frames above [minConfidence] (e.g. "NEED NEED
 * NEED" -> stable), and only hands the accumulated phrase to the language reasoner once
 * signing pauses for [pauseMs] (no hand / no stable candidate). This keeps the expensive
 * reasoning step (Qwen) from running on every frame - only once per completed phrase.
 *
 * Pure Kotlin, no Android dependencies, so it is unit-testable on its own.
 */
class TemporalDecoder(
    private val stabilityFrameCount: Int = 5,
    private val minConfidence: Float = 0.6f,
    private val pauseMs: Long = 700L,
    private val clock: () -> Long = System::currentTimeMillis
) {

    sealed interface Event {
        data object None : Event
        data class PhraseReady(val sequence: SignSequence) : Event
    }

    private val recentGlosses = ArrayDeque<String>()
    private val phraseBuffer = mutableListOf<SignFrameCandidates>()
    private var lastStableGloss: String? = null
    private var lastActivityMs: Long = clock()

    /** Call once per analyzed frame that produced no usable candidates (e.g. no hand visible). */
    fun onNoCandidates(): Event {
        recentGlosses.clear()
        return maybeFlushOnIdle()
    }

    /** Call once per analyzed frame with the classifier's ranked candidates for that frame. */
    fun onCandidates(frame: SignFrameCandidates): Event {
        lastActivityMs = clock()
        val top = frame.candidates.maxByOrNull { it.confidence }
        if (top == null || top.confidence < minConfidence) {
            recentGlosses.clear()
            return Event.None
        }

        recentGlosses.addLast(top.gloss)
        while (recentGlosses.size > stabilityFrameCount) recentGlosses.removeFirst()

        val isStable = recentGlosses.size == stabilityFrameCount && recentGlosses.all { it == top.gloss }
        if (isStable && top.gloss != lastStableGloss) {
            lastStableGloss = top.gloss
            phraseBuffer.add(frame)
        }
        return Event.None
    }

    /** Force the current phrase (if any) to be emitted, e.g. when the user taps "done signing". */
    fun forceFlush(): Event = if (phraseBuffer.isNotEmpty()) flush() else Event.None

    private fun maybeFlushOnIdle(): Event {
        val idleFor = clock() - lastActivityMs
        return if (phraseBuffer.isNotEmpty() && idleFor >= pauseMs) flush() else Event.None
    }

    private fun flush(): Event.PhraseReady {
        val sequence = SignSequence(frames = phraseBuffer.toList())
        phraseBuffer.clear()
        recentGlosses.clear()
        lastStableGloss = null
        return Event.PhraseReady(sequence)
    }
}
