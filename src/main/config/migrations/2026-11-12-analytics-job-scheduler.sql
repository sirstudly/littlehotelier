-- Daily analytics sync schedules (processors poll job_scheduler, not wp_lh_scheduled_jobs).
-- Apply to each hostel backoffice DB:
--   wp_crh_backoffice, wp_hsh_backoffice, wp_rmb_backoffice, wp_lsh_backoffice
--
-- Defaults: yesterday only (TODAY-1). For backfill, enqueue the same job class with
-- explicit start_date / end_date via InsertJob or MCP insert_job.

INSERT INTO `job_scheduler` (`classname`, `repeat_time_minutes`, `repeat_daily_at`, `active_yn`, `last_run_date`, `created_date`, `last_updated_date`)
SELECT 'com.macbackpackers.jobs.SyncBookingCreatedAnalyticsJob', NULL, '06:00:00', 'Y', NULL, NOW(), NOW()
  FROM DUAL
 WHERE NOT EXISTS (
       SELECT 1 FROM `job_scheduler`
        WHERE `classname` = 'com.macbackpackers.jobs.SyncBookingCreatedAnalyticsJob' );

INSERT INTO `job_scheduler_param` (`job_id`, `name`, `value`)
SELECT j.job_id, 'start_date', 'TODAY-1'
  FROM `job_scheduler` j
 WHERE j.classname = 'com.macbackpackers.jobs.SyncBookingCreatedAnalyticsJob'
   AND NOT EXISTS (
       SELECT 1 FROM `job_scheduler_param` p
        WHERE p.job_id = j.job_id AND p.name = 'start_date' );

INSERT INTO `job_scheduler_param` (`job_id`, `name`, `value`)
SELECT j.job_id, 'end_date', 'TODAY-1'
  FROM `job_scheduler` j
 WHERE j.classname = 'com.macbackpackers.jobs.SyncBookingCreatedAnalyticsJob'
   AND NOT EXISTS (
       SELECT 1 FROM `job_scheduler_param` p
        WHERE p.job_id = j.job_id AND p.name = 'end_date' );

INSERT INTO `job_scheduler` (`classname`, `repeat_time_minutes`, `repeat_daily_at`, `active_yn`, `last_run_date`, `created_date`, `last_updated_date`)
SELECT 'com.macbackpackers.jobs.SyncChannelStayDayAnalyticsJob', NULL, '06:30:00', 'Y', NULL, NOW(), NOW()
  FROM DUAL
 WHERE NOT EXISTS (
       SELECT 1 FROM `job_scheduler`
        WHERE `classname` = 'com.macbackpackers.jobs.SyncChannelStayDayAnalyticsJob' );

INSERT INTO `job_scheduler_param` (`job_id`, `name`, `value`)
SELECT j.job_id, 'start_date', 'TODAY-1'
  FROM `job_scheduler` j
 WHERE j.classname = 'com.macbackpackers.jobs.SyncChannelStayDayAnalyticsJob'
   AND NOT EXISTS (
       SELECT 1 FROM `job_scheduler_param` p
        WHERE p.job_id = j.job_id AND p.name = 'start_date' );

INSERT INTO `job_scheduler_param` (`job_id`, `name`, `value`)
SELECT j.job_id, 'end_date', 'TODAY-1'
  FROM `job_scheduler` j
 WHERE j.classname = 'com.macbackpackers.jobs.SyncChannelStayDayAnalyticsJob'
   AND NOT EXISTS (
       SELECT 1 FROM `job_scheduler_param` p
        WHERE p.job_id = j.job_id AND p.name = 'end_date' );
