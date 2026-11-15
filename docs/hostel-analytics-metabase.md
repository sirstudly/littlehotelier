# Hostel analytics (Metabase)

Non-technical dashboards for CRH / HSH / RMB / LSH: **booked-date** booking value and **stay-date** Channel Production, plus Edinburgh **competitive position** (Google Hotels bed prices + Hostelworld search rank), stored in MariaDB `mac_analytics` and explored in Metabase.

## Metric definitions (do not mix)

| Dashboard | Question | Source | Grain |
|-----------|----------|--------|-------|
| **Booked-date performance** | How much booking value did we take in on day X? | Cloudbeds reservation list → folio `grandTotal` by `booked_date` + channel (statuses: confirmed, not_confirmed, canceled, no_show, checked_in, checked_out, pending_payment) | `fact_booking_created` |
| **Stay-date production** | How much room revenue did nights on day X generate? | Data Insights stock report 191 (`room_revenue` / `rooms_sold` / ADR) | `fact_channel_stay_day` |

Stay-date matches the Cloudbeds Channel Production UI. Booked-date does **not**.

## Databases

| Database | Role |
|----------|------|
| `mac_analytics` | Warehouse (facts + views). Collation `utf8mb4_general_ci`. |
| `metabase` | Metabase application DB only (users, dashboards). Collation `utf8mb4_unicode_ci`. |

DDL: [`src/main/config/migrations/2026-11-12-mac-analytics.sql`](../src/main/config/migrations/2026-11-12-mac-analytics.sql)  
Phase 2 alter + match seeds: [`2026-11-15-mac-analytics-phase2.sql`](../src/main/config/migrations/2026-11-15-mac-analytics-phase2.sql)

Create users (or run [`scripts/apply-mac-analytics.sh`](../scripts/apply-mac-analytics.sh) with admin `MYSQL_PWD`). Processor creds: GCP Secret Manager `analyticsdb_username` / `analyticsdb_password`. Example grants:

```sql
CREATE USER IF NOT EXISTS 'analytics_rw'@'%' IDENTIFIED BY '...';
GRANT SELECT, INSERT, UPDATE, DELETE ON mac_analytics.* TO 'analytics_rw'@'%';

CREATE USER IF NOT EXISTS 'metabase_ro'@'%' IDENTIFIED BY '...';
GRANT SELECT ON mac_analytics.* TO 'metabase_ro'@'%';

CREATE USER IF NOT EXISTS 'metabase_app'@'%' IDENTIFIED BY '...';
GRANT ALL PRIVILEGES ON metabase.* TO 'metabase_app'@'%';
FLUSH PRIVILEGES;
```

## Collection jobs

| Job | Schedule (`job_scheduler`) | Params |
|-----|----------------------------|--------|
| `SyncBookingCreatedAnalyticsJob` | Daily 06:00 | `start_date`, `end_date` (default `TODAY-1`) |
| `SyncChannelStayDayAnalyticsJob` | Daily 06:30 | same |

Scheduler seed (each `wp_*_backoffice`): [`2026-11-12-analytics-job-scheduler.sql`](../src/main/config/migrations/2026-11-12-analytics-job-scheduler.sql)

### Phase 2 — competitive position (Edinburgh only)

| Job | Runner | Default schedule (CRH) | Params | Writes |
|-----|--------|------------------------|--------|--------|
| `SyncCompetitorBedPriceJob` | **CRH only** | 07:00 | `horizon_days=30`, `city=Edinburgh` | `fact_competitor_bed_price` SCD2 (`property_code=edn`, `room_category` dorm/private, `is_own`, `valid_from`/`valid_to`) |
| `SyncHostelworldSearchRankJob` | **CRH only** | 07:15 | `guests=1`, `nights=1`, `arrival_offset_days=7`, `city=Edinburgh` | `fact_hw_search_rank` for crh/hsh/rmb |

- **LSH out of scope.** Accidental enqueues on HSH/RMB/LSH log-skip and complete.
- Scheduler seed: [`2026-11-15-analytics-phase2-job-scheduler-crh.sql`](../src/main/config/migrations/2026-11-15-analytics-phase2-job-scheduler-crh.sql) → `wp_crh_backoffice` only.
- Matching: `dim_property_match` (display names, aliases, HW numbers 551 / 552 / 11242).
- GHS uses a **dedicated** Chrome profile (`chromeprofile/ghs`, compose mount on `crh-processor` only) — not the Cloudbeds profile. Stay dates are driven by the protobuf `ts=` trip blob in the search URL (plus `dates=`); the scraper asserts Check-in/out match before parsing. List scrape stores “Prices starting from £N” as **dorm** (hostel lowest); private rows when exposed.
- **SCD2 prices:** each sync end-dates prior current rows for that `stay_date` (`valid_to = now`) and inserts a new snapshot (`valid_from = now`, `valid_to` NULL). Charts / current reports use `WHERE valid_to IS NULL`. History / as-of: `valid_from <= @ts AND (valid_to IS NULL OR valid_to > @ts)`. Fully booked properties that vanish from GHS results are end-dated with no new current row (chart gap — expected).
- Migration: [`2026-11-16-mac-analytics-competitor-bed-price-scd2.sql`](../src/main/config/migrations/2026-11-16-mac-analytics-competitor-bed-price-scd2.sql).
- HW uses classic SSR search HTML (`/hostels/{city}?DateFrom&DateTo&NumberOfGuests`) via HtmlUnit (`webClientForHostelworld`); PWA `/pwa/wds/s` shells need JS.
- MCP allowlist includes both jobs (optional params).
- Metabase: **Competitive position** dashboard — GHS heatmap / own-vs-market lines / HW rank series. See [`metabase/dashboard-setup.md`](../metabase/dashboard-setup.md) + [`metabase/bootstrap-questions.sql`](../metabase/bootstrap-questions.sql).

### Backfill

Enqueue via MCP `insert_job` (allowlisted) or InsertJob, per property processor:

- `SyncBookingCreatedAnalyticsJob` with e.g. `start_date=2024-01-01`, `end_date=2026-11-11`
- `SyncChannelStayDayAnalyticsJob` with the same range (chunks of 31 days internally)

Spot-check:

- Booked date: Cloudbeds reservation list filtered by booked date vs `SUM(grand_total)` for that day in Metabase / SQL.
- Stay date: Channel Production month total vs `SUM(room_revenue)` for that month in `fact_channel_stay_day`.

## Metabase deploy

Compose project: [`metabase/`](../metabase/)

```bash
cd metabase   # on the deploy host
cp .env.example .env   # fill MB_DB_* and METABASE_BIND (Tailscale IP)
docker compose up -d
```

- **Staff / WhatsApp:** public HTTPS `https://analytics.macbackpackers.com` (Plesk reverse-proxy to Metabase; no-login `/public/…` links). Staff do not need Tailscale.
- **Admin / ops:** Metabase also listens on Tailscale (`METABASE_BIND` + `METABASE_PORT`, default 3002) for logged-in editing.

Set `MB_DB_HOST` to the MariaDB **Tailscale IP** if MagicDNS does not resolve inside the container. Admin login and warehouse `metabase_ro` password are in gitignored `metabase/.credentials-notes`. Site URL in Metabase Admin must be `https://analytics.macbackpackers.com`.

1. Complete Metabase setup wizard (admin user).
2. **Admin → Databases → Add** → MySQL → host = same as `MB_DB_HOST`, database `mac_analytics`, user `metabase_ro`.
3. Sync schema; mark models (see below).

WhatsApp authoring uses `metabase_*` MCP tools and must share `public_url` only. For ad-hoc warehouse SQL outside Metabase, use MCP/`mac_analytics` or Cloudbeds tools as appropriate.

### Metabot / Ollama (same host)

Metabase 0.65+ can use a local Ollama for Metabot. Compose sets:

| Variable | Value |
|----------|--------|
| `MB_LLM_ALLOWED_NETWORKS` | `allow-all` (required so Metabase may call a host-local LLM URL) |
| `MB_LLM_OLLAMA_API_BASE_URL` | `http://host.docker.internal:11434/v1` (must end in `/v1`) |

[`metabase/docker-compose.yml`](../metabase/docker-compose.yml) also adds `extra_hosts: host.docker.internal:host-gateway` so the Linux container can reach the host. Do **not** use `http://127.0.0.1:11434/v1` inside Metabase — that is the container’s own loopback.

Ollama must listen on `0.0.0.0:11434` (e.g. `OLLAMA_HOST=0.0.0.0:11434`), not only `127.0.0.1`, or Docker cannot connect. Metabot needs a model context window of at least **16,384** tokens; after recreate, pick the default model under **Admin → AI**.

Ship compose/env/docs via **git** (no rsync). Recreate **only** the Metabase Compose project after pull — never restart littlehotelier processor services for this change.

### WhatsApp authoring (ronbot)

When `METABASE_URL` and `METABASE_API_KEY` are set on `ronbot-bridge`, ronbot-ops exposes `metabase_*` tools so staff can ask WhatsApp ronbot to create native-SQL questions and dashboards (REST `X-API-Key`, not Metabase MCP OAuth). Create the key in Metabase Admin → Authentication → API Keys (group that can edit collection **Hostel analytics** and query `mac_analytics`). New cards/dashboards get a **public link** automatically; WhatsApp replies should use `public_url` (no login). Set `METABASE_PUBLIC_URL` (and usually `METABASE_URL`) to the public HTTPS origin staff use (see local `AGENTS.md` for the real hostname). Admin → Settings → Public sharing must be enabled.

Warehouse spot-checks use `metabase_run_sql` (Metabase `/api/dataset` on `mac_analytics`). Do **not** use `run_sql` for that — `run_sql` only queries `wp_*_backoffice` via `RONBOT_SQL_RO_*` (no `mac_analytics` grant). Rebuild **ronbot-bridge** only after pull — not processors.

## Metabase models & dashboards

Dashboards are created in the Metabase UI (OSS has no in-repo serialization). Apply the checklist in [`metabase/dashboard-setup.md`](../metabase/dashboard-setup.md); paste native SQL from [`metabase/bootstrap-questions.sql`](../metabase/bootstrap-questions.sql).

Friendly models (Admin → Table Metadata or Models):

- **Booking created** → `fact_booking_created` / `v_booking_created_daily`  
  Display: Booked date, Channel, Booking value (£), Hostel (`property_code` → `dim_property.display_name`)
- **Channel stay day** → `fact_channel_stay_day` / `v_channel_stay_daily_total`  
  Display: Stay date, Channel, Room revenue (£), Room nights, ADR

Collection: **Hostel analytics**. Filters on both dashboards: Hostel + date range.

**Booked-date performance** dashboard:

- Line: booking value by day
- Table/bar: ISO week under year/month groups
- Bar: by month; by day-of-week
- Stacked bar / pie: channel mix

**Stay-date production** dashboard:

- Same layout on `stay_date` + `room_revenue` / `rooms_sold`
- Blurb: “Matches Cloudbeds Channel Production (nights occupied), not when the guest booked.”

**Competitive position** dashboard (Edinburgh / Phase 2):

- GHS dorm (and private) price heatmap: stay_date × competitor
- Line: GHS dorm prices by hostel (`competitor_name` series) with **View** preset (`our` / `cheapest3` / `dearest3` / `our_and_extremes` / `custom`) and **Hostels** multi-select for `custom`
- Line: Hostelworld search `position` over `search_date` for CRH / HSH / RMB
- Remove any leftover “coming soon” text stubs from Booked/Stay dashboards

Optional: Metabase dashboard subscription (email) on the booked-date overview.

## Processor config

- JDBC: `analyticsdb.*` in `application.properties` → GCP `analyticsdb_username` / `analyticsdb_password`
- Compose: `ANALYTICSDB_HOST=mysql-tailscale` on processors and `ronbot-read-api`
