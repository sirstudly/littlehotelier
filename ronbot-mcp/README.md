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
| `get_booking` | Booking search by visible id / OTA ref / guest name (calendar DB first for names, then Cloudbeds; returns a list) |
| `list_transactions` | Live folio lines (read-api; unique match required) |
| `get_booking_timeline` | Live booking + transactions + job history (name queries: calendar first) |
| `check_stay_continuation` | Cleaning / extension: staying on past checkout? (same reservation or linked follow-on in same beds) |
| `get_availability` | Live sellable beds/rooms by room type (read-api; single property, subset, or all hostels in one call) |
| `get_channel_production` | Live Channel Production numbers by source for one month (read-api; single property, subset, or all hostels in one call) |
| `get_occupancy` | Live % of beds occupied over a date range, with monthly breakdown (read-api; single property, subset, or all hostels) |
| `insert_job` | Allowlisted enqueue only (`email` / `to_emails` params accept aliases accounts/hannah/jay/ron) |
| `enqueue_quarterly_evl_6plus_report` | Enqueue EVL nights-6+ room revenue xlsx email job (Edinburgh crh/hsh/rmb or all) |
| `enqueue_channel_production_report` | Enqueue Channel Production xlsx email job (month vs same month in previous 2 years) |

### `insert_job` allowlist

| `job_type` | Required params |
|------------|-----------------|
| `HousekeepingJob` | `selected_date` (YYYY-MM-DD) |
| `AllocationScraperJob` | `start_date`, `days_ahead` |
| `CalculateEdinburghVisitorLevyForBookingJob` | `reservation_id` |
| `ChargeNonRefundableBookingJob` | `reservation_id` |
| `RunQuarterlyEvl6PlusNightsReportJob` | `email` (or alias accounts/hannah/jay/ron) |
| `RunChannelProductionReportJob` | `year_month` (YYYY-MM), `to_emails` (comma-delimited; each entry may be an alias) |

### Email aliases

Shorthand for job `email` parameters (config/`email-aliases.json`):

| Alias | Address |
|-------|---------|
| `accounts` / `hannah` | accounts@macbackpackers.com |
| `jay` | jay@macbackpackers.com |
| `ron` | ron@macbackpackers.com |

### `enqueue_quarterly_evl_6plus_report`

- Required: `email` (address or alias above)
- Property: `property` = `crh`\|`hsh`\|`rmb`\|`all`, or `properties` array; omit neither — tool errors so the agent asks
- Edinburgh only (not `lsh`)

### `enqueue_channel_production_report`

- Required: `year_month` (YYYY-MM), `to_emails` (array of addresses or aliases)
- Property: `property` = `crh`\|`hsh`\|`rmb`\|`lsh`\|`all`, or `properties` array; omit neither — tool errors so the agent asks
- One job per property; each emails `{PROP} Channel Report {Month} {Year}.xlsx` with one sheet per year (requested month plus the same month in the previous 2 years)

### `get_channel_production`

- Required: `month` (YYYY-MM); optional `property` / `properties` (omit both for all hostels)
- Proxies `GET /ronbot/{property}/channel-production?month=YYYY-MM` on read-api
- Returns per-source revenue, room nights, ADR, % shares and `commission` (revenue / divisor from `wp_lh_booking_source_lookup`, e.g. Booking.com, Agoda, Airbnb; null when none applies), plus totals: `revenue`, `roomsSold`, `commission` (sum of per-source commission), `netRevenue`, `avgPricePerBed`, `occupancyPct`

### `get_occupancy`

- Required: `from`, `to` (YYYY-MM-DD, inclusive, max 366 nights); optional `property` / `properties` (omit both for all hostels)
- Proxies `GET /ronbot/{property}/occupancy?from=&to=` on read-api (Data Insights classic Occupancy report; one request per calendar year)
- Returns overall `occupancyPct` (weighted by nights of each month inside the range), `bedsBooked`, `revenue`, and `months[]` with the same fields per month

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

Read-api via Compose (MCP stays on the host for Cursor):

```bash
export RONBOT_TOKEN='some-shared-secret'
docker compose up -d --build ronbot-read-api
```

Set the same `RONBOT_TOKEN` in `.cursor/mcp.json` when calling that read-api. Production WhatsApp uses MCP baked into the `ronbot-bridge` image — there is no separate `ronbot-mcp` Compose service.

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
- "Run the quarterly EVL nights 6+ report for hsh to accounts" → `enqueue_quarterly_evl_6plus_report` with `property=hsh`, `email=accounts`
- "EVL over 5 nights report for all Edinburgh hostels, email jay" → `enqueue_quarterly_evl_6plus_report` with `property=all`, `email=jay`
- "Channel report for August 2026 at rmb, email accounts and ron" → `enqueue_channel_production_report` with `property=rmb`, `year_month=2026-08`, `to_emails=["accounts","ron"]`
- "Revenue by channel at hsh for July 2026" → `get_channel_production` with `property=hsh`, `month=2026-07`
- "Occupancy at rmb from 2026-06-01 to 2026-08-31" → `get_occupancy` with `property=rmb`, `from=2026-06-01`, `to=2026-08-31`

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
docker compose up -d --build waha ronbot-bridge
```

MCP `dist` is compiled inside the `ronbot-bridge` image; rebuilding the bridge is enough after MCP source changes.

## Security notes

- MCP never calls Cloudbeds directly
- `insert_job` classname comes only from `config/job-allowlist.json`
- Booking/transaction responses redact full card numbers
- Optional shared secret: `X-Ronbot-Token` / `RONBOT_TOKEN`
- Keep secrets in gitignored `.env` files (repo root and/or `ronbot-mcp/.env`); do not commit them
