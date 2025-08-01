# Securepool
#### Georgia Southwestern State University  
#### CSCI 6130 - Mobile Security  
#### Summer 2025  
  
Purpose: To design an insecure Android application, identify and model threats, and then secure the app by applying techniques learned from CSCI 6130 - Mobile Security.  

**✅ Security Features Implemented:**
- **Certificate Pinning**: App validates server certificate against known SHA-256 hash
- **Custom Trust Manager**: Only trusts the specific server certificate
- **Hostname Verification**: Restricts connections to authorized hosts only

## Prerequisites  
- Android Studio  
- MySQL  
- NodeJS  
- OpenSSL (for certificate operations)

## Backend Server Setup
1. From the `backend` directory, run `npm install`  
2. Run `cp .env.example .env` to copy the template .env file.
3. Populate the `.env` file with your secret keys and database connection properties.
4. Ensure that MySQL is installed and running.
5. Run `npm run start` to start the server, or `npm run dev` for hot reloading of server.js changes.
6. Server runs on `https://localhost:443` with SSL/TLS.
7. Development certificate (self-signed) located at `backend/dev_cert/securepool_cert.pem`. Production must have a certificate signed by a valid CA. Updated certificates must be named `securepool_cert.pem` and saved in `backend/dev_cert` **AND** `app/src/main/assets` (for certificate pinning hash generation).

## Android Application

1. **Using Android Studio (Recommended):**
   - Open project in Android Studio
   - Start an emulator or connect a device
   - Click "Run" button

2. **Using Command Line:**
   ```bash
   # Build the app (Windows)
   .\gradlew.bat assembleDebug
   
   # Build the app (Linux/Mac)  
   ./gradlew assembleDebug
   
   # Install on device/emulator
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```n20+

3. **Monitor Certificate Pinning:**
   ```bash
   # Watch for certificate pinning logs
   adb logcat | findstr "Certificate\|Pinning\|SecurePool"
   ```

## Enabling Biometric Authentication
1. Ensure that your Android Device supports Biometric Authentication.
2. Ensure that a Biometric fingerprint is registered in your Android Device Settings.
3. Launch the SecurePool app and register with a username and password (password is necessary for account recovery when switching Android Devices/Biometric Keys)
4. From the SecurePool home screen, click `Enable Biometric Login`
5. Close and re-launch the SecurePool application
6. Ensure the correct username is input and click `Use Fingerprint` to log in!

### 🔐 Certificate Pinning (NEW - Automatic)
**No manual certificate installation needed!** The app now includes:
- Built-in certificate pinning using SHA-256 hash: `LQYY6Uo/fFj1qLoDm9ZYbW0xBSEfSHzof5qrxvNheTY=` (development only, replace with CA signed certificate/hash for production)
- Certificate file automatically bundled in app assets
- Custom SSL validation that bypasses system certificate store

## Testing Certificate Pinning

### Expected Log Output:
```
D/CertificateUtils: Certificate SHA-256 Hash: sha256:LQYY6Uo/fFj1qLoDm9ZYbW0xBSEfSHzof5qrxvNheTY= //default development certificate
I/CertificatePinning: Certificate pinning enabled
I/CertificatePinning: Certificate pinning is working correctly
```

### Test Checklist:
- [ ] Backend server running on HTTPS port 443
- [ ] App connects successfully to server
- [ ] Login/registration works
- [ ] Leaderboard loads (tests API connectivity)
- [ ] Certificate pinning logs appear
- [ ] No SSL certificate errors

## Security Architecture

### Certificate Pinning Implementation:
- **Location**: `app/src/main/java/com/example/securepool/security/CertificatePinning.kt`
- **Method**: SHA-256 public key hash validation
- **Fallback**: Custom trust manager for additional security
- **Hosts**: Restricted to `10.0.2.2` (emulator) and `localhost`

### Development vs Production:
- **Development**: Certificate pinning can be disabled via `DEBUG_DISABLE_CERT_PINNING` flag (debug builds only)
- **Production**: Always enabled for maximum security, cannot be disabled

## Troubleshooting

### Common Issues:
1. **"Certificate pinning failure"**: Ensure backend server is running with correct certificate
2. **"Network connection failed"**: Check that server is accessible at `https://10.0.2.2:443`
3. **Build errors**: Run `.\gradlew clean assembleDebug` to clean build

### Documentation:
- **Complete Guide**: `CERTIFICATE_PINNING_COMPLETE_GUIDE.md` - Comprehensive implementation and usage guide
- **Testing Guide**: `HOW_TO_RUN_AND_TEST.md`

---

**Note**: The old method of manually installing certificates on the device is no longer needed. Certificate pinning is now handled automatically by the app code, providing stronger security than relying on the system certificate store.  
