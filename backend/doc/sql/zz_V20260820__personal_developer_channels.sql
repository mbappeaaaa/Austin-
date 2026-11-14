-- Austin 个人开发者版渠道迁移（MySQL 5.7+）
-- 历史模板和历史渠道账号均不删除；渠道停用由运行时配置控制。

USE `austin`;

ALTER TABLE `channel_account`
    MODIFY COLUMN `send_channel` INT(10) NOT NULL DEFAULT '0'
        COMMENT '消息发送渠道：保留既有 10-120 编码；130.通用Webhook',
    MODIFY COLUMN `account_config` VARCHAR(4096) NOT NULL DEFAULT ''
        COMMENT '账号配置（API 返回时必须脱敏）';

CREATE TABLE IF NOT EXISTS `in_app_message`
(
    `id`                  BIGINT(20)    NOT NULL AUTO_INCREMENT,
    `receiver`            VARCHAR(128)  NOT NULL COMMENT '接收用户标识',
    `title`               VARCHAR(200)  NOT NULL DEFAULT '' COMMENT '站内信标题',
    `content`             VARCHAR(4096) NOT NULL DEFAULT '' COMMENT '站内信正文',
    `url`                 VARCHAR(1024)          DEFAULT NULL COMMENT '可选业务跳转链接',
    `message_template_id` BIGINT(20)             DEFAULT NULL,
    `business_id`         BIGINT(20)             DEFAULT NULL,
    `message_id`          VARCHAR(64)   NOT NULL COMMENT '发送链路消息ID',
    `is_read`             TINYINT(4)    NOT NULL DEFAULT '0' COMMENT '0.未读 1.已读',
    `read_at`             INT(11)                DEFAULT NULL COMMENT '已读时间（秒）',
    `is_deleted`          TINYINT(4)    NOT NULL DEFAULT '0' COMMENT '0.正常 1.逻辑删除',
    `created`             INT(11)       NOT NULL DEFAULT '0',
    `updated`             INT(11)       NOT NULL DEFAULT '0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_in_app_message_receiver` (`message_id`, `receiver`),
    KEY `idx_in_app_receiver_created` (`receiver`, `is_deleted`, `created`),
    KEY `idx_in_app_receiver_unread` (`receiver`, `is_deleted`, `is_read`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='IM站内信';
