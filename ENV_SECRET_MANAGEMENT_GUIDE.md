
# 📄 ENV_SECRET_MANAGEMENT_GUIDE.md

## ✅ What Was Implemented

We enhanced the security of the SecurePool backend by replacing hardcoded secrets with **environment variables** managed via the `dotenv` library. This allows secrets like JWT keys and DB credentials to be managed securely and separately from source code.

Specifically:
- Removed hardcoded secrets from source files (`server.js`, `auth.js`, etc.).
- Created a `.env` file to store sensitive credentials.
- Used the `dotenv` package to load variables at runtime.
- Ensured `.env` is ignored via `.gitignore` to avoid accidental commits.
- Verified secret loading and fallback behavior on missing env vars.

---

## 🛠️ Implementation Approaches

We followed this structured approach to integrate secure environment variable handling:

1. **Installed `dotenv`**
   ```bash
   npm install dotenv
   ```

2. **Created `.env` file**
   ```env
   DB_USER=root
   DB_PASS=secure_password
   JWT_SECRET=super_secret_key
   ```

3. **Updated `.gitignore`**
   ```gitignore
   .env
   ```

4. **Modified `server.js` and other files**
   - At the top of the main entry point (`server.js`):
     ```js
     import dotenv from 'dotenv';
     dotenv.config();
     ```
   - Used values securely:
     ```js
     const jwtSecret = process.env.JWT_SECRET || 'fallback_key';
     const dbUser = process.env.DB_USER;
     ```

5. **Tested fallback & error handling**
   - Verified app still runs without `.env`, but warns if critical secrets are missing.

---

## 🧪 Testing the Setup

### ✅ Check if variables are loaded:
```bash
node
> require('dotenv').config()
> console.log(process.env.JWT_SECRET)
```

### ✅ Simulate missing variable:
Comment out `JWT_SECRET` in `.env` and re-run the app. It should log a fallback warning or throw an error if not handled.

---

## 📱 Testing from Android Emulator

This feature doesn’t require app-side testing, but indirectly ensures that backend secrets (e.g., JWT) are not exposed when accessed from the app via the emulator.

1. **Login through emulator app** → ensures JWTs are generated using env-configured secrets.
2. **Confirm API still authenticates and returns tokens** via `process.env.JWT_SECRET`.

---


🔐 **Environment-based secret management is critical to secure and scalable backend development.**
