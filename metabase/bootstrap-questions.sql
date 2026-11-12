-- Reference queries for Metabase native questions (paste into Metabase).
-- Replace property filter as needed; prefer Metabase models + GUI once connected.

-- Booked-date daily totals by hostel
SELECT
  p.display_name AS hostel,
  v.booked_on AS booked_date,
  v.booking_source AS channel,
  v.booking_value,
  v.booking_count
FROM v_booking_created_daily v
JOIN dim_property p ON p.property_code = v.property_code
WHERE v.booked_on >= CURDATE() - INTERVAL 90 DAY
ORDER BY v.booked_on, p.display_name, v.booking_source;

-- Booked-date channel mix (last 90 days)
SELECT
  p.display_name AS hostel,
  v.booking_source AS channel,
  SUM(v.booking_value) AS booking_value
FROM v_booking_created_daily v
JOIN dim_property p ON p.property_code = v.property_code
WHERE v.booked_on >= CURDATE() - INTERVAL 90 DAY
GROUP BY p.display_name, v.booking_source
ORDER BY booking_value DESC;

-- Stay-date production by day
SELECT
  p.display_name AS hostel,
  v.stay_date,
  v.room_revenue,
  v.rooms_sold AS room_nights,
  v.adr
FROM v_channel_stay_daily_total v
JOIN dim_property p ON p.property_code = v.property_code
WHERE v.stay_date >= CURDATE() - INTERVAL 90 DAY
ORDER BY v.stay_date, p.display_name;

-- Stay-date channel mix
SELECT
  p.display_name AS hostel,
  f.booking_source AS channel,
  SUM(f.room_revenue) AS room_revenue,
  SUM(f.rooms_sold) AS room_nights
FROM fact_channel_stay_day f
JOIN dim_property p ON p.property_code = f.property_code
WHERE f.stay_date >= CURDATE() - INTERVAL 90 DAY
GROUP BY p.display_name, f.booking_source
ORDER BY room_revenue DESC;

-- Stub visibility (should return 0 until Phase 2)
SELECT COUNT(*) AS competitor_rows FROM fact_competitor_bed_price;
SELECT COUNT(*) AS hw_rank_rows FROM fact_hw_search_rank;
