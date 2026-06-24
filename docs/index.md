# Cloudbeds Backoffice Automation

Java-based automation for hostel backoffice operations. The project originally targeted [Little Hotelier](https://www.littlehotelier.com/) but **only Cloudbeds is actively supported today**. Legacy Little Hotelier classes and configuration remain in the codebase but have not been maintained or tested in years and are unlikely to work.

Used in production for [MacBackpackers](https://www.macbackpackers.com/) properties in Edinburgh and Lochside.

## What it does

A long-running **job processor** polls a MySQL job queue (WordPress plugin tables), executes work items, and records results. Jobs automate repetitive Cloudbeds admin tasks that would otherwise be done manually in the browser.

| Area | Examples |
|---|---|
| **Payments** | Deposit and balance charges via Stripe; prepaid/non-refundable booking charges; refunds; payment-link emails |
| **OTA integrations** | Hostelworld (card retrieval, payment acknowledgement, late-cancellation charges); Booking.com (login verification, invalid-card marking); Agoda charges; Expedia card API |
| **Guest communications** | Templated emails via Gmail (registration, payment confirmations/declines, group-booking reminders, bulk sends) |
| **Reservations & folios** | Fixed-rate reservation creation, booking cancellation, card-detail copy into Cloudbeds, folio notes |
| **Edinburgh Visitor Levy** | Real-time calendar monitoring via WebSocket; automated EVL calculation and folio adjustments by booking source |
| **Reporting & ops** | Allocation/bed-count scrapers, booking reports, blacklist checks, housekeeping, DB purge, session refresh |

Each property runs its own Docker container (`docker-compose.yml`) with a Spring profile (`crh`, `hsh`, `rmb`, `lsh`).

## Architecture

```
WordPress / MySQL job queue (wp_lh_jobs)
        │
        ▼
RunProcessor  ──►  ProcessorService  ──►  Job classes (~80)
        │                    │
        │                    ├── CloudbedsScraper (HtmlUnit HTTP + JSON API)
        │                    ├── HostelworldScraper / BookingComScraper / AgodaScraper
        │                    ├── PaymentProcessorService (Stripe)
        │                    └── GmailService
        │
        └── CloudbedsWebSocketService (real-time calendar events in server mode)
```

- **Stack:** Java 17, Spring Boot 3.4, Quartz, HtmlUnit, Selenium (Chrome for Testing), Stripe, Google Secret Manager, Gmail API
- **Entry point:** `com.macbackpackers.RunProcessor` — server mode (`-S`) runs continuously; standard mode runs once
- **Job insertion:** `com.macbackpackers.InsertJob` creates jobs from scheduled-job templates
- **Secrets:** Google Cloud Secret Manager; per-property DB and OAuth credentials via Spring profiles

## Properties

| Profile | Property |
|---|---|
| `crh` | Castle Rock Hostel |
| `hsh` | High Street Hostel |
| `rmb` | Royal Mile Backpackers |
| `lsh` | Lochside Hostel |

## Documentation

- [Edinburgh Visitor Levy (EVL) — Cloudbeds by booking source](edinburgh-visitor-levy.html) — how EVL appears on folios per channel, with examples and screenshots

Developer reference for EVL implementation details lives in the repo root: `EDINBURGH_VISITOR_LEVY.md` (not published to GitHub Pages).

## Building & running

```bash
mvn clean package
java -jar target/lilhotelier.jar com.macbackpackers.RunProcessor -S
```

Or via Docker Compose (one service per property):

```bash
docker compose up -d crh-processor
```

Requires MySQL (job queue), Google Cloud credentials, and per-property config under `application-{profile}.properties`.

## Legacy note

The Maven artifact and many class names still say *littlehotelier*. Artifacts such as `LittleHotelierConfig`, `UpdateLittleHotelierSettingsJob`, and `LHJsonCardMask` predate the Cloudbeds migration. Treat them as historical unless you explicitly intend to revive Little Hotelier support.
