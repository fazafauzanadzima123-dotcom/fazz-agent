package com.fazz.agent.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Wraps Android TextToSpeech. Speaks plain text (markdown symbols removed). */
class TextToSpeechController(context: Context) {
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()
    private var ready = false
    private var pending: String? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.getDefault()
            pending?.let { speak(it) }
            pending = null
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { _speaking.value = true }
            override fun onDone(utteranceId: String?) { _speaking.value = false }
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { _speaking.value = false }
            override fun onError(utteranceId: String?, errorCode: Int) { _speaking.value = false }
        })
    }

    fun speak(text: String) {
        val clean = stripMarkdown(text).take(TextToSpeech.getMaxSpeechInputLength() - 1)
        if (clean.isBlank()) return
        if (!ready) { pending = clean; return }
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "fazz-${System.currentTimeMillis()}")
    }

    fun stop() { tts.stop(); _speaking.value = false }

    fun shutdown() { tts.stop(); tts.shutdown() }

    companion object {
        fun stripMarkdown(s: String): String = s
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("[*_`#>]+"), "")
            .replace(Regex("(?m)^\\s*[-•]\\s+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
