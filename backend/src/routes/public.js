'use strict';
const fs = require('node:fs');
const path = require('node:path');
const express = require('express');
const { compareVersions } = require('./../security');

const PLATFORMS = ['windows', 'android'];
const EVENTS = ['launch', 'play', 'install', 'update', 'join'];

const newsView = (row) => ({
  id: row.id,
  title: row.title,
  body: row.body,
  category: row.category,
  pinned: !!row.pinned,
  createdAt: new Date(row.created_at * 1000).toISOString(),
  updatedAt: new Date(row.updated_at * 1000).toISOString(),
});

const releaseView = (row) => row && ({
  id: row.id,
  platform: row.platform,
  version: row.version,
  notes: row.notes,
  mandatory: !!row.mandatory,
  minSupported: row.min_supported,
  size: row.size,
  sha256: row.sha256,
  url: row.file_name ? `/downloads/${encodeURIComponent(row.file_name)}` : null,
  publishedAt: new Date(row.created_at * 1000).toISOString(),
});

function latestRelease(db, platform) {
  const rows = db.prepare(
    'SELECT * FROM releases WHERE platform = ? AND published = 1 AND file_name IS NOT NULL',
  ).all(platform);
  return rows.sort((a, b) => compareVersions(b.version, a.version))[0] || null;
}

/**
 * Windows release described by website/update.json, which the installer build writes next to the installer.
 * It is part of the deployed site, so it survives restarts on hosts without a disk. Uploaded releases win.
 */
function fileRelease(config, platform) {
  if (platform !== 'windows') return null;
  try {
    const info = JSON.parse(fs.readFileSync(path.join(config.websiteDir, 'update.json'), 'utf8'));
    if (!info.version || !info.url) return null;
    return {
      id: 0, platform, version: String(info.version), notes: String(info.notes || ''), mandatory: !!info.mandatory,
      minSupported: String(info.minSupported || '0.0.0'), size: Number(info.size) || 0, sha256: String(info.sha256 || ''),
      url: String(info.url), publishedAt: String(info.publishedAt || ''),
    };
  } catch {
    return null;
  }
}

function publicRoutes(ctx) {
  const { db, config, status, now } = ctx;
  const router = express.Router();

  router.get('/health', (req, res) => res.json({ ok: true, time: new Date().toISOString() }));

  // One call a launcher can make on start-up to render the home screen.
  router.get('/config', (req, res) => res.json({
    server: {
      name: config.server.name,
      java: { host: config.server.javaHost, port: config.server.javaPort },
      bedrock: { host: config.server.bedrockHost, port: config.server.bedrockPort },
      discordUrl: config.server.discordUrl,
      websiteUrl: config.server.websiteUrl,
    },
  }));

  router.get('/server/status', async (req, res, next) => {
    try {
      res.set('Cache-Control', 'public, max-age=15');
      res.json(await status.get());
    } catch (error) { next(error); }
  });

  router.get('/news', (req, res) => {
    const limit = Math.min(Math.max(Number.parseInt(req.query.limit, 10) || 20, 1), 100);
    const offset = Math.max(Number.parseInt(req.query.offset, 10) || 0, 0);
    const category = ['launcher', 'server'].includes(req.query.category) ? req.query.category : null;
    const rows = db.prepare(
      `SELECT * FROM news WHERE published = 1 ${category ? 'AND category = ?' : ''}
       ORDER BY pinned DESC, created_at DESC, id DESC LIMIT ? OFFSET ?`,
    ).all(...(category ? [category] : []), limit, offset);
    res.json({ items: rows.map(newsView) });
  });

  router.get('/news/:id', (req, res) => {
    const row = db.prepare('SELECT * FROM news WHERE id = ? AND published = 1').get(Number(req.params.id));
    if (!row) return res.status(404).json({ error: 'News post not found' });
    return res.json(newsView(row));
  });

  router.get('/announcements', (req, res) => {
    const t = now();
    const rows = db.prepare(
      `SELECT * FROM announcements WHERE active = 1
         AND (starts_at IS NULL OR starts_at <= ?) AND (ends_at IS NULL OR ends_at >= ?)
       ORDER BY created_at DESC`,
    ).all(t, t);
    res.json({
      items: rows.map((r) => ({ id: r.id, message: r.message, level: r.level, createdAt: new Date(r.created_at * 1000).toISOString() })),
    });
  });

  // Launcher update check. `current` is the version installed on the caller's device.
  router.get('/launcher/update', (req, res) => {
    const platform = String(req.query.platform || '').toLowerCase();
    if (!PLATFORMS.includes(platform)) return res.status(400).json({ error: 'platform must be windows or android' });
    const current = String(req.query.current || '0.0.0').slice(0, 32);
    const uploaded = latestRelease(db, platform);
    if (!uploaded) {
      const fromFile = fileRelease(config, platform);
      if (!fromFile) return res.json({ updateAvailable: false, mandatory: false, current, latest: null });
      const available = compareVersions(fromFile.version, current) > 0;
      const required = available && (fromFile.mandatory || compareVersions(current, fromFile.minSupported) < 0);
      return res.json({ updateAvailable: available, mandatory: required, current, latest: fromFile });
    }
    const latest = uploaded;
    const updateAvailable = compareVersions(latest.version, current) > 0;
    const mandatory = updateAvailable && (!!latest.mandatory || compareVersions(current, latest.min_supported) < 0);
    return res.json({ updateAvailable, mandatory, current, latest: releaseView(latest) });
  });

  router.get('/launcher/releases', (req, res) => {
    const out = {};
    for (const platform of PLATFORMS) out[platform] = releaseView(latestRelease(db, platform));
    res.json(out);
  });

  // Anonymous usage counters. installId is a random id generated by the app, never a hardware id.
  router.post('/stats/event', (req, res) => {
    const { installId, platform, version, event } = req.body || {};
    if (typeof installId !== 'string' || !/^[A-Za-z0-9-]{8,64}$/.test(installId)
      || !PLATFORMS.includes(platform) || !EVENTS.includes(event)
      || typeof version !== 'string' || version.length > 32) {
      return res.status(400).json({ error: 'Invalid event' });
    }
    db.prepare('INSERT INTO events (install_id, platform, version, event, created_at) VALUES (?, ?, ?, ?, ?)')
      .run(installId, platform, version, event, now());
    return res.status(202).json({ ok: true });
  });

  return router;
}

/** Serves uploaded installers and counts downloads. */
publicRoutes.downloads = (ctx) => {
  const router = express.Router();
  router.get('/:file', (req, res, next) => {
    const name = path.basename(req.params.file);
    const release = ctx.db.prepare('SELECT * FROM releases WHERE file_name = ? AND published = 1').get(name);
    if (!release) return res.status(404).send('Not found');
    // Ranged/resumed requests (`Range` header) should not inflate the counter.
    if (!req.headers.range) ctx.db.prepare('UPDATE releases SET downloads = downloads + 1 WHERE id = ?').run(release.id);
    return res.download(path.join(ctx.config.downloadsDir, name), name, { dotfiles: 'deny' }, (error) => {
      if (error && !res.headersSent) next(error);
    });
  });
  return router;
};

/** Stable link for the website: /download/windows and /download/android redirect to the newest build. */
publicRoutes.latestDownload = (ctx) => (req, res) => {
  const platform = String(req.params.platform).toLowerCase();
  const latest = PLATFORMS.includes(platform) ? latestRelease(ctx.db, platform) : null;
  // Until the first Windows release is published through the admin panel, serve the bundled setup.
  if (!latest && platform === 'windows') return res.redirect(302, '/VSMPClientSetup.exe');
  if (!latest && platform === 'android') return res.redirect(302, '/V-Client.apk');
  if (!latest) return res.status(404).send('No release has been published for this platform yet.');
  return res.redirect(302, `/downloads/${encodeURIComponent(latest.file_name)}`);
};

module.exports = publicRoutes;
module.exports.releaseView = releaseView;
module.exports.newsView = newsView;
