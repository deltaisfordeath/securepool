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

    fun createSecureClient(context: Context): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()

        if (BuildConfig.DEBUG && BuildConfig.DEBUG_DISABLE_CERT_PINNING) {
            Log.w(TAG, "WARNING: Certificate pinning is DISABLED - DEBUG BUILD ONLY!")
            return builder
        }

        return try {
            val certificatePinner = getCertificatePinner(context)
            val trustManager = createCustomTrustManager(context)
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(trustManager), null)

            builder
                .certificatePinner(certificatePinner)
                .sslSocketFactory(sslContext.socketFactory, trustManager)
                .hostnameVerifier(createHostnameVerifier())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup certificate pinning: ${e.message}", e)
            builder.certificatePinner(getCertificatePinner(context))
        }
    }

    private fun getCertificatePinner(context: Context): CertificatePinner {
        Log.d(TAG, "Creating certificate pinner with build-time configuration")
        val builder = CertificatePinner.Builder()

        if (BuildConfig.CERT_PIN_DEV.isNotEmpty()) {
            val devPin = "sha256/${BuildConfig.CERT_PIN_DEV}"
            builder.add("10.0.2.2", devPin)
            builder.add("localhost", devPin)
            Log.d(TAG, "Added development certificate pin from BuildConfig")
        }

        if (BuildConfig.CERT_PIN_PROD.isNotEmpty() &&
            !BuildConfig.CERT_PIN_PROD.startsWith("PLACEHOLDER") &&
            !BuildConfig.PRODUCTION_DOMAIN.contains("your-production-domain")) {
            val prodPin = "sha256/${BuildConfig.CERT_PIN_PROD}"
            builder.add(BuildConfig.PRODUCTION_DOMAIN, prodPin)
            Log.d(TAG, "Added production certificate pin for ${BuildConfig.PRODUCTION_DOMAIN}")
        }

        // ✅ Fallback only if nothing is configured
        if (BuildConfig.CERT_PIN_DEV.isEmpty() &&
            (BuildConfig.CERT_PIN_PROD.isEmpty() || BuildConfig.CERT_PIN_PROD.startsWith("PLACEHOLDER"))) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Using emergency fallback certificate pin (development only)")
                val fallbackPin = "sha256/LQYY6Uo/fFj1qLoDm9ZYbW0xBSEfSHzof5qrxvNheTY="
                builder.add("10.0.2.2", fallbackPin)
                builder.add("localhost", fallbackPin)
            } else {
                Log.e(TAG, "No valid certificate pins configured for production build - configuration error")
                throw IllegalStateException("Certificate pinning cannot be initialized: no valid pins configured")
            }
        }

        return builder.build()
    }

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

    private fun createHostnameVerifier(): HostnameVerifier {
        return HostnameVerifier { hostname, _ ->
            val isValid = hostname == "10.0.2.2" ||
                    hostname == "localhost" ||
                    (BuildConfig.PRODUCTION_DOMAIN.isNotEmpty() &&
                            !BuildConfig.PRODUCTION_DOMAIN.contains("your-production-domain") &&
                            hostname == BuildConfig.PRODUCTION_DOMAIN)
            if (!isValid) {
                Log.w(TAG, "Hostname verification failed for: $hostname")
            }
            isValid
        }
    }

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

    fun getEmptyPinnerForLogging(): CertificatePinner {
        return CertificatePinner.Builder().build()
    }
}
