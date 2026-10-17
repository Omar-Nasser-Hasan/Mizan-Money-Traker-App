// Copyright (c) 2026 Mizan contributors
package app.mizan.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class OnDeviceVoiceRecognizer(
    private val context: Context,
) {
    private var recognizer: SpeechRecognizer? = null

    fun start(
        language: String,
        onTranscript: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            onError("On-device recognition is unavailable. SPIKE-V1 has not approved an online fallback.")
            return
        }
        destroy()
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also { speech ->
            speech.setRecognitionListener(
                object : RecognitionListener {
                    override fun onResults(results: android.os.Bundle) {
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onTranscript)
                            ?: onError("No transcript was returned")
                    }

                    override fun onError(error: Int) = onError("Recognition error $error")
                    override fun onReadyForSpeech(params: android.os.Bundle) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onPartialResults(partialResults: android.os.Bundle) = Unit
                    override fun onEvent(eventType: Int, params: android.os.Bundle) = Unit
                },
            )
            speech.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true),
            )
        }
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }
}
