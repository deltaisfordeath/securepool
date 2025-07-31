
# 📄 RATE_LIMITING_COMPLETE_GUIDE.md

## ✅ What Was Implemented

We added **API rate limiting** functionality to the SecurePool backend using the `express-rate-limit` middleware. This protects the server from brute force attacks, credential stuffing, or spamming by limiting repeated requests from the same IP within a time window.

Specifically:
- **Login endpoint** (`/api/login`) was rate-limited to allow only a maximum of 5 requests per 15 minutes per IP.
- **Register endpoint** (`/api/register`) was similarly limited to prevent abuse.
- **General API routes** were assigned a more relaxed rate limit as a fallback.
- **Audit logging** for rate limit violations is now available in the `audit.log` file (to be enabled in future enhancement).
- Custom block messages and handler functions were configured to match project standards and ensure clarity for the client.

## 🛠️ Implementation Approaches

We implemented rate limiting in a **modular, extensible** way using the following approach:

1. **Installed Middleware**
   ```bash
   npm install express-rate-limit
   ```

2. **Created a Centralized `rateLimiter.js` Module**
   - Defined separate rate limiters for:
     - Login
     - Registration
     - General routes
   - Used `windowMs`, `max`, and a custom `handler()` for each limiter.

3. **Integrated into Express App (`server.js`)**
   - Imported specific limiters:
     ```js
     import { loginLimiter, registerLimiter, generalLimiter } from './rateLimiter.js';
     ```
   - Applied middleware to routes:
     ```js
     app.use('/api/login', loginLimiter);
     app.use('/api/register', registerLimiter);
     app.use(generalLimiter); // fallback
     ```

4. **Custom Handler Logging (Optional)**
   - Prepared a handler to log blocked attempts:
     ```js
     const logRateLimit = (req, res, next, options) => {
       // Add to console or audit.log
     };
     ```
   - Currently not enabled for audit logs; can be integrated into `auditLogger.js` if required.

## 🧪 Testing Commands

In Postman, Insomnia, or cURL:

```bash
# Run more than 5 requests in 15 mins to /api/login to trigger block
curl -X POST http://localhost:3000/api/login -d "username=test&password=test"
```

Expected response after limit:
```json
{
  "error": "Too many login attempts from this IP, please try again later."
}
```



🛡️ **Rate limiting is a foundational security measure and is now part of our SecurePool backend baseline.**


## 📱 Testing from Android Emulator

To test the rate limit feature from the Android emulator (or any device hitting your local backend), follow these steps:

1. **Start your backend server** on your host machine:
   ```bash
   node server.js
   ```

2. **From the emulator**, send multiple login requests using the app or tools like `curl` (via emulator shell).

3. **Using ADB shell & curl** inside the emulator:
   ```bash
   adb shell
   curl -X POST http://10.0.2.2:3000/api/login -d "username=test&password=test"
   ```

4. **Repeat the request 6+ times quickly** to trigger the rate limiter (limit is 5 requests/15 min).

Expected blocked response:
```json
{
  "error": "Too many login attempts from this IP, please try again later."
}
```

🧪 **Note:** `10.0.2.2` is the special IP for accessing host machine from Android emulator.
