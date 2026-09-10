package com.pisquarelabs.mudra.core.sign

import com.pisquarelabs.mudra.core.model.HandFrame
import com.pisquarelabs.mudra.core.model.SignFrameCandidates

/**
 * Turns raw hand landmarks for a single camera frame into a ranked list of sign-gloss
 * candidates ("Top-K sign candidates" in the architecture doc). Implementations are swappable:
 * the pipeline downstream (temporal decoder, language reasoner) never depends on how the
 * classification was produced.
 */
interface SignClassifier {
    /** [hands] is typically 0 or 1 entries for a one-handed vocabulary, 2 for two-handed signs. */
    fun classify(hands: List<HandFrame>): SignFrameCandidates?
}
