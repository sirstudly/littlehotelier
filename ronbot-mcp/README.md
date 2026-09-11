# ronbot-mcp

MCP ops server for Cloudbeds. Exposes allowlisted job enqueue, job/queue queries, log search, and live Cloudbeds booking/availability tools (proxied to `ronbot-read-api`).

## Architecture

- **This package (TypeScript)** — MCP tools over stdio; MySQL for jobs/scheduled/queue; log file search; HTTP client to read-api
- **`ronbot-read-api` (Java)** — live `CloudbedsScraper` reads; one child Spring context per property (`crh`/`hsh`/`rmb`/`lsh`)

Cloudbeds mutations are never done from MCP. Writes only insert allowlisted rows into `wp_lh_jobs`.

## Tools

| Tool | Purpose |
|------|---------|
| `list_properties` | Property ids |
| `get_job` / `list_jobs` | `wp_lh_jobs` |
| `list_scheduled_jobs` / `get_scheduled_job` | Cron templates |
| `get_job_queue_stats` | submitted / processing / retry counts |
| `search_logs` | Grep property log dirs |
| `get_booking` | Live reservation search by visible id / OTA ref / guest name (read-api; returns a list) |
| `list_transactions` | Live folio lines (read-api; unique match required) |
| `get_booking_timeline` | Live booking + transactions + job history |
| `check_stay_continuation` | Cleaning / extension: staying on past checkout? (same reservation or linked follow-on in same beds) |
| `get_availability` | Live sellable beds/rooms by room type (read-api; single property, subset, or all hostels in one call) |
| `insert_job` | Allowlisted enqueue only |

### `insert_job` allowlist

| `job_type` | Required params |
|------------|-----------------|
| `HousekeepingJob` | `selected_date` (YYYY-MM-DD) |
| `AllocationScraperJob` | `start_date`, `days_ahead` |
| `CalculateEdinburghVisitorLevyForBookingJob` | `reservation_id` |
| `ChargeNonRefundableBookingJob` | `reservation_id` |

## Local development

```bash
cd ronbot-mcp
npm install
npm run build

# Terminal A — Java read API (needs GCP creds + Spring profile secrets)
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/credentials/gcp-service-account.json
java -Dloader.main=com.macbackpackers.ronbot.RunRonbotReadApi \
  -jar ../target/lilhotelier.jar

# Terminal B — MCP (stdio; Cursor uses .cursor/mcp.json)
export RONBOT_READ_API_URL=http://127.0.0.1:8080
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/credentials/gcp-service-account.json
# Optional overrides when not using Docker log mounts:
# export LOG_DIR_CRH=../logs/crh LOG_DIR_HSH=../logs/hsh ...
node dist/index.js
```

Or via Docker Compose from the repo root:

```bash
export RONBOT_TOKEN='some-shared-secret'
docker compose up -d --build ronbot-read-api ronbot-mcp
```

Set the same `RONBOT_TOKEN` in `.cursor/mcp.json` when calling a remote read-api.

## Cursor wiring

See [`.cursor/mcp.json`](../.cursor/mcp.json). Point `args` at `ronbot-mcp/dist/index.js` after `npm run build`.

### Smoke prompts (IDE chat)

- "How many submitted jobs are queued at hsh?" → `get_job_queue_stats`
- "What's the live balance for reservation 4586958844219 at crh?" → `get_booking` with `query`
- "Find bookings for Jane Smith at hsh" → `get_booking` with guest name as `query`
- "Timeline of events for reservation 4586958844219 at crh" → `get_booking_timeline` (unique match)
- "Is booking 4586958844219 at crh staying another night / extended?" → `check_stay_continuation`
- "How many beds free tomorrow at hsh?" → `get_availability` with `property=hsh` (or omit from/to for today→tomorrow)
- "Availability tonight across all hostels" → `get_availability` once with `properties` omitted (fans out to crh/hsh/rmb/lsh)
- "Run housekeeping for crh for today" → `insert_job` with `HousekeepingJob` / `selected_date`

## Phase 2: Local SDK `Agent.prompt` smoke

One-shot script that runs a **local** Cursor agent against the repo root with **inline** `ronbot-ops` MCP (stdio). Proves:

1. **Codebase** — agent cites `CloudbedsScraper` reservation-load method from source
2. **Ops** — agent calls MCP `get_job_queue_stats` for `hsh` (MySQL via Secret Manager)

Live Cloudbeds (`get_booking`) is optional and only runs when `RONBOT_SMOKE_RESERVATION` is set **and** read-api health is OK.

### Preconditions / `.env`

`npm run smoke:agent` loads env files automatically:

1. Repo root [`.env`](../.env)
2. [`ronbot-mcp/.env`](.env) (overrides root for the same keys)

Already-exported shell variables always win. Both `.env` paths are gitignored.

```bash
CURSOR_API_KEY=cursor_...
GOOGLE_APPLICATION_CREDENTIALS=/absolute/path/to/credentials/gcp-service-account.json
RONBOT_TOKEN=...
RONBOT_READ_API_URL=http://127.0.0.1:8080
GCP_PROJECT_ID=macbackpackers-backoffice
# Optional live Cloudbeds leg:
# RONBOT_SMOKE_RESERVATION=1234567
# RONBOT_SMOKE_PROPERTY=crh
# LOG_DIR_CRH=/absolute/path/to/logs/crh
```

`CURSOR_API_KEY` can be omitted if you have already run SDK login (`~/.cursor/sdk/auth.json`):

```bash
node --input-type=module -e 'import { Cursor } from "@cursor/sdk"; await Cursor.auth.login()'
```

Requires **Node ≥ 22.13** (`@cursor/sdk`). Needs network access to GCP Secret Manager and MySQL (same as the MCP server).

### Run

```bash
cd ronbot-mcp
npm install
npm run build
npm run smoke:agent
```

### Exit codes

| Code | Meaning |
|------|---------|
| 0 | Run finished successfully |
| 1 | `CursorAgentError` — did not start (auth / config / network), or missing env / unbuilt MCP |
| 2 | Run executed but `result.status === "error"` |

### Optional live booking

Set in `.env` (or the shell):

```bash
RONBOT_SMOKE_RESERVATION=1234567
RONBOT_SMOKE_PROPERTY=crh   # default crh
```

Ensure read-api is up (`docker compose up -d ronbot-read-api`), then `npm run smoke:agent`.

## WhatsApp bridge (Phase 3)

Staff groups / authorized DMs via WAHA + [`ronbot-bridge`](../ronbot-bridge/README.md):

```bash
(cd ronbot-mcp && npm run build)
docker compose up -d --build waha ronbot-bridge
```

## Security notes

- MCP never calls Cloudbeds directly
- `insert_job` classname comes only from `config/job-allowlist.json`
- Booking/transaction responses redact full card numbers
- Optional shared secret: `X-Ronbot-Token` / `RONBOT_TOKEN`
- Keep secrets in gitignored `.env` files (repo root and/or `ronbot-mcp/.env`); do not commit them
