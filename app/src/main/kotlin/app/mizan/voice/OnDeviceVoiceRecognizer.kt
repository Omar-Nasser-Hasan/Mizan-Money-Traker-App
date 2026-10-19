// Copyright (c) 2026 Mizan contributors
package app.mizan.voice

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
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
    private var continuousSession = false
    private var stopRequested = false
    private var completed = false
    private var onStoppedCallback: () -> Unit = {}
    private val mainHandler = Handler(Looper.getMainLooper())

    fun start(
        language: String,
        onTranscript: (String) -> Unit,
        onError: (String) -> Unit,
        onPartialTranscript: (String) -> Unit = {},
        continuous: Boolean = false,
        onStopped: () -> Unit = {},
    ) {
        destroy()
        continuousSession = continuous
        stopRequested = false
        completed = false
        onStoppedCallback = onStopped
        pending =
            CoroutineScope(Dispatchers.Main).launch {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    onError(context.getString(app.mizan.R.string.p13_voice_unavailable))
                    return@launch
                }
                val repository =
                    EntryPointAccessors.fromApplication(context.applicationContext, WorkerEntryPoint::class.java).repository()
                val onlineOnly = repository.settings.snapshots.first()[SettingFlag.ONLINE_VOICE]
                var onlineActive = false
                lateinit var begin: (Boolean) -> Unit

                fun listenAgain(useOnline: Boolean) {
                    if (stopRequested || completed) return
                    mainHandler.postDelayed({ if (!stopRequested && !completed) begin(useOnline) }, 250)
                }

                fun finish() {
                    if (completed) return
                    completed = true
                    onStoppedCallback()
                }

                begin = { useOnline ->
                    onlineActive = useOnline
                    val speech = createRecognizer(useOnline)
                    recognizer = speech
                    speech.also {
                        it.setRecognitionListener(
                            object : RecognitionListener {
                                override fun onResults(results: android.os.Bundle) {
                                    val phrase = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                                    if (!phrase.isNullOrBlank()) onTranscript(phrase)
                                    when {
                                        continuousSession && !stopRequested -> {
                                            listenAgain(useOnline)
                                        }

                                        phrase.isNullOrBlank() -> {
                                            onError(
                                                context.getString(app.mizan.R.string.p13_voice_empty),
                                            ).also { finish() }
                                        }

                                        else -> {
                                            finish()
                                        }
                                    }
                                }

                                override fun onError(error: Int) {
                                    val missingPack =
                                        error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                                            error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                                    if (!onlineActive && !onlineOnly && missingPack && !stopRequested) {
                                        recognizer?.destroy()
                                        recognizer = null
                                        begin(true)
                                        return
                                    }
                                    if (continuousSession && !stopRequested &&
                                        (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                                    ) {
                                        listenAgain(useOnline)
                                        return
                                    }
                                    if (stopRequested) {
                                        finish()
                                        return
                                    }
                                    onError(
                                        when {
                                            missingPack -> {
                                                context.getString(app.mizan.R.string.p14_voice_language_unavailable)
                                            }

                                            error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                                context.getString(app.mizan.R.string.p14_voice_no_speech)
                                            }

                                            else -> {
                                                context.getString(app.mizan.R.string.p13_voice_error, error)
                                            }
                                        },
                                    )
                                    completed = true
                                }

                                override fun onReadyForSpeech(params: android.os.Bundle) = Unit

                                override fun onBeginningOfSpeech() = Unit

                                override fun onRmsChanged(rmsdB: Float) = Unit

                                override fun onBufferReceived(buffer: ByteArray) = Unit

                                override fun onEndOfSpeech() = Unit

                                override fun onPartialResults(partialResults: android.os.Bundle) {
                                    partialResults
                                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                        ?.firstOrNull()
                                        ?.let(onPartialTranscript)
                                }

                                override fun onEvent(
                                    eventType: Int,
                                    params: android.os.Bundle,
                                ) = Unit
                            },
                        )
                        it.startListening(
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                                .putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                ).putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !useOnline),
                        )
                    }
                }

                if (onlineOnly || Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                ) {
                    begin(true)
                } else {
                    begin(false)
                }
            }
    }

    fun stopListening() {
        stopRequested = true
        recognizer?.stopListening()
    }

    @SuppressLint("NewApi")
    private fun createRecognizer(useOnline: Boolean): SpeechRecognizer =
        if (useOnline || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        }

    fun destroy() {
        pending?.cancel()
        pending = null
        stopRequested = true
        completed = true
        recognizer?.destroy()
        recognizer = null
        mainHandler.removeCallbacksAndMessages(null)
    }
}
