// gateway.js
import express from 'express';
import cors from 'cors';
import https from 'https';
import fs from 'fs';
import { createProxyMiddleware } from 'http-proxy-middleware';
import dotenv from 'dotenv';

dotenv.config();

const app = express();
app.use(cors());

// --- Service Locations ---
// These would be environment variables in a real deployment
const AUTH_SERVICE_URL = 'http://localhost:3001';
const RESOURCE_SERVICE_URL = 'http://localhost:3002';

// --- Proxy Middleware ---

// Proxy for Authentication endpoints
app.use('/auth', createProxyMiddleware({
    target: AUTH_SERVICE_URL,
    changeOrigin: true,
    pathRewrite: { '^/auth': '' }, // remove /auth prefix when forwarding
    onProxyReq: (proxyReq, req, res) => {
        console.log(`Gateway -> Auth Service: ${req.method} ${req.originalUrl}`);
    }
}));

// Proxy for Resource endpoints
const resourceProxy = createProxyMiddleware({
    target: RESOURCE_SERVICE_URL,
    changeOrigin: true
});

// Proxy for websocket
const wsProxy = createProxyMiddleware({ target: 'wss://localhost:3002', changeOrigin: true });

app.use('/api', resourceProxy);
app.use(wsProxy); // The path Socket.IO client uses

// --- Server Setup ---
const options = {
    key: fs.readFileSync('./dev_cert/securepool_key.pem'),
    cert: fs.readFileSync('./dev_cert/securepool_cert.pem')
};

const server = https.createServer(options, app);

server.listen(443, '0.0.0.0', () => {
    console.log('🚀 API Gateway running on port 443');
    console.log(`-> Forwarding /auth/** to ${AUTH_SERVICE_URL}`);
    console.log(`-> Forwarding /api/** and WebSockets to ${RESOURCE_SERVICE_URL}`);
});