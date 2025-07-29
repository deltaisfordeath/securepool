// authServer.js
import express, { json } from 'express';
import cors from 'cors';
import moment from 'moment';
import initializeDatabase from './initializeDatabase.js';
import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import crypto from 'crypto';
import dotenv from 'dotenv';

dotenv.config();

const app = express();
app.use(cors());
app.use(json());

// --- Environment Variables & Constants ---
const JWT_SECRET = process.env.JWT_SECRET;
const JWT_EXPIRATION = '15m';
const REFRESH_TOKEN_SECRET = process.env.REFRESH_TOKEN_SECRET;
const CHALLENGE_DURATION = 15 * 1000 * 60;
const PORT = 3001;

// --- Database Connection ---
let db;
try {
    db = await initializeDatabase();
    console.log('Auth Service: Database connected successfully');
} catch (error) {
    console.error('Auth Service: Database connection failed. Exiting.', error);
    process.exit(1);
}

// --- JWT Middleware & Helpers ---
const verifyJwt = (req, res, next) => {
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1];
    if (token == null) return res.status(401).json({ message: 'No token provided.' });

    try {
        const user = jwt.verify(token, JWT_SECRET);
        req.user = user;
        next();
    } catch (err) {
        return res.status(403).json({ message: 'Token is invalid or expired.' });
    }
};

const getJwtTokenResponse = (user, res) => {
    const accessToken = jwt.sign({ id: user.username }, JWT_SECRET, { expiresIn: JWT_EXPIRATION });
    const refreshToken = jwt.sign({ id: user.username }, REFRESH_TOKEN_SECRET);
    // In a real app, you would persist the refresh token against the user record in the DB
    return res.json({
        success: true,
        username: user.username,
        accessToken,
        refreshToken
    });
};


// --- Authentication Routes ---

app.post('/register', async (req, res) => {
    /* ... (Copy the full /api/register logic here) ... */
    console.log('Registration request received:', req.body);
 
    const { username, password, publicKey } = req.body;
    if (!username || !password) {
        return res.status(400).json({ error: 'Missing credentials' });
    }

    const insertKey = publicKey ?? null;
    const salt = await bcrypt.genSalt(10);
    const pwHash = await bcrypt.hash(password, salt);

    try {
        const checkSql = 'SELECT * FROM users WHERE username = ?';
        const [existingUsers] = await db.query(checkSql, [username]);

        if (existingUsers.length > 0) {
            return res.json({ success: false, message: 'Username already exists' });
        }

        const insertSql = 'INSERT INTO users SET username = ?, password = ?, score = 100, publicKey = ?';
        await db.query(insertSql, [username, pwHash, insertKey]);

        const findUserSql = 'SELECT username, score, lastZeroTimestamp FROM users WHERE username = ?';
        const [newUser] = await db.query(findUserSql, [username]);
        console.log('New user registered:', newUser[0]);
        // Note: score and lastZeroTimestamp are included for the initial login response
        return getJwtTokenResponse(newUser[0], res);
    } catch (error) {
        console.error('Error during registration:', error);
        return res.status(500).json({ success: false, error: 'Database error during registration' });
    }
});

app.post('/login', async (req, res) => {
    /* ... (Copy the full /api/login logic here) ... */
    const { username, password } = req.body;
    if (!username || !password) {
        return res.status(400).json({ error: 'Missing credentials' });
    }

    const sql = 'SELECT * FROM users WHERE username = ?';

    try {
        const [results] = await db.query(sql, [username]);
        if (results.length === 0) {
            return res.status(401).json({ success: false });
        }
        const user = results[0];

        if (user.failedLoginAttempts > 4) {
            return res.status(403).json({success: false, message: "Account locked from too many failed login attempts"});
        }

        const isMatch = await bcrypt.compare(password, user.password);
        if (!isMatch) {
            const failedSql = 'UPDATE users SET failedLoginAttempts = ? WHERE username = ?';
            await db.query(failedSql, [user.failedLoginAttempts + 1, username]);
            return res.status(401).json({ success: false });
        }

        const successSql = 'UPDATE users SET failedLoginAttempts = 0 WHERE username = ?';
        await db.query(successSql, [username]);

        return getJwtTokenResponse(user, res);
    } catch (error) {
        console.error('Error executing login query:', error);
        return res.status(500).json({ error: 'Query error' });
    }
});

// Biometric registration requires an existing, valid token
app.post('/register-biometric', verifyJwt, async (req, res) => {
    const userName = req.user?.id;
    console.log(`Registering biometric key for user: ${userName.toString()}`);
    const { publicKey } = req.body;

    if (!userName || !publicKey) {
        return res.status(400).send({ error: 'User ID and public key are required.' });
    }

    const findUserSql = 'SELECT username FROM users WHERE username = ?';

    const [user] = await db.query(findUserSql, [userName]);

    if (user.length === 0) {
        return res.status(404).send({ message: 'User account not found' });
    }

    const updateUserSql = 'UPDATE users SET publicKey = ? WHERE username = ?';

    const result = await db.query(updateUserSql, [publicKey, userName]);

    console.log(result);

    res.status(204).send({ message: 'Biometric key registered successfully.' });
});

app.get('/challenge', async (req, res) => {
    const { username } = req.query;

    const findUserSql = 'SELECT username FROM users WHERE username = ?';

    const [user] = await db.query(findUserSql, [username]);

    if (user.length === 0) {
    return res.status(404).send({ message: 'User account not found' });
    }

    const deleteSql = 'DELETE FROM challenges WHERE username = ?';
    await db.query(deleteSql, [username]);

    // Generate a cryptographically secure random challenge.
    const challenge = crypto.randomBytes(32).toString('hex');

    const expiration = moment().add(15, 'minutes').format('YYYY-MM-DD HH:mm:ss');

    const sql = 'INSERT INTO challenges (username, challenge, expiration) VALUES (?, ?, ?)'
    const [result] = await db.query(sql, [username, challenge, expiration]);

    console.log(result);

    setTimeout(async () => {
    console.log('Purging expired challenges');
    const now = moment().format('YYYY-MM-DD HH:mm:ss');
    const expiredChallengesSql = 'DELETE FROM challenges WHERE expiration < ?'
    await db.query(expiredChallengesSql, [now]);
    }, CHALLENGE_DURATION);

    console.log(`Inserting challenge into db ${result}`);

    res.send({ challenge });
});

app.post('/challenge', async (req, res) => {
    const { userId, signedChallenge } = req.body;

    if (!userId || !signedChallenge) {
        return res.status(400).send({ error: 'User ID and signed challenge are required.' });
    }

    const userSql = 'SELECT username, score, lastZeroTimestamp, publicKey FROM users WHERE username = ?';
    const [userRes] = await db.query(userSql, [userId]);

    if (!userRes[0]) {
        return res.status(400).send({ error: 'No user found for the given username' });
    }

    const user = userRes[0];

    const sql = 'SELECT * FROM challenges WHERE username = ?';
    const [challenge] = await db.query(sql, [userId]);

    if (challenge.length === 0) {
        return res.status(400).send({ error: 'No challenge was issued for this user. Please request a challenge first.' });
    }

    try {
        const verify = crypto.createVerify('SHA256');
        verify.update(challenge[0].challenge);
        verify.end();

        // The public key from the Android Keystore is in X.509 format.
        // We need to wrap it in PEM headers for Node.js's crypto module to parse it correctly.
        const publicKey = `-----BEGIN PUBLIC KEY-----\n${user.publicKey}\n-----END PUBLIC KEY-----`;

        const isSignatureValid = verify.verify(publicKey, signedChallenge, 'base64');

        if (isSignatureValid) {
        console.log(`Login successful for user: ${userId}`);
        return getJwtTokenResponse(user, res);
        } else {
        console.log(`Login failed for user: ${userId} - Invalid signature.`);
        res.status(401).send({ error: 'Invalid signature.' });
        }
    } catch (error) {
        console.error('Error during verification:', error);
        res.status(500).send({ error: 'An error occurred during verification.' });
    } finally {
        const deleteSql = 'DELETE FROM challenges WHERE username = ?';
        await db.query(deleteSql, [userId]);
    }
});

app.post('/token/refresh', (req, res) => {
    const { token } = req.body;
    if (token == null) return res.sendStatus(401);

    const user = Object.values(userStore).find(u => u.refreshToken === token);
    if (!user) return res.status(403).json({ message: 'Refresh token is invalid.' });

    try {
    const decodedUser = jwt.verify(token, REFRESH_TOKEN_SECRET);
    if (user.username !== decodedUser.id) {
        return res.status(403).json({ message: 'Refresh token is invalid.' });
    }
    const accessToken = jwt.sign({ id: decodedUser.id }, JWT_SECRET, { expiresIn: JWT_EXPIRATION });
    res.json({ accessToken, refreshToken: user.refreshToken });
    } catch (err) {
    res.status(403).json({ message: 'Refresh token is invalid or expired.' });
    }
});

app.listen(PORT, () => {
    console.log(`🔑 Auth Service running on http://localhost:${PORT}`);
});