package com.pisquarelabs.mudra.core.sign

import com.pisquarelabs.mudra.core.model.HandFrame
import com.pisquarelabs.mudra.core.model.HandLandmark
import com.pisquarelabs.mudra.core.model.SignFrameCandidates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeuristicSignClassifierTest {

    private val classifier = HeuristicSignClassifier()

    private data class Dir(val x: Float, val y: Float, val z: Float)

    private val thumbDir = Dir(1f, 0f, 0f)
    private val indexDir = Dir(0.3f, 1f, 0f)
    private val middleDir = Dir(0f, 1f, 0f)
    private val ringDir = Dir(-0.3f, 1f, 0f)
    private val pinkyDir = Dir(-0.6f, 1f, 0f)

    private fun point(dir: Dir, scale: Float) = HandLandmark(dir.x * scale, dir.y * scale, dir.z * scale)

    /**
     * Builds a synthetic 21-landmark hand matching MediaPipe's index layout (wrist=0,
     * thumb MCP/TIP=2/4, index MCP/TIP=5/8, middle=9/12, ring=13/16, pinky=17/20 - the
     * same indices [HeuristicSignClassifier] reads). Each finger's MCP sits at distance 1
     * from the wrist along its own direction; its tip sits at distance 2 (extended) or
     * coincides with the MCP (curled), matching how `extension()` measures fingers.
     */
    private fun buildHand(
        thumb: Boolean,
        index: Boolean,
        middle: Boolean,
        ring: Boolean,
        pinky: Boolean
    ): HandFrame {
        val wrist = HandLandmark(0f, 0f, 0f)
        val landmarks = MutableList(21) { wrist }

        fun place(mcpIndex: Int, tipIndex: Int, dir: Dir, extended: Boolean) {
            landmarks[mcpIndex] = point(dir, 1f)
            landmarks[tipIndex] = point(dir, if (extended) 2f else 1f)
        }

        place(2, 4, thumbDir, thumb)
        place(5, 8, indexDir, index)
        place(9, 12, middleDir, middle)
        place(13, 16, ringDir, ring)
        place(17, 20, pinkyDir, pinky)

        return HandFrame(landmarks = landmarks, handedness = "Right", timestampMs = 0L)
    }

    private fun topGloss(frame: SignFrameCandidates?): String? =
        frame?.candidates?.maxByOrNull { it.confidence }?.gloss

    @Test
    fun `fist classifies as HELP`() {
        val result = classifier.classify(listOf(buildHand(thumb = false, index = false, middle = false, ring = false, pinky = false)))
        assertEquals("HELP", topGloss(result))
    }

    @Test
    fun `open palm classifies as HELLO`() {
        val result = classifier.classify(listOf(buildHand(thumb = true, index = true, middle = true, ring = true, pinky = true)))
        assertEquals("HELLO", topGloss(result))
    }

    @Test
    fun `thumb only classifies as YES`() {
        val result = classifier.classify(listOf(buildHand(thumb = true, index = false, middle = false, ring = false, pinky = false)))
        assertEquals("YES", topGloss(result))
    }

    @Test
    fun `index only classifies as YOU`() {
        val result = classifier.classify(listOf(buildHand(thumb = false, index = true, middle = false, ring = false, pinky = false)))
        assertEquals("YOU", topGloss(result))
    }

    @Test
    fun `index and middle classify as PLEASE`() {
        val result = classifier.classify(listOf(buildHand(thumb = false, index = true, middle = true, ring = false, pinky = false)))
        assertEquals("PLEASE", topGloss(result))
    }

    @Test
    fun `thumb and pinky classify as DOCTOR`() {
        val result = classifier.classify(listOf(buildHand(thumb = true, index = false, middle = false, ring = false, pinky = true)))
        assertEquals("DOCTOR", topGloss(result))
    }

    @Test
    fun `touching thumb and index tips with three other fingers extended classifies as GOOD`() {
        val base = buildHand(thumb = false, index = false, middle = true, ring = true, pinky = true)
        val landmarks = base.landmarks.toMutableList()
        val meetingPoint = HandLandmark(0.5f, 0.2f, 0f)
        landmarks[4] = meetingPoint // thumb tip
        landmarks[8] = meetingPoint // index tip

        val result = classifier.classify(listOf(base.copy(landmarks = landmarks)))
        assertEquals("GOOD", topGloss(result))
    }

    @Test
    fun `returns null when no hand is present`() {
        assertNull(classifier.classify(emptyList()))
    }

    @Test
    fun `returns null when landmarks are incomplete`() {
        val incomplete = HandFrame(landmarks = List(10) { HandLandmark(0f, 0f, 0f) }, handedness = "Right", timestampMs = 0L)
        assertNull(classifier.classify(listOf(incomplete)))
    }

    @Test
    fun `classifies using only the first hand when multiple are present`() {
        val fist = buildHand(thumb = false, index = false, middle = false, ring = false, pinky = false)
        val openPalm = buildHand(thumb = true, index = true, middle = true, ring = true, pinky = true)

        val result = classifier.classify(listOf(fist, openPalm))
        assertEquals("HELP", topGloss(result))
    }

    @Test
    fun `never returns more than two candidates`() {
        val result = classifier.classify(listOf(buildHand(thumb = false, index = false, middle = false, ring = false, pinky = false)))
        assertTrue((result?.candidates?.size ?: 0) <= 2)
    }
}
