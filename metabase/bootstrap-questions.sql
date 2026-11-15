-- Reference queries for Metabase native questions (paste into Metabase).
-- Prefer Metabase models + GUI once connected; use these when building native cards.
-- Default window: last 90 days. Wire hostel/date as Metabase field filters or dashboard filters.

-- =============================================================================
-- Booked-date performance
-- =============================================================================

-- Booking value by day × hostel (stacked area/line: series = hostel)
SELECT
  p.display_name AS hostel,
  v.booked_on AS booked_date,
  SUM(v.booking_value) AS booking_value
FROM v_booking_created_daily v
JOIN dim_property p ON p.property_code = v.property_code
WHERE v.booked_on >= CURDATE() - INTERVAL 90 DAY
GROUP BY p.display_name, v.booked_on
ORDER BY v.booked_on, hostel;

-- Booking value share by hostel (pie)
SELECT
  p.display_name AS hostel,
  SUM(v.booking_value) AS booking_value
FROM v_booking_created_daily v
JOIN dim_property p ON p.property_code = v.property_code
WHERE v.booked_on >= CURDATE() - INTERVAL 90 DAY
GROUP BY p.display_name
ORDER BY booking_value DESC;

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

-- Booking value by month × hostel (stacked bar; use ym for a single x-axis)
SELECT
  p.display_name AS hostel,
  CONCAT(YEAR(v.booked_on), '-', LPAD(MONTH(v.booked_on), 2, '0')) AS ym,
  SUM(v.booking_value) AS booking_value,
  SUM(v.booking_count) AS booking_count
FROM v_booking_created_daily v
JOIN dim_property p ON p.property_code = v.property_code
WHERE v.booked_on >= CURDATE() - INTERVAL 90 DAY
GROUP BY p.display_name, YEAR(v.booked_on), MONTH(v.booked_on)
ORDER BY YEAR(v.booked_on), MONTH(v.booked_on), hostel;

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

-- Booked-date channel mix × hostel (stacked bar: x=channel, series=hostel)
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

-- Room revenue by stay day × hostel (stacked area)
SELECT
  p.display_name AS hostel,
  v.stay_date,
  SUM(v.room_revenue) AS room_revenue
FROM v_channel_stay_daily_total v
JOIN dim_property p ON p.property_code = v.property_code
WHERE v.stay_date >= CURDATE() - INTERVAL 90 DAY
GROUP BY p.display_name, v.stay_date
ORDER BY v.stay_date, hostel;

-- Room revenue share by hostel (pie)
SELECT
  p.display_name AS hostel,
  SUM(v.room_revenue) AS room_revenue
FROM v_channel_stay_daily_total v
JOIN dim_property p ON p.property_code = v.property_code
WHERE v.stay_date >= CURDATE() - INTERVAL 90 DAY
GROUP BY p.display_name
ORDER BY room_revenue DESC;

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

-- Room revenue by month × hostel (stacked bar)
SELECT
  p.display_name AS hostel,
  CONCAT(YEAR(v.stay_date), '-', LPAD(MONTH(v.stay_date), 2, '0')) AS ym,
  SUM(v.room_revenue) AS room_revenue,
  SUM(v.rooms_sold) AS room_nights
FROM v_channel_stay_daily_total v
JOIN dim_property p ON p.property_code = v.property_code
WHERE v.stay_date >= CURDATE() - INTERVAL 90 DAY
GROUP BY p.display_name, YEAR(v.stay_date), MONTH(v.stay_date)
ORDER BY YEAR(v.stay_date), MONTH(v.stay_date), hostel;

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

-- Stay-date channel mix × hostel (stacked bar: x=channel, series=hostel)
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
-- Phase 2: Competitive position (GHS + Hostelworld)
-- =============================================================================

-- GHS dorm heatmap grain: stay_date × competitor (own + market)
SELECT
  stay_date,
  competitor_name,
  is_own,
  lowest_bed_price
FROM fact_competitor_bed_price
WHERE source = 'ghs'
  AND room_category = 'dorm'
  AND stay_date >= CURDATE()
  AND stay_date < CURDATE() + INTERVAL 30 DAY
ORDER BY stay_date, competitor_name;

-- GHS private heatmap grain (when private rows exist)
SELECT
  stay_date,
  competitor_name,
  is_own,
  lowest_bed_price
FROM fact_competitor_bed_price
WHERE source = 'ghs'
  AND room_category = 'private'
  AND stay_date >= CURDATE()
  AND stay_date < CURDATE() + INTERVAL 30 DAY
ORDER BY stay_date, competitor_name;

-- Our lowest dorm vs cheapest competitor dorm (by stay_date)
SELECT
  stay_date,
  MIN(CASE WHEN is_own = 1 THEN lowest_bed_price END) AS our_lowest_dorm,
  MIN(CASE WHEN is_own = 0 THEN lowest_bed_price END) AS competitor_min_dorm
FROM fact_competitor_bed_price
WHERE source = 'ghs'
  AND room_category = 'dorm'
  AND stay_date >= CURDATE()
  AND stay_date < CURDATE() + INTERVAL 30 DAY
GROUP BY stay_date
ORDER BY stay_date;

-- Hostelworld search rank time series (CRH / HSH / RMB)
SELECT
  p.display_name AS hostel,
  f.search_date,
  f.arrival_date,
  f.query_label,
  f.position
FROM fact_hw_search_rank f
JOIN dim_property p ON p.property_code = f.property_code
WHERE f.search_date >= CURDATE() - INTERVAL 90 DAY
ORDER BY f.search_date, hostel;
