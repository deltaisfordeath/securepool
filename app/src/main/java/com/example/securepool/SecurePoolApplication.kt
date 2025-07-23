package com.example.securepool

import android.app.Application
import com.example.securepool.security.CertificateUtils

class SecurePoolApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Only in debug builds, log the certificate hash for development
        // You can also check BuildConfig.DEBUG if you have it configured
        CertificateUtils.logCertificateHash(this)
    }
}
