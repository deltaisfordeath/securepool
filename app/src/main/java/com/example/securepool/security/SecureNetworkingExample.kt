package com.example.securepool.examples

import android.content.Context
import com.example.securepool.BuildConfig
import com.example.securepool.security.CertificatePinning
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Example demonstrating build-time certificate pinning usage
 */
class SecureNetworkingExample {
    
    fun createSecureRetrofitClient(context: Context): Retrofit {
        // Create secure OkHttp client with build-time certificate pinning
        val secureClient = CertificatePinning.createSecureClient(context).build()
        
        // Build Retrofit instance with secure client
        return Retrofit.Builder()
            .baseUrl(getBaseUrl())
            .client(secureClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    private fun getBaseUrl(): String {
        // Use different base URLs based on build configuration
        return if (BuildConfig.DEBUG) {
            "https://10.0.2.2:3000/" // Development server
        } else {
            "https://your-production-domain.com/" // Production server
        }
    }
    
    /**
     * Example showing how to verify certificate configuration at runtime
     */
    fun verifyCertificateConfiguration() {
        println("=== Certificate Pinning Configuration ===")
        println("Development Pin: ${if (BuildConfig.CERT_PIN_DEV.isNotEmpty()) "✅ Configured" else "❌ Missing"}")
        println("Production Pin: ${if (BuildConfig.CERT_PIN_PROD.isNotEmpty()) "✅ Configured" else "⚠️ Not Set"}")
        println("Dynamic Pinning: ${if (BuildConfig.USE_DYNAMIC_PINNING) "✅ Enabled" else "❌ Disabled"}")
        println("Build Type: ${if (BuildConfig.DEBUG) "Debug" else "Release"}")
        
        // Log the configured pins (be careful with this in production)
        if (BuildConfig.DEBUG) {
            println("Dev Pin Hash: ${BuildConfig.CERT_PIN_DEV}")
            println("Prod Pin Hash: ${BuildConfig.CERT_PIN_PROD}")
        }
    }
}
