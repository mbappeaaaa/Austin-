CREATE TABLE `billing_quota_package`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `package_code`  VARCHAR(64)  NOT NULL,
    `display_name`  VARCHAR(128) NOT NULL,
    `currency`      CHAR(3)      NOT NULL,
    `amount_minor`  BIGINT       NOT NULL,
    `quota_amount`  BIGINT       NOT NULL,
    `sandbox_only`  BIT          NOT NULL DEFAULT 1,
    `active`        BIT          NOT NULL DEFAULT 1,
    `created`       INT          NOT NULL,
    `updated`       INT          NOT NULL,
    `version`       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_quota_package_code` (`package_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='固定价格消息额度包';

CREATE TABLE `billing_payment_order`
(
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`             BIGINT       NOT NULL,
    `order_no`              VARCHAR(64)  NOT NULL,
    `idempotency_key`       VARCHAR(128) NOT NULL,
    `provider`              VARCHAR(32)  NOT NULL,
    `provider_order_id`     VARCHAR(128)          DEFAULT NULL,
    `package_code`          VARCHAR(64)  NOT NULL,
    `package_name_snapshot` VARCHAR(128) NOT NULL,
    `currency`              CHAR(3)      NOT NULL,
    `amount_minor`          BIGINT       NOT NULL,
    `quota_amount`          BIGINT       NOT NULL,
    `status`                VARCHAR(32)  NOT NULL,
    `checkout_url`          VARCHAR(2048)         DEFAULT NULL,
    `paid_at`               INT                   DEFAULT NULL,
    `created`               INT          NOT NULL,
    `updated`               INT          NOT NULL,
    `version`               BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payment_order_no` (`order_no`),
    UNIQUE KEY `uk_payment_tenant_idempotency` (`tenant_id`, `idempotency_key`),
    UNIQUE KEY `uk_payment_provider_order` (`provider`, `provider_order_id`),
    KEY `idx_payment_tenant_created` (`tenant_id`, `created`, `id`),
    KEY `idx_payment_tenant_status` (`tenant_id`, `status`, `updated`),
    CONSTRAINT `fk_payment_order_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='支付订单';

CREATE TABLE `billing_payment_event`
(
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `provider`            VARCHAR(32)  NOT NULL,
    `provider_event_id`   VARCHAR(128) NOT NULL,
    `provider_order_id`   VARCHAR(128)          DEFAULT NULL,
    `order_no`            VARCHAR(64)           DEFAULT NULL,
    `event_type`          VARCHAR(128) NOT NULL,
    `event_status`        VARCHAR(32)  NOT NULL,
    `payload_hash`        CHAR(64)     NOT NULL,
    `provider_created_at` INT                   DEFAULT NULL,
    `last_error_code`     VARCHAR(64)           DEFAULT NULL,
    `created`             INT          NOT NULL,
    `processed_at`        INT                   DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payment_event_provider_id` (`provider`, `provider_event_id`),
    KEY `idx_payment_event_order` (`provider`, `order_no`, `created`),
    KEY `idx_payment_event_status` (`event_status`, `created`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='验签后的支付回调事件';

CREATE TABLE `billing_outbox_event`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`      BIGINT       NOT NULL,
    `aggregate_type` VARCHAR(64)  NOT NULL,
    `aggregate_id`   VARCHAR(128) NOT NULL,
    `event_type`     VARCHAR(128) NOT NULL,
    `event_key`      VARCHAR(160) NOT NULL,
    `payload`        MEDIUMTEXT   NOT NULL,
    `status`         VARCHAR(32)  NOT NULL,
    `attempt_count`  INT          NOT NULL DEFAULT 0,
    `next_retry_at`  INT                   DEFAULT NULL,
    `published_at`   INT                   DEFAULT NULL,
    `last_error`     VARCHAR(512)          DEFAULT NULL,
    `created`        INT          NOT NULL,
    `updated`        INT          NOT NULL,
    `version`        BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_billing_outbox_event_key` (`event_key`),
    KEY `idx_billing_outbox_dispatch` (`status`, `next_retry_at`, `created`),
    KEY `idx_billing_outbox_tenant` (`tenant_id`, `created`, `id`),
    CONSTRAINT `fk_billing_outbox_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='账务域事务Outbox';

INSERT INTO `billing_quota_package`
    (`package_code`, `display_name`, `currency`, `amount_minor`, `quota_amount`,
     `sandbox_only`, `active`, `created`, `updated`, `version`)
VALUES ('SANDBOX_STARTER', 'Sandbox Starter', 'USD', 100, 1000,
        1, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP(), 0);
