package com.pisquarelabs.mudra.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val TAG = "TextToSpeechManager"

/** Thin wrapper around Android's built-in TextToSpeech - the final "TTS / captions" stage. */
class TextToSpeechManager(context: Context) {

    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.getDefault()
        } else {
            Log.e(TAG, "TextToSpeech init failed with status $status")
        }
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    /** Emits once when this utterance finishes speaking (or fails), for UI sequencing. */
    fun speakAwaitingCompletion(text: String): Flow<Unit> = callbackFlow {
        if (!ready || text.isBlank()) {
            close()
            return@callbackFlow
        }
        val utteranceId = UUID.randomUUID().toString()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) {
                if (id == utteranceId) trySend(Unit)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) trySend(Unit)
            }
        })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        awaitClose { }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
