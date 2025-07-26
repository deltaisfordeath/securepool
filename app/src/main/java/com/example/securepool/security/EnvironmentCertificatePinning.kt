package com.example.securepool.security

import android.content.Context
import android.util.Log
import okhttp3.CertificatePinner
import java.util.Properties

/**
 * Environment-based Certificate Pinning Configuration
 * Loads certificate pins from build configuration or external files
 */
object EnvironmentCertificatePinning {
    
    private const val TAG = "EnvCertPinning"
    private const val CONFIG_FILE = "cert_pins.properties"
    
    /**
     * Creates certificate pinner from environment configuration
     */
    fun createFromEnvironment(context: Context): CertificatePinner {
        val pins = loadPinsFromConfig(context)
        
        val builder = CertificatePinner.Builder()
        pins.forEach { (hostname, pinList) ->
            pinList.forEach { pin ->
                builder.add(hostname, pin)
                Log.d(TAG, "Added pin for $hostname: $pin")
            }
        }
        
        return builder.build()
    }
    
    private fun loadPinsFromConfig(context: Context): Map<String, List<String>> {
        val pins = mutableMapOf<String, List<String>>()
        
        try {
            // Try to load from assets/cert_pins.properties
            val inputStream = context.assets.open(CONFIG_FILE)
            val properties = Properties()
            properties.load(inputStream)
            
            // Parse properties like: hostname.pins.0=sha256/hash1, hostname.pins.1=sha256/hash2
            val hostnames = properties.stringPropertyNames()
                .map { it.substringBefore(".pins.") }
                .distinct()
            
            hostnames.forEach { hostname ->
                val hostPins = mutableListOf<String>()
                var index = 0
                while (true) {
                    val pin = properties.getProperty("$hostname.pins.$index")
                    if (pin != null) {
                        hostPins.add(pin.trim())
                        index++
                    } else {
                        break
                    }
                }
                if (hostPins.isNotEmpty()) {
                    pins[hostname] = hostPins
                }
            }
            
            inputStream.close()
            Log.i(TAG, "Loaded certificate pins for ${pins.size} hostnames")
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load certificate pins from config, using defaults", e)
            // Fallback to build config or hardcoded values
            return getDefaultPins()
        }
        
        return pins.ifEmpty { getDefaultPins() }
    }
    
    private fun getDefaultPins(): Map<String, List<String>> {
        return mapOf(
            "10.0.2.2" to listOf("sha256/bWsw3WqdtgiEWsOtKrjFEOAjebBzD4GruTg+uO0mQ8g="),
            "localhost" to listOf("sha256/bWsw3WqdtgiEWsOtKrjFEOAjebBzD4GruTg+uO0mQ8g=")
        )
    }
}
