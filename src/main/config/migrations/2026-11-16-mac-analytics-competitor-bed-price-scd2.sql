-- mac_analytics: SCD2 versioning for Google Hotels competitor bed prices.
-- Each sync end-dates prior current rows (valid_to) for a stay_date and inserts a new snapshot
-- (valid_from, valid_to NULL). Charts use WHERE valid_to IS NULL.
-- Apply to database mac_analytics (MariaDB 10.11).

USE `mac_analytics`;

ALTER TABLE `fact_competitor_bed_price`
  ADD COLUMN IF NOT EXISTS `valid_from` timestamp NULL DEFAULT NULL
    COMMENT 'Version start (scrape time)' AFTER `scraped_at`,
  ADD COLUMN IF NOT EXISTS `valid_to` timestamp NULL DEFAULT NULL
    COMMENT 'NULL = current version' AFTER `valid_from`;

-- Existing rows become the current version as of their scrape time.
UPDATE `fact_competitor_bed_price`
   SET `valid_from` = COALESCE( `valid_from`, `scraped_at` )
 WHERE `valid_from` IS NULL;

ALTER TABLE `fact_competitor_bed_price`
  MODIFY COLUMN `valid_from` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Multiple versions per competitor×stay; drop the one-row unique key if present.
-- MariaDB has no DROP INDEX IF EXISTS on older builds — ignore duplicate/missing via procedure.
DROP PROCEDURE IF EXISTS `mac_analytics_drop_uk_fcbp`;
DELIMITER //
CREATE PROCEDURE `mac_analytics_drop_uk_fcbp`()
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'fact_competitor_bed_price'
       AND index_name = 'uk_fcbp'
  ) THEN
    ALTER TABLE `fact_competitor_bed_price` DROP INDEX `uk_fcbp`;
  END IF;
END //
DELIMITER ;
CALL `mac_analytics_drop_uk_fcbp`();
DROP PROCEDURE IF EXISTS `mac_analytics_drop_uk_fcbp`;

-- Idempotent secondary indexes
DROP PROCEDURE IF EXISTS `mac_analytics_add_fcbp_scd2_indexes`;
DELIMITER //
CREATE PROCEDURE `mac_analytics_add_fcbp_scd2_indexes`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'fact_competitor_bed_price'
       AND index_name = 'idx_fcbp_current'
  ) THEN
    ALTER TABLE `fact_competitor_bed_price`
      ADD KEY `idx_fcbp_current` (`property_code`, `source`, `stay_date`, `valid_to`);
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'fact_competitor_bed_price'
       AND index_name = 'idx_fcbp_hist'
  ) THEN
    ALTER TABLE `fact_competitor_bed_price`
      ADD KEY `idx_fcbp_hist` (`property_code`, `competitor_name`, `room_category`, `stay_date`, `valid_from`);
  END IF;
END //
DELIMITER ;
CALL `mac_analytics_add_fcbp_scd2_indexes`();
DROP PROCEDURE IF EXISTS `mac_analytics_add_fcbp_scd2_indexes`;
