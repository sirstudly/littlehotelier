# Edinburgh Visitor Levy (EVL)

Reference for developers and AI agents working on EVL in this codebase. Covers statutory rules, Cloudbeds configuration, calculation logic, jobs, and accounting caveats discovered during implementation.

## Statutory rules (Edinburgh)

| Rule | Value |
|---|---|
| Rate | **5%** of **pre-VAT accommodation** |
| Night cap | First **5 consecutive** eligible nights |
| Stay eligible from | **2026-07-24** (last night = day before checkout) |
| Canceled / no-show | Levy = **£0** |
| VAT | If VAT-registered, levy is taxable turnover (20% VAT on levy) |

Council remittance is via **visitorlevy.scot** (quarterly, by stay dates). The platform is for reporting/remittance, not guest charging.

Official sources:
- [Edinburgh Council – information for businesses](https://www.edinburgh.gov.uk/business/information-businesses)
- [VisitScotland provider FAQs (PDF)](https://support.visitscotland.org/binaries/content/assets/bsh/2025/10/visitor-levy-faqs.pdf)

---

## Cloudbeds configuration

Each property needs **two EVL taxes**, linked **per reservation source** in Cloudbeds Settings → Taxes & Fees.

### Tax types (same on every property)

| Role | Typical English label | Type | Used for |
|---|---|---|---|
| Exclusive | Edinburgh Visitor Levy 2026 | **Exclusive** (5%) | Direct, walk-in, Hostelworld |
| Inclusive | see below | **Inclusive** (6% of net) | Booking.com, Agoda, Agoda / Priceline |

- **Exclusive** = added on top of rate (direct/HWL).
- **Inclusive** = carved out of fixed OTA price (BDC, Agoda/Priceline); must not inflate channel total. Configured at **6% of net** so the EVL line includes VAT-on-levy (consistent with the exclusive tax on direct/HWL).

Cloudbeds tax/fee UI is identical for taxes and fees; **Inclusive vs Exclusive** controls pricing behaviour, not the tax/fee type name.

### Tax IDs are property-specific

Cloudbeds assigns a **unique numeric tax ID per property** when each tax is created. Do **not** assume IDs are shared across hostels — only the **English label** and **exclusive/inclusive type** need to match.

Known examples (from HAR captures in this repo):

| Property | Cloudbeds property ID | Exclusive (`tax_*`) | Inclusive (`tax_*`) | Inclusive label in Cloudbeds |
|---|---|---|---|---|
| Royal Mile Backpackers (RMB) | 18265 | `824186` | `824360` | Edinburgh Visitor Levy (Inclusive) |
| High Street Hostel (HSH) | 17959 | `824362` | `824364` | Edinburgh Visitor Levy 2026 (Inclusive) |
| Castle Rock Hostel (CRH) | 17363 | not captured | not captured | verify in Cloudbeds |

The exclusive label **Edinburgh Visitor Levy 2026** has been consistent where seen; the **inclusive label can differ by property** (RMB vs HSH above). If `resolveTaxIdByLabel()` fails on a property, check the exact label in Cloudbeds and set `evl.tax.inclusive.label` in that property's `application-*.properties` profile.

### Runtime resolution (not hardcoded IDs)

Tax IDs for API calls are resolved at runtime by **English label** via `CloudbedsScraper.resolveTaxIdByLabel()` (cached property content from `getPropertyContent`). Labels come from:

```properties
evl.tax.exclusive.label=Edinburgh Visitor Levy 2026
evl.tax.inclusive.label=Edinburgh Visitor Levy (Inclusive)
```

(`application.properties` defaults — override per profile if Cloudbeds uses a different inclusive name.)

Do not hardcode numeric IDs in production code. Tests/fixtures may use RMB IDs: `src/test/resources/get_property_content_taxes.json`.

### Auto-post vs adjustment job

Cloudbeds auto-applies EVL per source config but:
- Does not reliably cap at 5 nights
- May miss eligibility boundaries (pre/post 24 Jul)
- BDC and Agoda/Priceline multi-guest per-person rates need correct guest count

The **adjustment job** compares `balance_details.tax_breakdown` to our calculated expected levy and posts corrections.

---

## Architecture

```
Jobs / WebSocket                 Service                         Calculator (pure)
─────────────────────────────────────────────────────────────────────────────
CreateCalculate...Job  ──►       EdinburghVisitorLevyService  ──► EdinburghVisitorLevyCalculator
Calculate...ForBookingJob            │ assess / apply
Calculate...DryRunJob                │
Calculate...BookingEventListener ────┘  (real-time, on `booked` WS events)
                                  ▼
                            CloudbedsScraper
                              - getReservationRetry
                              - resolveTaxIdByLabel
                              - addVisitorLevyCharge  → add_new_fee_or_tax
                              - adjustVisitorLevyCharge → add_new_adjust
```

### Key classes

| Class | Path | Role |
|---|---|---|
| `EdinburghVisitorLevyCalculator` | `services/EdinburghVisitorLevyCalculator.java` | Pure calculation; no I/O |
| `EdinburghVisitorLevyService` | `services/EdinburghVisitorLevyService.java` | Orchestration, filtering, assess/apply |
| `CalculateEdinburghVisitorLevyForBookingJob` | `jobs/` | Single reservation; posts adjustments |
| `CreateCalculateEdinburghVisitorLevyForBookingJob` | `jobs/` | Batch: fans out jobs for bookings needing correction |
| `CalculateEdinburghVisitorLevyDryRunJob` | `jobs/` | Batch assess + log only |
| `CalculateEdinburghVisitorLevyBookingEventListener` | `scrapers/cloudbedsws/` | Real-time: enqueues jobs on new `booked` WebSocket events |
| `EdinburghVisitorLevyBookingCriteria` | `scrapers/cloudbedsws/` | Cheap eligibility for calendar `booked` events |
| `EdinburghVisitorLevyCalculatorTest` | `test/.../` | Unit tests for calculator |
| `EdinburghVisitorLevyBookingCriteriaTest` | `test/.../` | Unit tests for WebSocket eligibility |

### Related changes elsewhere

| Class | Change |
|---|---|
| `Reservation` | `channelPriceListed`, `channelCommission`, `channelBalance`; `getVisitorLevyTotal()` reads `balance_details.tax_breakdown` |
| `CloudbedsScraper` | `resolveTaxIdByLabel`, `addVisitorLevyCharge`, `adjustVisitorLevyCharge` |
| `CloudbedsJsonRequestFactory` | `createAddNewFeeOrTaxRequest`, `createAddNewAdjustRequest` |
| `PaymentProcessorService` | Non-refundable charges use `getBalanceDueExcludingVisitorLevy()`; skips if already paid |
| `CalculateEdinburghVisitorLevyBookingEventListener` | `@Component` `CloudbedsEventListener`; auto-registered via Spring `List<CloudbedsEventListener>` in `CloudbedsWebSocketService` |
| `CloudbedsCalendarEvent` | `getBookingDate()` exposes `booking_date` from WebSocket payloads |
| `WordPressDAO` | `hasCalculateEdinburghVisitorLevyJobForReservation()` — blocks enqueue only while a job is pending |

---

## Configuration properties

Set in `application.properties` (override per profile in `application-*.properties`):

```properties
evl.enabled=false
evl.tax.exclusive.label=Edinburgh Visitor Levy 2026
evl.tax.inclusive.label=Edinburgh Visitor Levy 2026 (Inclusive)
evl.stay.date.from=2026-07-24
```

`evl.enabled` must be `true` for batch assessment, adjustment jobs, and real-time WebSocket enqueueing to consider bookings. `EdinburghVisitorLevyService.isPotentiallyEligible()` returns `false` when disabled, so create/dry-run jobs and `requiresVisitorLevyAdjustment()` do nothing. The WebSocket listener uses the same flag via `isPotentiallyEligibleForNewBooking()`.

All bookings in Cloudbeds were created after the statutory booking cutoff (Oct 2025); the codebase does not apply booking-date exemption checks.

---

## Calculation by source

The calculator (`EdinburghVisitorLevyCalculator.calculate`) branches on source. **Tolerance** for adjustments: `|delta| < £0.01` → no action.

### Eligible nights (all sources)

1. Night date **≥ stayDateFrom** and **< checkoutDate**
2. Sort chronologically
3. Cap at **first 5** nights

### Direct / walk-in / other (exclusive tax)

- Uses `getRatesByDate()` — nightly room totals from `booking_rooms[].detailed_rates`
- **Guest levy** = **5% of gross** (VAT-inclusive accommodation), **rounded per night**, then summed
- Equivalent to council + VAT-on-levy for exclusive posting

**Example:** 3 nights × £55.00 gross  
→ £2.75 + £2.75 + £2.75 = **£8.25**

### Booking.com, Agoda / Priceline (inclusive tax, 6% of net)

Applies when `reservation.isInclusiveTaxBooking()` — source name starts with `Booking.com` or `Agoda` (covers Agoda and Agoda / Priceline, hotel and channel collect).

`detailed_rates` are **per person per night**, all-in (net + EVL + room VAT).

For each eligible person-night:

```
netPerGuest = round(gross ÷ 1.26, 2)     # 1.26 = 1 + 6% levy + 20% VAT on net
levyPerGuest = round(netPerGuest × 6%, 2)   # 6% = 5% council + 20% VAT on levy
total += levyPerGuest × guestCount
```

**Guest count:** `adults + kids` on rate line; if zero, fall back to `reservation.getNumberOfGuests()` (min 1).

**Important:** Cloudbeds rounds **net to 2dp before** multiplying by guests and summing. Without this, levy base can be off.

**Council remittance from folio:** `SUM(EVL inclusive line) ÷ 1.2` (EVL line is VAT-inclusive guest levy, like exclusive tax on direct bookings).

#### Example A — £188.80, 2 guests, 2 nights

Rates pp: £47.20, £47.20

| Step | Per person-night | × 2 guests | × 2 nights |
|---|---:|---:|---:|
| Net | £47.20 ÷ 1.26 = **£37.46** | | |
| EVL | 6% × £37.46 = **£2.25** | | |
| **Totals** | | Subtotal **£149.84** | EVL **£9.00**, VAT **£29.96**, Grand **£188.80** |

Council remittance: £9.00 ÷ 1.2 = **£7.50** (statutory ÷1.26 ≈ £7.49).

#### Example B — £298.86, 2 guests, 5 nights

Rates pp: £26.15, £30.82 × 4

| Folio line | Amount |
|---|---:|
| Subtotal (net) | £237.18 |
| EVL (Inclusive) | £14.26 |
| VAT | £47.42 |
| **Grand total** | **£298.86** |

Per-person rate sum = £149.43; × 2 guests = £298.86.

### Hostelworld (exclusive tax)

Cloudbeds `detailed_rates` are **net** (post-commission). Use **channel listed price** for levy base:

```
priceListed = channel_price_listed
           OR channel_balance + channel_commission
levyBase = priceListed × (eligibleNetRates / allNetRates)
expectedLevy = round(levyBase × 5%, 2)
```

**Example:** Listed £181.45, 3 eligible nights at £51.41 each (of £154.23 total)  
→ levyBase = £181.45 → **£9.07**

**Example:** Kyra — balance £79.87 + commission £14.09 = listed £93.96, 2 nights  
→ **£4.70**

---

## Adjustment job flow

1. Load reservation (`getReservationRetry`)
2. `expectedLevy` = calculator
3. `currentLevy` = sum of `tax_breakdown` lines matching either EVL label
4. `delta = expected - current`
5. If `|delta| ≥ £0.01`:
   - **BDC, Agoda, Agoda / Priceline** (inclusive tax) → log expected EVL and VAT vs folio; **no write** (channel total is fixed; corrections need coordinated EVL/VAT/room-rate adjustments)
   - **Other sources, delta < 0** → `adjustVisitorLevyCharge` (reduce)
   - **Other sources, delta > 0** → `addVisitorLevyCharge` (increase)
6. Tax ID: inclusive label for BDC/Agoda/Priceline, exclusive otherwise
7. Adjustment notes suffixed with `-RONBOT`

### API endpoints

| Action | Endpoint | ID format |
|---|---|---|
| Add charge | `POST /hotel/add_new_fee_or_tax` | `id=tax-{numericId}` |
| Adjust | `POST /hotel/add_new_adjust` | `adjust[id]=tax_{numericId}` |

---

## Batch jobs

### `CreateCalculateEdinburghVisitorLevyForBookingJob`

Parameters: `booking_date_start`, `booking_date_end`

Queries active bookings (`confirmed,not_confirmed`) in range, applies cheap eligibility checks (`evl.enabled`, booking/stay dates), then loads each reservation and compares folio EVL to the calculated amount.

| Include if | Reason |
|---|---|
| Potentially eligible (`evl.enabled`, has eligible stay dates) **and** `expectedLevy − currentLevy` outside tolerance | Folio EVL needs correction |

Excludes: `evl.enabled=false`, no eligible stay dates, levy already correct (within £0.01).

Creates one `CalculateEdinburghVisitorLevyForBookingJob` per reservation (`reservation_id`) that needs adjustment only — no no-op jobs.

### `CalculateEdinburghVisitorLevyForBookingJob`

Parameter: `reservation_id` — calls `processVisitorLevyForBooking`.

### `CalculateEdinburghVisitorLevyDryRunJob`

Parameters: `booking_date_start`, `booking_date_end`

Same booking-date range and eligibility checks as create job; **single job** assesses every potentially eligible reservation and logs:
- `ADJUSTMENT NEEDED: ... expected=, current=, delta=`
- `OK: ... levy=`
- Summary: `X reservations checked, Y need adjustment`

No Cloudbeds writes.

---

## Real-time (WebSocket)

When the processor runs in server mode (`RunProcessor.runInServerMode()`), `CloudbedsWebSocketService` maintains a calendar WebSocket connection and fans out incremental updates to all `CloudbedsEventListener` beans. See `CLOUDBEDS_WEBSOCKET_PROTOCOL.md` for payload shapes.

### `CalculateEdinburghVisitorLevyBookingEventListener`

Enqueues a `CalculateEdinburghVisitorLevyForBookingJob` as soon as a new booking appears on the calendar WebSocket — i.e. a row with `type=booked` (see `CloudbedsCalendarEvent.isReservationBooking()`). This covers both assigned `Events[]` rows and unassigned `NonAssignedReservations[]` rows.

**Only `onUpdate` is handled.** The initial `on_migrate` snapshot is ignored so existing bookings are not processed when the monitor connects or reconnects (same pattern as `ChargeNonRefundableBookingEventListener`).

#### Eligibility (cheap checks)

Mirrors `EdinburghVisitorLevyService.isPotentiallyEligible()` using fields available on the calendar event, via `EdinburghVisitorLevyBookingCriteria.matchesNewBookingCalendarEvent()` / `EdinburghVisitorLevyService.isPotentiallyEligibleForNewBooking()`:

| Check | Source on event |
|---|---|
| `evl.enabled=true` | property config |
| `type=booked`, valid `booking_id` | `type`, `booking_id` |
| Not canceled | `status` |
| Stay includes eligible nights (checkout after `evl.stay.date.from`) | `end_date` |

If `end_date` is absent, the stay-date check is skipped.

The listener does **not** compare folio EVL before enqueueing — that happens inside `CalculateEdinburghVisitorLevyForBookingJob` (`processVisitorLevyForBooking`), which no-ops when levy is already correct.

#### Deduplication

| Layer | Behaviour |
|---|---|
| Same WebSocket batch | One job per `booking_id` per update (multiple room rows deduped) |
| In-memory | `recentlyEnqueuedReservationIds` avoids duplicate inserts within the JVM session |
| Database | `WordPressDAO.hasCalculateEdinburghVisitorLevyJobForReservation()` skips enqueue when a job for that reservation is already **pending** (`submitted`, `processing`, or `retry`) |

There is **no cooldown** on completed or failed jobs — a subsequent `booked` event (e.g. after a room move) may enqueue a fresh job once the previous one has finished.

#### Batch vs real-time

| Path | When | Pre-enqueue filter |
|---|---|---|
| **WebSocket** (`CalculateEdinburghVisitorLevyBookingEventListener`) | New `booked` event | Cheap eligibility only |
| **Batch** (`CreateCalculateEdinburghVisitorLevyForBookingJob`) | Scheduled booking-date range | Cheap eligibility **and** folio delta outside tolerance |

Both paths enqueue the same `CalculateEdinburghVisitorLevyForBookingJob`, which loads the full reservation and applies or skips the adjustment.

---

## Payment processing

`PaymentProcessorService.chargeNonRefundableBooking()`:

- Charges `getBalanceDueExcludingVisitorLevy()` — EVL collected on arrival
- Logs excluded levy amount
- If `paidValue > 0`, skips (already charged once)

---

## Accounting & VAT (critical context)

Cloudbeds folio splits and statutory remittance **differ**. Do not use Cloudbeds VAT report alone for BDC HMRC returns.

### Three amounts (do not conflate)

| Amount | Meaning |
|---|---|
| **Council remittance** | 5% of pre-VAT accommodation |
| **VAT on levy** | 20% × council amount |
| **Guest levy (direct)** | 5% of gross accom = council + VAT on levy |

### BDC inclusive folio (÷ 1.26 split, 6% EVL)

Cloudbeds allocates fixed OTA total as:

```
Gross = Net + 6%×Net (EVL line) + 20%×Net (VAT line)
      = Net × 1.26
```

- EVL line = **guest levy including VAT-on-levy** (6% of net = 5% council + 1% VAT-on-levy)
- VAT line = **room VAT only**
- Council remittance: `SUM(inclusive EVL folio line) ÷ 1.2`
- Aligns with exclusive direct posting and statutory ÷1.26 unwind (within rounding)

### Strict statutory unwind from guest total T

```
Net accommodation  = T ÷ 1.26
Council EVL        = T × 5% ÷ 1.26
VAT on room        = T × 20% ÷ 1.26
VAT on levy        = T × 1% ÷ 1.26   (5% × 20%)
Total VAT to HMRC  = T × 21% ÷ 1.26
```

**£188.80 example (6% inclusive config):**

| Method | EVL line | Council (÷1.2) | Room VAT | VAT on levy |
|---|---:|---:|---:|---:|
| Cloudbeds folio | £9.00 | £7.50 | £29.96 | £1.50 (in EVL line) |
| Statutory ÷1.26 | — | £7.49 | £29.96 | £1.50 |

### Direct exclusive

- EVL line at **5% of gross** includes VAT on levy
- Council remittance: `SUM(exclusive EVL folio line) ÷ 1.2`
- Inclusive OTA folio lines also use ÷1.2 for council (EVL line is VAT-inclusive)

### Guest invoices

Use Cloudbeds **Invoice Footer** (Settings → Finance → Invoices) for static EVL/VAT wording. No per-booking dynamic footnotes. Generate formal invoice PDFs, not folio print, for guest-facing docs.

---

## OTA behaviour (external)

| Channel | EVL handling |
|---|---|
| **Booking.com** | 5% attributed in rate from 1 Oct 2025; **cannot cap at 5 nights** — provider must refund excess; rates must include levy |
| **Agoda / Priceline** | EVL baked into fixed OTA total (inclusive tax); hotel and channel collect — same calculation as BDC |
| **Hostelworld** | Listed price in `channel_price_listed` / balance+commission |
| **Airbnb** | No auto-tool yet; build into calendar pricing |
| **Direct** | Exclusive EVL tax on source |

BDC partner hub: [Edinburgh Visitor Levy](https://partner.booking.com/en-gb/help/commission-invoices-tax/local-taxes/edinburgh-visitor-levy)

---

## Tests

`EdinburghVisitorLevyCalculatorTest` — key scenarios:

| Test | Expected EVL |
|---|---:|
| Direct 3×£55 | £8.25 |
| BDC 1 night £165 (1 guest) | £7.86 |
| BDC 2 guests, 2×£47.20 | £9.00 (base £149.84) |
| BDC 2 guests, 5 nights (298.86 booking) | £14.26 (base £237.18) |
| Agoda channel collect 1 night £165 | £7.86 |
| Agoda / Priceline hotel collect 2×£47.20 × 2 guests | £9.00 (base £149.84) |
| HWL listed £181.45 / 3 nights | £9.07 |
| HWL balance+commission Kyra | £4.70 |
| HWL partial eligibility | £5.88 |
| Straddling 24 Jul (2 of 4 nights) | £10.00 |
| 7 nights capped at 5 | £15.00 |
| Exempt / canceled | £0 |

Test helper `reservationWithRates()` clears `channelPriceListed/Balance/Commission` to avoid stale fixture data.

Run: `mvn test -Dtest=EdinburghVisitorLevyCalculatorTest`

`EdinburghVisitorLevyBookingCriteriaTest` — WebSocket eligibility:

| Test | Expected |
|---|---|
| `booked` + eligible dates, `evl.enabled=true` | match |
| `evl.enabled=false` | no match |
| Canceled | no match |
| Checkout on levy start date (no eligible nights) | no match |
| `blocked_dates` | no match |

Run: `mvn test -Dtest=EdinburghVisitorLevyBookingCriteriaTest`

---

## Known limitations & future work

1. **Inclusive-tax OTA adjustments are log-only** (BDC, Agoda, Agoda / Priceline) — fixed channel total requires coordinated EVL/VAT/room-rate changes.
2. **HWL** uses aggregate 5% on prorated listed price, not per-night inclusive-OTA rounding.
3. **Inclusive OTA guest count** relies on `detailed_rates.adults/kids` or reservation guest count — verify when Cloudbeds sends `adults:0`.
4. **Inclusive OTA VAT-on-levy** is inside the 6% EVL line; room VAT is a separate folio line. Council remittance = EVL ÷ 1.2.
5. **5-night cap** on inclusive OTAs: channel may attribute levy for whole stay; manual refund + our job logs discrepancy.
6. **PaymentProcessorService** `paidValue > 0` early return — may need revisiting for partial payments.
7. **Per-property tax labels** — inclusive EVL name may differ (e.g. RMB vs HSH); verify Cloudbeds and set `evl.tax.inclusive.label` per profile if needed.

---

## Quick reference: which tax when adjusting

```java
EdinburghVisitorLevyCalculator.useInclusiveTax(reservation)
// true  → inclusive label (evl.tax.inclusive.label) → BDC, Agoda, Agoda / Priceline
// false → exclusive label (evl.tax.exclusive.label) → direct, HWL, walk-in
```

Numeric `tax_*` IDs differ per property; the service resolves them via label at runtime.

Current levy on folio:

```java
reservation.getVisitorLevyTotal(
    EdinburghVisitorLevyCalculator.EXCLUSIVE_TAX_LABEL,
    EdinburghVisitorLevyCalculator.INCLUSIVE_TAX_LABEL)
```
