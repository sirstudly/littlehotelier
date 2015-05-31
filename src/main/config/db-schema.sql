
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
  KEY `lh_c_jobid` (`job_id`)
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
  PRIMARY KEY (`job_param_id`),
  FOREIGN KEY (`job_id`) REFERENCES `wp_lh_jobs`(`job_id`)
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
  `viewed_yn` char(1) DEFAULT NULL
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
  PRIMARY KEY (`id`),
  KEY `lh_r_idx` (`room`,`bed_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6770, '11', '01 Night&Day', 10, 'LT FEMALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6773, '11', '02 Tall&Short', 10, 'LT FEMALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6777, '11', '03 Up&Down', 10, 'LT FEMALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6778, '11', '04 Ebony&Ivory', 10, 'LT FEMALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6779, '11', '05 Love&Hate', 10, 'LT FEMALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6780, '11', '06 Sex&Chastity', 10, 'LT FEMALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6781, '11', '07 Porsche&Lada', 10, 'LT FEMALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6782, '11', '08 Lust&Rage', 10, 'LT FEMALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6783, '11', '09 SYHA&Bpackers', 10, 'LT FEMALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6786, '11', '10 Drunk&Sober', 10, 'LT FEMALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6789, '13', '01 Coconut', 8, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6790, '13', '02 Peanut', 8, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6792, '13', '03 Walnut', 8, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6793, '13', '04 Michael Jackson', 8, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6795, '13', '05 Cashew', 8, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6796, '13', '06 Hazelnut', 8, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6797, '13', '07 Brazil Nut', 8, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6799, '13', '08 Pecan', 8, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6802, '21', '01 VW', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6803, '21', '02 Horned', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6804, '21', '03 Spotted', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6805, '21', '04 Stag', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6806, '21', '05 Bailey', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6807, '21', '06 John Lennon', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6808, '21', '07 Christmas', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6809, '21', '08 Let It Be', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6810, '21', '09 Stink', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6811, '21', '10 Dung', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6812, '44', '01 Coll', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6813, '44', '02 Tiree', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6814, '44', '03 Jura', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6815, '44', '04 Islay', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6816, '44', '05 Lewis', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6817, '44', '06 Harris', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6818, '44', '07 Skye', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6819, '44', '08 Raasay', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6820, '44', '09 Shetlands', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6822, '44', '10 Orkney', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6823, '45', '01 MacGregor', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6825, '45', '02 Campbell', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6826, '45', '03 Wallace', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6827, '45', '04 MacLeod', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6828, '45', '05 Stewart', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6829, '45', '06 MacDonald', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6830, '45', '07 Hamilton', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6831, '45', '08 Murray', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6832, '45', '09 Graham', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6833, '45', '10 MacMillan', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6835, '47', '01 Braveheart', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6836, '47', '02 Rob Roy', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6837, '47', '03 Trainspotting', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6838, '47', '04 39 Steps', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6839, '47', '05 Brigadoon', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6840, '47', '06 Whisky Galore', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6841, '47', '07 Burke & Hare', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6842, '47', '08 Wickerman', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6843, '47', '09 Highlander', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6844, '47', '10 Young Adam', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6845, '12', '01 Pumpy', 10, 'LT FEMALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6846, '12', '02 Pukie', 10, 'LT FEMALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6847, '12', '03 Spanky', 10, 'LT FEMALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6848, '12', '04 Burpy', 10, 'LT FEMALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6849, '12', '05 Snow White', 10, 'LT FEMALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6850, '12', '06 Touchy-Feely', 10, 'LT FEMALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6851, '12', '07 Pished', 10, 'LT FEMALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6852, '12', '08 Poopy', 10, 'LT FEMALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6853, '12', '09 Pervy', 10, 'LT FEMALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6854, '12', '10 Pimp', 10, 'LT FEMALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6857, '42', '01 Tickle', 12, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6858, '42', '02 Grumpy', 12, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6859, '42', '03 Fussy', 12, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6860, '42', '04 Tall', 12, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6861, '42', '05 Small', 12, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6862, '42', '06 Messy', 12, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6864, '42', '07 Bounce', 12, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6865, '42', '08 Skinny', 12, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6867, '42', '09 Chatterbox', 12, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6868, '42', '10 Strong', 12, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6869, '42', '11 Happy', 12, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6870, '42', '12 Jelly', 12, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6871, '43', '01 Franc', 12, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6872, '43', '02 Amex', 12, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6873, '43', '03 Kroner', 12, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6874, '43', '04 Rupee', 12, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6875, '43', '05 Peseta', 12, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6876, '43', '06 Escudo', 12, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6877, '43', '07 Pound', 12, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6878, '43', '08 Dollar', 12, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6879, '43', '09 Yen', 12, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6880, '43', '10 Mark', 12, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6881, '43', '11 Lira', 12, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6882, '43', '12 Guilder', 12, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6883, '41', '01 Boxers', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6884, '41', '02 Pouch', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6885, '41', '03 Thong', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6886, '41', '04 Panties', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6887, '41', '05 Knickers', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6888, '41', '06 Drawers', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6889, '41', '07 Crotchless', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6890, '41', '08 Edible', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6891, '41', '09 G String', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6893, '41', '10 Apple Catchers', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6894, '41', '11 Jocks', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6896, '41', '12 Bloomers', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6898, '14', '01 Last Drop', 14, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6899, '14', '02 Maggie Dicksons', 14, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6900, '14', '03 Biddy Mulligans', 14, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6901, '14', '04 Green Tree', 14, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6902, '14', '05 Whistle Binkies', 14, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6903, '14', '06 Bannermans', 14, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6904, '14', '07 Jocks', 14, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6905, '14', '08 Deacon Brodies', 14, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6906, '14', '09 Mitre', 14, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6907, '14', '10 Scotsman', 14, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6908, '14', '11 Sneaky Petes', 14, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6911, '14', '12 Royal Mile', 14, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6912, '14', '13 Worlds End', 14, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6913, '14', '14 Auld Hoose', 14, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6915, '25', '01 Steak & Ale Pie', 14, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6916, '25', '02 Baked Potato', 14, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6917, '25', '03 Haggis', 14, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6918, '25', '04 Deep Fried Mars', 14, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6919, '25', '05 Blood Pudding', 14, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6920, '25', '06 Scotch Egg', 14, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6921, '25', '07 Highland Toffee', 14, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6922, '25', '08 Nessie Burger', 14, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6924, '25', '09 Fish & Chips', 14, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6925, '25', '10 Venison', 14, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6926, '25', '11 Neeps', 14, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6927, '25', '12 Tatties', 14, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6928, '25', '13 Porridge', 14, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6929, '25', '14 Smoked Salmon', 14, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6931, '46', '01 Nice Pear', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6932, '46', '02 Root', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6933, '46', '03 Woody', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6935, '46', '04 Coconuts', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6936, '46', '05 Rubber', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6937, '46', '06 Cherry', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6938, '46', '07 Cocoa', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6939, '46', '08 Hemp', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6940, '46', '09 Bush', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6941, '46', '10 Melons', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6942, '46', '11 Wild Oats', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6943, '46', '12 Kumquat', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6947, '51', '01 Pinkie', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6948, '51', '02 Arnold', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6949, '51', '03 Joani', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6950, '51', '04 Jenny P', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6951, '51', '05 Richie', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6952, '51', '06 Fonzie', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6953, '51', '07 AAAAAA', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6954, '51', '08 Chachi', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6956, '51', '09 Ralph Malf', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6957, '51', '10 Potsy', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6958, '51', '11 Mr C', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6959, '51', '12 Big Al', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6963, '75', '01 Indiana Jones', 4, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6964, '75', '02 James Bond', 4, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6965, '75', '03 Superman', 4, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6966, '75', '04 Batman', 4, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6967, '76', '01 Captain America', 4, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6968, '76', '02 Thor', 4, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6969, '76', '03 Iron Man', 4, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6970, '76', '04 Hulk', 4, 'F' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6971, '61', '01 Rabbie Burns', 4, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6972, '61', '02 Walter Scott', 4, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6973, '61', '03 Ian Rankin', 4, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6974, '61', '04 Irvine Welsh', 4, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6975, '62', '01 John', 4, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6976, '62', '02 Paul', 4, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6977, '62', '03 George', 4, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6978, '62', '04 Ringo', 4, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6979, '63', '01 Shinty', 4, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6981, '63', '02 Golf', 4, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6982, '63', '03 Curling', 4, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6983, '63', '04 Fitba', 4, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6984, '64', '01 Nae Bother', 4, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6985, '64', '02 Och Aye', 4, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6986, '64', '03 Dinnae Ken', 4, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6987, '64', '04 Slainte Mhath', 4, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6993, '48', '01 Topus Bunkus', 6, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6994, '48', '02 Bottomus Bunkus', 6, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6995, '48', '03 Gluteus Maximum', 6, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6996, '48', '04 Pontius Pilot', 6, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6998, '48', '05 Caeser Saladus', 6, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 6999, '48', '06 Bigus Dickus', 6, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7001, '49', '01 Records', 8, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7002, '49', '02 Madonna', 8, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7004, '49', '03 Cliff Richard', 8, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7005, '49', '04 Immac Conception', 8, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7006, '49', '05 Brooke Shields', 8, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7007, '49', '06 Jesus', 8, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7008, '49', '07 Airlines', 8, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7009, '49', '08 Sandra Dee', 8, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7010, '53', '', 2, 'DBL' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7011, '54', '', 2, 'DBL' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7012, '55', '', 2, 'DBL' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7013, '71', '', 2, 'DBL' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7014, '72', '', 2, 'DBL' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7015, '73', '', 2, 'DBL' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7016, '78', '', 2, 'DBL' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7017, '79', '', 2, 'DBL' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7018, '52', '', 2, 'TWIN' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7019, '56', '', 4, 'QUAD' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7020, '57', '', 4, 'QUAD' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7021, '74', '', 4, 'QUAD' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7022, '58', '', 3, 'TRIPLE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7023, '65', '', 3, 'TRIPLE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7024, '77', '', 3, 'TRIPLE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7410, '10', '01 Rubiks Cube', 12, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7411, '10', '02 Jigsaw', 12, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7412, '10', '03 Pandoras Box', 12, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7413, '10', '04 Crosswords', 12, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7414, '10', '05 Riddle', 12, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7415, '10', '06 Maze', 12, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7416, '10', '07 Life', 12, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7417, '10', '08 Relationships', 12, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7418, '10', '09 Bermude Triangle', 12, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7419, '10', '10 Should I Stay?', 12, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7420, '20', '01 Laphroaig', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7421, '20', '02 Talisker', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7422, '20', '03 Bells', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7423, '20', '04 Grants', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7424, '20', '05 Glayva', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7425, '20', '06 Oban', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7426, '20', '07 Edradour', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7427, '20', '08 Old Inverness', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7428, '20', '09 Spring Bank', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7429, '20', '10 Dalwhinnie', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7430, '20', '11 Glenmorangie', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7431, '20', '12 Highland Park', 12, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7432, '22', '10 Dunottar', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7433, '22', '01 Urquhart', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7434, '22', '02 Edinburgh', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7435, '22', '03 Dunvegan', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7436, '22', '04 Hermitage', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7437, '22', '05 Moil', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7438, '22', '06 Rock', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7439, '22', '07 Eilean Donan', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7440, '22', '08 Dunollie', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7441, '22', '09 Stalker', 10, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7450, '23', '01 Rangers', 16, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7451, '23', '02 Celtic', 16, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7452, '23', '03 Allow', 16, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7453, '23', '04 Ft William', 16, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7454, '23', '05 Partick Thistle', 16, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7455, '23', '06 Hamilton Acks', 16, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7456, '23', '07 Dundee United', 16, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7457, '23', '08 Motherwell', 16, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7458, '23', '09 Kilmarnock', 16, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7459, '23', '10 Aberdeen', 16, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7460, '23', '11 Hibs', 16, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7461, '23', '12 Hearts', 16, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7462, '23', '13 Falkirk', 16, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7463, '23', '14 Cowanbeath', 16, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7464, '23', '15 St Johnston', 16, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7465, '23', '16 Dunfermline', 16, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7466, '24', '01 Brainy Smurf', 8, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7467, '24', '02 Lame Brain', 8, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7469, '24', '04 Brain Strain', 8, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7470, '24', '05 Brainiac', 8, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7471, '24', '06 Brainless', 8, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7472, '24', '07 Pea Brain', 8, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 7473, '24', '08 Brain Surgeon', 8, 'MX' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 9150, '10', '11 Labyrinth', 12, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 9151, '10', '12 Missing Sock', 12, 'LT MALE' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 18848, '2', 'James Dean', 8, 'OVERFLOW' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 18849, '3', 'Jimi Hendrix', 8, 'OVERFLOW' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 18850, '4', 'Justin Bieber', 8, 'OVERFLOW' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 18851, '5', 'Janis Joplin', 8, 'OVERFLOW' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 18852, '6', 'Amy Winehouse', 8, 'OVERFLOW' );
INSERT INTO `wp_lh_rooms` ( `id`, `room`, `bed_name`, `capacity`, `room_type` ) VALUES( 18853, '7', 'Marilyn Monroe', 8, 'OVERFLOW' );
