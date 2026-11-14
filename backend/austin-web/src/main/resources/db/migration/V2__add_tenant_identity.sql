CREATE TABLE `tenant`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `tenant_code` VARCHAR(64) NOT NULL COMMENT '稳定的租户业务标识',
    `name`        VARCHAR(128) NOT NULL COMMENT '租户展示名称',
    `status`      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    `timezone`    VARCHAR(64) NOT NULL DEFAULT 'UTC',
    `created`     INT         NOT NULL,
    `updated`     INT         NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='Austin租户';

INSERT INTO `tenant` (`id`, `tenant_code`, `name`, `status`, `timezone`, `created`, `updated`)
VALUES (1, 'legacy', 'Legacy Austin', 'ACTIVE', 'UTC', UNIX_TIMESTAMP(), UNIX_TIMESTAMP());

ALTER TABLE `sys_user`
    ADD COLUMN `tenant_id` BIGINT NULL AFTER `id`;

UPDATE `sys_user`
SET `tenant_id` = 1
WHERE `tenant_id` IS NULL;

ALTER TABLE `sys_user`
    MODIFY COLUMN `tenant_id` BIGINT NOT NULL,
    ADD KEY `idx_sys_user_tenant_id` (`tenant_id`),
    ADD CONSTRAINT `fk_sys_user_tenant`
        FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`);
