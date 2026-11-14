ALTER TABLE `billing_quota_reservation`
    ADD COLUMN `settled_amount` BIGINT NOT NULL DEFAULT 0 AFTER `amount`,
    ADD COLUMN `released_amount` BIGINT NOT NULL DEFAULT 0 AFTER `settled_amount`;
