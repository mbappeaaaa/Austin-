ALTER TABLE `billing_quota_account`
    ADD COLUMN `alert_cycle` BIGINT NOT NULL DEFAULT 0 AFTER `warning_threshold`,
    ADD COLUMN `threshold_alert_active` TINYINT(1) NOT NULL DEFAULT 0 AFTER `alert_cycle`,
    ADD COLUMN `insufficient_alert_active` TINYINT(1) NOT NULL DEFAULT 0 AFTER `threshold_alert_active`;

