-- Phase 2 analytics: GHS competitor prices + Hostelworld search rank.
-- Apply on MariaDB database mac_analytics (not each wp_*_backoffice).
-- Scheduler seed for CRH only: see 2026-11-15-analytics-phase2-job-scheduler-crh.sql

USE `mac_analytics`;

-- Edinburgh market context for GHS competitor rows (not a processor profile).
INSERT INTO `dim_property` (`property_code`, `display_name`) VALUES
  ('edn', 'Edinburgh market')
ON DUPLICATE KEY UPDATE `display_name` = VALUES(`display_name`);

CREATE TABLE IF NOT EXISTS `dim_property_match` (
  `property_code` varchar(16) NOT NULL,
  `display_name` varchar(128) NOT NULL,
  `hw_hostel_number` varchar(32) DEFAULT NULL,
  `name_aliases` varchar(512) DEFAULT NULL COMMENT 'Comma-separated lowercase aliases for GHS/HW name match',
  `edinburgh_yn` char(1) NOT NULL DEFAULT 'N',
  PRIMARY KEY (`property_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `dim_property_match`
  (`property_code`, `display_name`, `hw_hostel_number`, `name_aliases`, `edinburgh_yn`) VALUES
  ('crh', 'Castle Rock Hostel', '551',
   'castle rock hostel,castle rock,castlerock', 'Y'),
  ('hsh', 'High Street Hostel', '552',
   'high street hostel,high street,highstreet hostel', 'Y'),
  ('rmb', 'Royal Mile Backpackers', '11242',
   'royal mile backpackers,royal mile,royal mile hostel', 'Y')
ON DUPLICATE KEY UPDATE
  `display_name` = VALUES(`display_name`),
  `hw_hostel_number` = VALUES(`hw_hostel_number`),
  `name_aliases` = VALUES(`name_aliases`),
  `edinburgh_yn` = VALUES(`edinburgh_yn`);

-- Rebuild competitor unique key with room_category + is_own (idempotent-ish).
ALTER TABLE `fact_competitor_bed_price`
  ADD COLUMN IF NOT EXISTS `room_category` varchar(16) NOT NULL DEFAULT 'dorm'
    COMMENT 'dorm|private' AFTER `competitor_name`,
  ADD COLUMN IF NOT EXISTS `is_own` tinyint(1) NOT NULL DEFAULT 0
    COMMENT '1 if listing matched one of our Edinburgh hostels' AFTER `source`;

-- Recreate unique key to include room_category (ignore error if already updated).
ALTER TABLE `fact_competitor_bed_price` DROP INDEX `uk_fcbp`;
ALTER TABLE `fact_competitor_bed_price`
  ADD UNIQUE KEY `uk_fcbp` (`property_code`, `competitor_name`, `room_category`, `stay_date`, `source`);

ALTER TABLE `fact_hw_search_rank`
  ADD COLUMN IF NOT EXISTS `result_count` int(11) DEFAULT NULL AFTER `position`,
  ADD COLUMN IF NOT EXISTS `page_url` varchar(1024) DEFAULT NULL AFTER `result_count`;
