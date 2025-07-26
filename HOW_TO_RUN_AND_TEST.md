# How to Run SecurePool App with Certificate Pinning

## Prerequisites
- Android Studio installed
- Android device or emulator
- USB debugging enabled (for physical device)

## Method 1: Using Physical Android Device

### Step 1: Enable Developer Options
1. Go to **Settings > About Phone**
2. Tap **Build Number** 7 times
3. Go back to **Settings > Developer Options**
4. Enable **USB Debugging**

### Step 2: Connect and Install
```powershell
# Connect your device via USB, then run:
adb devices                    # Verify device is detected
adb install app\build\outputs\apk\debug\app-debug.apk
```

### Step 3: Start Your Backend Server
```powershell
# In the backend directory, run:
cd backend
node server.js
```

### Step 4: Launch and Test
1. Open the app on your device
2. Check logcat for certificate pinning messages:
```powershell
adb logcat -s CertificatePinning CertificateUtils SecurePoolApplication
```

## Method 2: Using Android Emulator

### Step 1: Create/Start Emulator
```powershell
# List available emulators
emulator -list-avds

# Start an emulator (replace 'avd_name' with your AVD)
emulator -avd avd_name

# Or use Android Studio: Tools > AVD Manager > Start
```

### Step 2: Install APK
```powershell
adb install app\build\outputs\apk\debug\app-debug.apk
```

### Step 3: Start Backend Server
The emulator maps `10.0.2.2` to your host machine's localhost, so:
```powershell
cd backend
node server.js
```

## Method 3: Using Android Studio

### Direct Run (Recommended for Development)
```powershell
# This will build, install, and run automatically
.\gradlew.bat installDebug

# Or use Android Studio:
# 1. Open the project in Android Studio
# 2. Click the green "Run" button
# 3. Select your device/emulator
```

## Testing Certificate Pinning

### 1. Monitor Logs
```powershell
# Watch for certificate pinning activity
adb logcat | Select-String "Certificate|Pinning|SecurePool|SSL|TLS"

# Or more specific:
adb logcat -s CertificatePinning:* CertificateUtils:* SecurePoolApplication:*
```

### 2. Expected Log Messages
Look for these messages when the app starts:
```
D/CertificateUtils: Certificate SHA-256 Hash: sha256:LQYY6Uo/fFj1qLoDm9ZYbW0xBSEfSHzof5qrxvNheTY=
I/CertificatePinning: Certificate pinning enabled
```

### 3. Test API Calls
1. **Login/Register**: Try creating an account
2. **Score Sync**: Check if leaderboard loads
3. **Match Results**: Submit a game result

### 4. Network Monitoring
```powershell
# Watch network traffic
adb logcat | Select-String "OkHttp|Retrofit|HTTP"
```

## Troubleshooting

### Common Issues

#### "No devices found"
```powershell
# Check ADB connection
adb kill-server
adb start-server
adb devices
```

#### "Installation failed"
```powershell
# Uninstall existing version first
adb uninstall com.example.securepool
adb install app\build\outputs\apk\debug\app-debug.apk
```

#### "Certificate pinning failure"
- Check that backend server is running on correct port (443 for HTTPS)
- Verify certificate file is in app assets
- Check certificate hash matches in logs

#### "Network connection failed"
- Ensure backend server is running: `node backend/server.js`
- For emulator: server should be accessible at `https://10.0.2.2:443`
- For device: use your computer's IP address

### Backend Server Commands
```powershell
# Navigate to backend
cd backend

# Install dependencies (if not done)
npm install

# Start the server
node server.js

# Should see: "Server running on https://localhost:443"
```

## Testing Checklist

### ✅ Certificate Pinning Tests
1. **App Launches**: Check logs for certificate hash generation
2. **Pinning Enabled**: Verify pinning is active in logs  
3. **API Calls Work**: Login, score sync, leaderboard should work
4. **Wrong Certificate Rejection**: Try connecting to different server (should fail)

### ✅ Network Security Tests
1. **HTTPS Only**: All API calls should use HTTPS
2. **Hostname Verification**: Only `10.0.2.2` and `localhost` allowed
3. **Custom Trust Manager**: App should reject system CAs

### ✅ Development Tests
1. **Disable Pinning**: Set `ENABLE_CERTIFICATE_PINNING = false`, rebuild, test
2. **Re-enable Pinning**: Set back to `true`, rebuild, verify protection works
3. **Certificate Hash**: Verify hash in logs matches expected value

## Quick Start Commands
```powershell
# 1. Start backend server
cd backend && node server.js

# 2. In another terminal - install and run app
adb install app\build\outputs\apk\debug\app-debug.apk

# 3. Monitor logs
adb logcat -s CertificatePinning CertificateUtils SecurePoolApplication

# 4. Launch app on device/emulator and test!
```
