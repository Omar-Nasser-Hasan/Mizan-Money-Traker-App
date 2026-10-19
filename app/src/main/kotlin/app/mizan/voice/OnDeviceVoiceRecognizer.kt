// Copyright (c) 2026 Mizan contributors
package app.mizan.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import app.mizan.data.settings.SettingFlag
import app.mizan.worker.WorkerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OnDeviceVoiceRecognizer(
    private val context: Context,
) {
    private var recognizer: SpeechRecognizer? = null
    private var pending: Job? = null

    fun start(
        language: String,
        onTranscript: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        destroy()
        pending =
            CoroutineScope(Dispatchers.Main).launch {
                val offline = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                val onlineAllowed =
                    EntryPointAccessors
                        .fromApplication(context.applicationContext, WorkerEntryPoint::class.java)
                        .repository()
                        .settings.snapshots
                        .first()[SettingFlag.ONLINE_VOICE]
                if (!offline && !onlineAllowed) {
                    onError(context.getString(app.mizan.R.string.p13_voice_unavailable))
                    return@launch
                }
                if (!offline && !SpeechRecognizer.isRecognitionAvailable(context)) {
                    onError(context.getString(app.mizan.R.string.p13_voice_unavailable))
                    return@launch
                }
                var fellBack = false
                recognizer =
                    (
                        if (offline) {
                            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                        } else {
                            SpeechRecognizer.createSpeechRecognizer(context)
                        }
                    ).also { speech ->
                        speech.setRecognitionListener(
                            object : RecognitionListener {
                                override fun onResults(results: android.os.Bundle) {
                                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onTranscript)
                                        ?: onError(context.getString(app.mizan.R.string.p13_voice_empty))
                                }

                                override fun onError(error: Int) {
                                    val missingPack =
                                        error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                                            error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                                    if (offline && onlineAllowed && !fellBack && missingPack &&
                                        SpeechRecognizer.isRecognitionAvailable(context)
                                    ) {
                                        fellBack = true
                                        speech.destroy()
                                        recognizer =
                                            SpeechRecognizer.createSpeechRecognizer(context).also { fallback ->
                                                fallback.setRecognitionListener(this)
                                                fallback.startListening(
                                                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                                                        .putExtra(
                                                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                                        ).putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false),
                                                )
                                            }
                                    } else {
                                        onError(context.getString(app.mizan.R.string.p13_voice_error, error))
                                    }
                                }

                                override fun onReadyForSpeech(params: android.os.Bundle) = Unit

                                override fun onBeginningOfSpeech() = Unit

                                override fun onRmsChanged(rmsdB: Float) = Unit

                                override fun onBufferReceived(buffer: ByteArray) = Unit

                                override fun onEndOfSpeech() = Unit

                                override fun onPartialResults(partialResults: android.os.Bundle) = Unit

                                override fun onEvent(
                                    eventType: Int,
                                    params: android.os.Bundle,
                                ) = Unit
                            },
                        )
                        speech.startListening(
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                                .putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                ).putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, offline),
                        )
                    }
            }
    }

    fun destroy() {
        pending?.cancel()
        pending = null
        recognizer?.destroy()
        recognizer = null
    }
}
