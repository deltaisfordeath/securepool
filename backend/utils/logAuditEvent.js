// utils/logAuditEvent.js
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { dirname } from 'path';

// Setup __dirname for ES modules
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// Create /logs directory if it doesn't exist
const logDir = path.resolve(__dirname, '../logs');
if (!fs.existsSync(logDir)) {
  fs.mkdirSync(logDir, { recursive: true });
}

const logFile = path.join(logDir, 'audit.log');

/**
 * Logs an event to audit.log file with optional metadata
 *
 * @param {string} event - e.g., "Login Success"
 * @param {Object} metadata - e.g., { user: "admin", ip: "127.0.0.1" }
 */
export function logAuditEvent(event, metadata = {}) {
  const timestamp = new Date().toISOString();
  const metaString = Object.entries(metadata)
    .map(([k, v]) => `${k}=${v}`)
    .join(' ');
  const logLine = `[${timestamp}] EVENT: ${event}${metaString ? ' ' + metaString : ''}\n`;

  console.log(logLine.trim());

  fs.appendFile(logFile, logLine, (err) => {
    if (err) console.error('❌ Audit file write failed:', err);
  });
}
