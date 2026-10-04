package com.jarves.mh.runtime

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.jarves.mh.data.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Global Text-To-Speech (TTS) coordinator for Mobile Harness.
 * Provides voice feedback for AI agent responses, build/task completion notifications,
 * and hands-free coding coordination.
 */
class TtsManager private constructor(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val preferences = AppPreferences(appContext)
    private var tts: TextToSpeech? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentUtteranceId = MutableStateFlow<String?>(null)
    val currentUtteranceId: StateFlow<String?> = _currentUtteranceId.asStateFlow()

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    init {
        try {
            tts = TextToSpeech(appContext, this)
        } catch (e: Exception) {
            _isAvailable.value = false
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            val available = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            _isAvailable.value = available
            if (available) {
                applyConfig()
                setupListener()
            }
        } else {
            _isAvailable.value = false
        }
    }

    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                _currentUtteranceId.value = utteranceId
            }

            override fun onDone(utteranceId: String?) {
                if (_currentUtteranceId.value == utteranceId) {
                    _isSpeaking.value = false
                    _currentUtteranceId.value = null
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (_currentUtteranceId.value == utteranceId) {
                    _isSpeaking.value = false
                    _currentUtteranceId.value = null
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (_currentUtteranceId.value == utteranceId) {
                    _isSpeaking.value = false
                    _currentUtteranceId.value = null
                }
            }
        })
    }

    fun applyConfig() {
        tts?.setSpeechRate(preferences.ttsSpeechRate)
        tts?.setPitch(preferences.ttsPitch)
    }

    fun updateSpeechRate(rate: Float) {
        preferences.ttsSpeechRate = rate
        tts?.setSpeechRate(rate)
    }

    fun updatePitch(pitch: Float) {
        preferences.ttsPitch = pitch
        tts?.setPitch(pitch)
    }

    /**
     * Speaks the given text if TTS is enabled, or forces speech for direct user requests (e.g. tapping play).
     */
    fun speak(
        text: String,
        utteranceId: String = UUID.randomUUID().toString(),
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        force: Boolean = false,
    ) {
        if (!force && !preferences.ttsEnabled) return
        if (text.isBlank()) return

        val clean = cleanMarkdownForSpeech(text)
        if (clean.isBlank()) return

        applyConfig()
        _currentUtteranceId.value = utteranceId
        _isSpeaking.value = true
        tts?.speak(clean, queueMode, null, utteranceId)
    }

    /**
     * Toggles speech playback for a specific message / utterance.
     */
    fun toggleSpeak(text: String, utteranceId: String) {
        if (_isSpeaking.value && _currentUtteranceId.value == utteranceId) {
            stop()
        } else {
            speak(text, utteranceId = utteranceId, force = true)
        }
    }

    /**
     * Speaks a short coordination announcement (e.g., "Build succeeded", "Task completed").
     */
    fun announce(message: String) {
        if (!preferences.ttsEnabled || !preferences.ttsAnnounceTasks) return
        speak(message, utteranceId = "announcement-${System.currentTimeMillis()}", queueMode = TextToSpeech.QUEUE_ADD)
    }

    /**
     * Stops any currently ongoing speech.
     */
    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
        _isSpeaking.value = false
        _currentUtteranceId.value = null
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        _isAvailable.value = false
        _isSpeaking.value = false
        _currentUtteranceId.value = null
    }

    companion object {
        @Volatile
        private var instance: TtsManager? = null

        fun getInstance(context: Context): TtsManager =
            instance ?: synchronized(this) {
                instance ?: TtsManager(context).also { instance = it }
            }

        /**
         * Cleans Markdown formatting, code fences, inline syntax, and URLs to produce
         * natural-sounding spoken audio.
         */
        fun cleanMarkdownForSpeech(text: String): String {
            return text
                // Replace multi-line code blocks with a brief spoken note
                .replace(Regex("```[\\s\\S]*?```"), " code snippet omitted. ")
                // Replace inline code `foo` with foo
                .replace(Regex("`([^`]+)`"), "$1")
                // Replace markdown links [label](url) with label
                .replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)"), "$1")
                // Replace plain URLs with "link"
                .replace(Regex("https?://\\S+"), "link")
                // Remove header markers like ###
                .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")
                // Remove bold and italic markers
                .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
                .replace(Regex("\\*([^*]+)\\*"), "$1")
                .replace(Regex("__([^_]+)__"), "$1")
                .replace(Regex("_([^_]+)_"), "$1")
                // Remove blockquote markers
                .replace(Regex("^>\\s*", RegexOption.MULTILINE), "")
                // Remove horizontal rules
                .replace(Regex("^[\\-*_]{3,}\\s*$", RegexOption.MULTILINE), "")
                // Collapse whitespace
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}
