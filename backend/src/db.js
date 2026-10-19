'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { DatabaseSync } = require('node:sqlite');

function openDatabase(dataDir) {
  const inMemory = dataDir === ':memory:';
  if (!inMemory) fs.mkdirSync(dataDir, { recursive: true });
  const db = new DatabaseSync(inMemory ? ':memory:' : path.join(dataDir, 'vclient.db'));
  db.exec('PRAGMA journal_mode = WAL; PRAGMA foreign_keys = ON;');
  db.exec(`
    CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      username TEXT NOT NULL UNIQUE COLLATE NOCASE,
      email TEXT NOT NULL UNIQUE COLLATE NOCASE,
      password_hash TEXT NOT NULL,
      role TEXT NOT NULL DEFAULT 'user' CHECK (role IN ('user','admin')),
      minecraft_name TEXT NOT NULL DEFAULT '',
      elyby_name TEXT NOT NULL DEFAULT '',
      created_at INTEGER NOT NULL,
      last_login_at INTEGER
    );
    CREATE TABLE IF NOT EXISTS news (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      title TEXT NOT NULL,
      body TEXT NOT NULL,
      category TEXT NOT NULL DEFAULT 'launcher' CHECK (category IN ('launcher','server')),
      pinned INTEGER NOT NULL DEFAULT 0,
      published INTEGER NOT NULL DEFAULT 1,
      author_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS announcements (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      message TEXT NOT NULL,
      level TEXT NOT NULL DEFAULT 'info' CHECK (level IN ('info','warning','critical')),
      active INTEGER NOT NULL DEFAULT 1,
      starts_at INTEGER,
      ends_at INTEGER,
      created_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS releases (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      platform TEXT NOT NULL CHECK (platform IN ('windows','android')),
      version TEXT NOT NULL,
      notes TEXT NOT NULL DEFAULT '',
      min_supported TEXT NOT NULL DEFAULT '0.0.0',
      mandatory INTEGER NOT NULL DEFAULT 0,
      published INTEGER NOT NULL DEFAULT 0,
      file_name TEXT,
      sha256 TEXT,
      size INTEGER,
      downloads INTEGER NOT NULL DEFAULT 0,
      created_at INTEGER NOT NULL,
      UNIQUE (platform, version)
    );
    CREATE TABLE IF NOT EXISTS events (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      install_id TEXT NOT NULL,
      platform TEXT NOT NULL,
      version TEXT NOT NULL,
      event TEXT NOT NULL,
      created_at INTEGER NOT NULL
    );
    CREATE INDEX IF NOT EXISTS events_time ON events (created_at);
    CREATE INDEX IF NOT EXISTS events_install ON events (install_id);
    CREATE TABLE IF NOT EXISTS friendships (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      requester_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      addressee_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      status TEXT NOT NULL CHECK (status IN ('pending','accepted')),
      created_at INTEGER NOT NULL,
      responded_at INTEGER,
      UNIQUE (requester_id, addressee_id)
    );
    CREATE INDEX IF NOT EXISTS friendships_addressee ON friendships (addressee_id);
    CREATE TABLE IF NOT EXISTS presence (
      user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
      last_seen INTEGER NOT NULL,
      activity TEXT NOT NULL DEFAULT '',
      server TEXT NOT NULL DEFAULT '',
      platform TEXT NOT NULL DEFAULT ''
    );
    CREATE TABLE IF NOT EXISTS cosmetics (
      id TEXT PRIMARY KEY,
      version INTEGER NOT NULL DEFAULT 1,
      category TEXT NOT NULL CHECK (category IN ('cape','wings','hat','emote','animated','trail')),
      tier TEXT NOT NULL DEFAULT 'free' CHECK (tier IN ('free','premium','event')),
      name TEXT NOT NULL,
      description TEXT NOT NULL DEFAULT '',
      sort INTEGER NOT NULL DEFAULT 1000,
      enabled INTEGER NOT NULL DEFAULT 1,
      starts_at INTEGER,
      ends_at INTEGER,
      definition TEXT NOT NULL,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS cosmetic_assets (
      file_name TEXT PRIMARY KEY,
      sha256 TEXT NOT NULL,
      size INTEGER NOT NULL,
      created_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS players (
      uuid TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      account_type TEXT NOT NULL DEFAULT 'offline',
      version TEXT NOT NULL DEFAULT '',
      first_seen INTEGER NOT NULL,
      last_seen INTEGER NOT NULL,
      launches INTEGER NOT NULL DEFAULT 1
    );
    CREATE INDEX IF NOT EXISTS players_last_seen ON players (last_seen);
    CREATE TABLE IF NOT EXISTS cosmetic_grants (
      player_uuid TEXT NOT NULL,
      cosmetic_id TEXT NOT NULL,
      granted_at INTEGER NOT NULL,
      PRIMARY KEY (player_uuid, cosmetic_id)
    );
  `);
  // Older databases tied grants to rows in the cosmetics table; built-in cosmetics have no row, so drop that link.
  const grants = db.prepare("SELECT sql FROM sqlite_master WHERE name = 'cosmetic_grants'").get();
  if (grants && /REFERENCES/i.test(grants.sql)) {
    db.exec(`
      ALTER TABLE cosmetic_grants RENAME TO cosmetic_grants_old;
      CREATE TABLE cosmetic_grants (
        player_uuid TEXT NOT NULL,
        cosmetic_id TEXT NOT NULL,
        granted_at INTEGER NOT NULL,
        PRIMARY KEY (player_uuid, cosmetic_id)
      );
      INSERT INTO cosmetic_grants SELECT player_uuid, cosmetic_id, granted_at FROM cosmetic_grants_old;
      DROP TABLE cosmetic_grants_old;
    `);
  }
  return db;
}

module.exports = { openDatabase };
