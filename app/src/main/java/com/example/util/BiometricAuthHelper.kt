package com.example.util

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat

object BiometricAuthHelper {

    fun isBiometricAvailable(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val mgr = context.getSystemService(Context.BIOMETRIC_SERVICE) as? BiometricManager
                val res = mgr?.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                res == BiometricManager.BIOMETRIC_SUCCESS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val mgr = context.getSystemService(Context.BIOMETRIC_SERVICE) as? BiometricManager
                @Suppress("DEPRECATION")
                mgr?.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
            } else {
                val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                km?.isDeviceSecure == true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun authenticate(
        context: Context,
        title: String = "Déverrouillage Raven",
        subtitle: String = "Confirmez votre empreinte ou visage",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val executor = ContextCompat.getMainExecutor(context)
                val cancellationSignal = CancellationSignal()
                val builder = BiometricPrompt.Builder(context)
                    .setTitle(title)
                    .setSubtitle(subtitle)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                } else {
                    @Suppress("DEPRECATION")
                    builder.setNegativeButton("Utiliser le code", executor) { _, _ ->
                        onError("Saisissez votre code personnel.")
                    }
                }

                val prompt = builder.build()
                prompt.authenticate(
                    cancellationSignal,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                            onSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            onError(errString?.toString()?.ifBlank { null } ?: "Authentification annulée.")
                        }

                        override fun onAuthenticationFailed() {
                            onError("Empreinte ou visage non reconnu. Réessayez.")
                        }
                    }
                )
                return
            } catch (e: Exception) {
                onError(e.message ?: "Biométrie indisponible sur cet appareil.")
                return
            }
        }
        onError("Biométrie non supportée sur cette version Android (utilisez votre code).")
    }
}
