import rateLimit from 'express-rate-limit';
import { logAudit } from './utils/auditLogger.js';

export const generalLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 100,
  message: 'Too many requests from this IP, please try again later.',
  standardHeaders: true,
  legacyHeaders: false,
});

export const loginLimiter = rateLimit({
  windowMs: 10 * 60 * 1000,
  max: 5,
  handler: async (req, res) => {
    const username = req.body?.username || 'unknown';
    console.log(`[RATE LIMIT BLOCKED] IP: ${req.ip}, URL: ${req.originalUrl}`);
    try {
      await logAudit(username, 'Rate Limit Blocked: /api/login', req);
    } catch (err) {
      console.error('Audit logging failed:', err);
    }
    return res.status(429).json({
      message: 'Too many login attempts. Please try again later.'
    });
  },
  standardHeaders: true,
  legacyHeaders: false,
});

export const registerLimiter = rateLimit({
  windowMs: 10 * 60 * 1000,
  max: 5,
  handler: async (req, res) => {
    const username = req.body?.username || 'unknown';
    console.log(`[RATE LIMIT BLOCKED] IP: ${req.ip}, URL: ${req.originalUrl}`);
    try {
      await logAudit(username, 'Rate Limit Blocked: /api/register', req);
    } catch (err) {
      console.error('Audit logging failed:', err);
    }
    return res.status(429).json({
      message: 'Too many registration attempts. Please try again later.'
    });
  },
  standardHeaders: true,
  legacyHeaders: false,
});
