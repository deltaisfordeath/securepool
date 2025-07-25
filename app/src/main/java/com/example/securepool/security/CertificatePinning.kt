package com.example.securepool.security

import android.content.Context
import android.util.Log
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.*

object CertificatePinning {
    
    private const val TAG = "CertificatePinning"
    private const val ENABLE_CERTIFICATE_PINNING = true // Set to false to disable during development
    
    /**
     * Creates an OkHttpClient with certificate pinning enabled.
     * This method implements both certificate pinning and custom trust manager
     * for enhanced security.
     */
    fun createSecureClient(context: Context): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
        
        if (!ENABLE_CERTIFICATE_PINNING) {
            Log.w(TAG, "Certificate pinning is DISABLED - only use in development!")
            return builder
        }
        
        return try {
            // Method 1: Certificate Pinning using SHA-256 public key hash
            val certificatePinner = getCertificatePinner()
            
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
            builder.certificatePinner(getCertificatePinner())
        }
    }
    
    /**
     * Creates a certificate pinner with the SHA-256 hash of your certificate's public key.
     * Hash generated using OpenSSL from securepool_cert.pem
     */
    private fun getCertificatePinner(): CertificatePinner {
        return CertificatePinner.Builder()
            .add("10.0.2.2", "sha256/LQYY6Uo/fFj1qLoDm9ZYbW0xBSEfSHzof5qrxvNheTY=")
            .add("localhost", "sha256/LQYY6Uo/fFj1qLoDm9ZYbW0xBSEfSHzof5qrxvNheTY=")
            .build()
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
