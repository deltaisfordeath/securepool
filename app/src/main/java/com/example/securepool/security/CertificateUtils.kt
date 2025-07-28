package com.example.securepool.security

import android.content.Context
import android.util.Log
import com.example.securepool.BuildConfig

/**
 * Utility class to assist with certificate pinning setup.
 * Use this during development to extract the SHA-256 hash of the server certificate.
 */
object CertificateUtils {

    private const val TAG = "CertificateUtils"

    /**
     * Logs the SHA-256 hash of the certificate used in assets (securepool_cert.pem).
     * You can run this from your Application class or main activity *during development*.
     */
    fun logCertificateHash(context: Context) {
        if (!BuildConfig.DEBUG) {
            Log.w(TAG, "⚠️ CertificateUtils should only be used in DEBUG builds!")
        }

        val hash = CertificatePinning.getCertificateHash(context)
        if (hash != null) {
            Log.i(TAG, "📌 Certificate SHA-256 Hash: sha256:$hash")
            Log.i(TAG, "✅ Use this hash in your CertificatePinner configuration.")
        } else {
            Log.e(TAG, "❌ Failed to generate certificate hash")
        }
    }

    /**
     * Instructs developers on how to extract certificate pins dynamically from OkHttp logs.
     * This is useful when you don't have access to the certificate file directly.
     */
    fun logCertificatePinsOnConnection() {
        Log.i(TAG, "ℹ️ To get certificate pins from a real HTTPS connection:")
        Log.i(TAG, "   1. Temporarily disable certificate pinning.")
        Log.i(TAG, "   2. Make a network request with OkHttp.")
        Log.i(TAG, "   3. Check Logcat for: 'Certificate pinning failure!' → Suggested pins.")
    }
}
