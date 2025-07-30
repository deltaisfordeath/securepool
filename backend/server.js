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
import { GamePhysics } from "./gamePhysics.js";
import { Ball } from './ball.js';
import { logAudit } from './utils/auditLogger.js';
import { logAuditEvent } from './utils/logAuditEvent.js';
import { generalLimiter, loginLimiter, registerLimiter } from './rateLimiter.js';

const games = {}; // Stores all active games, keyed by gameId
let waitingPlayer = null; // Holds the socket of a player waiting for a match
import dotenv from 'dotenv';

// Load environment variables
dotenv.config();

const app = express();
app.use(cors());
app.use(json());
app.use(generalLimiter);
app.use('/api/login', loginLimiter);
app.use('/api/register', registerLimiter);

const options = {
  key: fs.readFileSync('./dev_cert/securepool_key.pem'),
  cert: fs.readFileSync('./dev_cert/securepool_cert.pem')
};

// TODO: Replace these with unique secret keys from environment variables
const JWT_SECRET = process.env.JWT_SECRET;
const JWT_EXPIRATION = '15m';
const REFRESH_TOKEN_SECRET = process.env.REFRESH_TOKEN_SECRET;

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

function createNewGame() {
  const VIRTUAL_WIDTH = GamePhysics.VIRTUAL_TABLE_WIDTH;
  const VIRTUAL_HEIGHT = GamePhysics.VIRTUAL_TABLE_HEIGHT;
  const VIRTUAL_POCKET_RADIUS = 45;
  const cornerPocketRadius = VIRTUAL_POCKET_RADIUS * 1.1;

  return {
    balls: [
      new Ball(VIRTUAL_WIDTH / 4, VIRTUAL_HEIGHT / 2, 'cue'),
      new Ball(VIRTUAL_WIDTH * 0.75, VIRTUAL_HEIGHT / 2, '8-ball')
    ],
    pockets: [
      { x: GamePhysics.CUSHION_THICKNESS, y: GamePhysics.CUSHION_THICKNESS, radius: cornerPocketRadius },
      { x: VIRTUAL_WIDTH / 2, y: GamePhysics.CUSHION_THICKNESS / 2, radius: VIRTUAL_POCKET_RADIUS },
      { x: VIRTUAL_WIDTH - GamePhysics.CUSHION_THICKNESS, y: GamePhysics.CUSHION_THICKNESS, radius: cornerPocketRadius },
      { x: GamePhysics.CUSHION_THICKNESS, y: VIRTUAL_HEIGHT - GamePhysics.CUSHION_THICKNESS, radius: cornerPocketRadius },
      { x: VIRTUAL_WIDTH / 2, y: VIRTUAL_HEIGHT - GamePhysics.CUSHION_THICKNESS / 2, radius: VIRTUAL_POCKET_RADIUS },
      { x: VIRTUAL_WIDTH - GamePhysics.CUSHION_THICKNESS, y: VIRTUAL_HEIGHT - GamePhysics.CUSHION_THICKNESS, radius: cornerPocketRadius },
    ],
    players: [],
    currentPlayer: null,
    gameState: 'Playing',
    winnerId: null
  };
}

function runShotSimulation(originalGameState, angle, power) {
  const gameState = cloneGameState(originalGameState);
  const { balls, pockets, players } = gameState;
  const cueBall = balls.find(b => b.id === 'cue');

  if (!cueBall) return originalGameState;

  const maxSpeed = 3000;
  const speed = maxSpeed * power;
  const angleRad = angle * (Math.PI / 180);
  cueBall.velocityX = speed * Math.cos(angleRad);
  cueBall.velocityY = speed * Math.sin(angleRad);

  const timeStep = 1 / 60;
  let simulationRunning = true;
  let pocketed8Ball = false;
  let maxSteps = 2000;
  let steps = 0;

  while (simulationRunning && steps < maxSteps) {
    let movingBalls = 0;
    balls.forEach(ball => {
      if (ball.isPocketed) return;
      ball.update(timeStep);
      if (ball.isMoving()) movingBalls++;
    });

    for (let i = 0; i < balls.length; i++) {
      if (balls[i].isPocketed) continue;
      GamePhysics.handleWallCollision(balls[i]);
      for (let j = i + 1; j < balls.length; j++) {
        if (balls[j].isPocketed) continue;
        GamePhysics.handleBallCollision(balls[i], balls[j]);
      }
    }

    balls.forEach(ball => {
      if (ball.id === 'cue') return;
      if (!ball.isPocketed && GamePhysics.isBallInAnyPocket(ball, pockets)) {
        ball.isPocketed = true;
        ball.velocityX = 0;
        ball.velocityY = 0;
        if (ball.id === '8-ball') pocketed8Ball = true;
      }
    });

    if (movingBalls === 0) simulationRunning = false;
    steps++;
  }

  if (pocketed8Ball) {
    gameState.gameState = "GameOver";
    gameState.reason = "8BallPocketed";
  } else {
    // --- ADDED LOGGING TO DEBUG THE TURN SWITCH ---
    console.log("--- DEBUG: Switching Turn ---");
    console.log("Players in game:", players);
    console.log("Current player was:", gameState.currentPlayer);

    const currentPlayerIndex = players.indexOf(gameState.currentPlayer);
    console.log("Index of current player:", currentPlayerIndex); // If this is -1, that's the bug!

    const nextPlayerIndex = (currentPlayerIndex + 1) % players.length;
    gameState.currentPlayer = players[nextPlayerIndex];

    console.log("Next player will be:", gameState.currentPlayer);
    console.log("----------------------------");

    gameState.currentPlayer = players[nextPlayerIndex];
  }

  return gameState;
}

function cloneGameState(gameState) {
  const newBalls = gameState.balls.map(b => {
    const newBall = new Ball(b.x, b.y, b.id);
    newBall.velocityX = b.velocityX;
    newBall.velocityY = b.velocityY;
    newBall.isPocketed = b.isPocketed;
    return newBall;
  });

  return {
    ...gameState,
    balls: newBalls
  };
}

/**
 * Helper to format game state for the client.
 * @param {object} gameState The current state of the game.
 * @returns {object} A serializable version of the game state.
 */
function serializeGameState(gameState) {
  if (!gameState || !gameState.balls) return { balls: [] };
  return {
    balls: gameState.balls.map(b => ({
      id: b.id,
      x: b.x,
      y: b.y,
      radius: b.radius,
      isPocketed: !!b.isPocketed
    })),
    gameState: gameState.gameState,
    currentPlayer: gameState.currentPlayer, // Include current player in state
    reason: gameState.reason,
    winnerId: gameState.winnerId
  };
}

app.use((req, res, next) => {
  logAuditEvent("HTTP Request", { method: req.method, url: req.url, body: JSON.stringify(req.body || {}) });
  next();
});

// Initialize the database connection
let db;
try {
  db = await initializeDatabase();
  if (db) {
    console.log('Database connected successfully');
  } else {
    console.log('Database connection failed, using in-memory storage for testing');
  }
} catch (error) {
  console.log('Database connection failed, using in-memory storage for testing');
  db = null;
}

// In-memory user storage for testing when database is not available
const memoryUsers = new Map();

app.get('/', (req, res) => {
  res.send('🔗 SecurePool backend is running');
});

// Test endpoint to verify certificate pinning is working
app.get('/api/test', (req, res) => {
  res.json({ 
    message: 'Certificate pinning test successful!', 
    timestamp: new Date().toISOString(),
    database_connected: !!db
  });
});

app.post('/auth/register', async (req, res) => {
  console.log('Registration request received:', req.body);
  
  const { username, password, publicKey } = req.body;
  if (!username || !password) {
    return res.status(400).json({ error: 'Missing credentials' });
  }

  // Use database if available, otherwise use in-memory storage
  if (db) {
    // Original database logic
    const insertKey = publicKey ?? null;
    const salt = await bcrypt.genSalt(10);
    const pwHash = await bcrypt.hash(password, salt);

    try {
      const checkSql = 'SELECT * FROM users WHERE username = ?';
      const [existingUsers] = await db.query(checkSql, [username]);

      if (existingUsers.length > 0) {
        logAuditEvent("User Register Failed", { username, error: 'Username already exists' });
        return res.json({ success: false, message: 'Username already exists' });
      }

      const insertSql = 'INSERT INTO users SET username = ?, password = ?, score = 100, publicKey = ?';
      await db.query(insertSql, [username, pwHash, insertKey]);

      const findUserSql = 'SELECT username, score, lastZeroTimestamp FROM users WHERE username = ?';
      const [newUser] = await db.query(findUserSql, [username]);
      
      console.log('New user registered:', newUser[0]);
      logAuditEvent("User Registered", { username, success: "true" });
      await logAudit(username, 'Registration Success', req);

      return getJwtTokenResponse(newUser[0], res);
    } catch (error) {
      console.error('Error during registration:', error);
      return res.status(500).json({ success: false, error: 'Database error during registration' });
    }
  } else {
    // In-memory storage fallback for testing
    console.log('Using in-memory storage for user registration');
    
    if (memoryUsers.has(username)) {
      return res.json({ success: false, message: 'Username already exists' });
    }

    const salt = await bcrypt.genSalt(10);
    const pwHash = await bcrypt.hash(password, salt);
    
    const user = {
      username: username,
      password: pwHash,
      score: 100,
      lastZeroTimestamp: null,
      publicKey: publicKey || null
    };
    
    memoryUsers.set(username, user);
    console.log('New user registered in memory:', user.username);
    
    // Generate a simple JWT-like response for testing
    const testToken = Buffer.from(JSON.stringify({ username, timestamp: Date.now() })).toString('base64');
    
    return res.json({
      success: true,
      message: 'User registered successfully (in-memory mode)',
      accessToken: testToken,
      user: { username: user.username, score: user.score }
    });
  }
});

app.post('/auth/register-biometric', verifyJwt, async (req, res) => {
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

app.post('/auth/login', async (req, res) => {
  const { username, password } = req.body;
  if (!username || !password) {
    return res.status(400).json({ error: 'Missing credentials' });
  }

  const sql = 'SELECT * FROM users WHERE username = ?';

  try {
    const [results] = await db.query(sql, [username]);
    if (results.length === 0) {
      logAuditEvent("Login Attempt", { username, result: "invalid user" });
      return res.status(401).json({ success: false });
    }
    const user = results[0];

    if (user.failedLoginAttempts > 4) {
      return res.status(403).json({success: false, message: "Account locked from too many failed login attempts"});
    }

    const isMatch = await bcrypt.compare(password, user.password);
    if (!isMatch) {
      logAuditEvent("Login Attempt", { username, result: "invalid password or user" });
      const failedSql = 'UPDATE users SET failedLoginAttempts = ? WHERE username = ?';
      await db.query(failedSql, [user.failedLoginAttempts + 1, username]);
      return res.status(401).json({ success: false });
    }

    logAuditEvent("Login Attempt", { username, result: "success" });
    await logAudit(username, 'Login Success', req);

    const successSql = 'UPDATE users SET failedLoginAttempts = 0 WHERE username = ?';
    await db.query(successSql, [username]);

    return getJwtTokenResponse(user, res);
  } catch (error) {
    console.error('Error executing login query:', error);
    return res.status(500).json({ error: 'Query error' });
  }
});

app.get('/auth/challenge', async (req, res) => {
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

app.post('/auth/challenge', async (req, res) => {
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
      logAuditEvent("Biometric Login", { userId, result: "success" });
      console.log(`Login successful for user: ${userId}`);
      return getJwtTokenResponse(user, res);
    } else {
      logAuditEvent("Biometric Login", { userId, result: "invalid signature" });
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

async function applyMatchResult(player, isWinner) {
  const scoreSql = isWinner ?
    'UPDATE users SET score = score + 10 WHERE username = ?' :
    `
    UPDATE users 
    SET score = GREATEST(score - 10, 0), 
        lastZeroTimestamp = IF(score - 10 <= 0, NOW(), lastZeroTimestamp)
    WHERE username = ?
  `;

  try {
    await db.query(scoreSql, [player]);

  } catch (error) {
    console.error('Error syncing match result:', error);
  }
}

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

app.post('auth/token/refresh', (req, res) => {
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
    error.data = { code: 'INVALID_TOKEN_FORMAT', message: 'Token must be in "Bearer <token>" format.' };
    return next(error);
  }

  const token = tokenParts[1];

  try {
    const decoded = jwt.verify(token, JWT_SECRET);

    socket.userData = decoded;
    console.log(`✅ Client ${socket.id} authenticated. User ID: ${decoded.id || 'N/A'}`);

    next();
  } catch (err) {
    if (err.name === 'TokenExpiredError') {
      console.warn(`❌ Auth failed for ${socket.id}: Token expired.`);
      const error = new Error('Authentication failed: Token expired.');
      // Send a custom code to the client
      error.data = { code: 'TOKEN_EXPIRED', message: 'Your session has expired.' };
      return next(error);
    }

    console.warn(`❌ Auth failed for ${socket.id}: Invalid token. Error: ${err.message}`);
    const error = new Error('Authentication failed: Invalid token.');
    error.data = { code: 'INVALID_TOKEN', message: 'Invalid authentication token.' };
    return next(error);
  }
});

wss.on('connection', (socket) => {
  console.log(`🔌 Socket.IO client connected: ${socket.id}`);
  logAuditEvent("WebSocket Connected", { socketId: socket.id });

  socket.on('joinGame', ({ mode }) => {
    if (mode === 'practice') {
      console.log(`Client ${socket.id} starting a practice game.`);
      const gameId = `practice_${socket.id}`;
      const newGame = createNewGame();
      newGame.players = [socket.id];
      newGame.currentPlayer = socket.id; // In practice, you are always the current player
      games[gameId] = newGame;

      socket.join(gameId);
      // Let the client know the match is ready (immediately for practice)
      socket.emit("matchFound", { gameId, gameState: serializeGameState(newGame) });

    } else if (mode === 'match') {
      if (waitingPlayer) {
        // Match found!
        console.log(`Match found between ${waitingPlayer.id} and ${socket.id}`);
        logAuditEvent("Match Started", { player1: waitingPlayer.userData.id, player2: socket.userData.id });
        const gameId = crypto.randomUUID();
        const newGame = createNewGame();

        // Assign players and set the first turn
        newGame.players = [waitingPlayer.id, socket.id];
        newGame.currentPlayer = waitingPlayer.id; // The first player to wait gets the first turn
        games[gameId] = newGame;

        // Join both players to the same game room
        waitingPlayer.join(gameId);
        socket.join(gameId);

        // Reset the waiting queue
        const opponentSocket = waitingPlayer;
        waitingPlayer = null;

        // Notify both players that the match has started
        const payload = { gameId, gameState: serializeGameState(newGame) };
        wss.to(gameId).emit("matchFound", payload);

      } else {
        // No one is waiting, so this player becomes the waiting player
        console.log(`Client ${socket.id} is waiting for a match.`);
        waitingPlayer = socket;
        socket.emit("waitingForOpponent");
      }
    }
  });

  socket.on("takeShot", ({ gameId, angle, power }) => {
    logAuditEvent(`Client ${socket.id} taking shot in game ${gameId} with angle ${angle} and power ${power}`)
    console.log(`Client ${socket.id} taking shot in game ${gameId} with angle ${angle} and power ${power}`);
    const game = games[gameId];
    if (!game) return;

    // Verify if it's the correct player's turn in a match
    if (game.players.length > 1 && game.currentPlayer !== socket.id) {
      logAuditEvent(`Shot attempt by ${socket.id} but it's ${game.currentPlayer}'s turn.`)
      console.log(`Shot attempt by ${socket.id} but it's ${game.currentPlayer}'s turn.`);
      return;
    }

    if (game.players.length > 1) {
      const opponentId = game.players.find(p => p !== socket.id);
      if (opponentId) {
        console.log(`Relaying shot from ${socket.id} to opponent ${opponentId}`);
        wss.to(opponentId).emit("opponentTookShot", { angle, power });
      }
    }

    console.log(`Client ${socket.id} taking shot in game ${gameId}`);
    const finalGameState = runShotSimulation(game, angle, power);
    games[gameId] = finalGameState;

    if (finalGameState.gameState === "GameOver") {
      logAuditEvent(`Game ${gameId} won by ${socket.userData.id}`, {gameData: games[gameId]});
      finalGameState.winnerId = socket.id;
      applyMatchResult(socket.userData.id);
      console.log(games);
    }

    wss.to(gameId).emit("gameStateUpdate", serializeGameState(finalGameState));
  });

  // Handle disconnects for matchmaking and matches
  socket.on("disconnect", (reason) => {
    logAuditEvent(`Client disconnected: ${socket.id}, reason: ${reason}`);
    console.log(`🔌 Client disconnected: ${socket.id}, reason: ${reason}`);

    // If the disconnecting client was waiting for a match, clear the queue
    if (waitingPlayer && waitingPlayer.id === socket.id) {
      waitingPlayer = null;
      console.log("Waiting player disconnected, queue cleared.");
    }

    // If the client was in a match, notify the opponent and end the game
    Object.keys(games).forEach(gameId => {
      const game = games[gameId];
      const playerIndex = game.players.indexOf(socket.id);

      if (playerIndex !== -1 && game.players.length > 1) {
        console.log(`Player ${socket.id} disconnected from match ${gameId}.`);
        // Notify the other player
        const opponentId = game.players[1 - playerIndex];
        wss.to(opponentId).emit("opponentDisconnected");

        // Clean up the game
        delete games[gameId];
      }
    });
  });
});