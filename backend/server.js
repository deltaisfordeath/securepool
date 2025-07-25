import express, { json } from 'express';
import cors from 'cors';
import moment from 'moment'; // ⏱️ Timestamp formatting
import initializeDatabase from './initializeDatabase.js';
import https from 'https';
import { WebSocketServer } from 'ws';
import fs from 'fs';
import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';

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
  const { username, password } = req.body;
  if (!username || !password) {
    return res.status(400).json({ error: 'Missing credentials' });
  }

  const salt = await bcrypt.genSalt(10);
  const pwHash = await bcrypt.hash(password, salt);

  try {
    const checkSql = 'SELECT * FROM users WHERE username = ?';
    const [existingUsers] = await db.query(checkSql, [username]);

    if (existingUsers.length > 0) {
      return res.json({ success: false, message: 'Username already exists' }); // 🚫 Username already exists
    }

    const insertSql = 'INSERT INTO users SET username = ?, password = ?, score = 100';
    await db.query(insertSql, [username, pwHash]);

    res.json({ success: true });

  } catch (error) {
    console.error('Error during registration:', error);
    return res.status(500).json({ success: false, error: 'Database error during registration' });
  }
});

// 🔐 Login (plaintext check)
app.post('/api/login', async (req, res) => {
  const { username, password } = req.body;
  if (!username || !password) {
    return res.status(400).json({ error: 'Missing credentials' });
  }

  const sql = 'SELECT * FROM users WHERE username = ?';

  try {
    const [results] = await db.query(sql, [username]);
    if (results.length === 0) {
      return res.json({ success: isMatch });
    }
    const user = results[0];
    const isMatch = await bcrypt.compare(password, user.password);
    if (!isMatch) {
      return res.json({ success: false });
    }
    
    const accessToken = jwt.sign({ id: user.username }, JWT_SECRET, { expiresIn: JWT_EXPIRATION });
    const refreshToken = jwt.sign({ id: user.username }, REFRESH_TOKEN_SECRET);

    res.json({
      success: true,
      username: user.username,
      score: user.score,
      lastZeroTimestamp: user.lastZeroTimestamp,
      accessToken,
      refreshToken
    });
  } catch (error) {
    console.error('Error executing login query:', error);
    return res.status(500).json({ error: 'Query error' });
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

const wss = new WebSocketServer({ server });

wss.on('connection', function connection(ws) {
  console.log('🔌 WebSocket client connected');

  ws.on('message', function message(data) {
    console.log('📩 Received:', data.toString());
    ws.send(`🔁 Echo: ${data}`);
  });

  ws.send('📡 Hello from SecurePool WebSocket server!');
});