import com.example.securepool.BuildConfig

fun main() {
    println("=== Build-Time Certificate Configuration Test ===")
    println("CERT_PIN_DEV: ${BuildConfig.CERT_PIN_DEV}")
    println("CERT_PIN_PROD: ${BuildConfig.CERT_PIN_PROD}")
    println("USE_DYNAMIC_PINNING: ${BuildConfig.USE_DYNAMIC_PINNING}")
    
    if (BuildConfig.CERT_PIN_DEV.isNotEmpty()) {
        println("✅ Development certificate pin loaded successfully")
    } else {
        println("❌ Development certificate pin is empty")
    }
    
    if (BuildConfig.CERT_PIN_PROD.isNotEmpty()) {
        println("✅ Production certificate pin loaded successfully") 
    } else {
        println("⚠️ Production certificate pin is empty (expected for dev)")
    }
    
    println("Dynamic pinning enabled: ${BuildConfig.USE_DYNAMIC_PINNING}")
}
