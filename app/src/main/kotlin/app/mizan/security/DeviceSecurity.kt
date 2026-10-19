// Copyright (c) 2026 Mizan contributors
package app.mizan.security

import android.content.Context
import androidx.biometric.BiometricManager

object DeviceSecurity {
    fun available(context: Context): Boolean =
        BiometricManager.from(context)?.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        ) == BiometricManager.BIOMETRIC_SUCCESS
}
