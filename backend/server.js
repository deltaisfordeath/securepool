import express, { json } from 'express';
import cors from 'cors';
import moment from 'moment';
import initializeDatabase from './initializeDatabase.js';
import https from 'https';
import { Server } from 'socket.io';
import fs from 'fs';
import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import crypto from 'crypto';

const app = express();
app.use(cors());
app.use(json());

const options = {
  key: fs.readFileSync('./dev_cert/securepool_key.pem'),
  cert: fs.readFileSync('./dev_cert/securepool_cert.pem')
};

// TODO: Replace these with unique secret keys from environment variables
const JWT_SECRET = 'your-super-secret-key-for-jwt';
const JWT_EXPIRATION = '15m';
const REFRESH_TOKEN_SECRET = 'your-super-secret-refresh-key';

const CHALLENGE_DURATION = 15 * 1000 * 60;

const verifyJwt = (req, res, next) => {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];

  if (token == null) {
    return res.status(401).json({ message: 'No token provided.' });
  }

  try {
    const user = jwt.verify(token, JWT_SECRET);
    console.log(`JWT verified for user: ${user.id}`);
    req.user = user; // Add decoded user payload to request object
    next();
  } catch (err) {
    return res.status(403).json({ message: 'Token is invalid or expired.' });
  }
};

const getJwtTokenResponse = (user, res) => {
  const accessToken = jwt.sign({ id: user.username }, JWT_SECRET, { expiresIn: JWT_EXPIRATION });
  const refreshToken = jwt.sign({ id: user.username }, REFRESH_TOKEN_SECRET);

  return res.json({
    success: true,
    username: user.username,
    score: user.score,
    lastZeroTimestamp: user.lastZeroTimestamp,
    accessToken,
    refreshToken
  });
}

app.use((req, res, next) => {
  console.log(`request received: ${req.method} ${req.url} ${JSON.stringify(req.body)}`);
  next();
});

// Initialize the database connection
const db = await initializeDatabase();

app.get('/', (req, res) => {
  res.send('🔗 SecurePool backend is running');
});

app.post('/api/register', async (req, res) => {
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
      return res.json({ success: false, message: 'Username already exists' }); // 🚫 Username already exists
    }

    const insertSql = 'INSERT INTO users SET username = ?, password = ?, score = 100, publicKey = ?';
    await db.query(insertSql, [username, pwHash, insertKey]);

    const findUserSql = 'SELECT username, score, lastZeroTimestamp FROM users WHERE username = ?';
    const [newUser] = await db.query(findUserSql, [username]);
    console.log('New user registered:', newUser[0]);

    return getJwtTokenResponse(newUser[0], res);

  } catch (error) {
    console.error('Error during registration:', error);
    return res.status(500).json({ success: false, error: 'Database error during registration' });
  }
});

app.post('/api/register-biometric', verifyJwt, async (req, res) => {
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

app.post('/api/login', async (req, res) => {
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
    const isMatch = await bcrypt.compare(password, user.password);
    if (!isMatch) {
      return res.status(401).json({ success: false });
    }

    return getJwtTokenResponse(user, res);
  } catch (error) {
    console.error('Error executing login query:', error);
    return res.status(500).json({ error: 'Query error' });
  }
});

app.get('/api/challenge', async (req, res) => {
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

app.post('/api/challenge', async (req, res) => {
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

// 📊 Get score + formatted timestamp
app.get('/api/score', verifyJwt, async (req, res) => {
  const { username } = req.query;
  if (!username) {
    return res.status(400).json({ error: 'Missing username' });
  }

  const sql = 'SELECT username, score, lastZeroTimestamp FROM users WHERE username = ?';
  try {
    const [results] = await db.query(sql, [username]);
    if (results.length === 0) {
      return res.status(404).json({ success: false, message: 'User not found' });
    }
    const player = results[0];
    const formattedTimestamp = player.lastZeroTimestamp
      ? moment(player.lastZeroTimestamp).format('YYYY-MM-DD HH:mm:ss')
      : null;

    res.json({
      username: player.username,
      score: player.score,
      lastZeroTimestamp: formattedTimestamp
    });

  } catch (error) {
    console.error('Error executing score query:', error);
    return res.status(500).json({ success: false, message: 'Query error' });
  }
});

app.post('/api/matchResult', verifyJwt, async (req, res) => {
  const { winner, loser, outcome } = req.body;
  if (!winner || !loser || !outcome) {
    return res.status(400).json({ error: 'Missing match data' });
  }

  const increaseSql = 'UPDATE users SET score = score + 10 WHERE username = ?';
  const decreaseSql = `
    UPDATE users 
    SET score = GREATEST(score - 10, 0), 
        lastZeroTimestamp = IF(score - 10 <= 0, NOW(), lastZeroTimestamp)
    WHERE username = ?
  `;

  try {
    await db.query(increaseSql, [winner]);
    await db.query(decreaseSql, [loser]);

    res.json({ message: 'Match result synced successfully' });

  } catch (error) {
    console.error('Error syncing match result:', error);
    return res.status(500).json({ error: 'Failed to sync match result' });
  }
});

app.post('/api/restore-score', verifyJwt, async (req, res) => {
  const { username } = req.body;
  if (!username) {
    return res.status(400).json({ error: 'Missing username' });
  }

  const sql = 'UPDATE users SET score = 100, lastZeroTimestamp = NULL WHERE username = ?';

  try {
    await db.query(sql, [username]);
    res.status(200).json({ success: true, message: 'Score restored' });

  } catch (error) {
    console.error('Error restoring score:', error);
    return res.status(500).json({ error: 'Restore failed' });
  }
});

app.get('/api/leaderboard', verifyJwt, async (req, res) => {
  const sql = 'SELECT username, score FROM users ORDER BY score DESC LIMIT 10';
  try {
    const [rows] = await db.query(sql);
    if (rows.length === 0) {
      return res.status(404).json({ success: false, message: 'No users found' });
    }
    return res.json(rows);
  }
  catch (err) {
    console.error('Error fetching leaderboard:', err);
    return res.status(500).json({ error: 'Leaderboard error' });
  }
});

app.post('api/token/refresh', (req, res) => {
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

const server = https.createServer(options, app);
server.on('error', (err) => {
  console.error('Server error:', err);
  process.exit(1);
});

server.listen(443, '0.0.0.0', () => {
  console.log('🚀 Server running on port 443');
});

const wss = new Server(server);

wss.use((socket, next) => {
  const authHeader = socket.handshake.auth.token;

  if (!authHeader) {
    console.warn('❌ Authentication failed: No token provided.');

    const error = new Error('Authentication failed: No token provided.');
    error.data = { message: 'No authentication token found. Please provide a JWT.' };
    return next(error);
  }

  const tokenParts = authHeader.split(' ');
  if (tokenParts.length !== 2 || tokenParts[0] !== 'Bearer') {
    console.warn('❌ Authentication failed: Invalid token format.');
    const error = new Error('Authentication failed: Invalid token format.');
    error.data = { message: 'Token must be in "Bearer <token>" format.' };
    return next(error);
  }

  const token = tokenParts[1];

  try {
    const decoded = jwt.verify(token, JWT_SECRET);

    socket.userData = decoded;
    console.log(`✅ Client ${socket.id} authenticated. User ID: ${decoded.userId || 'N/A'}`);

    next();
  } catch (err) {
    console.warn(`❌ Authentication failed: Invalid token. Error: ${err.message}`);
    const error = new Error('Authentication failed: Invalid token.');
    error.data = { message: 'Invalid or expired authentication token.' };
    return next(error); // Reject the connection
  }
});

wss.on('connection', (socket) => {
  console.log('🔌 Socket.IO client connected');
  console.log('Client ID:', socket.id);

  socket.emit('serverMessage', '📡 Hello from SecurePool Socket.IO server!');

  socket.on('message', (data) => {
    console.log('📩 Received from client:', data.toString());
    // Echo the message back to the client that sent it
    socket.emit('serverMessage', `🔁 Echo: ${data}`);
  });

  socket.on('disconnect', (reason) => {
    console.log(`🔌 Socket.IO client disconnected: ${reason}`);
  });
});