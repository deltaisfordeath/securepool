# 🧪 TESTING SECUREPOOL WITH CERTIFICATE PINNING

## STATUS: Ready to Test! ✅

### What's Done:
- ✅ App built successfully with certificate pinning
- ✅ APK installed on emulator (emulator-5554)
- ✅ Log monitoring started
- 🔄 **Next**: Start backend server manually

---

## STEP 1: Start Backend Server

**Open a new Command Prompt window and run:**

```cmd
cd "c:\Users\adari\OneDrive\Documents\Projects\securepool\backend"
node server.js
```

**Expected output:**
```
Server running on https://localhost:443
Database connected
```

If you get database errors, first run:
```cmd
node initializeDatabase.js
```

---

## STEP 2: Test the App

### 2.1 Launch the App
1. On your emulator, find and open the **SecurePool** app
2. The app should launch and show the login/registration screen

### 2.2 Watch for Certificate Pinning Logs
In VS Code terminal, check the log monitor for messages like:
```
D/CertificateUtils: Certificate SHA-256 Hash: sha256:LQYY6Uo/fFj1qLoDm9ZYbW0xBSEfSHzof5qrxvNheTY=
I/CertificatePinning: Certificate pinning enabled
I/SecurePoolApplication: App started with certificate pinning
```

### 2.3 Test Features
1. **Registration**: Try creating a new account
2. **Login**: Login with existing credentials
3. **Leaderboard**: Check if scores load (tests API connectivity)
4. **Network Security**: All connections should use certificate pinning

---

## STEP 3: Verify Certificate Pinning

### What Should Happen:
- ✅ App connects to backend using HTTPS
- ✅ Certificate pinning validates the server certificate
- ✅ API calls work (login, leaderboard, scores)
- ✅ No SSL/certificate errors in logs

### What Would Fail Without Pinning:
- ❌ Man-in-the-middle attacks would succeed
- ❌ Rogue certificates would be accepted
- ❌ System CA compromise wouldn't be detected

---

## TROUBLESHOOTING

### If Backend Won't Start:
```cmd
# Install dependencies first
cd backend
npm install

# Check if MySQL is running
# Update credentials in initializeDatabase.js if needed

# Initialize database
node initializeDatabase.js

# Start server
node server.js
```

### If App Won't Connect:
1. Ensure backend is running on port 443
2. Check emulator can reach `https://10.0.2.2:443`
3. Look for certificate pinning logs in logcat

### If Certificate Pinning Fails:
- Check logs for specific error messages
- Verify certificate hash matches: `LQYY6Uo/fFj1qLoDm9ZYbW0xBSEfSHzof5qrxvNheTY=`
- Ensure certificate file exists in app assets

---

## SUCCESS INDICATORS

### ✅ Certificate Pinning Working:
```
D/CertificateUtils: Certificate SHA-256 Hash: sha256:LQYY6Uo/fFj1qLoDm9ZYbW0xBSEfSHzof5qrxvNheTY=
I/CertificatePinning: Certificate pinning is working correctly
I/OkHttp: SSL handshake successful with pinned certificate
```

### ✅ App Functionality Working:
- Login/registration successful
- Leaderboard loads with user data
- No network or SSL errors
- Secure communication established

---

## NEXT STEPS

Once testing is successful:
1. Try connecting to a different server (should fail with certificate pinning)
2. Test with `ENABLE_CERTIFICATE_PINNING = false` (should work without validation)
3. Re-enable pinning for production deployment

**The certificate pinning implementation is complete and ready for testing!** 🚀
