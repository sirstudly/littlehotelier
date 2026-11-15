# Metabase dashboard setup

After Metabase is up and `mac_analytics` is connected as a MySQL database. Native SQL for every card lives in [`bootstrap-questions.sql`](bootstrap-questions.sql).

## Models

1. **New → Model** from `v_booking_created_daily`  
   - Join or map `property_code` → `dim_property.display_name` as **Hostel**  
   - Rename: `booked_on` → Booked date, `booking_source` → Channel, `booking_value` → Booking value (£)
2. **New → Model** from `v_channel_stay_daily_total` (+ optional detail model on `fact_channel_stay_day`)  
   - Same Hostel mapping  
   - Rename: `stay_date` → Stay date, `room_revenue` → Room revenue (£), `rooms_sold` → Room nights, `adr` → ADR

## Collection

Create collection **Hostel analytics**. Put all questions and dashboards there. Pin dashboards on the home page if useful.

## Dashboard filters (Booked-date / Stay-date)

| Filter | Type | Maps to |
|--------|------|---------|
| Hostel | Category / field filter on `display_name` or `property_code` | All cards |
| Date | Date range | `booked_on` or `stay_date` |

Wire each card to the dashboard filters (GUI: set filter target; native: `{{hostel}}` / `{{date}}` field filters, or replace with literals while prototyping).

## Dashboard: Booked-date performance

| Card | Viz | How |
|------|-----|-----|
| Booking value share by hostel | Pie | Sum Booking value by Hostel (chunk of the whole) |
| Booking value by day | Stacked area | Sum Booking value by Booked date, **series = Hostel** |
| Booking value by ISO week (month groups) | Table or bar | Native SQL: year / month / `WEEK(..., 3)` — see bootstrap-questions.sql |
| Booking value by month | Stacked bar | Sum Booking value by month, **series = Hostel** |
| Channel mix | Stacked bar | Sum Booking value by Channel, **series = Hostel** |
| Day-of-week pattern | Stacked bar | Sum Booking value by day of week, **series = Hostel** |

## Dashboard: Stay-date production

Same card layout on the stay-date model / `fact_channel_stay_day` (Room revenue (£), Room nights, ADR). Put this description on each revenue card:

> This matches Cloudbeds Channel Production (nights occupied), not when the guest booked.

| Card | Viz |
|------|-----|
| Room revenue share by hostel | Pie |
| Room revenue by stay day | Stacked area (series = Hostel) |
| Room revenue by ISO week (month groups) | Table or bar |
| Room revenue by month | Stacked bar (series = Hostel) |
| Channel mix | Stacked bar (series = Hostel) |
| Day-of-week pattern | Stacked bar (series = Hostel) |

## Dashboard: Competitive position (Phase 2)

Edinburgh-only (CRH / HSH / RMB). Data from CRH processor jobs into `mac_analytics`. Prefer a dedicated dashboard; remove any “coming soon” text stubs from Booked/Stay dashboards.

| Card | Viz | SQL / notes |
|------|-----|-------------|
| GHS dorm price grid | Pivot / heatmap | `stay_date` × `competitor_name`, value = `lowest_bed_price`, `room_category='dorm'` |
| GHS private price grid | Pivot / heatmap | Same for `room_category='private'` (may be empty until private prices are scraped) |
| Our dorm vs market | Line | `our_lowest_dorm` vs `competitor_min_dorm` by `stay_date` |
| HW search rank | Line | `position` over `search_date`, **series = Hostel** (CRH/HSH/RMB) |

Live cards are created via Metabase API after scrapers populate facts (same pattern as Phase 1), not OSS serialization.

## Optional

Metabase dashboard subscription (email) on Booked-date performance for a daily report.
