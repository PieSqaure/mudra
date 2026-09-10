package com.pisquarelabs.mudra.ui.caption

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pisquarelabs.mudra.di.AppContainer

fun captionViewModelFactory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        CaptionViewModel(
            signClassifier = container.signClassifier,
            settingsRepository = container.settingsRepository,
            conversationDao = container.database.conversationDao(),
            ttsManager = container.ttsManager,
            languageReasonerFor = { settings ->
                container.languageReasoner(
                    useOnDeviceQwen = settings.useOnDeviceQwen,
                    qwenModelPath = "/data/local/tmp/qwen-${settings.qwenModelVariant.paramCount}.gguf"
                )
            }
        )
    }
}
