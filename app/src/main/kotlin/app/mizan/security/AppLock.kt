// Copyright (c) 2026 Mizan contributors
package app.mizan.security

import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import app.mizan.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor

class AppLock(
    private val activity: FragmentActivity,
    private val executor: Executor,
) {
    private val gate = LockGate(SystemClock::elapsedRealtime)
    val state = gate.state
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
    private val mutablePrompting = MutableStateFlow(false)
    val prompting = mutablePrompting.asStateFlow()
    private val prompt =
        BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    mutablePrompting.value = false
                    gate.authenticated()
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    mutablePrompting.value = false
                    mutableMessage.value = errString.toString()
                }
            },
        )

    fun configure(
        enabled: Boolean,
        timeoutSeconds: Int,
    ) = gate.configure(enabled, timeoutSeconds)

    fun onBackground() = gate.onBackground()

    fun onResume() = gate.onForeground()

    fun unlock(deviceCredentialOnly: Boolean = false) {
        if (prompting.value || !state.value.enabled || !state.value.locked) return
        if (!DeviceSecurity.available(activity)) {
            mutableMessage.value = activity.getString(R.string.setup_lock_unavailable)
            return
        }
        mutablePrompting.value = true
        mutableMessage.value = null
        val authenticators =
            if (deviceCredentialOnly) {
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            } else {
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            }
        val info =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(activity.getString(R.string.lock_prompt_title))
                .setDescription(activity.getString(R.string.lock_prompt_description))
                .setAllowedAuthenticators(authenticators)
                .build()
        prompt.authenticate(info)
    }

    fun close() {
        prompt.cancelAuthentication()
    }
}
