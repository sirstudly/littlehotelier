-- Reference queries for Metabase native questions (paste into Metabase).
-- Prefer Metabase models + GUI once connected; use these when building native cards.
-- Default window: last 90 days. Wire hostel/date as Metabase field filters or dashboard filters.

-- =============================================================================
-- Booked-date performance
-- =============================================================================

-- Booking value by day (line chart: sum booking_value by booked_date)
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

-- Booking value by ISO week, grouped under year/month (table or bar)
SELECT
  p.display_name AS hostel,
  YEAR(v.booked_on) AS yr,
  MONTH(v.booked_on) AS mo,
  WEEK(v.booked_on, 3) AS iso_week,
  SUM(v.booking_value) AS booking_value,
  SUM(v.booking_count) AS booking_count
FROM v_booking_created_daily v
JOIN dim_property p ON p.property_code = v.property_code
WHERE v.booked_on >= CURDATE() - INTERVAL 90 DAY
GROUP BY p.display_name, YEAR(v.booked_on), MONTH(v.booked_on), WEEK(v.booked_on, 3)
ORDER BY yr, mo, iso_week, hostel;

-- Booking value by month (bar)
SELECT
  p.display_name AS hostel,
  YEAR(v.booked_on) AS yr,
  MONTH(v.booked_on) AS mo,
  SUM(v.booking_value) AS booking_value,
  SUM(v.booking_count) AS booking_count
FROM v_booking_created_daily v
JOIN dim_property p ON p.property_code = v.property_code
WHERE v.booked_on >= CURDATE() - INTERVAL 90 DAY
GROUP BY p.display_name, YEAR(v.booked_on), MONTH(v.booked_on)
ORDER BY yr, mo, hostel;

-- Booking value by day of week (bar; 1=Sunday .. 7=Saturday in MariaDB)
SELECT
  p.display_name AS hostel,
  DAYOFWEEK(v.booked_on) AS dow,
  DAYNAME(v.booked_on) AS day_name,
  SUM(v.booking_value) AS booking_value,
  SUM(v.booking_count) AS booking_count
FROM v_booking_created_daily v
JOIN dim_property p ON p.property_code = v.property_code
WHERE v.booked_on >= CURDATE() - INTERVAL 90 DAY
GROUP BY p.display_name, DAYOFWEEK(v.booked_on), DAYNAME(v.booked_on)
ORDER BY dow, hostel;

-- Booked-date channel mix (stacked bar or pie)
SELECT
  p.display_name AS hostel,
  v.booking_source AS channel,
  SUM(v.booking_value) AS booking_value,
  SUM(v.booking_count) AS booking_count
FROM v_booking_created_daily v
JOIN dim_property p ON p.property_code = v.property_code
WHERE v.booked_on >= CURDATE() - INTERVAL 90 DAY
GROUP BY p.display_name, v.booking_source
ORDER BY booking_value DESC;

-- =============================================================================
-- Stay-date production (matches Cloudbeds Channel Production — nights occupied)
-- =============================================================================

-- Room revenue by stay day (line)
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

-- Room revenue by ISO week, grouped under year/month
SELECT
  p.display_name AS hostel,
  YEAR(v.stay_date) AS yr,
  MONTH(v.stay_date) AS mo,
  WEEK(v.stay_date, 3) AS iso_week,
  SUM(v.room_revenue) AS room_revenue,
  SUM(v.rooms_sold) AS room_nights
FROM v_channel_stay_daily_total v
JOIN dim_property p ON p.property_code = v.property_code
WHERE v.stay_date >= CURDATE() - INTERVAL 90 DAY
GROUP BY p.display_name, YEAR(v.stay_date), MONTH(v.stay_date), WEEK(v.stay_date, 3)
ORDER BY yr, mo, iso_week, hostel;

-- Room revenue by month (bar)
SELECT
  p.display_name AS hostel,
  YEAR(v.stay_date) AS yr,
  MONTH(v.stay_date) AS mo,
  SUM(v.room_revenue) AS room_revenue,
  SUM(v.rooms_sold) AS room_nights
FROM v_channel_stay_daily_total v
JOIN dim_property p ON p.property_code = v.property_code
WHERE v.stay_date >= CURDATE() - INTERVAL 90 DAY
GROUP BY p.display_name, YEAR(v.stay_date), MONTH(v.stay_date)
ORDER BY yr, mo, hostel;

-- Room revenue by day of week
SELECT
  p.display_name AS hostel,
  DAYOFWEEK(v.stay_date) AS dow,
  DAYNAME(v.stay_date) AS day_name,
  SUM(v.room_revenue) AS room_revenue,
  SUM(v.rooms_sold) AS room_nights
FROM v_channel_stay_daily_total v
JOIN dim_property p ON p.property_code = v.property_code
WHERE v.stay_date >= CURDATE() - INTERVAL 90 DAY
GROUP BY p.display_name, DAYOFWEEK(v.stay_date), DAYNAME(v.stay_date)
ORDER BY dow, hostel;

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

-- =============================================================================
-- Phase 2 stubs (should return 0 until scrapers land)
-- =============================================================================
SELECT COUNT(*) AS competitor_rows FROM fact_competitor_bed_price;
SELECT COUNT(*) AS hw_rank_rows FROM fact_hw_search_rank;
