CREATE TABLE `billing_quota_account`
(
    `id`                BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id`         BIGINT NOT NULL,
    `available_quota`   BIGINT NOT NULL DEFAULT 0,
    `reserved_quota`    BIGINT NOT NULL DEFAULT 0,
    `warning_threshold` BIGINT NOT NULL DEFAULT 0,
    `created`           INT    NOT NULL,
    `updated`           INT    NOT NULL,
    `version`           BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_billing_quota_account_tenant` (`tenant_id`),
    CONSTRAINT `fk_quota_account_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='租户消息额度账户';

CREATE TABLE `billing_quota_reservation`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`       BIGINT       NOT NULL,
    `idempotency_key` VARCHAR(128) NOT NULL,
    `business_type`   VARCHAR(32)  NOT NULL,
    `business_id`     VARCHAR(128) NOT NULL,
    `amount`          BIGINT       NOT NULL,
    `status`          VARCHAR(32)  NOT NULL,
    `created`         INT          NOT NULL,
    `updated`         INT          NOT NULL,
    `version`         BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_quota_reservation_tenant_key` (`tenant_id`, `idempotency_key`),
    KEY `idx_quota_reservation_business` (`tenant_id`, `business_type`, `business_id`),
    KEY `idx_quota_reservation_status` (`tenant_id`, `status`, `updated`),
    CONSTRAINT `fk_quota_reservation_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='额度预占单';

CREATE TABLE `billing_quota_ledger`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`       BIGINT       NOT NULL,
    `account_id`      BIGINT       NOT NULL,
    `reservation_id`  BIGINT                DEFAULT NULL,
    `entry_type`      VARCHAR(32)  NOT NULL,
    `entry_key`       VARCHAR(160) NOT NULL,
    `available_delta` BIGINT       NOT NULL,
    `reserved_delta`  BIGINT       NOT NULL,
    `available_after` BIGINT       NOT NULL,
    `reserved_after`  BIGINT       NOT NULL,
    `business_type`   VARCHAR(32)  NOT NULL,
    `business_id`     VARCHAR(128) NOT NULL,
    `created`         INT          NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_quota_ledger_tenant_key` (`tenant_id`, `entry_key`),
    KEY `idx_quota_ledger_tenant_created` (`tenant_id`, `created`, `id`),
    KEY `idx_quota_ledger_business` (`tenant_id`, `business_type`, `business_id`),
    CONSTRAINT `fk_quota_ledger_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
    CONSTRAINT `fk_quota_ledger_account` FOREIGN KEY (`account_id`) REFERENCES `billing_quota_account` (`id`),
    CONSTRAINT `fk_quota_ledger_reservation` FOREIGN KEY (`reservation_id`)
        REFERENCES `billing_quota_reservation` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='不可变额度流水';

INSERT INTO `billing_quota_account`
    (`tenant_id`, `available_quota`, `reserved_quota`, `warning_threshold`, `created`, `updated`, `version`)
SELECT `id`, 0, 0, 0, UNIX_TIMESTAMP(), UNIX_TIMESTAMP(), 0
FROM `tenant`;
