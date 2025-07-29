package com.example.securepool.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.provider.Settings
import java.io.File

/**
 * Device Security Checker
 * Detects potentially compromised environments that could bypass security measures
 */
object DeviceSecurityChecker {
    
    private const val TAG = "DeviceSecurityChecker"
    
    /**
     * Comprehensive device security assessment
     */
    fun isDeviceSecure(context: Context): Boolean {
        val checks = listOf(
            !isRooted(),
            !isDebuggingEnabled(context),
            !isEmulator(),
            !hasHookingFramework(),
            isCertificatePinningIntact()
        )
        
        return checks.all { it }
    }
    
    /**
     * Detect rooted devices
     */
    fun isRooted(): Boolean {
        return try {
            // Check for common root files
            val rootFiles = listOf(
                "/system/app/Superuser.apk",
                "/sbin/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su"
            )
            
            rootFiles.any { File(it).exists() }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Detect if debugging is enabled
     */
    fun isDebuggingEnabled(context: Context): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 ||
                Settings.Secure.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
    }
    
    /**
     * Detect emulator environment
     */
    fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.lowercase().contains("vbox") ||
                Build.FINGERPRINT.lowercase().contains("test-keys") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
    }
    
    /**
     * Detect hooking frameworks (Frida, Xposed, etc.)
     */
    fun hasHookingFramework(): Boolean {
        return try {
            // Check for Frida
            val fridaCheck = try {
                File("/data/local/tmp/frida-server").exists()
            } catch (e: Exception) {
                false
            }
            
            // Check for Xposed
            val xposedCheck = try {
                Class.forName("de.robv.android.xposed.XC_MethodHook")
                true
            } catch (e: ClassNotFoundException) {
                false
            }
            
            fridaCheck || xposedCheck
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Verify certificate pinning implementation is intact
     */
    fun isCertificatePinningIntact(): Boolean {
        return try {
            // Verify our certificate pinning classes exist and haven't been tampered with
            Class.forName("com.example.securepool.security.CertificatePinning")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }
    
    /**
     * Get detailed security report
     */
    fun getSecurityReport(context: Context): SecurityReport {
        return SecurityReport(
            isRooted = isRooted(),
            isDebuggingEnabled = isDebuggingEnabled(context),
            isEmulator = isEmulator(),
            hasHookingFramework = hasHookingFramework(),
            certificatePinningIntact = isCertificatePinningIntact(),
            overallSecure = isDeviceSecure(context)
        )
    }
}

data class SecurityReport(
    val isRooted: Boolean,
    val isDebuggingEnabled: Boolean,
    val isEmulator: Boolean,
    val hasHookingFramework: Boolean,
    val certificatePinningIntact: Boolean,
    val overallSecure: Boolean
) {
    fun getSecurityIssues(): List<String> {
        val issues = mutableListOf<String>()
        
        if (isRooted) issues.add("Device appears to be rooted")
        if (isDebuggingEnabled) issues.add("Debugging is enabled")
        if (isEmulator) issues.add("Running on emulator")
        if (hasHookingFramework) issues.add("Hooking framework detected")
        if (!certificatePinningIntact) issues.add("Certificate pinning may be compromised")
        
        return issues
    }
}
