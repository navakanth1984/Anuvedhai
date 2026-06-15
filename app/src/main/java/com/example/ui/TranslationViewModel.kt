package com.example.ui

import android.app.Application
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import com.example.util.AudioPlayer
import com.example.util.AudioRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class TranslationViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val db = TranslationDatabase.getDatabase(application)
    private val repository = TranslationRepository(application, db)
    private val recorder = AudioRecorder(application)
    private val player = AudioPlayer(application)

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    // State Holders
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _activeConversationId = MutableStateFlow<Long?>(null)
    val activeConversationId: StateFlow<Long?> = _activeConversationId.asStateFlow()

    private val _turns = MutableStateFlow<List<TranslationTurn>>(emptyList())
    val turns: StateFlow<List<TranslationTurn>> = _turns.asStateFlow()

    // Configuration States
    val sourceLanguage = MutableStateFlow(LanguageOption("Auto-Detect", "auto"))
    val targetLanguage = MutableStateFlow(LanguageOption("Hindi", "hi-IN"))
    val useSarvam = MutableStateFlow(false)
    val sarvamApiKey = MutableStateFlow("")
    val sarvamTtsSpeakerGender = MutableStateFlow("Male") // "Male" or "Female"
    val autoPlayTts = MutableStateFlow(false)
    val darkThemeConfig = MutableStateFlow("system") // "system", "light", "dark"
    val ttsVolume = MutableStateFlow(1.0f) // Volume level between 0.0f and 1.0f
    val ttsSpeed = MutableStateFlow(1.0f) // Playback speed level between 0.5f and 2.0f

    val typedText = MutableStateFlow("")
    val isRecording = MutableStateFlow(false)
    val isPlaying = MutableStateFlow(false)
    val isTranslating = MutableStateFlow(false)
    val activePlayingTurnId = MutableStateFlow<Long?>(null)

    val errorMessage = MutableStateFlow<String?>(null)

    // Call Translator & Overlay simulator states
    val selectedCallPlatform = MutableStateFlow("WhatsApp")
    val isCallActive = MutableStateFlow(false)
    val isCallVideoEnabled = MutableStateFlow(true)
    val callPartnerLanguage = MutableStateFlow(LanguageOption("Hindi", "hi-IN"))
    val isOverlayVisible = MutableStateFlow(false)

    init {
        tts = TextToSpeech(application, this)
        loadConversations()
        viewModelScope.launch {
            ttsVolume.collect { volume ->
                player.setVolume(volume)
            }
        }
        viewModelScope.launch {
            ttsSpeed.collect { speed ->
                player.setSpeed(speed)
            }
        }
    }

    private fun loadConversations() {
        viewModelScope.launch {
            repository.getAllConversations().collect { list ->
                _conversations.value = list
                if (_activeConversationId.value == null && list.isNotEmpty()) {
                    selectConversation(list.first().id)
                } else if (list.isEmpty()) {
                    startNewConversation()
                }
            }
        }
    }

    fun selectConversation(id: Long) {
        _activeConversationId.value = id
        viewModelScope.launch {
            repository.getTurns(id).collect { turnsList ->
                _turns.value = turnsList
            }
        }
    }

    fun startNewConversation() {
        viewModelScope.launch {
            val title = "Translate dialog (${System.currentTimeMillis() % 100000})"
            val newId = repository.createConversation(title)
            _activeConversationId.value = newId
            selectConversation(newId)
        }
    }

    fun deleteCurrentConversation() {
        val currentId = _activeConversationId.value ?: return
        viewModelScope.launch {
            repository.deleteConversation(currentId)
            _activeConversationId.value = null
            _turns.value = emptyList()
            loadConversations()
        }
    }

    fun clearTurnsInCurrentConversation() {
        val currentId = _activeConversationId.value ?: return
        viewModelScope.launch {
            repository.clearHistory(currentId)
        }
    }

    // Audio Capture Handlers
    fun toggleRecording() {
        val isCurrentlyRecording = isRecording.value
        if (!isCurrentlyRecording) {
            val file = recorder.startRecording()
            if (file != null) {
                isRecording.value = true
                errorMessage.value = null
            } else {
                errorMessage.value = "Failed to open micro. Check microphone system permissions."
            }
        } else {
            val recordedFile = recorder.stopRecording()
            isRecording.value = false
            if (recordedFile != null && recordedFile.exists()) {
                submitTranslation(inputType = "AUDIO", file = recordedFile)
            }
        }
    }

    fun cancelRecording() {
        recorder.cancelRecording()
        isRecording.value = false
    }

    // Submit Action Handlers
    fun submitTextTranslation() {
        val text = typedText.value.trim()
        if (text.isBlank()) return
        typedText.value = ""
        submitTranslation(inputType = "TEXT", file = null, textOverride = text)
    }

    fun translateQuickPhrase(phrase: String) {
        if (phrase.isBlank()) return
        submitTranslation(inputType = "TEXT", file = null, textOverride = phrase)
    }

    fun submitImageTranslation(file: File) {
        submitTranslation(inputType = "IMAGE", file = file)
    }

    fun submitVideoTranslation(file: File) {
        submitTranslation(inputType = "VIDEO", file = file)
    }

    private fun submitTranslation(inputType: String, file: File?, textOverride: String = "") {
        val convId = _activeConversationId.value
        if (convId == null) {
            errorMessage.value = "Initialize or select an interpreter session first."
            return
        }

        isTranslating.value = true
        errorMessage.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val turn = repository.processTranslation(
                    conversationId = convId,
                    inputType = inputType,
                    file = file,
                    typedText = textOverride,
                    sourceLangCode = sourceLanguage.value.code,
                    sourceLangName = sourceLanguage.value.displayName,
                    targetLangCode = targetLanguage.value.code,
                    targetLangName = targetLanguage.value.displayName,
                    useSarvam = useSarvam.value,
                    sarvamApiKey = sarvamApiKey.value
                )
                withContext(Dispatchers.Main) {
                    if (autoPlayTts.value) {
                        playAudioTurn(turn)
                    }
                }
            } catch (e: Exception) {
                Log.e("TranslationViewModel", "API failure", e)
                withContext(Dispatchers.Main) {
                    errorMessage.value = "Translation error: ${e.localizedMessage}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isTranslating.value = false
                }
            }
        }
    }

    // Interchange Source and Target
    fun swapLanguages() {
        if (sourceLanguage.value.code == "auto") {
            errorMessage.value = "Cannot swap languages when source language is Auto-Detect."
            return
        }
        val temp = sourceLanguage.value
        sourceLanguage.value = targetLanguage.value
        targetLanguage.value = temp
    }

    // Speech Output Synthesis Playbacks
    fun playAudioTurn(turn: TranslationTurn) {
        if (isPlaying.value && activePlayingTurnId.value == turn.id) {
            player.stopPlaying()
            isPlaying.value = false
            activePlayingTurnId.value = null
            return
        }

        // If turn has raw path of recorder input, play it
        if (turn.mediaPath != null && turn.inputType == "AUDIO") {
            val f = File(turn.mediaPath)
            if (f.exists()) {
                activePlayingTurnId.value = turn.id
                isPlaying.value = true
                player.playFile(f) {
                    isPlaying.value = false
                    activePlayingTurnId.value = null
                }
                return
            }
        }

        // Play the translated text as TTS
        activePlayingTurnId.value = turn.id
        isPlaying.value = true

        viewModelScope.launch {
            var playedWithSarvam = false
            val apiKey = sarvamApiKey.value.ifBlank { BuildConfig.SARVAM_API_KEY }

            if (useSarvam.value && apiKey.isNotBlank() && apiKey != "YOUR_SARVAM_API_KEY") {
                val b64 = repository.getSarvamTtsAudio(turn.translatedText, turn.targetLanguageCode, apiKey, sarvamTtsSpeakerGender.value)
                if (b64 != null) {
                    playedWithSarvam = true
                    player.playBase64Audio(b64, ttsSpeed.value) {
                        isPlaying.value = false
                        activePlayingTurnId.value = null
                    }
                }
            }

            if (!playedWithSarvam) {
                // Fallback to Android Native text-to-speech engine
                speakWithNativeTts(turn.translatedText, turn.targetLanguageCode)
            }
        }
    }

    private fun speakWithNativeTts(text: String, languageCode: String) {
        if (!isTtsInitialized || tts == null) {
            isPlaying.value = false
            activePlayingTurnId.value = null
            errorMessage.value = "Speech synthesiser engine not ready."
            return
        }

        val locale = getLocaleForCode(languageCode)
        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Log & speak using default speech parameters
            tts?.setLanguage(Locale.getDefault())
        }

        // Set speech rate before calling speak
        tts?.setSpeechRate(ttsSpeed.value)

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "translation_utterance")
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, ttsVolume.value)
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "translation_utterance")
        
        // Listen to finish
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                viewModelScope.launch {
                    isPlaying.value = false
                    activePlayingTurnId.value = null
                }
            }
            override fun onError(utteranceId: String?) {
                viewModelScope.launch {
                    isPlaying.value = false
                    activePlayingTurnId.value = null
                }
            }
        })
    }

    private fun getLocaleForCode(code: String): Locale {
        return when (code) {
            "en-IN" -> Locale("en", "IN")
            "hi-IN" -> Locale("hi", "IN")
            "bn-IN" -> Locale("bn", "IN")
            "ta-IN" -> Locale("ta", "IN")
            "te-IN" -> Locale("te", "IN")
            "kn-IN" -> Locale("kn", "IN")
            "ml-IN" -> Locale("ml", "IN")
            "mr-IN" -> Locale("mr", "IN")
            "gu-IN" -> Locale("gu", "IN")
            "pa-IN" -> Locale("pa", "IN")
            else -> Locale.getDefault()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
        } else {
            Log.e("TranslationViewModel", "Native TextToSpeech initialization failed")
        }
    }

    fun simulateCallTranslation(textInput: String, partnerLang: LanguageOption, ourLang: LanguageOption, isPartnerSpeaking: Boolean) {
        val convId = _activeConversationId.value ?: return
        isTranslating.value = true
        errorMessage.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // If partner spoke, source code is partnerLang, target code is ourLang
                // If we spoke, source code is ourLang, target code is partnerLang
                val sourceCode = if (isPartnerSpeaking) partnerLang.code else ourLang.code
                val sourceName = if (isPartnerSpeaking) partnerLang.displayName else ourLang.displayName
                val targetCode = if (isPartnerSpeaking) ourLang.code else partnerLang.code
                val targetName = if (isPartnerSpeaking) ourLang.displayName else partnerLang.displayName

                val turn = repository.processTranslation(
                    conversationId = convId,
                    inputType = "TEXT",
                    file = null,
                    typedText = textInput,
                    sourceLangCode = sourceCode,
                    sourceLangName = sourceName,
                    targetLangCode = targetCode,
                    targetLangName = targetName,
                    useSarvam = useSarvam.value,
                    sarvamApiKey = sarvamApiKey.value
                )
                withContext(Dispatchers.Main) {
                    playAudioTurn(turn)
                }
            } catch (e: Exception) {
                Log.e("TranslationViewModel", "Call simulator translation failure", e)
                withContext(Dispatchers.Main) {
                    errorMessage.value = "Call translation error: ${e.localizedMessage}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isTranslating.value = false
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        recorder.cancelRecording()
        player.stopPlaying()
        tts?.stop()
        tts?.shutdown()
    }
}

data class LanguageOption(val displayName: String, val code: String)
