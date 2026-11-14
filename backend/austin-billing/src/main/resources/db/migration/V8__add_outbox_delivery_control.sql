ALTER TABLE `billing_outbox_event`
    ADD COLUMN `max_attempts` INT NOT NULL DEFAULT 5 AFTER `attempt_count`,
    ADD COLUMN `replay_count` INT NOT NULL DEFAULT 0 AFTER `max_attempts`,
    ADD COLUMN `locked_by` VARCHAR(128) DEFAULT NULL AFTER `next_retry_at`,
    ADD COLUMN `locked_until` INT DEFAULT NULL AFTER `locked_by`;

ALTER TABLE `billing_outbox_event`
    DROP INDEX `idx_billing_outbox_dispatch`,
    ADD KEY `idx_billing_outbox_dispatch`
        (`status`, `next_retry_at`, `locked_until`, `created`);
