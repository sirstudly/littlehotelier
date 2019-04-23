
CREATE TABLE `wp_lh_calendar` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `job_id` bigint(20) unsigned DEFAULT NULL,
  `room_id` varchar(255) DEFAULT NULL,
  `room_type_id` int(10) unsigned DEFAULT NULL,
  `room` varchar(50) NOT NULL,
  `bed_name` varchar(50) DEFAULT NULL,
  `reservation_id` bigint(20) unsigned DEFAULT NULL,
  `guest_name` varchar(255) DEFAULT NULL,
  `checkin_date` datetime NOT NULL,
  `checkout_date` datetime NOT NULL,
  `payment_total` decimal(10,2) DEFAULT NULL,
  `payment_outstanding` decimal(10,2) DEFAULT NULL,
  `rate_plan_name` varchar(255) DEFAULT NULL,
  `payment_status` varchar(50) DEFAULT NULL,
  `num_guests` int(10) unsigned DEFAULT NULL,
  `data_href` varchar(255) DEFAULT NULL,
  `lh_status` varchar(50) DEFAULT NULL,
  `booking_reference` varchar(50) DEFAULT NULL,
  `booking_source` varchar(50) DEFAULT NULL,
  `booked_date` timestamp NULL DEFAULT NULL,
  `eta` varchar(50) DEFAULT NULL,
  `notes` text,
  `viewed_yn` char(1) DEFAULT NULL,
  `created_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `lh_c_checkin` (`job_id`,`checkin_date`),
  KEY `lh_c_checkout` (`job_id`,`checkout_date`),
  KEY `lh_c_jobid` (`job_id`),
  KEY `lh_c_jobid_reservationid` (`job_id`,`reservation_id`),
  KEY `lh_c_roomid` (`job_id`,`room_id`),
  KEY `lh_c_booking_ref` (`booking_reference`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;


CREATE TABLE `wp_lh_jobs` (
  `job_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `classname` varchar(255) NOT NULL,
  `status` varchar(20) NOT NULL,
  `start_date` timestamp NULL DEFAULT NULL,
  `end_date` timestamp NULL DEFAULT NULL,
  `processed_by` varchar(255) NULL,
  `created_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_updated_date` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`job_id`),
  KEY `lh_j_classname` (`classname`),
  KEY `lh_j_class_status` (`classname`, `status`) 
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;


CREATE TABLE `wp_lh_job_param` (
  `job_param_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `job_id` bigint(20) unsigned NOT NULL,
  `name` varchar(255) NOT NULL,
  `value` varchar(255) NOT NULL,
  PRIMARY KEY (`job_param_id`),
  KEY `lh_j_job_id` (`job_id`)
--  FOREIGN KEY (`job_id`) REFERENCES `wp_lh_jobs`(`job_id`)  -- removed cause of hibernate
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;


CREATE TABLE `wp_lh_scheduled_jobs` (
  `job_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `classname` varchar(255) NOT NULL,
  `cron_schedule` varchar(255) NOT NULL,
  `active_yn` char(1) DEFAULT 'Y',
  `last_scheduled_date` timestamp NULL DEFAULT NULL,
  `last_updated_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`job_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

CREATE TABLE `wp_lh_scheduled_job_param` (
  `job_param_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `job_id` bigint(20) unsigned NOT NULL,
  `name` varchar(255) NOT NULL,
  `value` varchar(255) NOT NULL,
  PRIMARY KEY (`job_param_id`)
--  FOREIGN KEY (`job_id`) REFERENCES `wp_lh_scheduled_jobs`(`id`) -- removed cause of hibernate
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;


CREATE TABLE `job_scheduler` (
  `job_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `classname` varchar(255) NOT NULL,
  `repeat_time_minutes` int(10) unsigned DEFAULT NULL,
  `repeat_daily_at` varchar(8) NULL DEFAULT NULL, -- 24 hour clock e.g. 23:00:00 
  `active_yn` char(1) DEFAULT NULL,
  `last_run_date` timestamp NULL DEFAULT NULL,
  `created_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_updated_date` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`job_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

CREATE TABLE `job_scheduler_param` (
  `job_param_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `job_id` bigint(20) unsigned NOT NULL,
  `name` varchar(255) NOT NULL,
  `value` varchar(255) NOT NULL,
  PRIMARY KEY (`job_param_id`)
--  FOREIGN KEY (`job_id`) REFERENCES `job_scheduler`(`job_id`) -- removed cause of hibernate
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

CREATE TABLE `wp_lh_job_dependency` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `job_id` bigint(20) unsigned NOT NULL,
  `depends_on_job_id` bigint(20) unsigned NOT NULL,
  PRIMARY KEY (`id`),
  KEY `lh_jd_job_id` (`job_id`)
--  FOREIGN KEY (`job_id`) REFERENCES `wp_lh_jobs`(`job_id`)  -- removed cause of hibernate
--  FOREIGN KEY (`depends_on_job_id`) REFERENCES `wp_lh_jobs`(`job_id`)  -- removed cause of hibernate
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;


-- reporting table for reservations split across multiple rooms of the same type
CREATE TABLE `wp_lh_rpt_split_rooms` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `job_id` bigint(20) unsigned NOT NULL,
  `reservation_id` bigint(20) unsigned DEFAULT NULL,
  `guest_name` TEXT,
  `checkin_date` datetime NOT NULL,
  `checkout_date` datetime NOT NULL,
  `data_href` varchar(255) DEFAULT NULL,
  `lh_status` varchar(50) DEFAULT NULL,
  `booking_reference` varchar(50) DEFAULT NULL,
  `booking_source` varchar(50) DEFAULT NULL,
  `booked_date` timestamp NULL DEFAULT NULL,
  `eta` varchar(50) DEFAULT NULL,
  `notes` text,
  `viewed_yn` char(1) DEFAULT NULL,
  `created_date` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `job_id_idx` (`job_id`),
  FOREIGN KEY (`job_id`) REFERENCES `wp_lh_jobs`(`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- bookings where no deposit had been paid yet
CREATE TABLE `wp_lh_rpt_unpaid_deposit` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `job_id` bigint(20) unsigned NOT NULL,
  `guest_name` TEXT,
  `checkin_date` datetime NOT NULL,
  `checkout_date` datetime NOT NULL,
  `payment_total` decimal(10,2) DEFAULT NULL,
  `data_href` varchar(255) DEFAULT NULL,
  `booking_reference` varchar(50) DEFAULT NULL,
  `booking_source` varchar(50) DEFAULT NULL,
  `booked_date` timestamp NULL DEFAULT NULL,
  `notes` text,
  `viewed_yn` char(1) DEFAULT NULL,
  `created_date` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `job_id_idx` (`job_id`),
  FOREIGN KEY (`job_id`) REFERENCES `wp_lh_jobs`(`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `wp_lh_group_bookings` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `job_id` bigint(20) unsigned NOT NULL,
  `reservation_id` bigint(20) unsigned DEFAULT NULL,
  `guest_name` TEXT,
  `booking_reference` varchar(50) DEFAULT NULL,
  `booking_source` varchar(50) DEFAULT NULL,
  `checkin_date` datetime NOT NULL,
  `checkout_date` datetime NOT NULL,
  `booked_date` timestamp NULL DEFAULT NULL,
  `payment_outstanding` decimal(10,2) DEFAULT NULL,
  `data_href` varchar(255) DEFAULT NULL,
  `num_guests` int(10) unsigned NOT NULL DEFAULT '0',
  `notes` text,
  `viewed_yn` char(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `job_id_idx` (`job_id`),
  FOREIGN KEY (`job_id`) REFERENCES `wp_lh_jobs`(`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


CREATE TABLE `wp_lh_rpt_guest_comments` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `reservation_id` bigint(20) unsigned DEFAULT NULL,
  `comments` text,
  `acknowledged_date` timestamp NULL DEFAULT NULL,
  `created_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `lh_rpt_gc_reservation` (`reservation_id`),
  UNIQUE (`reservation_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;


CREATE TABLE `wp_pxpost_transaction` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `booking_reference` varchar(255) NOT NULL,
  `job_id` bigint(20) unsigned DEFAULT NULL,
  `post_date` timestamp NULL DEFAULT NULL,
  `masked_card_number` varchar(255) DEFAULT NULL,
  `payment_amount` decimal(10,2) NOT NULL,
  `payment_request_xml` text DEFAULT NULL,
  `payment_response_http_code` smallint(6) unsigned DEFAULT NULL,
  `payment_response_xml` text DEFAULT NULL,
  `payment_status_response_xml` text DEFAULT NULL,
  `successful` tinyint(3) unsigned DEFAULT NULL,
  `help_text` varchar(255) DEFAULT NULL, -- reason why txn failed
  `created_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_updated_date` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `job_id_idx` (`job_id`)
) ENGINE=InnoDB AUTO_INCREMENT=200000 DEFAULT CHARSET=utf8;

CREATE TABLE `wp_lh_send_email` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `email` varchar(255) NOT NULL,
  `first_name` varchar(255) DEFAULT NULL,
  `last_name` varchar(255) DEFAULT NULL,
  `send_date` timestamp NULL DEFAULT NULL,
  `send_subject` varchar(255) DEFAULT NULL,
  `send_body` text DEFAULT NULL,
  `created_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_updated_date` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

CREATE TABLE `wp_lh_rooms` (
  `id` varchar(255) NOT NULL,
  `room` varchar(50) DEFAULT NULL,
  `bed_name` varchar(50) DEFAULT NULL,
  `capacity` smallint(6) DEFAULT NULL,
  `room_type_id` int(10) unsigned DEFAULT NULL,
  `room_type` varchar(45) DEFAULT NULL,
  `active_yn` char(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `lh_r_idx` (`room`,`bed_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- used for analytic reporting
CREATE TABLE `rpt_bookings` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `job_id` bigint(20) unsigned NOT NULL,
  `reservation_id` bigint(20) unsigned DEFAULT NULL,
  `guest_name` varchar(255) DEFAULT NULL,
  `checkin_date` datetime NOT NULL,
  `checkout_date` datetime NOT NULL,
  `booking_reference` varchar(50) DEFAULT NULL,
  `booking_source` varchar(50) DEFAULT NULL,
  `country` varchar(50) DEFAULT NULL,
  `payment_total` decimal(10,2) DEFAULT NULL,
  `paid_value` decimal(10,2) DEFAULT NULL,
  `num_guests` int(10) unsigned DEFAULT NULL,
  `booked_date` timestamp NULL DEFAULT NULL,
  `created_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `rpt_bookings_checkin` (`job_id`,`checkin_date`),
  KEY `rpt_bookings_checkout` (`job_id`,`checkout_date`),
  KEY `rpt_bookings_job_id` (`job_id`),
  KEY `rpt_bookings_job_id_reservationid` (`job_id`,`reservation_id`),
  KEY `rpt_bookings_job_id_bookingref` (`booking_reference`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

-- If you're dumping the table from wp_lh_calendar; then you'll need to fill these in manually

 /***  START CRH *********
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112612-0', '11', '01- Night &amp; Day', 10, 112612, 'LT_FEMALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112612-1', '11', '02- Tall &amp; Short', 10, 112612, 'LT_FEMALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112612-2', '11', '03- Up &amp; Down', 10, 112612, 'LT_FEMALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112612-3', '11', '04- Ebony &amp; Ivory', 10, 112612, 'LT_FEMALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112612-4', '11', '05- Love &amp; Hate', 10, 112612, 'LT_FEMALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112612-5', '11', '06- Sex &amp; Chastity', 10, 112612, 'LT_FEMALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112612-6', '11', '07- Porsche &amp; Lada', 10, 112612, 'LT_FEMALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112612-7', '11', '08- Lust &amp; Rage', 10, 112612, 'LT_FEMALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112612-8', '11', '09- SYHA &amp; Backpacker', 10, 112612, 'LT_FEMALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112612-9', '11', '10- Drunk &amp; Sober', 10, 112612, 'LT_FEMALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112612-10', '12', '01- Pumpy', 10, 112612, 'LT_FEMALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112612-11', '12', '02- Pukie', 10, 112612, 'LT_FEMALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112612-12', '12', '03- Spanky', 10, 112612, 'LT_FEMALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112612-13', '12', '04- Burpy', 10, 112612, 'LT_FEMALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112612-14', '12', '05- Snow White', 10, 112612, 'LT_FEMALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112612-15', '12', '06- Touchy Feely', 10, 112612, 'LT_FEMALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112612-16', '12', '07- Pished', 10, 112612, 'LT_FEMALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112612-17', '12', '08- Poopy', 10, 112612, 'LT_FEMALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112612-18', '12', '09- Pervy', 10, 112612, 'LT_FEMALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112612-19', '12', '10- Pimp', 10, 112612, 'LT_FEMALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112612', 'Unallocated', null, 10, 112612, 'LT_FEMALE', 'N' );

INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112619-0', '10', '01- Rubik''s Cube', 10, 112619, 'LT_MALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112619-1', '10', '02- Jigsaw', 10, 112619, 'LT_MALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112619-2', '10', '03- Riddle', 10, 112619, 'LT_MALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112619-3', '10', '04- Maze', 10, 112619, 'LT_MALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112619-4', '10', '05- Life', 10, 112619, 'LT_MALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112619-5', '10', '06- Relationships', 10, 112619, 'LT_MALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112619-6', '10', '07- Bermuda Triangle', 10, 112619, 'LT_MALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112619-7', '10', '08- Should I Stay?', 10, 112619, 'LT_MALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112619-8', '10', '09- Labryinth', 10, 112619, 'LT_MALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112619-9', '10', '10- Missing Sock', 10, 112619, 'LT_MALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112619', 'Unallocated', null, 10, 112619, 'LT_MALE', 'N' );

INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112620-0', '13', '01- Coconut', 8, 112620, 'LT_MIXED', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112620-1', '13', '02- Peanut', 8, 112620, 'LT_MIXED', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112620-2', '13', '03- Walnut', 8, 112620, 'LT_MIXED', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112620-3', '13', '04- Michael Jackson', 8, 112620, 'LT_MIXED', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112620-4', '13', '05- Cashew', 8, 112620, 'LT_MIXED', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112620-5', '13', '06- Hazelnut', 8, 112620, 'LT_MIXED', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112620-6', '13', '07- Brazil Nut', 8, 112620, 'LT_MIXED', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112620-7', '13', '08- Pecan', 8, 112620, 'LT_MIXED', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112620', 'Unallocated', null, 8, 112620, 'LT_MIXED', 'N' );

INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112621-0', '14', '01- Last Drop', 14, 112621, 'LT_MALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112621-1', '14', '02- Maggie Dickson''s', 14, 112621, 'LT_MALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112621-2', '14', '03- Biddy Mulligans', 14, 112621, 'LT_MALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112621-3', '14', '04- Green Tree', 14, 112621, 'LT_MALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112621-4', '14', '05- Whistle Binkie''s', 14, 112621, 'LT_MALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112621-5', '14', '06- Bannermans', 14, 112621, 'LT_MALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112621-6', '14', '07- Jocks', 14, 112621, 'LT_MALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112621-7', '14', '08- Deacon Brodie''s', 14, 112621, 'LT_MALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112621-8', '14', '09- Mitre', 14, 112621, 'LT_MALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112621-9', '14', '10- Scotsman', 14, 112621, 'LT_MALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112621-10', '14', '11- Sneaky Pete''s', 14, 112621, 'LT_MALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112621-11', '14', '12- Royal Mile', 14, 112621, 'LT_MALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112621-12', '14', '13- World''s End', 14, 112621, 'LT_MALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112621-13', '14', '14- Aulde Hoose', 14, 112621, 'LT_MALE', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112621', 'Unallocated', null, 14, 112621, 'LT_MALE', 'N' );

update wp_lh_rooms set capacity = 10, room_type = 'MX' where room in (21,22,44,45,47) or id = 112014;
update wp_lh_rooms set capacity = 12, room_type = 'F' where room in (42,43) or id = 112174;
update wp_lh_rooms set capacity = 12, room_type = 'MX' where room in (20,41,46,51) or id = 112188;
update wp_lh_rooms set capacity = 14, room_type = 'MX' where room in (25) or id = 112528;
update wp_lh_rooms set capacity = 16, room_type = 'MX' where room in (23) or id = 112559;
update wp_lh_rooms set capacity = 4, room_type = 'F' where room in (75,76) or id = 112562;
update wp_lh_rooms set capacity = 4, room_type = 'MX' where room in (61,62,63,64) or id = 112564;
update wp_lh_rooms set capacity = 6, room_type = 'MX' where room in (48) or id = 112583;
update wp_lh_rooms set capacity = 8, room_type = 'MX' where room in (24,49) or id = 112591;
update wp_lh_rooms set capacity = 2, room_type = 'DBL' where room in (53,54,55,71,72,73,78,79) or id = 112600;
update wp_lh_rooms set capacity = 4, room_type = 'QUAD' where room in (56,57,74) or id = 112601;
update wp_lh_rooms set capacity = 3, room_type = 'TRIPLE' where room in (58,65,77) or id = 112602;

*************** END CRH ***************/

 /***  START HSH *********
 
INSERT INTO wp_lh_rooms(id, room_type_id, room, bed_name)
SELECT DISTINCT room_id, room_type_id, room, bed_name
  FROM wp_lh_calendar WHERE job_id = 383741
  AND room_id is not null
  order by room, bed_name;

UPDATE wp_lh_rooms
   SET capacity = 2, room_type = 'TWIN', active_yn = 'Y' WHERE room IN ( 'TA', 'TB' );
UPDATE wp_lh_rooms
   SET capacity = 2, room_type = 'DBL', active_yn = 'Y' WHERE room IN ( 'TC' );
UPDATE wp_lh_rooms
   SET capacity = 18, room_type = 'MX', active_yn = 'Y' WHERE room IN ( '6R' );
UPDATE wp_lh_rooms
   SET capacity = 4, room_type = 'F', active_yn = 'Y' WHERE room IN ( '5A' );
UPDATE wp_lh_rooms
   SET capacity = 4, room_type = 'MX', active_yn = 'Y' WHERE room IN ( '5C', '6P', 'LOTR', 'T3M', 'TMNT' );
UPDATE wp_lh_rooms
   SET capacity = 6, room_type = 'MX', active_yn = 'Y' WHERE room IN ( '3E', '5F' );
UPDATE wp_lh_rooms
   SET capacity = 8, room_type = 'F', active_yn = 'Y' WHERE room IN ( '3A' );
UPDATE wp_lh_rooms
   SET capacity = 10, room_type = 'F', active_yn = 'Y' WHERE room IN ( '3C', '3D' );
UPDATE wp_lh_rooms
   SET capacity = 8, room_type = 'MX', active_yn = 'Y' WHERE room IN ( '5B', '5D', '5E' );
UPDATE wp_lh_rooms
   SET capacity = 10, room_type = 'MX', active_yn = 'Y' WHERE room IN ( '3B', '5G' );
UPDATE wp_lh_rooms
   SET capacity = 8, room_type = 'LT_FEMALE', active_yn = 'Y' WHERE room IN ( 'F&V' );
UPDATE wp_lh_rooms
   SET capacity = 16, room_type = 'MX', active_yn = 'Y' WHERE room IN ( 'Zoo' );

-- insert 'Unallocated' rows with room_type_id as PK
INSERT INTO wp_lh_rooms(id, room_type_id, room, bed_name, active_yn)
SELECT DISTINCT room_type_id, room_type_id, 'Unallocated', null, 'N'
  FROM wp_lh_calendar WHERE job_id = 383741
  AND room_id is null
  order by room, bed_name;

-- find out which room types correspond to each "Unallocated" row
-- select * from wp_lh_rooms order by room_type_id, room;
UPDATE wp_lh_rooms
   SET capacity = 2, room_type = 'TWIN', active_yn = 'N' WHERE id = '111743';
UPDATE wp_lh_rooms
   SET capacity = 2, room_type = 'DBL', active_yn = 'N' WHERE id = '111744';
UPDATE wp_lh_rooms
   SET capacity = 4, room_type = 'F', active_yn = 'N' WHERE id = '111745';
UPDATE wp_lh_rooms
   SET capacity = 4, room_type = 'MX', active_yn = 'N' WHERE id = '111746';
UPDATE wp_lh_rooms
   SET capacity = 6, room_type = 'MX', active_yn = 'N' WHERE id = '111747';
UPDATE wp_lh_rooms
   SET capacity = 8, room_type = 'F', active_yn = 'N' WHERE id = '111748';
UPDATE wp_lh_rooms
   SET capacity = 10, room_type = 'F', active_yn = 'N' WHERE id = '111752';
UPDATE wp_lh_rooms
   SET capacity = 8, room_type = 'MX', active_yn = 'N' WHERE id = '111754';
UPDATE wp_lh_rooms
   SET capacity = 10, room_type = 'MX', active_yn = 'N' WHERE id = '111755';
UPDATE wp_lh_rooms
   SET capacity = 18, room_type = 'MX', active_yn = 'N' WHERE id = '111756';

-- Staff rooms have no records so insert manually
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111758-0', 'Zoo', '01AnteaterT', 16, 111758, 'LT_MIXED', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111758-1', 'Zoo', '02BoaB', 16, 111758, 'LT_MIXED', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111758-2', 'Zoo', '03CheetahT', 16, 111758, 'LT_MIXED', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111758-3', 'Zoo', '04DingoB', 16, 111758, 'LT_MIXED', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111758-4', 'Zoo', '05ElephantT', 16, 111758, 'LT_MIXED', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111758-5', 'Zoo', '06FerretB', 16, 111758, 'LT_MIXED', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111758-6', 'Zoo', '07GiraffeT', 16, 111758, 'LT_MIXED', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111758-7', 'Zoo', '08HippoB', 16, 111758, 'LT_MIXED', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111758-8', 'Zoo', '09IguanaT', 16, 111758, 'LT_MIXED', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111758-9', 'Zoo', '10JackalB', 16, 111758, 'LT_MIXED', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111758-10', 'Zoo', '11KraitT', 16, 111758, 'LT_MIXED', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111758-11', 'Zoo', '12LionB', 16, 111758, 'LT_MIXED', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111758-12', 'Zoo', '13MonkeyT', 16, 111758, 'LT_MIXED', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111758-13', 'Zoo', '14OrangutanB', 16, 111758, 'LT_MIXED', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111758-14', 'Zoo', '15PeackockT', 16, 111758, 'LT_MIXED', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111758-15', 'Zoo', '16QuokkaB', 16, 111758, 'LT_MIXED', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111758', 'Unallocated', null, 16, 111758, 'LT_MIXED', 'N');

INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111757-0', 'F&V', '01ApricotT', 8, 111757, 'LT_FEMALE', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111757-1', 'F&V', '02BroccoliB', 8, 111757, 'LT_FEMALE', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111757-2', 'F&V', '03CoconutT', 8, 111757, 'LT_FEMALE', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111757-3', 'F&V', '04DamsonB', 8, 111757, 'LT_FEMALE', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111757-4', 'F&V', '05EggplantT', 8, 111757, 'LT_FEMALE', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111757-5', 'F&V', '06FigB', 8, 111757, 'LT_FEMALE', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111757-6', 'F&V', '07GherkinT', 8, 111757, 'LT_FEMALE', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111757-7', 'F&V', '08HaggisB', 8, 111757, 'LT_FEMALE', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('111757', 'Unallocated', null, 8, 111757, 'LT_FEMALE', 'N');

*************** END HSH ***************/

 /***  START RMB *********
 
UPDATE wp_lh_rooms
   SET capacity = 10, room_type = 'F', active_yn = 'Y' WHERE room IN ( 'LM' );
UPDATE wp_lh_rooms
   SET capacity = 4, room_type = 'MX', active_yn = 'Y' WHERE room IN ( 'T' );
UPDATE wp_lh_rooms
   SET capacity = 8, room_type = 'MX', active_yn = 'Y' WHERE room IN ( 'ME', 'GC', 'HW' );

-- unallocated
UPDATE `wp_lh_rooms` SET `room_type`='F', `capacity`='10' WHERE `id`='112202';
UPDATE `wp_lh_rooms` SET `room_type`='MX', `capacity`='8' WHERE `id`='112210';
UPDATE `wp_lh_rooms` SET `room_type`='MX', `capacity`='4' WHERE `id`='112211';

-- staff room
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112212-0', 'SW', '01 Darth Vader', 8, 112212, 'LT_MIXED', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112212-1', 'SW', '02 Yoda', 8, 112212, 'LT_MIXED', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112212-2', 'SW', '03 R2D2', 8, 112212, 'LT_MIXED', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112212-3', 'SW', '04 C3PO', 8, 112212, 'LT_MIXED', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112212-4', 'SW', '05 Han Solo', 8, 112212, 'LT_MIXED', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112212-5', 'SW', '06 Chewbacca', 8, 112212, 'LT_MIXED', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112212-6', 'SW', '07 Luke', 8, 112212, 'LT_MIXED', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112212-7', 'SW', '08 Leia', 8, 112212, 'LT_MIXED', 'Y' );
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('112212', 'Unallocated', null, 8, 112212, 'LT_MIXED', 'N' );

*************** END RMB ***************/
 
/***  START FORT WILLIAM *********

INSERT INTO wp_lh_rooms(id, room_type_id, room, bed_name)
SELECT DISTINCT room_id, room_type_id, room, bed_name
  FROM wp_lh_calendar
  WHERE room_id IS NOT NULL;

UPDATE wp_lh_rooms
   SET capacity = 6, room_type = 'M', active_yn = 'Y' WHERE room IN ( '6' );
   
UPDATE wp_lh_rooms
   SET capacity = 6, room_type = 'MX', active_yn = 'Y' WHERE room IN ( '4' );

UPDATE wp_lh_rooms
   SET capacity = 8, room_type = 'F', active_yn = 'Y' WHERE room IN ( '5' );

UPDATE wp_lh_rooms
   SET capacity = 8, room_type = 'MX', active_yn = 'Y' WHERE room IN ( '1', '2' );

UPDATE wp_lh_rooms
   SET capacity = 2, room_type = 'TWIN', active_yn = 'Y' WHERE room IN ( 'Room 03' );

INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`)
SELECT DISTINCT 100000 + room_type_id `id`, 'Unallocated' `room`, null `bed_name`, capacity, room_type_id, room_type, 'N' `active_yn` FROM wp_lh_rooms
 WHERE room IN ('1','2','4','5','6','Room 03');

*************** END FORT WILLIAM ***************/


/***  START LOCHSIDE *********
INSERT INTO wp_lh_rooms(id, room_type_id, room, bed_name)
SELECT DISTINCT room_id, room_type_id, room, bed_name
  FROM wp_lh_calendar WHERE job_id = 1
  AND room_id is not null
  order by room, bed_name;  
  
UPDATE wp_lh_rooms
   SET capacity = 4, room_type = 'MX', active_yn = 'Y' WHERE room IN ( 'Room 01', 'Room 16', 'Room 21', 'Room 24', 'Room 26' );
UPDATE wp_lh_rooms
   SET capacity = 4, room_type = 'F', active_yn = 'Y' WHERE room IN ( 'Room 12', 'Room 13' );
UPDATE wp_lh_rooms
   SET capacity = 6, room_type = 'MX', active_yn = 'Y' WHERE room IN ( 'Room 23' );
UPDATE wp_lh_rooms
   SET capacity = 8, room_type = 'MX', active_yn = 'Y' WHERE room IN ( 'Room 03' );
UPDATE wp_lh_rooms
   SET capacity = 1, room_type = 'SGL', active_yn = 'Y' WHERE room IN ( 'Single Room' );
UPDATE wp_lh_rooms
   SET capacity = 2, room_type = 'TWN', active_yn = 'Y' WHERE room IN ( 'Twin Room 02', 'Twin Room 14' );

-- Staff rooms have no records so insert manually
-- i don't actually know what the ids are but don't think it matters unless guests are actually booked into them
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('222222-1', 'xRoom 22', 'A', 4, 222222, 'LT_MIXED', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('222222-2', 'xRoom 22', 'B', 4, 222222, 'LT_MIXED', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('222222-3', 'xRoom 22', 'C', 4, 222222, 'LT_MIXED', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('222222-4', 'xRoom 22', 'D', 4, 222222, 'LT_MIXED', 'Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type_id`,`room_type`,`active_yn`) VALUES ('333333-1', 'xRoom 25', 'TWIN', 2, 333333, 'LT_TWIN', 'Y');

*************** END LOCHSIDE ***************/

-- scheduled jobs
/*
INSERT INTO `wp_lh_scheduled_jobs` (`job_id`,`classname`,`cron_schedule`,`active_yn`,`last_scheduled_date`,`last_updated_date`) VALUES (1,'com.macbackpackers.jobs.ScrapeReservationsBookedOnJob','0 31 * * * ?','Y',NULL,NOW());
INSERT INTO `wp_lh_scheduled_jobs` (`job_id`,`classname`,`cron_schedule`,`active_yn`,`last_scheduled_date`,`last_updated_date`) VALUES (2,'com.macbackpackers.jobs.HousekeepingJob','0 29 6 * * ?','Y',NULL,NOW());
INSERT INTO `wp_lh_scheduled_jobs` (`job_id`,`classname`,`cron_schedule`,`active_yn`,`last_scheduled_date`,`last_updated_date`) VALUES (3,'com.macbackpackers.jobs.AllocationScraperJob','0 1 22 * * ?','Y',NULL,NOW());
INSERT INTO `wp_lh_scheduled_jobs` (`job_id`,`classname`,`cron_schedule`,`active_yn`,`last_scheduled_date`,`last_updated_date`) VALUES (4,'com.macbackpackers.jobs.HousekeepingJob','0 59 8 * * ?','Y',NULL,NOW());
INSERT INTO `wp_lh_scheduled_jobs` (`job_id`,`classname`,`cron_schedule`,`active_yn`,`last_scheduled_date`,`last_updated_date`) VALUES (5,'com.macbackpackers.jobs.HousekeepingJob','0 19 10 * * ?','Y',NULL,NOW());
INSERT INTO `wp_lh_scheduled_jobs` (`job_id`,`classname`,`cron_schedule`,`active_yn`,`last_scheduled_date`,`last_updated_date`) VALUES (6,'com.macbackpackers.jobs.BedCountJob','0 20 4 * * ?','Y',NULL,NOW());
INSERT INTO `wp_lh_scheduled_jobs` (`job_id`,`classname`,`cron_schedule`,`active_yn`,`last_scheduled_date`,`last_updated_date`) VALUES (7,'com.macbackpackers.jobs.DiffBookingEnginesJob','0 9 1 * * ?','Y',NULL,NOW());
INSERT INTO `wp_lh_scheduled_jobs` (`job_id`,`classname`,`cron_schedule`,`active_yn`,`last_scheduled_date`,`last_updated_date`) VALUES (8,'com.macbackpackers.jobs.DbPurgeJob','0 31 4 * * ?','Y',NULL,NOW());
INSERT INTO `wp_lh_scheduled_jobs` (`job_id`,`classname`,`cron_schedule`,`active_yn`,`last_scheduled_date`,`last_updated_date`) VALUES (10,'com.macbackpackers.jobs.CreateDepositChargeJob','0 00 22 * * ?','Y',NULL,NOW());
INSERT INTO `wp_lh_scheduled_jobs` (`job_id`,`classname`,`cron_schedule`,`active_yn`,`last_scheduled_date`,`last_updated_date`) VALUES (11,'com.macbackpackers.jobs.CreatePrepaidChargeJob','0 2 3 * * ?','Y',NULL,NOW());

INSERT INTO `wp_lh_scheduled_job_param` (`job_param_id`,`job_id`,`name`,`value`) VALUES (1,1,'booked_on_date','TODAY');
INSERT INTO `wp_lh_scheduled_job_param` (`job_param_id`,`job_id`,`name`,`value`) VALUES (2,2,'selected_date','TODAY');
INSERT INTO `wp_lh_scheduled_job_param` (`job_param_id`,`job_id`,`name`,`value`) VALUES (3,3,'start_date','TODAY');
INSERT INTO `wp_lh_scheduled_job_param` (`job_param_id`,`job_id`,`name`,`value`) VALUES (4,3,'days_ahead','140');
INSERT INTO `wp_lh_scheduled_job_param` (`job_param_id`,`job_id`,`name`,`value`) VALUES (5,4,'selected_date','TODAY');
INSERT INTO `wp_lh_scheduled_job_param` (`job_param_id`,`job_id`,`name`,`value`) VALUES (6,5,'selected_date','TODAY');
INSERT INTO `wp_lh_scheduled_job_param` (`job_param_id`,`job_id`,`name`,`value`) VALUES (7,6,'selected_date','TODAY-1');
INSERT INTO `wp_lh_scheduled_job_param` (`job_param_id`,`job_id`,`name`,`value`) VALUES (8,7,'checkin_date','TODAY');
INSERT INTO `wp_lh_scheduled_job_param` (`job_param_id`,`job_id`,`name`,`value`) VALUES (9,8,'days','90');
INSERT INTO `wp_lh_scheduled_job_param` (`job_param_id`,`job_id`,`name`,`value`) VALUES (10,10,'days_back','14');
*/

-- housekeeping

CREATE TABLE `wp_lh_cleaner` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `first_name` varchar(255) DEFAULT NULL,
  `last_name` varchar(255) DEFAULT NULL,
  `active_yn` char(1) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


CREATE TABLE `wp_lh_cleaner_bed_assign` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `lh_cleaner_id` bigint(20) unsigned NOT NULL,
  `room_id` bigint(20) unsigned NOT NULL,
  `start_date` datetime NOT NULL,
  `end_date` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
--  FOREIGN KEY (`lh_cleaner_id`) REFERENCES `wp_lh_cleaner`(`id`)  -- removed cause of hibernate
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


CREATE TABLE `wp_lh_cleaner_task` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(255) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `default_hours` int(10) unsigned DEFAULT 0,
  `active_yn` char(1) DEFAULT NULL,
  `show_in_daily_tasks_yn` char(1) DEFAULT NULL,
  `sort_order` int(10) unsigned DEFAULT 0,
  `frequency` int(10) unsigned DEFAULT 0,
  `created_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_updated_date` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

