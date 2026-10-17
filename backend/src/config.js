'use strict';
const fs = require('node:fs');
const path = require('node:path');

// Minimal .env loader so the backend has no configuration dependencies.
function loadDotEnv() {
  const file = path.join(__dirname, '..', '.env');
  if (!fs.existsSync(file)) return;
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    if (line.trim().startsWith('#')) continue;
    const match = /^\s*([A-Z0-9_]+)\s*=\s*(.*?)\s*$/.exec(line);
    if (!match) continue;
    if (process.env[match[1]] === undefined) process.env[match[1]] = match[2].replace(/^["']|["']$/g, '');
  }
}
loadDotEnv();

const env = (key, fallback) => (process.env[key] !== undefined && process.env[key] !== '' ? process.env[key] : fallback);
const int = (key, fallback) => {
  const value = Number.parseInt(env(key, ''), 10);
  return Number.isInteger(value) ? value : fallback;
};

function buildConfig(overrides = {}) {
  const dataDir = path.resolve(overrides.dataDir || env('DATA_DIR', path.join(__dirname, '..', 'data')));
  return {
    port: int('PORT', 8080),
    dataDir,
    downloadsDir: path.join(dataDir, 'downloads'),
    cosmeticsDir: path.join(dataDir, 'cosmetics'),
    websiteDir: path.resolve(env('WEBSITE_DIR', path.join(__dirname, '..', '..', 'website'))),
    tokenSecret: env('TOKEN_SECRET', ''),
    trustProxy: env('TRUST_PROXY', '0') === '1',
    admin: {
      username: env('ADMIN_USERNAME', 'admin'),
      email: env('ADMIN_EMAIL', 'admin@vsmp.local'),
      password: env('ADMIN_PASSWORD', ''),
    },
    server: {
      name: env('VSMP_NAME', 'VSMP'),
      javaHost: env('JAVA_HOST', 'neuron.zeroxhost.space'),
      javaPort: int('JAVA_PORT', 19145),
      bedrockHost: env('BEDROCK_HOST', env('JAVA_HOST', 'neuron.zeroxhost.space')),
      bedrockPort: int('BEDROCK_PORT', 19145),
      discordUrl: env('DISCORD_URL', ''),
      websiteUrl: env('WEBSITE_URL', ''),
    },
    maxUploadBytes: int('MAX_UPLOAD_MB', 300) * 1024 * 1024,
    ...overrides,
  };
}

module.exports = { buildConfig };
