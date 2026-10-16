// Copyright (c) 2026 Mizan contributors
package app.mizan.security

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import java.util.concurrent.Executor

class AppLock(private val activity: Activity, private val executor: Executor) {
    private var backgroundedAt = 0L
    private var prompting = false

    fun onStop() { backgroundedAt = System.currentTimeMillis() }

    fun onResume() {
        if (backgroundedAt == 0L || System.currentTimeMillis() - backgroundedAt < TIMEOUT_MILLIS || prompting) return
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (activity.getSystemService(BiometricManager::class.java).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            activity.moveTaskToBack(true)
            return
        }
        prompting = true
        BiometricPrompt.Builder(activity)
            .setTitle("Unlock Mizan")
            .setDescription("Use your device fingerprint, face, or screen lock")
            .setAllowedAuthenticators(authenticators)
            .build()
            .authenticate(
                CancellationSignal(),
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        prompting = false
                        backgroundedAt = 0L
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        prompting = false
                        activity.moveTaskToBack(true)
                    }
                },
            )
    }

    private companion object { const val TIMEOUT_MILLIS = 30_000L }
}
