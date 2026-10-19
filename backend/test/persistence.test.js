'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { openDatabase } = require('../src/db');
const { createPersistence } = require('../src/persistence');

const silent = { log() {}, info() {}, warn() {}, error() {} };

/** A tiny stand-in for the GitHub contents API: one file, GET and PUT with a sha. */
function fakeGithub() {
  const state = { content: null, sha: 0, puts: 0, failGet: false };
  const fetch = async (url, init = {}) => {
    if ((init.method || 'GET') === 'PUT') {
      const body = JSON.parse(init.body);
      if (state.content && String(body.sha) !== String(state.sha)) return new Response('{}', { status: 409 });
      state.content = body.content;
      state.sha += 1;
      state.puts += 1;
      return new Response(JSON.stringify({ content: { sha: String(state.sha) } }), { status: 200 });
    }
    if (state.failGet) return new Response('{}', { status: 500 });
    if (!state.content) return new Response('{}', { status: 404 });
    return new Response(JSON.stringify({ sha: String(state.sha), content: state.content }), { status: 200 });
  };
  return { state, fetch };
}

const tempConfig = () => ({ dataDir: fs.mkdtempSync(path.join(os.tmpdir(), 'vclient-persist-')), tokenSecret: '' });
const persistence = (config, github) => createPersistence(config, {
  settings: { token: 't', repo: 'me/private', file: 'backup.json' }, fetch: github.fetch, log: silent,
});

test('grants, accounts and the token secret survive a wiped disk', async () => {
  const github = fakeGithub();
  const config = tempConfig();
  fs.writeFileSync(path.join(config.dataDir, 'secret.key'), 'the-secret');
  const db = openDatabase(':memory:');
  const first = persistence(config, github);
  await first.restore(db);
  db.prepare("INSERT INTO users (username, email, password_hash, created_at) VALUES ('izyrn', 'a@b.c', 'hash', 1)").run();
  db.prepare("INSERT INTO cosmetic_grants (player_uuid, cosmetic_id, granted_at) VALUES ('3bf39b66aa3832a9b8c955a490978486', 'ice_cape', 5)").run();
  await first.flush(db);
  assert.equal(github.state.puts, 1);

  // The host restarts: new empty database, new empty data directory.
  const wiped = tempConfig();
  const fresh = openDatabase(':memory:');
  const second = persistence(wiped, github);
  await second.restore(fresh);
  assert.equal(fresh.prepare('SELECT username FROM users').get().username, 'izyrn');
  assert.equal(fresh.prepare('SELECT cosmetic_id FROM cosmetic_grants').get().cosmetic_id, 'ice_cape');
  assert.equal(fs.readFileSync(path.join(wiped.dataDir, 'secret.key'), 'utf8'), 'the-secret');

  // Nothing changed, so another flush must not write again.
  await second.flush(fresh);
  assert.equal(github.state.puts, 1);
});

test('a failed restore never overwrites the stored backup', async () => {
  const github = fakeGithub();
  github.state.content = Buffer.from(JSON.stringify({ version: 1, secret: '', tables: { users: [] } })).toString('base64');
  github.state.failGet = true;
  const db = openDatabase(':memory:');
  const p = persistence(tempConfig(), github);
  await p.restore(db);
  db.prepare("INSERT INTO users (username, email, password_hash, created_at) VALUES ('x', 'x@y.z', 'h', 1)").run();
  await p.flush(db);
  assert.equal(github.state.puts, 0);
});

test('without settings the backup is off and harmless', async () => {
  const off = createPersistence(tempConfig(), { settings: null });
  assert.equal(off.enabled, false);
  assert.equal(await off.restore(openDatabase(':memory:')), 0);
});
