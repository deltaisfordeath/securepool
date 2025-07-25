package com.example.securepool.security

import android.content.Cont            if (isSuccessful) {
                Log.i(tag, "✅ Unpinned connection test PASSED")
                TestResult.Success("Server is reachable without pinning")
            } else {
                Log.w(tag, "⚠️ Unpinned connection made but server returned ${response.code()}")
                TestResult.Warning("Server reachable but returned ${response.code()}")
            }port android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Test class to verify certificate pinning functionality.
 * Use this during development to test your certificate pinning setup.
 */
class CertificatePinningTester(private val context: Context) {
    
    private val tag = "CertPinningTest"
    
    /**
     * Test certificate pinning with the configured client.
     * This should succeed if pinning is configured correctly.
     */
    suspend fun testPinnedConnection(): TestResult = withContext(Dispatchers.IO) {
        try {
            val client = CertificatePinning.createSecureClient(context).build()
            val request = Request.Builder()
                .url("https://10.0.2.2:443/api/test") // Adjust URL as needed
                .build()
            
            val response = client.newCall(request).execute()
            val isSuccessful = response.isSuccessful
            response.close()
            
            if (isSuccessful) {
                Log.i(tag, "✅ Certificate pinning test PASSED - Connection successful")
                TestResult.Success("Certificate pinning is working correctly")
            } else {
                Log.w(tag, "⚠️ Connection made but server returned ${response.code()}")
                TestResult.Warning("Connection made but server returned ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ Certificate pinning test FAILED", e)
            when {
                e.message?.contains("pin") == true -> 
                    TestResult.PinningFailure("Certificate pinning failed: ${e.message}")
                e.message?.contains("hostname") == true -> 
                    TestResult.HostnameFailure("Hostname verification failed: ${e.message}")
                e is IOException -> 
                    TestResult.NetworkFailure("Network error: ${e.message}")
                else -> 
                    TestResult.UnknownFailure("Unknown error: ${e.message}")
            }
        }
    }
    
    /**
     * Test connection without certificate pinning (for comparison).
     * This helps verify that the server is reachable.
     */
    suspend fun testUnpinnedConnection(): TestResult = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder().build()
            val request = Request.Builder()
                .url("https://10.0.2.2:443/api/test") // Adjust URL as needed
                .build()
            
            val response = client.newCall(request).execute()
            val isSuccessful = response.isSuccessful
            response.close()
            
            if (isSuccessful) {
                Log.i(tag, "✅ Unpinned connection test PASSED")
                TestResult.Success("Server is reachable without pinning")
            } else {
                Log.w(tag, "⚠️ Unpinned connection made but server returned ${response.code}")
                TestResult.Warning("Server reachable but returned ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ Unpinned connection test FAILED", e)
            TestResult.NetworkFailure("Cannot reach server: ${e.message}")
        }
    }
    
    /**
     * Get certificate hash for logging/debugging.
     */
    fun logCertificateInfo() {
        val hash = CertificatePinning.getCertificateHash(context)
        if (hash != null) {
            Log.i(tag, "📋 Certificate SHA-256 Hash: sha256:$hash")
            Log.i(tag, "📋 Add this to your CertificatePinner:")
            Log.i(tag, "📋 .add(\"10.0.2.2\", \"sha256:$hash\")")
            Log.i(tag, "📋 .add(\"localhost\", \"sha256:$hash\")")
        } else {
            Log.e(tag, "❌ Failed to get certificate hash - check certificate file in assets")
        }
    }
    
    sealed class TestResult {
        data class Success(val message: String) : TestResult()
        data class Warning(val message: String) : TestResult()
        data class PinningFailure(val message: String) : TestResult()
        data class HostnameFailure(val message: String) : TestResult()
        data class NetworkFailure(val message: String) : TestResult()
        data class UnknownFailure(val message: String) : TestResult()
    }
}
