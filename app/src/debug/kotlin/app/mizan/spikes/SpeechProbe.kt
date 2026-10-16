// Copyright (c) 2026 Mizan contributors
package app.mizan.spikes

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.concurrent.Executor

data class SpeechProbeState(
    val languageTag: String,
    val onDeviceAvailable: Boolean,
    val support: String = "Not checked",
    val transcript: String = "",
    val latencyMillis: Long? = null,
)

class SpeechProbe(
    private val context: Context,
    private val update: (SpeechProbeState) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private var startedAt = 0L

    fun check(languageTag: String) {
        val available = SpeechRecognizer.isRecognitionAvailable(context)
        val initial = SpeechProbeState(languageTag, available)
        if (!available) {
            update(initial.copy(support = "No recognition service is installed"))
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            update(initial.copy(onDeviceAvailable = false, support = "On-device recognition is unavailable on this emulator"))
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            update(initial.copy(support = "Android 13+ is required for offline-model support checks"))
            return
        }
        runCatching {
            val probe = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            val executor = Executor { command -> command.run() }
            probe.checkRecognitionSupport(
                intentFor(languageTag),
                executor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                        update(initial.copy(support = recognitionSupport.toString()))
                        probe.destroy()
                    }

                    override fun onError(error: Int) {
                        update(initial.copy(support = "Support check error: $error"))
                        probe.destroy()
                    }
                },
            )
        }.onFailure { error ->
            update(
                initial.copy(
                    onDeviceAvailable = false,
                    support = "On-device support check failed: ${error.message ?: error::class.simpleName}",
                ),
            )
        }
    }

    fun download(languageTag: String) {
        if (!onDeviceServiceAvailable(languageTag)) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            update(SpeechProbeState(languageTag, false, "Android 14+ is required for model download"))
            return
        }
        runCatching {
            val probe = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            val executor = Executor { command -> command.run() }
            probe.triggerModelDownload(
                intentFor(languageTag),
                executor,
                object : ModelDownloadListener {
                    override fun onProgress(completedPercent: Int) {
                        update(SpeechProbeState(languageTag, true, "Model download: $completedPercent%"))
                    }

                    override fun onScheduled() {
                        update(SpeechProbeState(languageTag, true, "Model download scheduled"))
                    }

                    override fun onSuccess() {
                        update(SpeechProbeState(languageTag, true, "Model download complete"))
                        probe.destroy()
                    }

                    override fun onError(error: Int) {
                        update(SpeechProbeState(languageTag, true, "Model download error: $error"))
                        probe.destroy()
                    }
                },
            )
        }.onFailure { error ->
            update(SpeechProbeState(languageTag, false, "Model download failed: ${error.message ?: error::class.simpleName}"))
        }
    }

    fun listen(languageTag: String) {
        if (!onDeviceServiceAvailable(languageTag)) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            update(SpeechProbeState(languageTag, false, "Android 12+ is required for on-device recognition"))
            return
        }
        runCatching {
            recognizer?.destroy()
            val probe = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer = probe
            startedAt = SystemClock.elapsedRealtime()
            probe.setRecognitionListener(
                object : RecognitionListener {
                    override fun onResults(results: android.os.Bundle) {
                        val transcript =
                            results
                                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                .orEmpty()
                        update(SpeechProbeState(languageTag, true, "On-device result", transcript, elapsed()))
                    }

                    override fun onError(error: Int) {
                        update(SpeechProbeState(languageTag, true, "Recognition error: $error", latencyMillis = elapsed()))
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
            probe.startListening(intentFor(languageTag))
        }.onFailure { error ->
            recognizer?.destroy()
            recognizer = null
            update(SpeechProbeState(languageTag, false, "On-device recognition failed: ${error.message ?: error::class.simpleName}"))
        }
    }

    fun close() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun elapsed(): Long = SystemClock.elapsedRealtime() - startedAt

    private fun onDeviceServiceAvailable(languageTag: String): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            update(SpeechProbeState(languageTag, false, "No recognition service is installed"))
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            update(SpeechProbeState(languageTag, false, "On-device recognition is unavailable on this emulator"))
            return false
        }
        return true
    }

    private fun intentFor(languageTag: String) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
}
