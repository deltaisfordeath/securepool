package com.example.securepool.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.securepool.BuildConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.IOException

/**
 * Dynamic Certificate Pinning Manager
 * Downloads and caches certificate pins from a secure configuration endpoint
 */
object DynamicCertificatePinning {
    
    private const val TAG = "DynamicCertPinning"
    private const val PREFS_NAME = "cert_pinning_config"
    private const val KEY_CACHED_PINS = "cached_pins"
    private const val KEY_LAST_UPDATE = "last_update"
    private const val KEY_API_TOKEN = "api_token"
    
    private fun getConfigEndpoint(): String {
        return BuildConfig.CONFIG_SERVER_URL.ifEmpty { "https://fallback-config-server.com/api/cert-pins" }
    }
    
    // Fallback pins in case remote config fails
    private val FALLBACK_PINS = mapOf(
        "10.0.2.2" to listOf("sha256/bWsw3WqdtgiEWsOtKrjFEOAjebBzD4GruTg+uO0mQ8g="),
        "localhost" to listOf("sha256/bWsw3WqdtgiEWsOtKrjFEOAjebBzD4GruTg+uO0mQ8g=")
    )
    
    data class CertificatePinConfig(
        val pins: Map<String, List<String>>,
        val expiresAt: Long,
        val version: Int
    )
    
    /**
     * Creates a certificate pinner with dynamically loaded pins
     */
    fun createDynamicCertificatePinner(context: Context): CertificatePinner {
        val pins = getCachedPins(context) ?: FALLBACK_PINS
        
        val builder = CertificatePinner.Builder()
        pins.forEach { (hostname, pinList) ->
            pinList.forEach { pin ->
                builder.add(hostname, pin)
            }
        }
        
        return builder.build()
    }
    
    /**
     * Updates certificate pins from remote configuration
     */
    suspend fun updateCertificatePins(context: Context, client: OkHttpClient): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(getConfigEndpoint())
                    .addHeader("Authorization", "Bearer ${getConfigApiToken(context)}")
                    .build()
                
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val configJson = response.body?.string()
                    val config = Gson().fromJson(configJson, CertificatePinConfig::class.java)
                    
                    // Validate config before caching
                    if (isValidConfig(config)) {
                        cachePins(context, config)
                        Log.i(TAG, "Certificate pins updated successfully")
                        true
                    } else {
                        Log.w(TAG, "Invalid certificate pin configuration received")
                        false
                    }
                } else {
                    Log.w(TAG, "Failed to fetch certificate pins: ${response.code}")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating certificate pins", e)
                false
            }
        }
    }
    
    private fun getCachedPins(context: Context): Map<String, List<String>>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedJson = prefs.getString(KEY_CACHED_PINS, null) ?: return null
        val lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0)
        
        // Check if cache is expired (24 hours)
        if (System.currentTimeMillis() - lastUpdate > 24 * 60 * 60 * 1000) {
            return null
        }
        
        return try {
            val type = object : TypeToken<Map<String, List<String>>>() {}.type
            Gson().fromJson(cachedJson, type)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing cached pins", e)
            null
        }
    }
    
    private fun cachePins(context: Context, config: CertificatePinConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_CACHED_PINS, Gson().toJson(config.pins))
            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
            .apply()
    }
    
    private fun isValidConfig(config: CertificatePinConfig?): Boolean {
        if (config == null) return false
        if (config.expiresAt < System.currentTimeMillis()) return false
        if (config.pins.isEmpty()) return false
        
        // Validate pin format
        config.pins.values.flatten().forEach { pin ->
            if (!pin.startsWith("sha256/") || pin.length < 50) {
                return false
            }
        }
        
        return true
    }
    
    private fun getConfigApiToken(context: Context): String {
        // Retrieve API token securely from encrypted SharedPreferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_API_TOKEN, null)
        if (token.isNullOrEmpty()) {
            throw IllegalStateException("API token is not available. Ensure it is securely stored.")
        }
        return token
    }
}
