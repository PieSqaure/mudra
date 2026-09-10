package com.pisquarelabs.mudra.core.sign

import com.pisquarelabs.mudra.core.model.HandFrame
import com.pisquarelabs.mudra.core.model.HandLandmark
import com.pisquarelabs.mudra.core.model.SignCandidate
import com.pisquarelabs.mudra.core.model.SignFrameCandidates
import kotlin.math.sqrt

/**
 * A dependency-free, geometry-based gesture classifier standing in for the trained
 * "custom sign model" from the architecture doc until real training data + a TFLite
 * export exist. It recognizes a handful of clearly distinguishable static hand shapes
 * and maps them to demo glosses. This is NOT linguistically accurate sign language
 * recognition, and it is intentionally never a place where language reasoning happens -
 * it only ever emits (gloss, confidence) candidates, same contract a trained model would.
 *
 * Swap this out for [TfLiteSignClassifier] once a trained temporal model is available;
 * nothing else in the pipeline needs to change.
 */
class HeuristicSignClassifier : SignClassifier {

    override fun classify(hands: List<HandFrame>): SignFrameCandidates? {
        val hand = hands.firstOrNull() ?: return null
        val lm = hand.landmarks
        if (lm.size < 21) return null

        val wrist = lm[0]
        val thumbTip = lm[4]
        val thumbMcp = lm[2]
        val indexTip = lm[8]
        val indexPip = lm[6]
        val indexMcp = lm[5]
        val middleTip = lm[12]
        val middlePip = lm[10]
        val middleMcp = lm[9]
        val ringTip = lm[16]
        val ringPip = lm[14]
        val ringMcp = lm[13]
        val pinkyTip = lm[20]
        val pinkyPip = lm[18]
        val pinkyMcp = lm[17]

        val indexExt = extension(wrist, indexMcp, indexPip, indexTip)
        val middleExt = extension(wrist, middleMcp, middlePip, middleTip)
        val ringExt = extension(wrist, ringMcp, ringPip, ringTip)
        val pinkyExt = extension(wrist, pinkyMcp, pinkyPip, pinkyTip)
        val thumbExt = extension(wrist, thumbMcp, thumbMcp, thumbTip)

        val handScale = distance(wrist, middleMcp).coerceAtLeast(1e-3f)
        val thumbIndexTouching = distance(thumbTip, indexTip) / handScale < 0.35f

        val scores = linkedMapOf<String, Float>()

        // OPEN_PALM -> HELLO: all five fingers extended
        scores["HELLO"] = average(indexExt, middleExt, ringExt, pinkyExt, thumbExt)

        // FIST -> HELP: nothing extended
        scores["HELP"] = average(inv(indexExt), inv(middleExt), inv(ringExt), inv(pinkyExt), inv(thumbExt))

        // THUMBS_UP -> YES: only thumb extended
        scores["YES"] = average(thumbExt, inv(indexExt), inv(middleExt), inv(ringExt), inv(pinkyExt))

        // POINTING -> YOU: only index extended
        scores["YOU"] = average(indexExt, inv(middleExt), inv(ringExt), inv(pinkyExt))

        // PEACE (index + middle) -> PLEASE
        scores["PLEASE"] = average(indexExt, middleExt, inv(ringExt), inv(pinkyExt))

        // OK sign (thumb+index tips touching, other three extended) -> GOOD
        scores["GOOD"] = average(
            if (thumbIndexTouching) 1f else 0f,
            middleExt, ringExt, pinkyExt
        )

        // Shaka (thumb + pinky extended, middle three curled) -> DOCTOR
        scores["DOCTOR"] = average(thumbExt, pinkyExt, inv(indexExt), inv(middleExt), inv(ringExt))

        val candidates = scores.entries
            .sortedByDescending { it.value }
            .take(2)
            .map { SignCandidate(it.key, it.value.coerceIn(0f, 1f)) }
            .filter { it.confidence > 0.15f }

        if (candidates.isEmpty()) return null
        return SignFrameCandidates(candidates)
    }

    /** ~1 when the finger is clearly extended, ~0 when curled, smooth in between. */
    private fun extension(wrist: HandLandmark, mcp: HandLandmark, pip: HandLandmark, tip: HandLandmark): Float {
        val wristToTip = distance(wrist, tip)
        val wristToMcp = distance(wrist, mcp).coerceAtLeast(1e-3f)
        val ratio = wristToTip / wristToMcp
        // Curled fingertips sit near the MCP (ratio ~1); extended fingertips sit much farther (ratio ~1.6-2.2).
        return ((ratio - 1.1f) / 0.7f).coerceIn(0f, 1f)
    }

    private fun inv(v: Float) = 1f - v

    private fun average(vararg v: Float) = v.average().toFloat()

    private fun distance(a: HandLandmark, b: HandLandmark): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
