'use strict';
const net = require('node:net');
const dgram = require('node:dgram');
const crypto = require('node:crypto');

const TIMEOUT_MS = 3500;

function varInt(value) {
  const bytes = [];
  let v = value >>> 0;
  do {
    let byte = v & 0x7f;
    v >>>= 7;
    if (v !== 0) byte |= 0x80;
    bytes.push(byte);
  } while (v !== 0);
  return Buffer.from(bytes);
}

/** Returns { value, next } or null when the buffer ends before the VarInt does. */
function readVarInt(buffer, offset) {
  let result = 0;
  let shift = 0;
  let position = offset;
  while (position < buffer.length) {
    const byte = buffer[position++];
    result |= (byte & 0x7f) << shift;
    if ((byte & 0x80) === 0) return { value: result, next: position };
    shift += 7;
    if (shift > 35) throw new Error('VarInt too large');
  }
  return null;
}

function packet(id, payload) {
  const body = Buffer.concat([varInt(id), payload]);
  return Buffer.concat([varInt(body.length), body]);
}

function motdText(description) {
  if (typeof description === 'string') return description;
  if (!description) return '';
  const own = typeof description.text === 'string' ? description.text : '';
  return own + (Array.isArray(description.extra) ? description.extra.map(motdText).join('') : '');
}

/** Minecraft Java "Server List Ping". Resolves { online, ... } and never rejects. */
function pingJava(host, port) {
  return new Promise((resolve) => {
    const started = Date.now();
    const socket = net.createConnection({ host, port });
    let received = Buffer.alloc(0);
    let done = false;
    const finish = (result) => {
      if (done) return;
      done = true;
      socket.destroy();
      resolve(result);
    };
    socket.setTimeout(TIMEOUT_MS, () => finish({ online: false, error: 'timeout' }));
    socket.on('error', (error) => finish({ online: false, error: error.code || error.message }));
    socket.on('close', () => finish({ online: false, error: 'closed' }));
    socket.on('connect', () => {
      const hostBytes = Buffer.from(host, 'utf8');
      const portBytes = Buffer.alloc(2);
      portBytes.writeUInt16BE(port);
      socket.write(Buffer.concat([
        packet(0x00, Buffer.concat([varInt(767), varInt(hostBytes.length), hostBytes, portBytes, varInt(1)])),
        packet(0x00, Buffer.alloc(0)),
      ]));
    });
    socket.on('data', (chunk) => {
      received = Buffer.concat([received, chunk]);
      try {
        const length = readVarInt(received, 0);
        if (!length || received.length < length.next + length.value) return;
        const id = readVarInt(received, length.next);
        const jsonLength = id && readVarInt(received, id.next);
        if (!jsonLength || received.length < jsonLength.next + jsonLength.value) return;
        const json = JSON.parse(received.subarray(jsonLength.next, jsonLength.next + jsonLength.value).toString('utf8'));
        finish({
          online: true,
          players: { online: json.players?.online ?? 0, max: json.players?.max ?? 0 },
          version: json.version?.name ?? '',
          motd: motdText(json.description).replace(/§./g, '').trim(),
          latencyMs: Date.now() - started,
        });
      } catch (error) {
        finish({ online: false, error: `bad response: ${error.message}` });
      }
    });
  });
}

const RAKNET_MAGIC = Buffer.from('00ffff00fefefefefdfdfdfd12345678', 'hex');

/** Bedrock RakNet unconnected ping. Resolves { online, ... } and never rejects. */
function pingBedrock(host, port) {
  return new Promise((resolve) => {
    const started = Date.now();
    const socket = dgram.createSocket('udp4');
    let done = false;
    let timer = null;
    const finish = (result) => {
      if (done) return;
      done = true;
      clearTimeout(timer);
      socket.close();
      resolve(result);
    };
    timer = setTimeout(() => finish({ online: false, error: 'timeout' }), TIMEOUT_MS);
    socket.on('error', (error) => finish({ online: false, error: error.code || error.message }));
    socket.on('message', (message) => {
      // 0x1c | time(8) | serverGuid(8) | magic(16) | len(2) | "MCPE;motd;proto;version;online;max;..."
      if (message[0] !== 0x1c || message.length < 35) return;
      const fields = message.subarray(35).toString('utf8').split(';');
      finish({
        online: true,
        players: { online: Number.parseInt(fields[4], 10) || 0, max: Number.parseInt(fields[5], 10) || 0 },
        version: fields[3] || '',
        motd: fields[1] || '',
        latencyMs: Date.now() - started,
      });
    });
    const ping = Buffer.alloc(33);
    ping[0] = 0x01;
    ping.writeBigUInt64BE(BigInt(Date.now()), 1);
    RAKNET_MAGIC.copy(ping, 9);
    crypto.randomBytes(8).copy(ping, 25);
    socket.send(ping, port, host, (error) => {
      if (error) finish({ online: false, error: error.code || error.message });
    });
  });
}

/** Caches the combined status briefly so many launchers don't hammer the game server. */
function createStatusService(serverConfig, ttlMs = 20_000) {
  let cache = null;
  let inflight = null;
  async function refresh() {
    const [java, bedrock] = await Promise.all([
      pingJava(serverConfig.javaHost, serverConfig.javaPort),
      pingBedrock(serverConfig.bedrockHost, serverConfig.bedrockPort),
    ]);
    cache = { at: Date.now(), value: { checkedAt: new Date().toISOString(), java, bedrock } };
    return cache.value;
  }
  return {
    async get() {
      if (cache && Date.now() - cache.at < ttlMs) return cache.value;
      inflight = inflight || refresh().finally(() => { inflight = null; });
      return inflight;
    },
  };
}

module.exports = { pingJava, pingBedrock, createStatusService, varInt, readVarInt };
