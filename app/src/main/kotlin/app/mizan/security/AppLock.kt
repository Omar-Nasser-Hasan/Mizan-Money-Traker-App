// Copyright (c) 2026 Mizan contributors
package app.mizan.security

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import java.util.concurrent.Executor

class AppLock(private val activity: Activity, private val executor: Executor) {
    private var backgroundedAt = 0L

    fun onStop() { backgroundedAt = System.currentTimeMillis() }

    fun onResume() {
        if (backgroundedAt == 0L || System.currentTimeMillis() - backgroundedAt < TIMEOUT_MILLIS) return
        BiometricPrompt.Builder(activity)
            .setTitle("Unlock Mizan")
            .setDescription("Use your device fingerprint, face, or screen lock")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
            .authenticate(CancellationSignal(), executor, object : BiometricPrompt.AuthenticationCallback() {})
    }

    private companion object { const val TIMEOUT_MILLIS = 30_000L }
}
