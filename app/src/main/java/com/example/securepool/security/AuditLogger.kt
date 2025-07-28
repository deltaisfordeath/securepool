package com.example.securepool.security

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AuditLogger {

    private const val TAG = "AUDIT_LOG"

    private fun getTimestamp(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return formatter.format(Date())
    }

    fun logEvent(event: String, metadata: Map<String, String>? = null) {
        val timestamp = getTimestamp()
        val details = metadata?.entries
            ?.joinToString(" ") { "${it.key}=${it.value}" }
            ?.trim()
            ?: ""
        val message = "[$timestamp] EVENT: $event $details"
        Log.i(TAG, message)

        // Optional file logging:
        // persistToFile(message)
    }

    // Stub for future file persistence (if required)
    // private fun persistToFile(message: String) {
    //     // Write to local storage or encrypted file if needed
    // }
}
