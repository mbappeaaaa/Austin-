ALTER TABLE `message_template`
    ADD COLUMN `tenant_id` BIGINT NULL AFTER `id`;
UPDATE `message_template` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL;
ALTER TABLE `message_template`
    MODIFY COLUMN `tenant_id` BIGINT NOT NULL,
    ADD KEY `idx_message_template_tenant_updated` (`tenant_id`, `is_deleted`, `updated`),
    ADD CONSTRAINT `fk_message_template_tenant`
        FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`);

ALTER TABLE `channel_account`
    ADD COLUMN `tenant_id` BIGINT NULL AFTER `id`;
UPDATE `channel_account` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL;
ALTER TABLE `channel_account`
    MODIFY COLUMN `tenant_id` BIGINT NOT NULL,
    ADD KEY `idx_channel_account_tenant_channel` (`tenant_id`, `is_deleted`, `send_channel`),
    ADD CONSTRAINT `fk_channel_account_tenant`
        FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`);

ALTER TABLE `sms_record`
    ADD COLUMN `tenant_id` BIGINT NULL AFTER `id`;
UPDATE `sms_record` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL;
ALTER TABLE `sms_record`
    MODIFY COLUMN `tenant_id` BIGINT NOT NULL,
    ADD KEY `idx_sms_record_tenant_date` (`tenant_id`, `send_date`),
    ADD CONSTRAINT `fk_sms_record_tenant`
        FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`);

ALTER TABLE `in_app_message`
    ADD COLUMN `tenant_id` BIGINT NULL AFTER `id`;
UPDATE `in_app_message` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL;
ALTER TABLE `in_app_message`
    MODIFY COLUMN `tenant_id` BIGINT NOT NULL,
    DROP INDEX `uk_in_app_message_receiver`,
    ADD UNIQUE KEY `uk_in_app_message_tenant_receiver` (`tenant_id`, `message_id`, `receiver`),
    ADD KEY `idx_in_app_tenant_receiver_created` (`tenant_id`, `receiver`, `is_deleted`, `created`),
    ADD CONSTRAINT `fk_in_app_message_tenant`
        FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`);
