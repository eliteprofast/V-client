# V-Client backend

One small Node.js service that powers the website, the admin panel, the Windows launcher and the Android app. No database server to install: data lives in a SQLite file.

- Node.js **22.13+** (uses the built-in `node:sqlite`; you may see an "experimental" warning on startup, which is expected)
- One dependency: `express`

```powershell
npm install
npm start          # http://localhost:8080
npm test           # API + server-ping tests
```

## Configuration

Environment variables or a `.env` file (see [.env.example](.env.example)).

| Variable | Default | Meaning |
| --- | --- | --- |
| `PORT` | `8080` | HTTP port |
| `DATA_DIR` | `./data` | SQLite database, signing secret, uploaded installers (back this folder up) |
| `TOKEN_SECRET` | generated | Signs login tokens; generated into `DATA_DIR/secret.key` when empty |
| `ADMIN_USERNAME` / `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `admin` / – / random | First admin, created only if no admin exists. A random password is printed once |
| `JAVA_HOST` / `JAVA_PORT` | `neuron.zeroxhost.space` / `19145` | VSMP Java address |
| `BEDROCK_HOST` / `BEDROCK_PORT` | same host / `19132` | VSMP Bedrock address used by the Android **Play** button. **Set the real Bedrock port** |
| `DISCORD_URL`, `WEBSITE_URL` | empty | Shown on the site and in both apps |
| `TRUST_PROXY` | `0` | Set `1` behind nginx/Caddy/Cloudflare so rate limits use the real client IP |
| `MAX_UPLOAD_MB` | `300` | Largest installer accepted |

## Deploying

Put it behind HTTPS (Caddy, nginx, Cloudflare Tunnel, …). Android release builds refuse plain `http://`. Example Caddyfile:

```
vclient.example.com {
    reverse_proxy localhost:8080
}
```

Run it as a service (`pm2 start src/server.js --name vclient`, a systemd unit, or a Windows service via NSSM) with `TRUST_PROXY=1`. The reverse proxy must allow request bodies at least as large as your installer (`request_body max_size` in Caddy, `client_max_body_size` in nginx).

## API

Public (no sign-in):

| Method + path | Returns |
| --- | --- |
| `GET /api/health` | `{ ok }` |
| `GET /api/config` | VSMP addresses (Java + Bedrock), Discord and website URLs |
| `GET /api/server/status` | Live `java` / `bedrock` status: online, players, version, latency (cached 20 s) |
| `GET /api/news?limit=&offset=&category=launcher\|server` | Published posts, pinned first |
| `GET /api/announcements` | Active announcements (`info`, `warning`, `critical`) |
| `GET /api/launcher/update?platform=windows\|android&current=1.0.0` | `updateAvailable`, `mandatory`, `latest { version, notes, size, sha256, url }` |
| `GET /api/launcher/releases` | Latest published release per platform (used by the website) |
| `POST /api/stats/event` | Anonymous usage counter `{ installId, platform, version, event }` |
| `GET /download/windows`, `/download/android` | Redirect to the newest published installer |

Accounts (`Authorization: Bearer <token>`): `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me`, `PUT /api/auth/profile`, `PUT /api/auth/password`. Passwords are hashed with scrypt; auth endpoints are rate limited.

Admin (admin role only): `GET/POST/PUT/DELETE /api/admin/news`, `/api/admin/announcements`, `/api/admin/releases` (+ `PUT /api/admin/releases/:id/file?ext=.apk` with the raw installer as the body), `GET /api/admin/users`, `GET /api/admin/stats`.

## Publishing an update

1. Admin panel → **Updates** → *New launcher release*: platform, version (`1.2.0`), notes. Tick *Mandatory*, or set *Minimum supported version*, to force older versions to update.
2. **Upload installer** (`.exe`/`.msi`/`.zip` for Windows, `.apk` for Android). The server stores it, records the size and SHA-256, and the apps verify both before installing.
3. **Publish.** Apps see it on their next launch. **Unpublish** or **Delete** to withdraw it.

## Privacy

Usage counters contain a random install id, platform, launcher version and an event name (`launch`, `play`, `join`, `update`). No IP addresses, hardware ids or account names are stored with them.
