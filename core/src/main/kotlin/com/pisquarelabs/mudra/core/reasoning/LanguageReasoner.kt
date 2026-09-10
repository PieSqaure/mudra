package com.pisquarelabs.mudra.core.reasoning

import com.pisquarelabs.mudra.core.model.ConversationContext
import com.pisquarelabs.mudra.core.model.ResolvedUtterance
import com.pisquarelabs.mudra.core.model.SignSequence

/**
 * Language reconstruction stage: turns an uncertain, phrase-length sequence of sign
 * candidates into a natural-language utterance + intent. This is the ONLY seam through
 * which "Qwen" (or any other reasoning backend) enters the app - everything upstream
 * (camera, hand tracking, sign classification, temporal decoding) never depends on it,
 * and everything downstream (TTS, captions, call actions) only depends on
 * [ResolvedUtterance], never on how it was produced.
 *
 * Swapping [QwenLanguageReasoner] for a different backend (a bigger Qwen variant, a
 * cloud fallback, a rules engine) never requires touching the rest of the app.
 */
interface LanguageReasoner {
    suspend fun resolve(sequence: SignSequence, context: ConversationContext): ResolvedUtterance
}
