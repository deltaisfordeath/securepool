
# 📄 AUDIT_LOGGING_COMPLETE_GUIDE.md

## ✅ What Was Implemented

We implemented **basic audit logging** in the SecurePool backend to record key security-sensitive actions such as login attempts and account registration. The goal is to establish a reliable, queryable audit trail to support monitoring and incident investigation.

Specifically:
- Added a dedicated `audit_logs` table in MySQL with columns for `username`, `action`, `ip_address`, `user_agent`, and `timestamp`.
- Implemented `auditLogger.js` to write events to the database.
- Integrated logging calls in the login and registration routes.
- Verified that log entries are persisted after successful login and registration.
- Enabled console and database audit recording.

---

## 🛠️ Implementation Approaches

We implemented audit logging in a **modular and scalable** fashion:

1. **Database Table Setup**
   - Modified `initializeDatabase.js` to create the `audit_logs` table with proper schema:
     ```sql
     CREATE TABLE audit_logs (
       id INT AUTO_INCREMENT PRIMARY KEY,
       username VARCHAR(255),
       action VARCHAR(255),
       ip_address VARCHAR(255),
       user_agent VARCHAR(512),
       timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
     );
     ```

2. **Created `auditLogger.js`**
   - Central utility to log actions:
     ```js
     import db from './db.js';

     export async function logAudit(username, action, ip, userAgent) {
       await db.query('INSERT INTO audit_logs (username, action, ip_address, user_agent) VALUES (?, ?, ?, ?)', [username, action, ip, userAgent]);
     }
     ```

3. **Integrated into Auth Routes**
   - In `authRoutes.js` (or equivalent), used:
     ```js
     await logAudit(username, 'Login Success', req.ip, req.headers['user-agent']);
     ```

4. **Verified via SQL**
   - Checked entries with:
     ```sql
     SELECT * FROM audit_logs ORDER BY timestamp DESC;
     ```

---

## 🧪 Testing Commands

### ✅ Using Postman / curl:
```bash
curl -X POST http://localhost:3000/api/login -d "username=test&password=123"
```

### ✅ SQL verification:
```sql
SELECT * FROM audit_logs WHERE username = 'test' ORDER BY timestamp DESC;
```

---

## 📱 Testing from Android Emulator

You can also trigger and verify audit logs using the SecurePool mobile app inside an emulator:

1. **Run backend on host:**
   ```bash
   node server.js
   ```

2. **In the Android Emulator**, open the app and log in using a valid test account.

3. **Check the backend MySQL DB**:
   ```sql
   SELECT * FROM audit_logs ORDER BY timestamp DESC;
   ```

   You should see logs like:
   ```
   | id | username | action        | ip_address | user_agent    | timestamp           |
   |----|----------|---------------|------------|---------------|---------------------|
   |  1 | gamerA   | Login Success | 127.0.0.1  | okhttp/4.12.0 | 2025-07-28 12:59:28 |
   ```

---

🛡️ **Audit logging ensures accountability and traceability in SecurePool’s security model.**
