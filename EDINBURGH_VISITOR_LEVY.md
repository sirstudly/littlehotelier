# Edinburgh Visitor Levy (EVL)

Reference for developers and AI agents working on EVL in this codebase. Covers statutory rules, Cloudbeds configuration, calculation logic, jobs, and accounting caveats discovered during implementation.

## Statutory rules (Edinburgh)

| Rule | Value |
|---|---|
| Rate | **5%** of **pre-VAT accommodation** |
| Night cap | First **5 consecutive** eligible nights |
| Stay eligible from | **2026-07-24** (last night = day before checkout) |
| Booking exempt if booked before | **2025-10-01** |
| Canceled / no-show | Levy = **£0** |
| VAT | If VAT-registered, levy is taxable turnover (20% VAT on levy) |

Council remittance is via **visitorlevy.scot** (quarterly, by stay dates). The platform is for reporting/remittance, not guest charging.

Official sources:
- [Edinburgh Council – information for businesses](https://www.edinburgh.gov.uk/business/information-businesses)
- [VisitScotland provider FAQs (PDF)](https://support.visitscotland.org/binaries/content/assets/bsh/2025/10/visitor-levy-faqs.pdf)

---

## Cloudbeds configuration (property 18265)

Two taxes, linked **per reservation source**:

| Tax ID | Label | Type | Used for |
|---|---|---|---|
| `tax_824186` | Edinburgh Visitor Levy 2026 | **Exclusive** | Direct, walk-in, Hostelworld |
| `tax_824360` | Edinburgh Visitor Levy (Inclusive) | **Inclusive** | Booking.com |

- **Exclusive** = added on top of rate (direct/HWL).
- **Inclusive** = carved out of fixed OTA price (BDC); must not inflate channel total.

Cloudbeds tax/fee UI is identical for taxes and fees; **Inclusive vs Exclusive** controls pricing behaviour, not the tax/fee type name.

Tax IDs are resolved at runtime by **English label** via `CloudbedsScraper.resolveTaxIdByLabel()` (cached property content from `getPropertyContent`). Do not hardcode IDs except in tests/fixtures.

Fixture: `src/test/resources/get_property_content_taxes.json`

### Auto-post vs adjustment job

Cloudbeds auto-applies EVL per source config but:
- Does not reliably cap at 5 nights
- May miss eligibility boundaries (pre/post 24 Jul)
- BDC multi-guest per-person rates need correct guest count

The **adjustment job** compares `balance_details.tax_breakdown` to our calculated expected levy and posts corrections.

---

## Architecture

```
Jobs                          Service                         Calculator (pure)
─────────────────────────────────────────────────────────────────────────────
CreateCalculate...Job  ──►    EdinburghVisitorLevyService  ──► EdinburghVisitorLevyCalculator
Calculate...ForBookingJob         │ assess / apply
Calculate...DryRunJob             │
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
| `CreateCalculateEdinburghVisitorLevyForBookingJob` | `jobs/` | Fans out jobs for HWL or >5-night stays |
| `CalculateEdinburghVisitorLevyDryRunJob` | `jobs/` | Batch assess + log only |
| `EdinburghVisitorLevyCalculatorTest` | `test/.../` | Unit tests for calculator |

### Related changes elsewhere

| Class | Change |
|---|---|
| `Reservation` | `channelPriceListed`, `channelCommission`, `channelBalance`; `getVisitorLevyTotal()` reads `balance_details.tax_breakdown` |
| `CloudbedsScraper` | `resolveTaxIdByLabel`, `addVisitorLevyCharge`, `adjustVisitorLevyCharge` |
| `CloudbedsJsonRequestFactory` | `createAddNewFeeOrTaxRequest`, `createAddNewAdjustRequest` |
| `PaymentProcessorService` | Non-refundable charges use `getBalanceDueExcludingVisitorLevy()`; skips if already paid |

---

## Configuration properties

```properties
evl.tax.exclusive.label=Edinburgh Visitor Levy 2026
evl.tax.inclusive.label=Edinburgh Visitor Levy (Inclusive)
evl.stay.date.from=2026-07-24
evl.booked.date.from=2025-10-01
```

---

## Calculation by source

The calculator (`EdinburghVisitorLevyCalculator.calculate`) branches on source. **Tolerance** for adjustments: `|delta| < £0.01` → no action.

### Eligible nights (all sources)

1. Night date **≥ stayDateFrom** and **< checkoutDate**
2. Sort chronologically
3. Cap at **first 5** nights

### Direct / walk-in / other (exclusive tax `824186`)

- Uses `getRatesByDate()` — nightly room totals from `booking_rooms[].detailed_rates`
- **Guest levy** = **5% of gross** (VAT-inclusive accommodation), **rounded per night**, then summed
- Equivalent to council + VAT-on-levy for exclusive posting

**Example:** 3 nights × £55.00 gross  
→ £2.75 + £2.75 + £2.75 = **£8.25**

### Booking.com (inclusive tax `824360`)

BDC `detailed_rates` are **per person per night**, all-in (net + EVL + room VAT).

For each eligible person-night:

```
netPerGuest = round(gross ÷ 1.25, 2)     # 1.25 = 1 + 5% levy + 20% VAT on net
levyPerGuest = round(netPerGuest × 5%, 2)
total += levyPerGuest × guestCount
```

**Guest count:** `adults + kids` on rate line; if zero, fall back to `reservation.getNumberOfGuests()` (min 1).

**Important:** Cloudbeds rounds **net to 2dp before** multiplying by guests and summing. Without this, levy base can be off (e.g. £239.09 vs £239.12).

#### Example A — £188.80, 2 guests, 2 nights

Rates pp: £47.20, £47.20

| Step | Per person-night | × 2 guests | × 2 nights |
|---|---:|---:|---:|
| Net | £47.20 ÷ 1.25 = **£37.76** | | |
| EVL | 5% × £37.76 = **£1.89** | | |
| **Totals** | | Subtotal **£151.04** | EVL **£7.56**, VAT **£30.20**, Grand **£188.80** |

#### Example B — £298.86, 2 guests, 5 nights

Rates pp: £26.15, £30.82 × 4

| Folio line | Amount |
|---|---:|
| Subtotal (net) | £239.12 |
| EVL (Inclusive) | £11.94 |
| VAT | £47.80 |
| **Grand total** | **£298.86** |

Per-person rate sum = £149.43; × 2 guests = £298.86.

### Hostelworld (exclusive tax `824186`)

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
   - **delta < 0** → `adjustVisitorLevyCharge` (reduce)
   - **delta > 0** → `addVisitorLevyCharge` (increase)
6. Tax ID: inclusive label for BDC, exclusive otherwise
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

Queries active bookings (`confirmed,not_confirmed`) in range, applies cheap eligibility checks (booking/stay dates), then loads each reservation and compares folio EVL to the calculated amount.

| Include if | Reason |
|---|---|
| Potentially eligible (not booking-exempt, has eligible stay dates) **and** `expectedLevy − currentLevy` outside tolerance | Folio EVL needs correction |

Excludes: booking exempt (pre Oct 2025), no eligible stay dates, levy already correct (within £0.01).

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

### BDC inclusive folio (÷ 1.25 split)

Cloudbeds allocates fixed OTA total as:

```
Gross = Net + 5%×Net (EVL line) + 20%×Net (VAT line)
      = Net × 1.25
```

- EVL line = **council only** (not VAT-inclusive guest levy)
- VAT line = **room VAT only** — **VAT on levy is not shown**
- `SUM(tax_824360)` ≈ council remittance (slightly high ~0.8% vs strict ÷1.26)

### Strict statutory unwind from guest total T

```
Net accommodation  = T ÷ 1.26
Council EVL        = T × 5% ÷ 1.26
VAT on room        = T × 20% ÷ 1.26
VAT on levy        = T × 1% ÷ 1.26   (5% × 20%)
Total VAT to HMRC  = T × 21% ÷ 1.26
```

**£188.80 example:**

| Method | Council | Total VAT |
|---|---:|---:|
| Cloudbeds folio | £7.56 | £30.20 (understates by ~£1.27) |
| Statutory ÷1.26 | £7.49 | £31.47 |

### Direct exclusive (`824186`)

- EVL line at **5% of gross** includes VAT on levy
- Council remittance: `SUM(824186) ÷ 1.2`
- Do **not** ÷1.2 on BDC `824360` sums (already council-only)

### Proposed 6% inclusive tax (not implemented)

Setting BDC tax to **6% of net** (÷1.26) would put VAT-on-levy inside EVL line; council = `SUM ÷ 1.2`. Discussed but not adopted — would require config migration and calculator constant change (`1.26`, `0.06`).

### Guest invoices

Use Cloudbeds **Invoice Footer** (Settings → Finance → Invoices) for static EVL/VAT wording. No per-booking dynamic footnotes. Generate formal invoice PDFs, not folio print, for guest-facing docs.

---

## OTA behaviour (external)

| Channel | EVL handling |
|---|---|
| **Booking.com** | 5% attributed in rate from 1 Oct 2025; **cannot cap at 5 nights** — provider must refund excess; rates must include levy |
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
| BDC 1 night £165 (1 guest) | £6.60 |
| BDC 2 guests, 2×£47.20 | £7.56 (base £151.04) |
| BDC 2 guests, 5 nights (298.86 booking) | £11.94 (base £239.12) |
| HWL listed £181.45 / 3 nights | £9.07 |
| HWL balance+commission Kyra | £4.70 |
| HWL partial eligibility | £5.88 |
| Straddling 24 Jul (2 of 4 nights) | £10.00 |
| 7 nights capped at 5 | £15.00 |
| Exempt / canceled | £0 |

Test helper `reservationWithRates()` clears `channelPriceListed/Balance/Commission` to avoid stale fixture data.

Run: `mvn test -Dtest=EdinburghVisitorLevyCalculatorTest`

---

## Known limitations & future work

1. **Calculator matches Cloudbeds posting**, not strict statutory ÷1.26 — intentional for adjustment job parity on BDC.
2. **HWL** uses aggregate 5% on prorated listed price, not per-night BDC-style rounding.
3. **BDC guest count** relies on `detailed_rates.adults/kids` or reservation guest count — verify when Cloudbeds sends `adults:0`.
4. **VAT-on-levy** invisible on BDC folio — handle in accounts export, not adjustment job.
5. **5-night cap** on BDC: BDC attributes 5% for whole stay; manual refund + our job correction.
6. **PaymentProcessorService** `paidValue > 0` early return — may need revisiting for partial payments.

---

## Quick reference: which tax ID when adjusting

```java
EdinburghVisitorLevyCalculator.useInclusiveTax(reservation)
// true  → "Edinburgh Visitor Levy (Inclusive)" → BDC
// false → "Edinburgh Visitor Levy 2026"       → direct, HWL, walk-in
```

Current levy on folio:

```java
reservation.getVisitorLevyTotal(
    EdinburghVisitorLevyCalculator.EXCLUSIVE_TAX_LABEL,
    EdinburghVisitorLevyCalculator.INCLUSIVE_TAX_LABEL)
```
