package com.pisquarelabs.mudra.ui.caption

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pisquarelabs.mudra.core.model.ConversationContext
import com.pisquarelabs.mudra.core.model.HandFrame
import com.pisquarelabs.mudra.core.model.ResolvedUtterance
import com.pisquarelabs.mudra.core.model.SignCandidate
import com.pisquarelabs.mudra.core.reasoning.LanguageReasoner
import com.pisquarelabs.mudra.core.reasoning.MockLanguageReasoner
import com.pisquarelabs.mudra.core.sign.SignClassifier
import com.pisquarelabs.mudra.core.temporal.TemporalDecoder
import com.pisquarelabs.mudra.data.db.ConversationDao
import com.pisquarelabs.mudra.data.db.ConversationEntry
import com.pisquarelabs.mudra.data.settings.MudraSettings
import com.pisquarelabs.mudra.data.settings.SettingsRepository
import com.pisquarelabs.mudra.tts.TextToSpeechManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CaptionUiState(
    val handDetected: Boolean = false,
    val liveCandidate: SignCandidate? = null,
    val isReasoning: Boolean = false,
    val resolved: ResolvedUtterance? = null,
    val usingOnDeviceQwen: Boolean = false
)

/**
 * Orchestrates the full pipeline end to end:
 *   hand landmarks (from the camera analyzer) -> [SignClassifier] -> [TemporalDecoder]
 *   -> phrase boundary -> [LanguageReasoner] -> [ResolvedUtterance] -> persist + speak.
 *
 * This is the only place that wires the stages together; every stage itself is unaware of
 * its neighbors.
 */
class CaptionViewModel(
    private val signClassifier: SignClassifier,
    private val settingsRepository: SettingsRepository,
    private val conversationDao: ConversationDao,
    private val ttsManager: TextToSpeechManager,
    private val languageReasonerFor: (MudraSettings) -> LanguageReasoner = { MockLanguageReasoner() }
) : ViewModel() {

    private val temporalDecoder = TemporalDecoder()

    private val _uiState = MutableStateFlow(CaptionUiState())
    val uiState: StateFlow<CaptionUiState> = _uiState.asStateFlow()

    private var currentSettings = MudraSettings()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                currentSettings = settings
                _uiState.value = _uiState.value.copy(usingOnDeviceQwen = settings.useOnDeviceQwen)
            }
        }
    }

    /** Called from the CameraX analysis callback for every processed frame. */
    fun onHandFrames(frames: List<HandFrame>) {
        if (frames.isEmpty()) {
            _uiState.value = _uiState.value.copy(handDetected = false, liveCandidate = null)
            val event = temporalDecoder.onNoCandidates()
            handleEvent(event)
            return
        }

        val frameCandidates = signClassifier.classify(frames)
        if (frameCandidates == null) {
            _uiState.value = _uiState.value.copy(handDetected = true, liveCandidate = null)
            handleEvent(temporalDecoder.onNoCandidates())
            return
        }

        val top = frameCandidates.candidates.maxByOrNull { it.confidence }
        _uiState.value = _uiState.value.copy(handDetected = true, liveCandidate = top)
        handleEvent(temporalDecoder.onCandidates(frameCandidates))
    }

    private fun handleEvent(event: TemporalDecoder.Event) {
        if (event !is TemporalDecoder.Event.PhraseReady) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isReasoning = true)

            val previousText = conversationDao.latest()?.text.orEmpty()
            val reasoner = languageReasonerFor(currentSettings)
            val resolved = reasoner.resolve(event.sequence, ConversationContext(previousText))

            conversationDao.insert(
                ConversationEntry(
                    text = resolved.text,
                    intent = resolved.intent,
                    confidence = resolved.confidence,
                    needsConfirmation = resolved.needsConfirmation,
                    timestampMs = System.currentTimeMillis()
                )
            )

            _uiState.value = _uiState.value.copy(isReasoning = false, resolved = resolved)

            if (currentSettings.ttsEnabled && !resolved.needsConfirmation) {
                ttsManager.speak(resolved.text)
            }
        }
    }

    fun confirmResolved() {
        val resolved = _uiState.value.resolved ?: return
        if (currentSettings.ttsEnabled) ttsManager.speak(resolved.text)
        _uiState.value = _uiState.value.copy(resolved = resolved.copy(needsConfirmation = false))
    }

    fun dismissResolved() {
        _uiState.value = _uiState.value.copy(resolved = null)
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
}
