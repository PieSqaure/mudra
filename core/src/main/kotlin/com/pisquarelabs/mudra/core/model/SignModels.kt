package com.pisquarelabs.mudra.core.model

/**
 * A single (x, y, z) hand landmark in normalized image coordinates, as emitted by
 * MediaPipe's HandLandmarker (21 points per hand).
 */
data class HandLandmark(
    val x: Float,
    val y: Float,
    val z: Float
)

/** One tracked hand for a single camera frame. */
data class HandFrame(
    val landmarks: List<HandLandmark>,
    val handedness: String, // "Left" or "Right"
    val timestampMs: Long
)

/** A candidate sign gloss with the classifier's confidence for it. */
data class SignCandidate(
    val gloss: String,
    val confidence: Float
)

/** The classifier's output for one temporally-stable window (one "frame" in the reasoning sense). */
data class SignFrameCandidates(
    val candidates: List<SignCandidate>
)

/** A stable phrase boundary: a sequence of sign frames ready to hand to the language reasoner. */
data class SignSequence(
    val frames: List<SignFrameCandidates>
)

/** Rolling conversational context passed to the language reasoner to resolve ambiguity. */
data class ConversationContext(
    val previousText: String = ""
)

/** The language reasoner's structured, validated output. */
data class ResolvedUtterance(
    val text: String,
    val intent: String,
    val confidence: Float,
    val needsConfirmation: Boolean
)
