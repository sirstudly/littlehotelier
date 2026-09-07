# ronbot-bridge

WhatsApp staff bridge: **WAHA** (unofficial WhatsApp Web HTTP API) → this service → **Cursor SDK** + **ronbot-ops MCP**.

## What it does

| Channel | Who | When we reply |
|---------|-----|----------------|
| Allowlisted groups (`@g.us`) | Everyone in the group (full context) | `@ronbot` mention, or follow-up within ~15 minutes of our last reply |
| Private DMs (`@c.us`) | Sender must be a **member of an allowlisted group** | Always (treated as directed at us) |

Answers use the littlehotelier repo + MCP tools (`get_booking`, `get_booking_timeline`, jobs, logs, allowlisted `insert_job`, etc.).

## Risk

WAHA is an unofficial client (ToS risk / ban risk). Use a **burner SIM**. Keep volume low.

## Docker DNS note

`FIX_CONTAINER_DNS=1` keeps Docker’s embedded DNS (`127.0.0.11`) for service names like `waha`, adds public resolvers as fallback, and pins `api.cursor.com` into `/etc/hosts` via DNS-over-HTTPS to `1.1.1.1` (avoids Docker Desktop `EAI_AGAIN` for Cursor).

Do **not** set compose `dns:` to only `8.8.8.8` — that breaks `waha` / `ronbot-read-api` name resolution.

The bridge image is **Debian** (`node:22-bookworm-slim`), not Alpine — `@cursor/sdk` needs glibc (`ld-linux-x86-64.so.2`).

## Prerequisites

1. `ronbot-mcp` built (`cd ../ronbot-mcp && npm run build`)
2. `ronbot-read-api` up for live Cloudbeds tools (optional for pure codebase/DB questions)
3. `CURSOR_API_KEY` or SDK login (`Cursor.auth.login()`)
4. `GOOGLE_APPLICATION_CREDENTIALS` for MCP Secret Manager / MySQL

## Env (gitignored `.env`)

Loads repo-root `.env` then `ronbot-bridge/.env` (shell wins).

```bash
# WAHA
WAHA_API_KEY=long-random-secret
WAHA_SESSION=default
WAHA_ENGINE=NOWEB
WAHA_DASHBOARD_USERNAME=admin
WAHA_DASHBOARD_PASSWORD=change-me

# Allowlisted staff groups (comma-separated WhatsApp group JIDs)
RONBOT_WHATSAPP_GROUPS=120363...@g.us,120363...@g.us
# Or edit config/groups.json

RONBOT_MENTION=@ronbot
RONBOT_FOLLOWUP_MINUTES=15

# Cursor + MCP
CURSOR_API_KEY=cursor_...
GOOGLE_APPLICATION_CREDENTIALS=/absolute/path/to/credentials/gcp-service-account.json
RONBOT_TOKEN=...
RONBOT_READ_API_URL=http://127.0.0.1:8080
GCP_PROJECT_ID=macbackpackers-backoffice
```

## Docker Compose

From repo root:

```bash
# Ensure MCP dist exists for the bridge volume mount
(cd ronbot-mcp && npm run build)

docker compose up -d --build waha ronbot-bridge
```

Build context is the **repo root** (Linux MCP deps are baked into the image — do not mount a Mac `ronbot-mcp/node_modules`).

### Link the burner SIM (one-time)

1. Open WAHA dashboard: http://127.0.0.1:3000/dashboard (creds from env)
2. Start session `default` (or `WAHA_SESSION`)
3. Scan QR with the burner phone’s WhatsApp
4. Add that contact to each staff group
5. Discover group ids (WAHA API / dashboard → groups) and set `RONBOT_WHATSAPP_GROUPS`
6. Restart `ronbot-bridge` after updating groups

Webhook is preconfigured in compose:

`WHATSAPP_HOOK_URL=http://ronbot-bridge:8787/webhooks/waha`  
events: `message`, `group.v2.participants`

### Health

```bash
curl -s http://127.0.0.1:8787/health
```

## Local dev (without Docker for the bridge)

```bash
# Terminal A — WAHA
docker compose up -d waha

# Terminal B — bridge
cd ronbot-bridge
npm install
npm run dev
```

Point WAHA webhook at `http://host.docker.internal:8787/webhooks/waha` if WAHA is in Docker and the bridge is on the host.

Compose sets `FIX_CONTAINER_DNS=1` on `ronbot-bridge` so `api.cursor.com` resolves over TCP DNS (same Docker Desktop issue as `ronbot-read-api`).

## Offline smoke (triggers / ACL)

```bash
cd ronbot-bridge
npm run smoke:triggers
```

Validates `@ronbot` / follow-up / authorized DM / stranger DM ignore without WAHA or Cursor.

## Staff prompts (examples)

- `@ronbot explain the EVL charges on reservation 1234567 (crh)`
- `@ronbot timeline for reservation 1234567 at rmb`
- DM (if you’re in a subscribed group): `if guest cancels last night on 1234567 crh, how much should we refund?`
- `@ronbot how often do we attempt to charge a non-refundable hostelworld booking?`

## Security notes

- Unknown DMs are ignored (membership cache from allowlisted groups)
- Refund answers are advisory only
- `insert_job` only when staff explicitly request an allowlisted enqueue
