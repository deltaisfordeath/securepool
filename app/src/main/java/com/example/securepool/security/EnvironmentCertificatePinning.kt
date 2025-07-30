package com.example.securepool.security

import android.content.Context
import android.util.Log
import okhttp3.CertificatePinner
import java.util.Properties

/**
 * Environment-based Certificate Pinning Configuration
 * Loads certificate pins from assets or falls back to defaults
 */
object EnvironmentCertificatePinning {

    private const val TAG = "EnvCertPinning"
    private const val CONFIG_FILE = "cert_pins.properties"

    /**
     * Creates certificate pinner from environment configuration or fallback pins
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

    /**
     * Loads certificate pins from assets/cert_pins.properties
     * Expected format:
     *   10.0.2.2.pins.0 = sha256/<hash>
     *   localhost.pins.1 = sha256/<hash>
     */
    private fun loadPinsFromConfig(context: Context): Map<String, List<String>> {
        val pins = mutableMapOf<String, List<String>>()

        try {
            context.assets.open(CONFIG_FILE).use { inputStream ->
                val properties = Properties().apply { load(inputStream) }

                val hostnames = properties.stringPropertyNames()
                    .filter { it.contains(".pins.") }
                    .map { it.substringBefore(".pins.") }
                    .distinct()

                hostnames.forEach { hostname ->
                    val hostPins = mutableListOf<String>()
                    var index = 0
                    while (true) {
                        val key = "$hostname.pins.$index"
                        val pin = properties.getProperty(key) ?: break
                        hostPins.add(pin.trim())
                        index++
                    }
                    if (hostPins.isNotEmpty()) {
                        pins[hostname] = hostPins
                    }
                }

                Log.i(TAG, "Loaded certificate pins for ${pins.size} hostnames")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load certificate pins from $CONFIG_FILE. Using fallback pins.", e)
            return getDefaultPins()
        }

        return pins.ifEmpty {
            Log.w(TAG, "No pins found in config file. Using fallback pins.")
            getDefaultPins()
        }
    }

    /**
     * Default hardcoded pins used when config file is missing or empty
     */
    private fun getDefaultPins(): Map<String, List<String>> {
        return mapOf(
            "10.0.2.2" to listOf("sha256/LQYY6Uo/fFj1qLoDm9ZYbW0xBSEfSHzof5qrxvNheTY="),
            "localhost" to listOf("sha256/LQYY6Uo/fFj1qLoDm9ZYbW0xBSEfSHzof5qrxvNheTY=")
        )
    }
}
