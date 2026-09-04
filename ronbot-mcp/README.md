# ronbot-mcp

MCP ops server for LittleHotelier/Cloudbeds. Exposes allowlisted job enqueue, job/queue queries, log search, and live Cloudbeds booking tools (proxied to `ronbot-read-api`).

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
| `get_booking` | Live reservation (read-api) |
| `list_transactions` | Live folio lines (read-api) |
| `get_booking_timeline` | Live booking + transactions + job history |
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

### Smoke prompts

- "How many submitted jobs are queued at hsh?" → `get_job_queue_stats`
- "What's the live balance for reservation 1234567 at crh?" → `get_booking`
- "Timeline of events for reservation 1234567 at crh" → `get_booking_timeline`
- "Run housekeeping for crh for today" → `insert_job` with `HousekeepingJob` / `selected_date`

## Security notes

- MCP never calls Cloudbeds directly
- `insert_job` classname comes only from `config/job-allowlist.json`
- Booking/transaction responses redact full card numbers
- Optional shared secret: `X-Ronbot-Token` / `RONBOT_TOKEN`
