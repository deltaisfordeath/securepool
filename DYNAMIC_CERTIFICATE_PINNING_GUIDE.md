# Dynamic Certificate Pinning Implementation Guide

## **Problem Solved**
Removes hardcoded certificate hashes from application code, enabling flexible certificate management and easier certificate rotation.

## **Available Approaches**

### **1. Build-Time Configuration (Recommended)**
**Pros:** Secure, simple, version controlled  
**Cons:** Requires rebuild for certificate updates

```properties
# gradle.properties (not committed to git)
CERT_PIN_DEV=bWsw3WqdtgiEWsOtKrjFEOAjebBzD4GruTg+uO0mQ8g=
CERT_PIN_PROD=your-production-hash-here
```

### **2. Asset File Configuration**
**Pros:** Easy to update, no code changes  
**Cons:** Visible in APK, requires app update for changes

```properties
# app/src/main/assets/cert_pins.properties
10.0.2.2.pins.0=sha256/bWsw3WqdtgiEWsOtKrjFEOAjebBzD4GruTg+uO0mQ8g=
production.pins.0=sha256/production-hash
production.pins.1=sha256/backup-hash-for-rotation
```

### **3. Remote Configuration (Advanced)**
**Pros:** Real-time updates, certificate rotation without app updates  
**Cons:** Network dependency, requires secure config server

```kotlin
// Updates pins from secure remote endpoint
DynamicCertificatePinning.updateCertificatePins(context, httpClient)
```

## **Implementation Steps**

### **Step 1: Update Build Configuration**
Add to `app/build.gradle.kts`:
```kotlin
val certPinDev: String? = project.findProperty("CERT_PIN_DEV") as String?
val certPinProd: String? = project.findProperty("CERT_PIN_PROD") as String?

buildConfigField("String", "CERT_PIN_DEV", "\"${certPinDev ?: ""}\"")
buildConfigField("String", "CERT_PIN_PROD", "\"${certPinProd ?: ""}\"")
```

### **Step 2: Create Configuration Files**
Create `gradle.properties` with certificate hashes (don't commit to git):
```properties
CERT_PIN_DEV=your-dev-hash
CERT_PIN_PROD=your-prod-hash  
```

### **Step 3: Update Certificate Pinning**
The `CertificatePinning.kt` now automatically chooses the best configuration method:
1. Remote configuration (if enabled)
2. Build configuration (if hashes provided)
3. Asset file configuration (fallback)

## **Security Benefits**

### **Certificate Rotation Made Easy**
- **Build Config:** Update `gradle.properties` and rebuild
- **Asset Config:** Update `cert_pins.properties` and redeploy
- **Remote Config:** Update server configuration, no app changes needed

### **Environment Separation**
- Different certificates for dev/staging/production
- No production secrets in development builds
- Flexible configuration per environment

### **Multiple Pin Support**
```properties
# Support multiple pins for certificate rotation
production.pins.0=sha256/current-certificate-hash
production.pins.1=sha256/backup-certificate-hash
production.pins.2=sha256/new-certificate-for-rotation
```

## **Usage Examples**

### **Development Build**
```bash
# Uses development certificate from gradle.properties
./gradlew assembleDebug
```

### **Production Build**
```bash
# Uses production certificate from gradle.properties
./gradlew assembleRelease
```

### **CI/CD Pipeline**
```bash
# Inject certificates via environment variables
./gradlew -PCERT_PIN_PROD=$PROD_CERT_HASH assembleRelease
```

## **Migration from Hardcoded Pins**

1. **Backup Current Implementation**
2. **Add New Dynamic Classes** (`DynamicCertificatePinning.kt`, `EnvironmentCertificatePinning.kt`)
3. **Update `CertificatePinning.kt`** to use dynamic loading
4. **Create Configuration Files** with current certificate hashes
5. **Test All Environments** to ensure pins work correctly
6. **Remove Hardcoded Hashes** from source code

## **Certificate Rotation Process**

### **Zero-Downtime Rotation**
1. **Add New Pin:** Add new certificate hash alongside existing one
2. **Deploy Update:** App now accepts both old and new certificates  
3. **Update Server:** Switch server to use new certificate
4. **Remove Old Pin:** Remove old certificate hash in next app update

### **Emergency Rotation**
Use remote configuration for immediate certificate updates without app store deployment.

## **Security Considerations**

- **Never commit certificate hashes to git** (use gradle.properties)
- **Use encrypted storage** for remote configuration tokens
- **Validate configuration integrity** before applying updates
- **Implement fallback mechanisms** for configuration failures
- **Monitor certificate expiration** and plan rotations

This approach provides maximum flexibility while maintaining security best practices!

## **Security Improvements & Configuration Properties**

### **Critical Security Fixes Applied**

#### **Removed Hardcoded Disable Flag**
**Problem**: Code contained `ENABLE_CERTIFICATE_PINNING = true` that could bypass security.

**Solution Applied**:
- Removed unsafe `ENABLE_CERTIFICATE_PINNING` constant
- Eliminated bypass logic in `createSecureClient()`
- Certificate pinning now always enabled by default

#### **Added Secure Debug Override**
**New Feature**: `DEBUG_DISABLE_CERT_PINNING` for development needs.

**Security Guarantees**:
- **ONLY works in DEBUG builds** - impossible to enable in release
- **Requires explicit gradle.properties setting** - not a code constant
- **Logs prominent warnings** when disabled
- **Build-time enforced** - production builds physically cannot disable pinning

### **Configuration Properties Explained**

#### **USE_DYNAMIC_PINNING**
```properties
USE_DYNAMIC_PINNING=false  # Default: build-time configuration
```

**When to set to `true`:**
- **Frequent Certificate Rotation**: Certificates change monthly/quarterly
- **Multiple Environments**: Different pins for dev/staging/production
- **Remote Management**: Security team updates pins without app releases
- **Emergency Response**: Quick pin updates for security incidents
- **Zero-Downtime Updates**: Certificate changes without app store releases

**Current Setting**: `false` (uses build-time gradle.properties configuration)

#### **DEBUG_DISABLE_CERT_PINNING**
```properties
DEBUG_DISABLE_CERT_PINNING=false  # NEVER set to true in production
```

**Purpose**: Allows disabling certificate pinning for development/testing only.

**Security Features**:
- **Debug builds only**: Automatically `false` in release builds
- **Explicit configuration**: Must be set in gradle.properties
- **Prominent logging**: Warns when certificate pinning is disabled
- **Production impossible**: Release builds cannot disable pinning

### **Updated Configuration Example**

```properties
# gradle.properties
# Certificate Pinning Configuration
CERT_PIN_DEV=bWsw3WqdtgiEWsOtKrjFEOAjebBzD4GruTg+uO0mQ8g=
CERT_PIN_PROD=your-production-hash

# Dynamic vs Build-time Configuration
USE_DYNAMIC_PINNING=false

# Debug-only override (NEVER use in production)
DEBUG_DISABLE_CERT_PINNING=false
```

### **Build Configuration Security**

The build system now enforces security at compile time:

```kotlin
// app/build.gradle.kts
buildTypes {
    debug {
        // Debug-only: Allow disabling for development/testing
        buildConfigField("boolean", "DEBUG_DISABLE_CERT_PINNING", 
                        "${debugDisableCertPinning?.toBoolean() ?: false}")
    }
    
    release {
        // Production: Certificate pinning ALWAYS enabled
        buildConfigField("boolean", "DEBUG_DISABLE_CERT_PINNING", "false")
    }
}
```

### **Migration Impact**

#### **Before (Unsafe)**:
```kotlin
private const val ENABLE_CERTIFICATE_PINNING = true // Could be changed
if (!ENABLE_CERTIFICATE_PINNING) {
    return builder // Bypass security
}
```

#### **After (Secure)**:
```kotlin
// No hardcoded disable flag
if (BuildConfig.DEBUG && BuildConfig.DEBUG_DISABLE_CERT_PINNING) {
    Log.w(TAG, "Certificate pinning DISABLED - DEBUG BUILD ONLY!")
    return builder // Only in debug, never in production
}
```

### **Security Recommendations**

1. **Keep `USE_DYNAMIC_PINNING=false`** unless you need remote certificate management
2. **Never set `DEBUG_DISABLE_CERT_PINNING=true`** in production environments
3. **Monitor application logs** for certificate pinning disable warnings
4. **Regular testing** with actual HTTPS endpoints to validate pinning
5. **Certificate rotation planning** for production deployments

This implementation eliminates security vulnerabilities while maintaining development flexibility through secure, build-time configuration.
