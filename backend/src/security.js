'use strict';
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

const SCRYPT = { N: 16384, r: 8, p: 1, maxmem: 64 * 1024 * 1024 };

function hashPassword(password) {
  const salt = crypto.randomBytes(16);
  const key = crypto.scryptSync(password, salt, 64, SCRYPT);
  return `scrypt$${salt.toString('base64')}$${key.toString('base64')}`;
}

function verifyPassword(password, stored) {
  const [scheme, saltB64, keyB64] = String(stored).split('$');
  if (scheme !== 'scrypt' || !saltB64 || !keyB64) return false;
  const expected = Buffer.from(keyB64, 'base64');
  const actual = crypto.scryptSync(password, Buffer.from(saltB64, 'base64'), expected.length, SCRYPT);
  return crypto.timingSafeEqual(actual, expected);
}

function loadSecret(config) {
  if (config.tokenSecret) return config.tokenSecret;
  const file = path.join(config.dataDir, 'secret.key');
  fs.mkdirSync(config.dataDir, { recursive: true });
  if (fs.existsSync(file)) return fs.readFileSync(file, 'utf8').trim();
  const secret = crypto.randomBytes(48).toString('base64url');
  fs.writeFileSync(file, secret, { mode: 0o600 });
  return secret;
}

// Compact signed token: base64url(payload).base64url(hmac). Payload carries the user id and expiry.
function createTokens(secret) {
  const sign = (body) => crypto.createHmac('sha256', secret).update(body).digest('base64url');
  return {
    issue(userId, ttlSeconds = 60 * 60 * 24 * 30) {
      const body = Buffer.from(JSON.stringify({ uid: userId, exp: Math.floor(Date.now() / 1000) + ttlSeconds })).toString('base64url');
      return `${body}.${sign(body)}`;
    },
    verify(token) {
      if (typeof token !== 'string') return null;
      const [body, signature] = token.split('.');
      if (!body || !signature) return null;
      const expected = Buffer.from(sign(body));
      const actual = Buffer.from(signature);
      if (expected.length !== actual.length || !crypto.timingSafeEqual(expected, actual)) return null;
      try {
        const payload = JSON.parse(Buffer.from(body, 'base64url').toString('utf8'));
        return payload.exp > Date.now() / 1000 ? payload : null;
      } catch {
        return null;
      }
    },
  };
}

// Fixed-window in-memory limiter, keyed per client IP.
function rateLimit({ windowMs, max, bucket }) {
  const hits = new Map();
  setInterval(() => {
    const now = Date.now();
    for (const [key, entry] of hits) if (entry.reset <= now) hits.delete(key);
  }, windowMs).unref();
  return (req, res, next) => {
    const key = `${bucket}:${req.ip}`;
    const now = Date.now();
    let entry = hits.get(key);
    if (!entry || entry.reset <= now) {
      entry = { count: 0, reset: now + windowMs };
      hits.set(key, entry);
    }
    entry.count += 1;
    if (entry.count > max) {
      res.set('Retry-After', String(Math.ceil((entry.reset - now) / 1000)));
      return res.status(429).json({ error: 'Too many requests. Try again shortly.' });
    }
    next();
  };
}

function compareVersions(a, b) {
  const pa = String(a).split(/[.\-+]/).map((n) => Number.parseInt(n, 10) || 0);
  const pb = String(b).split(/[.\-+]/).map((n) => Number.parseInt(n, 10) || 0);
  for (let i = 0; i < Math.max(pa.length, pb.length); i += 1) {
    const diff = (pa[i] || 0) - (pb[i] || 0);
    if (diff !== 0) return diff < 0 ? -1 : 1;
  }
  return 0;
}

module.exports = { hashPassword, verifyPassword, loadSecret, createTokens, rateLimit, compareVersions };
