-- mac_analytics data warehouse + empty Metabase application database.
-- Run once on the shared MariaDB host, NOT on each wp_*_backoffice.
--
-- After creating users, store analyticsdb credentials in GCP Secret Manager as
-- analyticsdb_username / analyticsdb_password (same project as shareddb_*).
-- Metabase app DB credentials go in metabase/.env on the deploy host (not GCP).
--
-- Collations:
--   mac_analytics  → utf8mb4_general_ci (match backoffice)
--   metabase       → utf8mb4_unicode_ci (Metabase requirement)

CREATE DATABASE IF NOT EXISTS `mac_analytics`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

CREATE DATABASE IF NOT EXISTS `metabase`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `mac_analytics`;

CREATE TABLE IF NOT EXISTS `dim_property` (
  `property_code` varchar(16) NOT NULL,
  `display_name` varchar(128) NOT NULL,
  PRIMARY KEY (`property_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `dim_property` (`property_code`, `display_name`) VALUES
  ('crh', 'Castle Rock Hostel'),
  ('hsh', 'High Street Hostel'),
  ('rmb', 'Royal Mile Backpackers'),
  ('lsh', 'Lochside Hostel')
ON DUPLICATE KEY UPDATE `display_name` = VALUES(`display_name`);

-- Booked-date axis: one row per Cloudbeds reservation (folio grandTotal).
CREATE TABLE IF NOT EXISTS `fact_booking_created` (
  `property_code` varchar(16) NOT NULL,
  `reservation_id` varchar(64) NOT NULL,
  `booked_on` date NOT NULL,
  `booking_source` varchar(128) DEFAULT NULL,
  `status` varchar(64) DEFAULT NULL,
  `grand_total` decimal(12,2) NOT NULL DEFAULT 0.00,
  `checkin_date` date DEFAULT NULL,
  `checkout_date` date DEFAULT NULL,
  `first_seen_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_seen_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`property_code`, `reservation_id`),
  KEY `idx_fbc_booked_on` (`property_code`, `booked_on`),
  KEY `idx_fbc_source` (`property_code`, `booking_source`, `booked_on`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Stay-date axis: Channel Production (DI stock report 191) at day × source grain.
CREATE TABLE IF NOT EXISTS `fact_channel_stay_day` (
  `property_code` varchar(16) NOT NULL,
  `stay_date` date NOT NULL,
  `booking_source` varchar(128) NOT NULL,
  `source_category` varchar(128) DEFAULT NULL,
  `room_revenue` decimal(12,2) NOT NULL DEFAULT 0.00,
  `rooms_sold` int(11) NOT NULL DEFAULT 0,
  `adr` decimal(12,2) DEFAULT NULL,
  `synced_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`property_code`, `stay_date`, `booking_source`),
  KEY `idx_fcsd_stay` (`property_code`, `stay_date`),
  KEY `idx_fcsd_source` (`property_code`, `booking_source`, `stay_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Phase 2 stubs (empty until scrapers exist).
CREATE TABLE IF NOT EXISTS `fact_competitor_bed_price` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `property_code` varchar(16) NOT NULL,
  `competitor_name` varchar(255) NOT NULL,
  `stay_date` date NOT NULL,
  `lowest_bed_price` decimal(12,2) DEFAULT NULL,
  `currency` varchar(8) NOT NULL DEFAULT 'GBP',
  `source` varchar(64) NOT NULL DEFAULT 'ghs',
  `scraped_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fcbp` (`property_code`, `competitor_name`, `stay_date`, `source`),
  KEY `idx_fcbp_stay` (`property_code`, `stay_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `fact_hw_search_rank` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `property_code` varchar(16) NOT NULL,
  `search_date` date NOT NULL,
  `arrival_date` date NOT NULL,
  `query_label` varchar(255) NOT NULL,
  `position` int(11) DEFAULT NULL,
  `scraped_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fhsr` (`property_code`, `search_date`, `arrival_date`, `query_label`),
  KEY `idx_fhsr_search` (`property_code`, `search_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE OR REPLACE VIEW `v_booking_created_daily` AS
SELECT
  `property_code`,
  `booked_on`,
  `booking_source`,
  SUM(`grand_total`) AS `booking_value`,
  COUNT(*) AS `booking_count`
FROM `fact_booking_created`
GROUP BY `property_code`, `booked_on`, `booking_source`;

CREATE OR REPLACE VIEW `v_channel_stay_daily_total` AS
SELECT
  `property_code`,
  `stay_date`,
  SUM(`room_revenue`) AS `room_revenue`,
  SUM(`rooms_sold`) AS `rooms_sold`,
  CASE
    WHEN SUM(`rooms_sold`) > 0 THEN ROUND(SUM(`room_revenue`) / SUM(`rooms_sold`), 2)
    ELSE NULL
  END AS `adr`
FROM `fact_channel_stay_day`
GROUP BY `property_code`, `stay_date`;

-- Privileges: replace passwords before running in production.
-- CREATE USER IF NOT EXISTS 'analytics_rw'@'%' IDENTIFIED BY 'CHANGE_ME_RW';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON mac_analytics.* TO 'analytics_rw'@'%';
--
-- CREATE USER IF NOT EXISTS 'metabase_ro'@'%' IDENTIFIED BY 'CHANGE_ME_RO';
-- GRANT SELECT ON mac_analytics.* TO 'metabase_ro'@'%';
--
-- CREATE USER IF NOT EXISTS 'metabase_app'@'%' IDENTIFIED BY 'CHANGE_ME_APP';
-- GRANT ALL PRIVILEGES ON metabase.* TO 'metabase_app'@'%';
--
-- FLUSH PRIVILEGES;
