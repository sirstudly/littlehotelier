# Metabase dashboard setup (Phase 1)

After Metabase is up and `mac_analytics` is connected as a MySQL database:

## Models

1. **New → Model** from `v_booking_created_daily`  
   - Join or map `property_code` → `dim_property.display_name` as **Hostel**  
   - Rename: `booked_on` → Booked date, `booking_source` → Channel, `booking_value` → Booking value (£)
2. **New → Model** from `v_channel_stay_daily_total` (+ optional detail model on `fact_channel_stay_day`)  
   - Same Hostel mapping  
   - Rename: `stay_date` → Stay date, `room_revenue` → Room revenue (£), `rooms_sold` → Room nights, `adr` → ADR

## Dashboard: Booked-date performance

| Card | How |
|------|-----|
| Booking value over time | Sum Booking value, group by Booked date (line), filter Hostel + date |
| By week / month / DOW | Duplicate; change group-by to week of year / month / day of week |
| Channel mix | Sum Booking value, group by Channel (stacked bar or pie) |
| Week-in-month table | Native SQL (example below) |
| Stub | Text card: “Competitor bed prices (coming soon)” |
| Stub | Text card: “Hostelworld search position (coming soon)” |

```sql
SELECT
  p.display_name AS hostel,
  YEAR(v.booked_on) AS yr,
  MONTH(v.booked_on) AS mo,
  WEEK(v.booked_on, 3) AS iso_week,
  SUM(v.booking_value) AS booking_value
FROM v_booking_created_daily v
JOIN dim_property p ON p.property_code = v.property_code
WHERE {{hostel}} AND {{date}}
GROUP BY 1, 2, 3, 4
ORDER BY 2, 3, 4;
```

(Use Metabase field filters or replace `{{…}}` with dashboard filters.)

## Dashboard: Stay-date production

Same card layout on stay-date model. Description on each revenue card:

> This matches Cloudbeds Channel Production (nights occupied), not when the guest booked.

## Landing

Pin both dashboards; collection name **Hostel analytics**. Optional email subscription on Booked-date performance (daily).
