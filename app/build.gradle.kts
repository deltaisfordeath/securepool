plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 🔐 Access secrets from gradle.properties or environment
val mySecretKey: String? = project.findProperty("MY_SECRET_KEY") as String?
val certPinDev: String? = project.findProperty("CERT_PIN_DEV") as String?
val certPinProd: String? = project.findProperty("CERT_PIN_PROD") as String?
val useDynamicPinning: String? = project.findProperty("USE_DYNAMIC_PINNING") as String?
val debugDisableCertPinning: String? = project.findProperty("DEBUG_DISABLE_CERT_PINNING") as String?

android {
    namespace = "com.example.securepool"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.securepool"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ✅ Make secrets accessible in Kotlin code via BuildConfig
        buildConfigField("String", "MY_SECRET_KEY", "\"${mySecretKey ?: ""}\"")
        buildConfigField("String", "CERT_PIN_DEV", "\"${certPinDev ?: ""}\"")
        buildConfigField("String", "CERT_PIN_PROD", "\"${certPinProd ?: ""}\"")
        buildConfigField("boolean", "USE_DYNAMIC_PINNING", "${useDynamicPinning?.toBoolean() ?: false}")
    }

    buildTypes {
        debug {
            // Debug-only: Allow disabling certificate pinning for development/testing
            buildConfigField("boolean", "DEBUG_DISABLE_CERT_PINNING", "${debugDisableCertPinning?.toBoolean() ?: false}")
        }
        
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Production: Certificate pinning is ALWAYS enabled
            buildConfigField("boolean", "DEBUG_DISABLE_CERT_PINNING", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    // ✅ Retrofit & JSON converter
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // ✅ Jetpack Compose + Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation("io.socket:socket.io-client:2.1.2")
// Use the latest stable version
// The socket.io-client library internally uses OkHttp, so ensure you have a compatible version
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
// Use a recent stable version

    // ✅ Compose BOM and UI components
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // ✅ Material Icons (🔧 Added for Visibility toggles)
    implementation("androidx.compose.material:material-icons-extended:1.6.0")

    // ✅ Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
