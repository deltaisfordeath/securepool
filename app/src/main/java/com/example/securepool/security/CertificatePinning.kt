package com.example.securepool.security

import android.content.Context
import android.util.Log
import com.example.securepool.BuildConfig
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.*

object CertificatePinning {
    
    private const val TAG = "CertificatePinning"
    
    /**
     * Creates an OkHttpClient with certificate pinning enabled.
     * This method implements both certificate pinning and custom trust manager
     * for enhanced security.
     */
    fun createSecureClient(context: Context): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
        
        // Debug-only disable check - NEVER active in production builds
        if (BuildConfig.DEBUG && BuildConfig.DEBUG_DISABLE_CERT_PINNING) {
            Log.w(TAG, "⚠️ Certificate pinning is DISABLED - DEBUG BUILD ONLY!")
            Log.w(TAG, "This should NEVER happen in production builds")
            return builder
        }
        
        return try {
            // Method 1: Dynamic Certificate Pinning
            val certificatePinner = getCertificatePinner(context)
            
            // Method 2: Custom Trust Manager (more secure for development/testing)
            val trustManager = createCustomTrustManager(context)
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(trustManager), null)
            
            builder
                .certificatePinner(certificatePinner)
                .sslSocketFactory(sslContext.socketFactory, trustManager)
                .hostnameVerifier(createHostnameVerifier())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup certificate pinning: ${e.message}", e)
            // Fallback to basic certificate pinning only
            builder.certificatePinner(getCertificatePinner(context))
        }
    }
    
    /**
     * Creates a certificate pinner with build-time configuration.
     * Uses certificate hashes from gradle.properties for flexible management.
     */
    private fun getCertificatePinner(context: Context): CertificatePinner {
        Log.d(TAG, "Creating certificate pinner with build-time configuration")
        
        val builder = CertificatePinner.Builder()
        
        // Add development certificate pins
        if (BuildConfig.CERT_PIN_DEV.isNotEmpty()) {
            val devPin = "sha256/${BuildConfig.CERT_PIN_DEV}"
            builder.add("10.0.2.2", devPin)
            builder.add("localhost", devPin)
            Log.d(TAG, "Added development certificate pin for 10.0.2.2 and localhost")
        }
        
        // Add production certificate pins
        if (BuildConfig.CERT_PIN_PROD.isNotEmpty()) {
            val prodPin = "sha256/${BuildConfig.CERT_PIN_PROD}"
            builder.add("your-production-domain.com", prodPin)  // Update with your production domain
            Log.d(TAG, "Added production certificate pin")
        }
        
        // Fallback to hardcoded pins if no build config is available
        if (BuildConfig.CERT_PIN_DEV.isEmpty() && BuildConfig.CERT_PIN_PROD.isEmpty()) {
            Log.w(TAG, "No certificate pins found in build config, using fallback")
            builder.add("10.0.2.2", "sha256/bWsw3WqdtgiEWsOtKrjFEOAjebBzD4GruTg+uO0mQ8g=")
            builder.add("localhost", "sha256/bWsw3WqdtgiEWsOtKrjFEOAjebBzD4GruTg+uO0mQ8g=")
        }
        
        return builder.build()
    }
    
    /**
     * Creates a custom trust manager that only trusts our specific certificate.
     * This is more secure than relying on the system's trust store.
     */
    private fun createCustomTrustManager(context: Context): X509TrustManager {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val certificate: X509Certificate
        
        context.assets.open("securepool_cert.pem").use { inputStream ->
            certificate = certificateFactory.generateCertificate(inputStream) as X509Certificate
        }
        
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setCertificateEntry("securepool", certificate)
        
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(keyStore)
        
        return trustManagerFactory.trustManagers[0] as X509TrustManager
    }
    
    /**
     * Creates a hostname verifier that accepts our specific hostnames.
     */
    private fun createHostnameVerifier(): HostnameVerifier {
        return HostnameVerifier { hostname, _ ->
            val isValid = hostname == "10.0.2.2" || hostname == "localhost"
            if (!isValid) {
                Log.w(TAG, "Hostname verification failed for: $hostname")
            }
            isValid
        }
    }
    
    /**
     * Utility method to get the SHA-256 hash of a certificate for pinning.
     * Use this in development to get the hash of your certificate.
     */
    fun getCertificateHash(context: Context): String? {
        return try {
            val certificateFactory = CertificateFactory.getInstance("X.509")
            val certificate: X509Certificate
            
            context.assets.open("securepool_cert.pem").use { inputStream ->
                certificate = certificateFactory.generateCertificate(inputStream) as X509Certificate
            }
            
            val publicKey = certificate.publicKey.encoded
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(publicKey)
            android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate certificate hash: ${e.message}", e)
            null
        }
    }
    
    /**
     * Alternative method for getting certificate pins using OkHttp's built-in helper.
     * This will log the pins when a connection is made without pinning configured.
     */
    fun getEmptyPinnerForLogging(): CertificatePinner {
        return CertificatePinner.Builder().build()
    }
}
