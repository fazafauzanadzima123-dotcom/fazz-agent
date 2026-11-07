package com.fazz.agent.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed interface VoiceInputState {
    data object Idle : VoiceInputState
    /** Microphone is open. [level] is 0..1 for the visual meter. */
    data class Listening(val level: Float, val partial: String) : VoiceInputState
    data object Processing : VoiceInputState
    data class Error(val message: String) : VoiceInputState
}

/**
 * Wraps Android's SpeechRecognizer (foreground only; the mic is open only while the user is
 * actively listening and the UI shows a clear "listening" indicator). Must be used on the main thread.
 */
class SpeechInputController(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private val _state = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()
    private var onFinal: ((String) -> Unit)? = null

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(onResult: (String) -> Unit) {
        if (!isAvailable) {
            _state.value = VoiceInputState.Error("Pengenalan suara tidak tersedia di HP ini. Pasang/aktifkan layanan suara Google.")
            return
        }
        stop()
        onFinal = onResult
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        _state.value = VoiceInputState.Listening(0f, "")
        r.startListening(intent)
    }

    /** Stop listening and process what was heard so far. */
    fun finish() { recognizer?.stopListening() }

    /** Abort and release the microphone immediately. */
    fun stop() {
        recognizer?.run { cancel(); destroy() }
        recognizer = null
        if (_state.value !is VoiceInputState.Error) _state.value = VoiceInputState.Idle
    }

    fun clearError() { if (_state.value is VoiceInputState.Error) _state.value = VoiceInputState.Idle }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            val s = _state.value
            if (s is VoiceInputState.Listening) _state.value = s.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { _state.value = VoiceInputState.Processing }
        override fun onError(error: Int) {
            recognizer?.destroy(); recognizer = null
            _state.value = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceInputState.Error("Suara tidak terdengar jelas. Ketuk mic dan coba lagi.")
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceInputState.Error("Izin mikrofon dibutuhkan untuk input suara.")
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> VoiceInputState.Error("Pengenalan suara butuh koneksi internet.")
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceInputState.Error("Pengenal suara sedang sibuk. Coba lagi sebentar.")
                else -> VoiceInputState.Error("Input suara gagal (kode $error).")
            }
        }
        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            recognizer?.destroy(); recognizer = null
            _state.value = VoiceInputState.Idle
            if (text.isNotBlank()) onFinal?.invoke(text)
            else _state.value = VoiceInputState.Error("Suara tidak terdengar jelas. Ketuk mic dan coba lagi.")
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
            val s = _state.value
            if (s is VoiceInputState.Listening) _state.value = s.copy(partial = text)
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
