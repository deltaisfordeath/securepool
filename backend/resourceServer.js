// resourceServer.js
import express, { json } from 'express';
import cors from 'cors';
import http from 'http';
import { Server } from 'socket.io';
import jwt from 'jsonwebtoken';
import crypto from 'crypto';
import dotenv from 'dotenv';

import initializeDatabase from './initializeDatabase.js';
import { GamePhysics } from "./gamePhysics.js";
import { Ball } from './ball.js';

dotenv.config();

const app = express();
app.use(cors());
app.use(json());

// --- Environment Variables & Constants ---
const JWT_SECRET = process.env.JWT_SECRET;
const PORT = 3002;

// --- In-memory Game State ---
const games = {};
let waitingPlayer = null;

// --- Database Connection ---
let db;
try {
    db = await initializeDatabase();
    console.log('Resource Service: Database connected successfully');
} catch (error) {
    console.error('Resource Service: Database connection failed. Exiting.', error);
    process.exit(1);
}

// --- JWT Middleware for HTTP Requests ---
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
    console.log(`Player ${player} ${isWinner ? 'won and gained 10 points!' : 'has been deducted 10 points.'}`);

  } catch (error) {
    console.error('Error syncing match result:', error);
  }
}

// --- REST API for Game/User Data ---
// All these routes are protected and require a valid JWT
app.get('/score', verifyJwt, async (req, res) => {
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

app.post('/restore-score', verifyJwt, async (req, res) => {
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

app.get('/leaderboard', verifyJwt, async (req, res) => {
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


// --- Server and WebSocket Setup ---
const server = http.createServer(app);
const wss = new Server(server, {
    cors: {
        origin: "*", // Adjust for your client's origin in production
        methods: ["GET", "POST"]
    }
});

// --- WebSocket Authentication Middleware ---
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

// --- WebSocket Connection Handling ---
wss.on('connection', (socket) => {
    console.log(`🔌 Socket.IO client connected: ${socket.id}`);

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
        console.log(`Client ${socket.id} taking shot in game ${gameId} with angle ${angle} and power ${power}`);
        const game = games[gameId];
        if (!game) return;

        // Verify if it's the correct player's turn in a match
        if (game.players.length > 1 && game.currentPlayer !== socket.id) {
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
        finalGameState.winnerId = socket.id;
        applyMatchResult(socket.userData.id);
        console.log(games);
        }

        wss.to(gameId).emit("gameStateUpdate", serializeGameState(finalGameState));
    });

    // Handle disconnects for matchmaking and matches
    socket.on("disconnect", (reason) => {
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


server.listen(PORT, () => {
    console.log(`🎱 Resource Service (API & Game) running on http://localhost:${PORT}`);
});