package com.lumina.engine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lumina.engine.settings.InMemorySettingsRepository
import com.lumina.engine.settings.SettingsDataSource
import com.lumina.engine.settings.SettingsRepository

/**
 * LuminaViewModel manages application state and bridges UI, AI, and Render layers.
 */
class LuminaViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val settingsRepository: SettingsDataSource = defaultSettingsRepository()
) : ViewModel() {

    private val _luminaState = MutableStateFlow(LuminaState())
    val luminaState: StateFlow<LuminaState> = _luminaState.asStateFlow()

    private val _userInput = MutableStateFlow("")
    val userInput: StateFlow<String> = _userInput.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _dynamicTheme = MutableStateFlow(false)
    val dynamicTheme: StateFlow<Boolean> = _dynamicTheme.asStateFlow()

    var nativeBridge: NativeBridge? = null
    var pythonOrchestrator: PythonOrchestrator? = null

    init {
        // Load persisted settings
        viewModelScope.launch(ioDispatcher) {
            settingsRepository.dynamicTheme.collect { enabled ->
                _dynamicTheme.value = enabled
            }
        }
    }

    fun processUserInput(input: String) {
        if (input.isBlank()) return

        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = "Processing..."

            try {
                updateState { copy(processingState = ProcessingState.PROCESSING) }

                val intent = withContext(ioDispatcher) {
                    pythonOrchestrator?.parseIntent(input) ?: AIIntent(
                        action = "unknown",
                        target = input,
                        confidence = 0f
                    )
                }

                updateState {
                    copy(
                        currentIntent = intent,
                        processingState = ProcessingState.RENDERING
                    )
                }

                nativeBridge?.updateState(_luminaState.value.toJson())
                _statusMessage.value = "Applied: ${intent.action}"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
                updateState { copy(processingState = ProcessingState.ERROR) }
            } finally {
                _isProcessing.value = false
                updateState { copy(processingState = ProcessingState.IDLE) }
            }
        }
    }

    fun updateState(transform: LuminaState.() -> LuminaState) {
        _luminaState.value = _luminaState.value.transform().copy(
            stateId = _luminaState.value.stateId + 1
        )
    }

    fun setRenderMode(mode: RenderMode) {
        updateState { copy(renderMode = mode) }
        nativeBridge?.setRenderMode(mode.value)
    }

    fun addEffect(effect: EffectParams) {
        val current = _luminaState.value
        if (current.activeEffectCount >= 4) return

        updateState {
            val newEffects = effects.toMutableList()
            newEffects[activeEffectCount] = effect
            copy(effects = newEffects, activeEffectCount = activeEffectCount + 1)
        }
    }

    fun clearEffects() {
        updateState {
            copy(
                effects = listOf(EffectParams(), EffectParams(), EffectParams(), EffectParams()),
                activeEffectCount = 0
            )
        }
    }

    fun updateUIStyle(params: GlassmorphicParams) {
        updateState { copy(uiStyle = params) }
    }

    fun setUserInput(text: String) {
        _userInput.value = text
    }

    fun setDynamicTheme(enabled: Boolean) {
        _dynamicTheme.value = enabled
        viewModelScope.launch(ioDispatcher) {
            settingsRepository.setDynamicTheme(enabled)
        }
    }

    companion object {
        private fun defaultSettingsRepository(): SettingsDataSource {
            return runCatching { SettingsRepository(LuminaApplication.instance) }
                .getOrElse { InMemorySettingsRepository() }
        }
    }

    fun refreshTiming() {
        nativeBridge?.let { bridge ->
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { bridge.getFrameTiming() }
                    .onSuccess { timing ->
                        updateState { copy(timing = timing) }
                    }
            }
        }
    }
}

interface NativeBridge {
    fun initialize(): Boolean
    fun updateState(jsonState: String)
    fun setRenderMode(mode: Int)
    fun getFrameTiming(): FrameTiming
    fun getVideoTextureId(): Int
    fun shutdown()
}

interface PythonOrchestrator {
    fun initialize(assetsPath: String): Boolean
    fun parseIntent(userInput: String): AIIntent
    fun shutdown()
}
