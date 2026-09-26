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
| `describe_sql_tables` | List backoffice DB tables (tagged key / other / defunct) or one table's columns. Only when `RONBOT_SQL_RO_*` is set |
| `run_sql` | One read-only ad-hoc SELECT on any table (row cap, 15s timeout). Only when `RONBOT_SQL_RO_*` is set |

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
| `office` / `alice` | office@example.com |
| `bob` | bob@example.com |
| `carol` | carol@example.com |

Each value is either an address string or an object with the user's WhatsApp identities:

```json
{
  "office": "office@example.com",
  "bob": { "email": "bob@example.com", "whatsapp": ["+447700900003", "12345678901234@lid"] }
}
```

`whatsapp` entries are phone numbers (`+`, spaces and dashes ignored; stored as `<digits>@c.us`) or full JIDs (`@c.us` / `@lid`). `ronbot-bridge` reads the same file to resolve the WhatsApp sender to a `Requester=<alias>` prompt line, so "send me the latest channel report" is emailed to that user. MCP itself only resolves aliases; it never sees the sender.

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

### `run_sql` / `describe_sql_tables`

Ad-hoc read-only queries against `wp_<prop>_backoffice`, over a separate pool that logs in as a dedicated MySQL user with only `SELECT` and `SHOW VIEW`. The tools are registered only when these are set: locally from the repo-root `.env` (loaded by Cursor via `envFile` in `.cursor/mcp.json`); in production from the host `.env` via the `ronbot-bridge` Compose service, which forwards them to MCP with `RONBOT_SQL_RO_HOST=mysql-tailscale`:

```bash
RONBOT_SQL_RO_USER=...
RONBOT_SQL_RO_PASSWORD=...
# Optional; default to host/port from each property's db_url_<prop> secret
# RONBOT_SQL_RO_HOST=...
# RONBOT_SQL_RO_PORT=3306
```

- `run_sql`: `property`, `sql`, optional `max_rows` (default 200, max 1000), optional `requested_by`
  - Exactly one `SELECT` (UNIONs, subqueries, cross-database joins OK); no table restrictions
  - Rejected: `SLEEP` / `BENCHMARK` / `GET_LOCK` / `LOAD_FILE` (and other lock functions), `FOR UPDATE`, `LOCK IN SHARE MODE`, `SELECT ... INTO`
  - A `LIMIT` is appended when absent and clamped when larger than `max_rows`; `truncated: true` means more rows exist
  - Cancelled with `KILL QUERY` after 15s (MySQL 5.5 has no `max_execution_time`)
  - MySQL 5.5 syntax only: no CTEs/`WITH`, window functions or `JSON_*`
  - Dates are returned as stored (strings), not converted to UTC
- `describe_sql_tables`: `property`, optional `table`. Without `table` lists tables and views; with `table` lists its columns. Key and legacy tables carry `notes` (`TABLE_NOTES` in `src/db/adhocSql.ts`) explaining how to query them
- Booking/stay SQL defaults to the `wp_lh_booking_assignment` views (`src/main/config/migrations/2026-09-26-booking-assignment-views.sql`): `v_wp_lh_booking_reservation` (one row per current reservation, money safe to sum), `v_wp_lh_booking_current` (one row per bed), `v_wp_lh_booking_removed` (cancelled/removed assignments), then `wp_lh_booking_assignment` itself for history and point-in-time queries
- Key tables (hinted to the agent): `v_wp_lh_booking_reservation`, `v_wp_lh_booking_current`, `v_wp_lh_booking_removed`, `wp_lh_booking_assignment`, `wp_lh_rooms`, `wp_lh_jobs`, `wp_lh_job_param`, `job_scheduler`, `job_scheduler_param`, `wp_booking_lookup_key`, `wp_invoice`, `wp_invoice_notes`, `wp_lh_bedcounts`, `wp_lh_group_bookings`, `wp_lh_housekeeping_bed`, `wp_lh_occupancy`, `wp_lh_rpt_guest_comments`, `wp_lh_rpt_mostly_full_dorms`, `wp_lh_rpt_split_rooms`, `wp_lh_rpt_unpaid_deposit`, `wp_stripe_transaction`, `wp_stripe_tx_refund`, `wp_tx_refund`, `wp_hwl_cancel_booking_exempt`, `wp_options`
- Legacy tables (still written, superseded): `wp_lh_calendar` (recent allocation snapshots only; use `wp_lh_booking_assignment`)
- Defunct tables (not for current data): `wp_hw_booking`, `wp_hw_booking_dates`, `wp_pxpost_transaction`, `wp_sagepay_transaction`, `wp_sagepay_tx_auth`, `wp_sagepay_tx_refund`

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
scripts/compose-build.sh ronbot-read-api
```

`scripts/compose-build.sh` wraps `docker compose up -d --build` and bakes the git commit into the image; check a running container with `docker inspect -f '{{index .Config.Labels "org.opencontainers.image.revision"}}' <container>`.

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
- WhatsApp: "send me the latest channel report" (from a tagged hsh group, sender listed in `email-aliases.json`) → `enqueue_channel_production_report` with `property=hsh`, previous month, `to_emails=[<Requester alias>]`
- "Revenue by channel at hsh for July 2026" → `get_channel_production` with `property=hsh`, `month=2026-07`
- "Occupancy at rmb from 2026-06-01 to 2026-08-31" → `get_occupancy` with `property=rmb`, `from=2026-06-01`, `to=2026-08-31`
- "How many HousekeepingJob runs failed at hsh this week?" → `run_sql` on `wp_lh_jobs`
- "Which beds at crh have checkin 2026-09-25?" → `describe_sql_tables` (`table=v_wp_lh_booking_current`), then `run_sql`
- "How many reservations checked in at hsh in March 2025 and what did they pay?" → `run_sql` on `v_wp_lh_booking_reservation`

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
scripts/compose-build.sh waha ronbot-bridge
```

MCP `dist` is compiled inside the `ronbot-bridge` image; rebuilding the bridge is enough after MCP source changes.

## Security notes

- MCP never calls Cloudbeds directly
- `insert_job` classname comes only from `config/job-allowlist.json`
- `run_sql` / `describe_sql_tables` use a dedicated read-only DB user (`SELECT` + `SHOW VIEW` only), never the read/write pool
- Those tools are not registered unless `RONBOT_SQL_RO_USER` / `RONBOT_SQL_RO_PASSWORD` are set. `ronbot-bridge` forwards them to its MCP child when set (Compose uses `RONBOT_SQL_RO_HOST=mysql-tailscale`), so WhatsApp staff get them in production
- Booking/transaction responses redact full card numbers
- Optional shared secret: `X-Ronbot-Token` / `RONBOT_TOKEN`
- Keep secrets in gitignored `.env` files (repo root and/or `ronbot-mcp/.env`); do not commit them
