-- Phase 2 Edinburgh scrapers — apply ONLY to wp_crh_backoffice.
-- Jobs skip no-op on non-crh profiles if accidentally enqueued elsewhere.

INSERT INTO `job_scheduler` (`classname`, `repeat_time_minutes`, `repeat_daily_at`, `active_yn`, `last_run_date`, `created_date`, `last_updated_date`)
SELECT 'com.macbackpackers.jobs.SyncCompetitorBedPriceJob', NULL, '07:00:00', 'Y', NULL, NOW(), NOW()
  FROM DUAL
 WHERE NOT EXISTS (
       SELECT 1 FROM `job_scheduler`
        WHERE `classname` = 'com.macbackpackers.jobs.SyncCompetitorBedPriceJob' );

INSERT INTO `job_scheduler_param` (`job_id`, `name`, `value`)
SELECT j.job_id, 'horizon_days', '30'
  FROM `job_scheduler` j
 WHERE j.classname = 'com.macbackpackers.jobs.SyncCompetitorBedPriceJob'
   AND NOT EXISTS (
       SELECT 1 FROM `job_scheduler_param` p
        WHERE p.job_id = j.job_id AND p.name = 'horizon_days' );

INSERT INTO `job_scheduler_param` (`job_id`, `name`, `value`)
SELECT j.job_id, 'city', 'Edinburgh'
  FROM `job_scheduler` j
 WHERE j.classname = 'com.macbackpackers.jobs.SyncCompetitorBedPriceJob'
   AND NOT EXISTS (
       SELECT 1 FROM `job_scheduler_param` p
        WHERE p.job_id = j.job_id AND p.name = 'city' );

INSERT INTO `job_scheduler` (`classname`, `repeat_time_minutes`, `repeat_daily_at`, `active_yn`, `last_run_date`, `created_date`, `last_updated_date`)
SELECT 'com.macbackpackers.jobs.SyncHostelworldSearchRankJob', NULL, '07:15:00', 'Y', NULL, NOW(), NOW()
  FROM DUAL
 WHERE NOT EXISTS (
       SELECT 1 FROM `job_scheduler`
        WHERE `classname` = 'com.macbackpackers.jobs.SyncHostelworldSearchRankJob' );

INSERT INTO `job_scheduler_param` (`job_id`, `name`, `value`)
SELECT j.job_id, 'guests', '1'
  FROM `job_scheduler` j
 WHERE j.classname = 'com.macbackpackers.jobs.SyncHostelworldSearchRankJob'
   AND NOT EXISTS (
       SELECT 1 FROM `job_scheduler_param` p
        WHERE p.job_id = j.job_id AND p.name = 'guests' );

INSERT INTO `job_scheduler_param` (`job_id`, `name`, `value`)
SELECT j.job_id, 'nights', '1'
  FROM `job_scheduler` j
 WHERE j.classname = 'com.macbackpackers.jobs.SyncHostelworldSearchRankJob'
   AND NOT EXISTS (
       SELECT 1 FROM `job_scheduler_param` p
        WHERE p.job_id = j.job_id AND p.name = 'nights' );

INSERT INTO `job_scheduler_param` (`job_id`, `name`, `value`)
SELECT j.job_id, 'arrival_offset_days', '7'
  FROM `job_scheduler` j
 WHERE j.classname = 'com.macbackpackers.jobs.SyncHostelworldSearchRankJob'
   AND NOT EXISTS (
       SELECT 1 FROM `job_scheduler_param` p
        WHERE p.job_id = j.job_id AND p.name = 'arrival_offset_days' );

INSERT INTO `job_scheduler_param` (`job_id`, `name`, `value`)
SELECT j.job_id, 'city', 'Edinburgh'
  FROM `job_scheduler` j
 WHERE j.classname = 'com.macbackpackers.jobs.SyncHostelworldSearchRankJob'
   AND NOT EXISTS (
       SELECT 1 FROM `job_scheduler_param` p
        WHERE p.job_id = j.job_id AND p.name = 'city' );
