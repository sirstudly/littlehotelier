# Metabase dashboard setup (Phase 1)

After Metabase is up and `mac_analytics` is connected as a MySQL database. Native SQL for every card lives in [`bootstrap-questions.sql`](bootstrap-questions.sql).

## Models

1. **New → Model** from `v_booking_created_daily`  
   - Join or map `property_code` → `dim_property.display_name` as **Hostel**  
   - Rename: `booked_on` → Booked date, `booking_source` → Channel, `booking_value` → Booking value (£)
2. **New → Model** from `v_channel_stay_daily_total` (+ optional detail model on `fact_channel_stay_day`)  
   - Same Hostel mapping  
   - Rename: `stay_date` → Stay date, `room_revenue` → Room revenue (£), `rooms_sold` → Room nights, `adr` → ADR

## Collection

Create collection **Hostel analytics**. Put all questions and both dashboards there. Pin both dashboards on the home page if useful.

## Dashboard filters (both dashboards)

| Filter | Type | Maps to |
|--------|------|---------|
| Hostel | Category / field filter on `display_name` or `property_code` | All cards |
| Date | Date range | `booked_on` or `stay_date` |

Wire each card to the dashboard filters (GUI: set filter target; native: `{{hostel}}` / `{{date}}` field filters, or replace with literals while prototyping).

## Dashboard: Booked-date performance

| Card | Viz | How |
|------|-----|-----|
| Booking value by day | Line | Sum Booking value (£), group by Booked date |
| Booking value by ISO week (month groups) | Table or bar | Native SQL: year / month / `WEEK(..., 3)` — see bootstrap-questions.sql |
| Booking value by month | Bar | Sum Booking value, group by year + month |
| Channel mix | Stacked bar + optional pie | Sum Booking value, group by Channel |
| Day-of-week pattern | Bar | Sum Booking value, group by day of week |
| Stub | Text | “Competitor bed prices (coming soon)” |
| Stub | Text | “Hostelworld search position (coming soon)” |

## Dashboard: Stay-date production

Same card layout on the stay-date model / `fact_channel_stay_day` (Room revenue (£), Room nights, ADR). Put this description on each revenue card:

> This matches Cloudbeds Channel Production (nights occupied), not when the guest booked.

| Card | Viz |
|------|-----|
| Room revenue by stay day | Line |
| Room revenue by ISO week (month groups) | Table or bar |
| Room revenue by month | Bar |
| Channel mix | Stacked bar + optional pie |
| Day-of-week pattern | Bar |
| Stub | Text: competitor / Hostelworld (same as booked-date) |

## Optional

Metabase dashboard subscription (email) on Booked-date performance for a daily report.
