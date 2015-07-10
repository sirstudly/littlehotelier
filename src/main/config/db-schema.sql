
CREATE TABLE `wp_lh_calendar` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `job_id` bigint(2) unsigned DEFAULT NULL,
  `room_id` int(10) unsigned DEFAULT NULL,
  `room` varchar(50) NOT NULL,
  `bed_name` varchar(50) DEFAULT NULL,
  `reservation_id` bigint(20) unsigned DEFAULT NULL,
  `guest_name` varchar(255) DEFAULT NULL,
  `checkin_date` datetime NOT NULL,
  `checkout_date` datetime NOT NULL,
  `payment_total` decimal(10,2) DEFAULT NULL,
  `payment_outstanding` decimal(10,2) DEFAULT NULL,
  `rate_plan_name` varchar(50) DEFAULT NULL,
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
  KEY `lh_c_checkin` (`checkin_date`),
  KEY `lh_c_checkout` (`checkout_date`),
  KEY `lh_c_jobid` (`job_id`),
  KEY `lh_c_roomid` (`room_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;


CREATE TABLE `wp_lh_jobs` (
  `job_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `classname` varchar(255) NOT NULL,
  `status` varchar(20) NOT NULL,
  `start_date` timestamp NULL DEFAULT NULL,
  `end_date` timestamp NULL DEFAULT NULL,
  `created_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_updated_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


CREATE TABLE `wp_lh_job_param` (
  `job_param_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `job_id` bigint(20) unsigned NOT NULL,
  `name` varchar(255) NOT NULL,
  `value` varchar(255) NOT NULL,
  PRIMARY KEY (`job_param_id`)
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `wp_lh_scheduled_job_param` (
  `job_param_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `job_id` bigint(20) unsigned NOT NULL,
  `name` varchar(255) NOT NULL,
  `value` varchar(255) NOT NULL,
  PRIMARY KEY (`job_param_id`)
--  FOREIGN KEY (`job_id`) REFERENCES `wp_lh_scheduled_jobs`(`id`) -- removed cause of hibernate
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

-- reporting table for reservations split across multiple rooms of the same type
CREATE TABLE `wp_lh_rpt_split_rooms` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `job_id` bigint(20) unsigned NOT NULL,
  `reservation_id` bigint(20) unsigned DEFAULT NULL,
  `guest_name` varchar(255) DEFAULT NULL,
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
  `guest_name` varchar(255) DEFAULT NULL,
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
  `guest_name` varchar(255) DEFAULT NULL,
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

-- application log data
CREATE TABLE `log4j_data`
(`id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
 `job_id` VARCHAR(255) DEFAULT NULL,
 `date_logged` DATETIME NOT NULL,
 `location` VARCHAR(255) NOT NULL,
 `log_level` VARCHAR(10) NOT NULL,
 `message` TEXT,
 `throwable` TEXT,
 `stacktrace` TEXT,
  PRIMARY KEY (`id`),
  KEY `job_id_idx` (`job_id`),
  KEY `date_idx` (`date_logged`)
);

CREATE TABLE `wp_lh_rooms` (
  `id` bigint(20) unsigned NOT NULL,
  `room` varchar(50) DEFAULT NULL,
  `bed_name` varchar(50) DEFAULT NULL,
  `capacity` smallint(6) DEFAULT NULL,
  `room_type` varchar(45) DEFAULT NULL,
  `active_yn` char(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `lh_r_idx` (`room`,`bed_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6802,'21','01 VW',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6803,'21','02 Horned',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6804,'21','03 Spotted',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6805,'21','04 Stag',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6806,'21','05 Bailey',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6807,'21','06 John Lennon',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6808,'21','07 Christmas',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6809,'21','08 Let It Be',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6810,'21','09 Stink',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6811,'21','10 Dung',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6812,'44','01 Coll',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6813,'44','02 Tiree',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6814,'44','03 Jura',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6815,'44','04 Islay',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6816,'44','05 Lewis',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6817,'44','06 Harris',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6818,'44','07 Skye',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6819,'44','08 Raasay',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6820,'44','09 Shetlands',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6822,'44','10 Orkney',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6823,'45','01 MacGregor',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6825,'45','02 Campbell',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6826,'45','03 Wallace',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6827,'45','04 MacLeod',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6828,'45','05 Stewart',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6829,'45','06 MacDonald',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6830,'45','07 Hamilton',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6831,'45','08 Murray',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6832,'45','09 Graham',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6833,'45','10 MacMillan',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6835,'47','01 Braveheart',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6836,'47','02 Rob Roy',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6837,'47','03 Trainspotting',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6838,'47','04 39 Steps',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6839,'47','05 Brigadoon',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6840,'47','06 Whisky Galore',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6841,'47','07 Burke & Hare',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6842,'47','08 Wickerman',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6843,'47','09 Highlander',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6844,'47','10 Young Adam',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6857,'42','01 Tickle',12,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6858,'42','02 Grumpy',12,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6859,'42','03 Fussy',12,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6860,'42','04 Tall',12,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6861,'42','05 Small',12,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6862,'42','06 Messy',12,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6864,'42','07 Bounce',12,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6865,'42','08 Skinny',12,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6867,'42','09 Chatterbox',12,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6868,'42','10 Strong',12,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6869,'42','11 Happy',12,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6870,'42','12 Jelly',12,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6871,'43','01 Franc',12,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6872,'43','02 Amex',12,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6873,'43','03 Kroner',12,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6874,'43','04 Rupee',12,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6875,'43','05 Peseta',12,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6876,'43','06 Escudo',12,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6877,'43','07 Pound',12,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6878,'43','08 Dollar',12,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6879,'43','09 Yen',12,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6880,'43','10 Mark',12,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6881,'43','11 Lira',12,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6882,'43','12 Guilder',12,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6915,'20','01 Laphroig',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6916,'20','02 Talisker',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6917,'20','03 Bells',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6918,'20','04 Grants',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6919,'20','05 Glayva',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6920,'20','06 Oban',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6921,'20','07 Edradour',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6922,'20','08 Old Inverness ',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6924,'20','09 Spring Bank ',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6925,'20','10 Dalwhinnie',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6926,'20','11 Glenmorangie',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6927,'20','12 Highland Park',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6931,'46','01 Nice Pear',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6932,'46','02 Root',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6933,'46','03 Woody',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6935,'46','04 Coconuts',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6936,'46','05 Rubber',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6937,'46','06 Cherry',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6938,'46','07 Cocoa',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6939,'46','08 Hemp',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6940,'46','09 Bush',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6941,'46','10 Melons',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6942,'46','11 Wild Oats',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6943,'46','12 Kumquat',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6947,'51','01 Pinkie',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6948,'51','02 Arnold',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6949,'51','03 Joani',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6950,'51','04 Jenny P',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6951,'51','05 Richie',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6952,'51','06 Fonzie',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6953,'51','07 AAAAAA',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6954,'51','08 Chachi',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6956,'51','09 Ralph Malf',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6957,'51','10 Potsy',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6958,'51','11 Mr C',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6959,'51','12 Big Al',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6963,'75','01 Indiana Jones',4,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6964,'75','02 James Bond',4,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6965,'75','03 Superman',4,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6966,'75','04 Batman',4,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6967,'76','01 Captain America',4,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6968,'76','02 Thor',4,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6969,'76','03 Iron Man',4,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6970,'76','04 Hulk',4,'F','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6971,'61','01 Rabbie Burns',4,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6972,'61','02 Walter Scott',4,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6973,'61','03 Ian Rankin',4,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6974,'61','04 Irvine Welsh',4,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6975,'62','01 John',4,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6976,'62','02 Paul',4,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6977,'62','03 George',4,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6978,'62','04 Ringo',4,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6979,'63','01 Shinty',4,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6981,'63','02 Golf',4,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6982,'63','03 Curling',4,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6983,'63','04 Fitba',4,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6984,'64','01 Nae Bother',4,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6985,'64','02 Och Aye',4,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6986,'64','03 Dinnae Ken',4,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6987,'64','04 Slainte Mhath',4,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6993,'48','01 Topus Bunkus',6,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6994,'48','02 Bottomus Bunkus',6,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6995,'48','03 Gluteus Maximum',6,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6996,'48','04 Pontius Pilot',6,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6998,'48','05 Caeser Saladus',6,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (6999,'48','06 Bigus Dickus',6,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7001,'49','01 Records',8,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7002,'49','02 Madonna',8,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7004,'49','03 Cliff Richard',8,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7005,'49','04 Immac Conception ',8,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7006,'49','05 Brooke Shields',8,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7007,'49','06 Jesus',8,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7008,'49','07 Airlines',8,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7009,'49','08 Sandra Dee',8,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7010,'53',NULL,2,'DBL','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7011,'54',NULL,2,'DBL','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7012,'55',NULL,2,'DBL','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7013,'71',NULL,2,'DBL','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7014,'72',NULL,2,'DBL','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7015,'73',NULL,2,'DBL','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7016,'78',NULL,2,'DBL','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7017,'79',NULL,2,'DBL','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7018,'52',NULL,2,'TWIN','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7019,'56',NULL,4,'QUAD','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7020,'57',NULL,4,'QUAD','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7021,'74',NULL,4,'QUAD','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7022,'58',NULL,3,'TRIPLE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7023,'65',NULL,3,'TRIPLE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7024,'77',NULL,3,'TRIPLE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7410,'10','01 Rubiks Cube',12,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7411,'10','02 Jigsaw',12,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7412,'10','03 Pandoras Box',12,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7413,'10','04 Crosswords',12,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7414,'10','05 Riddle',12,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7415,'10','06 Maze',12,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7416,'10','07 Life',12,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7417,'10','08 Relationships',12,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7418,'10','09 Bermude Triangle',12,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7419,'10','10 Should I Stay?',12,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7420,'14','01 Last Drop',14,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7421,'14','02 Maggie Dicksons',14,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7422,'14','10 Scotsman',14,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7423,'14','04 Green Tree1',14,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7424,'14','05 Whistle Binkies',14,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7425,'14','06 Bannermans',14,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7426,'14','08 Deacon Brodies',14,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7427,'14','11 Sneaky Petes',14,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7429,'14','03 Biddy Mulligans',14,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7430,'14','07 Jocks',14,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7431,'14','12 Royal Mile',14,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7432,'13','06Hazelnut',8,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7435,'13','01Coconut',8,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7438,'13','02Peanut',8,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7439,'13','05Cashew',8,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7440,'13','04MichaelJackson',8,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7441,'13','03Walnut',8,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7452,'11','01Night&Day',10,'LT_FEMALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7453,'11','02Tall&Short',10,'LT_FEMALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7454,'11','03Up&Down',10,'LT_FEMALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7455,'11','04Ebony&Ivory ',10,'LT_FEMALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7456,'11','05Love&Hate',10,'LT_FEMALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7457,'11','06Sex&Chastity',10,'LT_FEMALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7458,'11','07Porsche&Lada',10,'LT_FEMALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7459,'11','08Lust&Rage',10,'LT_FEMALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7460,'11','09SYHA&Bpacker',10,'LT_FEMALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7461,'11','10Drunk&Sober',10,'LT_FEMALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7462,'12','01Pumpy',10,'LT_FEMALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7463,'12','02Pukie',10,'LT_FEMALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7464,'12','03Spanky',10,'LT_FEMALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (7465,'12','04Burpy',10,'LT_FEMALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (9150,'10','11 Labyrinth',12,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (9151,'10','12 Missing Sock',12,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13271,'12','05SnowWhite',10,'LT_FEMALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13272,'12','06TouchyFeely',10,'LT_FEMALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13273,'12','07Pished',10,'LT_FEMALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13274,'12','08Poopy',10,'LT_FEMALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13275,'12','09Pervy',10,'LT_FEMALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13276,'12','10Pimp',10,'LT_FEMALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13363,'23','01Rangers',16,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13364,'23','02Celtic',16,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13365,'23','03Allow',16,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13366,'23','04Fort Wiliam',16,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13367,'23','05Partick Thistle',16,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13368,'23','06Hamilton Acks',16,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13369,'23','07Dundee United',16,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13370,'23','16Dunfermline',16,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13371,'23','08Motherwell',16,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13372,'23','09Kilmarnock',16,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13373,'23','10Aberdeen',16,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13374,'23','11Hibs',16,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13375,'23','12Hearts',16,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13376,'23','13Falkirk',16,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13377,'23','14Cowanbeath',16,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13378,'23','15St.Johnston',16,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13405,'24','01BrainySmurf',8,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13406,'24','02LameBrain',8,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13407,'24','03BrainDead',8,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13408,'24','04BrainStrain',8,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13409,'24','05Brainiac',8,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13410,'24','06Brainless',8,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13411,'24','07PeaBrain',8,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13412,'24','08BrainSurgeon',8,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13429,'14','13 Worlds End',14,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13433,'14','14 Auld Hoose',14,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13441,'22','01 Urqhuart',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13442,'22','02 Edinburgh',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13443,'22','03 Dunvegan',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13444,'22','04 Hermitage',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13445,'22','05 Moil',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13446,'22','06 Rock',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13447,'22','07 Eilean Donan',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13448,'22','08 Dunollie',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13449,'22','09 Stalker',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13450,'22','10 Dunottar',10,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13464,'13','07BrazilNut',8,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13465,'13','08Pecan',8,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13466,'25','01 SteaknAle',14,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13467,'25','02 Baked Potato',14,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13468,'25','03 Haggis',14,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13469,'25','04 DeepFriedMars',14,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13470,'25','05 BloodPudding',14,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13471,'25','06 ScotchEgg',14,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13472,'25','07 HighlandToffee',14,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13473,'25','08 NessieBurger',14,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13474,'25','09 FishnChips',14,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13476,'25','11 Neeps',14,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (13477,'25','12 Tatties',14,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (15069,'14','09 Mitre',14,'LT_MALE','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (15480,'25','13 Porridge',14,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (15481,'25','14 Smoked Salmon',14,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (15482,'25','10 Venison',14,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (18848,'02','James Dean',1,'OVERFLOW','N');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (18849,'03','Jimi Hendrix',1,'OVERFLOW','N');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (18850,'04','Justin Bieber',1,'OVERFLOW','N');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (18851,'05','Janis Joplin',1,'OVERFLOW','N');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (22373,'41','01 Boxers',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (22374,'41','02 Pouch',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (22375,'41','03 Thong',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (22376,'41','04 Panties',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (22377,'41','05 Knickers',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (22378,'41','06 Drawers',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (22379,'41','07 Crotchless',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (22380,'41','08 Edible',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (22381,'41','10 Apple Catchers',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (22383,'41','12 Bloomers',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (22384,'41','11 Jocks',12,'MX','Y');
INSERT INTO `wp_lh_rooms` (`id`,`room`,`bed_name`,`capacity`,`room_type`,`active_yn`) VALUES (22385,'41','09 G String',12,'MX','Y');


-- If you're dumping the table from wp_lh_calendar; then you'll need to fill these in manually

INSERT INTO wp_lh_rooms(id, room, bed_name)
SELECT DISTINCT room_id, room, bed_name
  FROM wp_lh_calendar;

UPDATE wp_lh_rooms
   SET capacity = 12, room_type = 'LT_MALE' WHERE room = 10;
UPDATE wp_lh_rooms
   SET capacity = 10, room_type = 'LT_FEMALE' WHERE room IN (11,12);
UPDATE wp_lh_rooms
   SET capacity = 8, room_type = 'LT_MALE' WHERE room = 13;
UPDATE wp_lh_rooms
   SET capacity = 14, room_type = 'LT_MALE' WHERE room = 14;
UPDATE wp_lh_rooms
   SET capacity = 12, room_type = 'MX' WHERE room IN (20,41,46,51);
UPDATE wp_lh_rooms
   SET capacity = 10, room_type = 'MX' WHERE room IN (21,22,44,45,47);
UPDATE wp_lh_rooms
   SET capacity = 16, room_type = 'MX' WHERE room = 23;
UPDATE wp_lh_rooms
   SET capacity = 8, room_type = 'MX' WHERE room IN (24,49);
UPDATE wp_lh_rooms
   SET capacity = 14, room_type = 'MX' WHERE room = 25;
UPDATE wp_lh_rooms
   SET capacity = 12, room_type = 'F' WHERE room IN (42,43);
UPDATE wp_lh_rooms
   SET capacity = 6, room_type = 'MX' WHERE room = 48;
UPDATE wp_lh_rooms
   SET capacity = 2, room_type = 'TWIN' WHERE room = 52;
UPDATE wp_lh_rooms
   SET capacity = 2, room_type = 'DBL' WHERE room IN (53,54,55,71,72,73,78,79);
UPDATE wp_lh_rooms
   SET capacity = 4, room_type = 'QUAD' WHERE room IN (56,57,74);
UPDATE wp_lh_rooms
   SET capacity = 3, room_type = 'TRIPLE' WHERE room IN(58,65,77);
UPDATE wp_lh_rooms
   SET capacity = 1, room_type = 'OVERFLOW' WHERE room IN (1,2,3,4,5,6,7,8);
UPDATE wp_lh_rooms
   SET capacity = 4, room_type = 'MX' WHERE room IN (61,62,63,64);
UPDATE wp_lh_rooms
   SET capacity = 4, room_type = 'F' WHERE room IN (75,76);

-- set the active flag depending on the most recent job(s)
 UPDATE wp_lh_rooms r
 SET active_yn = (
       SELECT DISTINCT 'Y'
         FROM wp_lh_calendar c
        WHERE c.room_id = r.id
          AND c.job_id IN ( [most recent jobs] ) );
 