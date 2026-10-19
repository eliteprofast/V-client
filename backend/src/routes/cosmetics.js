'use strict';
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const express = require('express');
const { requireAdmin } = require('./auth');
const { INVITE_REWARDS, rewardsFor } = require('../rewards');

const SCHEMA = 1;
const CATEGORIES = ['cape', 'wings', 'hat', 'emote', 'animated', 'trail'];
const SLOTS = ['cape', 'wings', 'hat', 'trail', 'emote'];
const TIERS = ['free', 'premium', 'event'];
const ID = /^[a-z0-9_.-]{1,64}$/;
const ASSET_NAME = /^[A-Za-z0-9._-]{1,64}\.png$/;
const PLAYER_UUID = /^[0-9a-f]{32}$/;
const PNG_MAGIC = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
const MAX_ASSET_BYTES = 2 * 1024 * 1024;
const MAX_DEFINITION_BYTES = 32 * 1024;

const epoch = (value) => {
  if (value === null || value === undefined || value === '') return 0;
  if (typeof value === 'number' && Number.isInteger(value) && value >= 0) return value;
  const ms = Date.parse(value);
  return Number.isNaN(ms) ? undefined : Math.floor(ms / 1000);
};

/** File names a definition refers to: texture.asset plus an optional explicit "assets" list. */
function referencedAssets(definition) {
  const names = new Set();
  if (definition && typeof definition.texture === 'object' && definition.texture && definition.texture.asset) {
    names.add(String(definition.texture.asset));
  }
  if (definition && Array.isArray(definition.assets)) definition.assets.forEach((name) => names.add(String(name)));
  return [...names];
}

/** Checks a cosmetic submitted by an admin; returns an error string or null. */
function validate(item) {
  if (!ID.test(item.id || '')) return 'id must be 1-64 characters of a-z, 0-9, _, . or -';
  if (!CATEGORIES.includes(item.category)) return `category must be one of ${CATEGORIES.join(', ')}`;
  if (!TIERS.includes(item.tier)) return `tier must be one of ${TIERS.join(', ')}`;
  if (typeof item.name !== 'string' || !item.name.trim() || item.name.length > 60) return 'name is required (max 60 characters)';
  if (typeof item.description !== 'string' || item.description.length > 300) return 'description must be at most 300 characters';
  if (!item.definition || typeof item.definition !== 'object' || Array.isArray(item.definition)) return 'definition must be an object';
  if (Buffer.byteLength(JSON.stringify(item.definition)) > MAX_DEFINITION_BYTES) return 'definition is larger than 32 KB';
  if (item.category === 'animated' && !SLOTS.includes(item.definition.slot)) {
    return `animated cosmetics need definition.slot (${SLOTS.join(', ')})`;
  }
  if (item.startsAt === undefined || item.endsAt === undefined) return 'startsAt/endsAt must be ISO dates or Unix seconds';
  if (item.endsAt && item.startsAt && item.endsAt <= item.startsAt) return 'endsAt must be after startsAt';
  if (!Number.isInteger(item.sort) || item.sort < 0 || item.sort > 100000) return 'sort must be an integer 0-100000';
  return null;
}

function cosmeticsRoutes(ctx) {
  const { db, config, now } = ctx;
  const router = express.Router();
  fs.mkdirSync(config.cosmeticsDir, { recursive: true });

  const assetRow = (name) => db.prepare('SELECT * FROM cosmetic_assets WHERE file_name = ?').get(name);

  const view = (row) => {
    const definition = JSON.parse(row.definition);
    const assets = referencedAssets(definition).map((name) => assetRow(name)).filter(Boolean).map((asset) => ({
      file: asset.file_name, url: `/api/cosmetics/assets/${encodeURIComponent(asset.file_name)}`,
      sha256: asset.sha256, size: asset.size,
    }));
    return {
      id: row.id, version: row.version, category: row.category, tier: row.tier, name: row.name,
      description: row.description, sort: row.sort,
      startsAt: row.starts_at || null, endsAt: row.ends_at || null, definition, assets,
    };
  };

  // ---- Public ----------------------------------------------------------
  // The catalog is the same for everybody; ownership is a separate, per-player call.
  router.get('/catalog', (req, res) => {
    const items = db.prepare('SELECT * FROM cosmetics WHERE enabled = 1 ORDER BY sort, name').all().map(view);
    const removed = db.prepare('SELECT id FROM cosmetics WHERE enabled = 0').all().map((row) => row.id);
    const body = { schema: SCHEMA, items, removed };
    const etag = `"${crypto.createHash('sha1').update(JSON.stringify(body)).digest('hex')}"`;
    res.set({ ETag: etag, 'Cache-Control': 'public, max-age=60' });
    if (req.headers['if-none-match'] === etag) return res.status(304).end();
    return res.json({ ...body, generatedAt: new Date().toISOString() });
  });

  router.get('/owned/:uuid', (req, res) => {
    const uuid = req.params.uuid.replace(/-/g, '').toLowerCase();
    if (!PLAYER_UUID.test(uuid)) return res.status(400).json({ error: 'A Minecraft UUID is required' });
    const owned = db.prepare('SELECT cosmetic_id FROM cosmetic_grants WHERE player_uuid = ?').all(uuid)
      .map((row) => row.cosmetic_id);
    res.set('Cache-Control', 'no-cache');
    return res.json({ uuid, owned });
  });

  router.get('/assets/:file', (req, res) => {
    const name = req.params.file;
    if (!ASSET_NAME.test(name) || !assetRow(name)) return res.status(404).json({ error: 'Asset not found' });
    res.set({ 'Content-Type': 'image/png', 'Cache-Control': 'public, max-age=3600' });
    return res.sendFile(path.join(config.cosmeticsDir, name), { dotfiles: 'deny' });
  });

  return router;
}

/** Admin endpoints, mounted under /api/admin/cosmetics. */
cosmeticsRoutes.admin = (ctx) => {
  const { db, config, now } = ctx;
  const router = express.Router();
  router.use(requireAdmin(ctx));

  const adminView = (row) => ({
    id: row.id, version: row.version, category: row.category, tier: row.tier, name: row.name,
    description: row.description, sort: row.sort, enabled: !!row.enabled,
    startsAt: row.starts_at ? new Date(row.starts_at * 1000).toISOString() : null,
    endsAt: row.ends_at ? new Date(row.ends_at * 1000).toISOString() : null,
    definition: JSON.parse(row.definition),
  });

  router.get('/', (req, res) => {
    res.json({
      items: db.prepare('SELECT * FROM cosmetics ORDER BY sort, name').all().map(adminView),
      assets: db.prepare('SELECT file_name, sha256, size FROM cosmetic_assets ORDER BY file_name').all()
        .map((a) => ({ file: a.file_name, sha256: a.sha256, size: a.size })),
    });
  });

  const normalize = (body, existing) => ({
    id: existing ? existing.id : String(body.id || '').toLowerCase(),
    category: body.category ?? existing?.category,
    tier: body.tier ?? existing?.tier ?? 'free',
    name: body.name ?? existing?.name,
    description: body.description ?? existing?.description ?? '',
    sort: body.sort ?? existing?.sort ?? 1000,
    startsAt: body.startsAt !== undefined ? epoch(body.startsAt) : (existing?.starts_at || 0),
    endsAt: body.endsAt !== undefined ? epoch(body.endsAt) : (existing?.ends_at || 0),
    enabled: body.enabled ?? (existing ? !!existing.enabled : true),
    definition: body.definition ?? (existing ? JSON.parse(existing.definition) : undefined),
  });

  router.post('/', (req, res) => {
    const item = normalize(req.body || {}, null);
    const problem = validate(item);
    if (problem) return res.status(400).json({ error: problem });
    if (db.prepare('SELECT 1 FROM cosmetics WHERE id = ?').get(item.id)) {
      return res.status(409).json({ error: `Cosmetic ${item.id} already exists` });
    }
    db.prepare(`INSERT INTO cosmetics (id, version, category, tier, name, description, sort, enabled, starts_at, ends_at,
      definition, created_at, updated_at) VALUES (?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`)
      .run(item.id, item.category, item.tier, item.name.trim(), item.description, item.sort, item.enabled ? 1 : 0,
        item.startsAt || null, item.endsAt || null, JSON.stringify(item.definition), now(), now());
    return res.status(201).json(adminView(db.prepare('SELECT * FROM cosmetics WHERE id = ?').get(item.id)));
  });

  router.put('/assets/:name', express.raw({ type: () => true, limit: MAX_ASSET_BYTES }), (req, res) => {
    const name = req.params.name;
    if (!ASSET_NAME.test(name)) return res.status(400).json({ error: 'Asset names are letters, digits, . _ - and end in .png' });
    const body = req.body;
    if (!Buffer.isBuffer(body) || body.length < 67 || !body.subarray(0, 8).equals(PNG_MAGIC)) {
      return res.status(400).json({ error: 'The body must be a PNG file (max 2 MB)' });
    }
    const sha256 = crypto.createHash('sha256').update(body).digest('hex');
    fs.writeFileSync(path.join(config.cosmeticsDir, name), body);
    db.prepare(`INSERT INTO cosmetic_assets (file_name, sha256, size, created_at) VALUES (?, ?, ?, ?)
      ON CONFLICT(file_name) DO UPDATE SET sha256 = excluded.sha256, size = excluded.size`).run(name, sha256, body.length, now());
    // Bump every cosmetic that uses the file so clients re-download it.
    for (const row of db.prepare('SELECT id, definition FROM cosmetics').all()) {
      const definition = JSON.parse(row.definition);
      if (referencedAssets(definition).includes(name)) {
        db.prepare('UPDATE cosmetics SET version = version + 1, updated_at = ? WHERE id = ?').run(now(), row.id);
      }
    }
    return res.status(201).json({ file: name, sha256, size: body.length });
  });

  router.delete('/assets/:name', (req, res) => {
    const name = req.params.name;
    if (!ASSET_NAME.test(name)) return res.status(400).json({ error: 'Invalid asset name' });
    const result = db.prepare('DELETE FROM cosmetic_assets WHERE file_name = ?').run(name);
    fs.rmSync(path.join(config.cosmeticsDir, name), { force: true });
    return result.changes ? res.json({ ok: true }) : res.status(404).json({ error: 'Asset not found' });
  });

  router.get('/grants', (req, res) => {
    const uuid = String(req.query.uuid || '').replace(/-/g, '').toLowerCase();
    const rows = PLAYER_UUID.test(uuid)
      ? db.prepare('SELECT player_uuid, cosmetic_id, granted_at FROM cosmetic_grants WHERE player_uuid = ?').all(uuid)
      : db.prepare('SELECT player_uuid, cosmetic_id, granted_at FROM cosmetic_grants ORDER BY granted_at DESC LIMIT 500').all();
    res.json({ items: rows.map((r) => ({ uuid: r.player_uuid, cosmeticId: r.cosmetic_id, grantedAt: new Date(r.granted_at * 1000).toISOString() })) });
  });

  /** The UUID Minecraft gives an offline (no-login) player: an MD5 of "OfflinePlayer:<name>" as a version-3 UUID. */
  function offlineUuid(name) {
    const hash = crypto.createHash('md5').update('OfflinePlayer:' + name, 'utf8').digest();
    hash[6] = (hash[6] & 0x0f) | 0x30;
    hash[8] = (hash[8] & 0x3f) | 0x80;
    return hash.toString('hex');
  }

  /**
   * A player is a 32-hex UUID, or a name. A name can belong to an offline account, an Ely.by account or both, and
   * each has a different UUID, so every UUID the name can have is returned (empty when the input is not valid).
   */
  async function resolvePlayer(value) {
    const text = String(value || '').trim();
    const compact = text.replace(/-/g, '').toLowerCase();
    if (PLAYER_UUID.test(compact)) return [compact];
    if (!/^[A-Za-z0-9_]{3,16}$/.test(text)) return [];
    const ids = [offlineUuid(text)];
    try {
      const response = await fetch('https://authserver.ely.by/api/users/profiles/minecraft/' + encodeURIComponent(text),
        { signal: AbortSignal.timeout(8000) });
      if (response.ok) {
        const id = String((await response.json()).id || '').replace(/-/g, '').toLowerCase();
        if (PLAYER_UUID.test(id) && !ids.includes(id)) ids.unshift(id);
      }
    } catch {
      // Ely.by unreachable: the offline UUID still works for offline accounts.
    }
    return ids;
  }

  const grant = (uuid, cosmeticId) => db.prepare(
    'INSERT OR IGNORE INTO cosmetic_grants (player_uuid, cosmetic_id, granted_at) VALUES (?, ?, ?)').run(uuid, cosmeticId, now());

  router.get('/rewards', (req, res) => res.json({ items: INVITE_REWARDS }));

  // Grants one cosmetic (cosmeticId) or an invite-reward tier (invites: N). The player is a UUID or Ely.by name.
  // Ids need not be in the database: the built-in catalog ships inside the mod.
  router.post('/grants', async (req, res) => {
    const uuids = await resolvePlayer(req.body?.player ?? req.body?.uuid);
    if (!uuids.length) return res.status(400).json({ error: 'Unknown player: enter a player name or a Minecraft UUID' });
    let ids;
    if (req.body?.invites !== undefined) {
      const invites = Number(req.body.invites);
      if (!Number.isInteger(invites) || invites < 0 || invites > 100000) return res.status(400).json({ error: 'invites must be a whole number' });
      ids = rewardsFor(invites);
    } else {
      const cosmeticId = String(req.body?.cosmeticId || '');
      if (!ID.test(cosmeticId)) return res.status(400).json({ error: 'cosmeticId is required' });
      ids = [cosmeticId];
    }
    uuids.forEach((uuid) => ids.forEach((id) => grant(uuid, id)));
    return res.status(201).json({ uuid: uuids[0], uuids, granted: ids, cosmeticId: ids[0] });
  });

  router.delete('/grants', (req, res) => {
    const uuid = String(req.body?.uuid || '').replace(/-/g, '').toLowerCase();
    const result = db.prepare('DELETE FROM cosmetic_grants WHERE player_uuid = ? AND cosmetic_id = ?')
      .run(uuid, String(req.body?.cosmeticId || ''));
    return result.changes ? res.json({ ok: true }) : res.status(404).json({ error: 'Grant not found' });
  });

  router.put('/:id', (req, res) => {
    const existing = db.prepare('SELECT * FROM cosmetics WHERE id = ?').get(req.params.id);
    if (!existing) return res.status(404).json({ error: 'Cosmetic not found' });
    const item = normalize(req.body || {}, existing);
    const problem = validate(item);
    if (problem) return res.status(400).json({ error: problem });
    db.prepare(`UPDATE cosmetics SET version = version + 1, category = ?, tier = ?, name = ?, description = ?, sort = ?,
      enabled = ?, starts_at = ?, ends_at = ?, definition = ?, updated_at = ? WHERE id = ?`)
      .run(item.category, item.tier, item.name.trim(), item.description, item.sort, item.enabled ? 1 : 0,
        item.startsAt || null, item.endsAt || null, JSON.stringify(item.definition), now(), item.id);
    return res.json(adminView(db.prepare('SELECT * FROM cosmetics WHERE id = ?').get(item.id)));
  });

  router.delete('/:id', (req, res) => {
    const result = db.prepare('DELETE FROM cosmetics WHERE id = ?').run(req.params.id);
    return result.changes ? res.json({ ok: true }) : res.status(404).json({ error: 'Cosmetic not found' });
  });

  return router;
};

module.exports = cosmeticsRoutes;
module.exports.validate = validate;
module.exports.referencedAssets = referencedAssets;
