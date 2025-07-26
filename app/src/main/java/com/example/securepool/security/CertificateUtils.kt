package com.example.securepool.security

import android.content.Context
import android.util.Log

/**
 * Utility class to help with certificate pinning setup.
 * Run this once in development to get the SHA-256 hash of your certificate.
 */
object CertificateUtils {
    
    private const val TAG = "CertificateUtils"
    
    /**
     * Call this method in your Application class or main activity during development
     * to get the SHA-256 hash of your certificate for pinning.
     */
    fun logCertificateHash(context: Context) {
        val hash = CertificatePinning.getCertificateHash(context)
        if (hash != null) {
            Log.d(TAG, "Certificate SHA-256 Hash: sha256:$hash")
            Log.d(TAG, "Use this hash in your CertificatePinner configuration")
        } else {
            Log.e(TAG, "Failed to generate certificate hash")
        }
    }
    
    /**
     * Alternative method using OkHttp's built-in certificate pinner helper.
     * This will log the pin when a connection is made with an unpinned certificate.
     */
    fun logCertificatePinsOnConnection() {
        Log.d(TAG, "To get certificate pins from a real connection, temporarily remove")
        Log.d(TAG, "certificate pinning and check the logs when making a request.")
        Log.d(TAG, "OkHttp will log the pins you should use.")
    }
}
