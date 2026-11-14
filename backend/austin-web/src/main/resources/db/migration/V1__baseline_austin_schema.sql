CREATE TABLE `message_template`
(
    `id`               BIGINT(20)    NOT NULL AUTO_INCREMENT,
    `name`             VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '标题',
    `audit_status`     TINYINT(4)    NOT NULL DEFAULT '0' COMMENT '当前消息审核状态',
    `flow_id`          VARCHAR(50)            DEFAULT NULL COMMENT '工单ID',
    `msg_status`       TINYINT(4)    NOT NULL DEFAULT '0' COMMENT '当前消息状态',
    `cron_task_id`     BIGINT(20)             DEFAULT NULL COMMENT '定时任务ID',
    `cron_crowd_path`  VARCHAR(500)            DEFAULT NULL COMMENT '定时发送人群文件路径',
    `expect_push_time` VARCHAR(100)            DEFAULT NULL COMMENT '期望发送时间或cron表达式',
    `id_type`          TINYINT(4)    NOT NULL DEFAULT '0' COMMENT '发送ID类型',
    `send_channel`     INT(10)       NOT NULL DEFAULT '0' COMMENT '消息发送渠道',
    `template_type`    TINYINT(4)    NOT NULL DEFAULT '0' COMMENT '模板类型',
    `msg_type`         TINYINT(4)    NOT NULL DEFAULT '0' COMMENT '消息类型',
    `shield_type`      TINYINT(4)    NOT NULL DEFAULT '0' COMMENT '夜间屏蔽类型',
    `msg_content`      VARCHAR(4096) NOT NULL DEFAULT '' COMMENT '消息内容',
    `send_account`     INT(10)       NOT NULL DEFAULT '0' COMMENT '发送账号',
    `creator`          VARCHAR(45)   NOT NULL DEFAULT '' COMMENT '创建者',
    `updator`          VARCHAR(45)   NOT NULL DEFAULT '' COMMENT '更新者',
    `auditor`          VARCHAR(45)   NOT NULL DEFAULT '' COMMENT '审核人',
    `team`             VARCHAR(45)   NOT NULL DEFAULT '' COMMENT '业务方团队',
    `proposer`         VARCHAR(45)   NOT NULL DEFAULT '' COMMENT '业务方',
    `is_deleted`       TINYINT(4)    NOT NULL DEFAULT '0' COMMENT '0正常 1删除',
    `created`          INT(11)       NOT NULL DEFAULT '0',
    `updated`          INT(11)       NOT NULL DEFAULT '0',
    PRIMARY KEY (`id`),
    KEY `idx_channel` (`send_channel`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='消息模板信息';

CREATE TABLE `sms_record`
(
    `id`                  BIGINT(20)   NOT NULL AUTO_INCREMENT,
    `message_template_id` BIGINT(20)   NOT NULL DEFAULT '0' COMMENT '消息模板ID',
    `phone`               BIGINT(20)   NOT NULL DEFAULT '0' COMMENT '手机号',
    `supplier_id`         TINYINT(4)   NOT NULL DEFAULT '0' COMMENT '短信供应商ID',
    `supplier_name`       VARCHAR(40)  NOT NULL DEFAULT '' COMMENT '短信供应商名称',
    `msg_content`         VARCHAR(600) NOT NULL DEFAULT '' COMMENT '短信内容',
    `series_id`           VARCHAR(100) NOT NULL DEFAULT '' COMMENT '供应商批次ID',
    `charging_num`        TINYINT(4)   NOT NULL DEFAULT '0' COMMENT '计费条数',
    `report_content`      VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '回执内容',
    `status`              TINYINT(4)   NOT NULL DEFAULT '0' COMMENT '短信状态',
    `send_date`           INT(11)      NOT NULL DEFAULT '0' COMMENT '发送日期yyyyMMdd',
    `created`             INT(11)      NOT NULL DEFAULT '0',
    `updated`             INT(11)      NOT NULL DEFAULT '0',
    PRIMARY KEY (`id`),
    KEY `idx_send_date` (`send_date`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='短信记录信息';

CREATE TABLE `channel_account`
(
    `id`             BIGINT(20)    NOT NULL AUTO_INCREMENT,
    `name`           VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '账号名称',
    `send_channel`   INT(10)       NOT NULL DEFAULT '0' COMMENT '消息发送渠道',
    `account_config` VARCHAR(4096) NOT NULL DEFAULT '' COMMENT '渠道账号配置',
    `creator`        VARCHAR(128)  NOT NULL DEFAULT 'Java3y' COMMENT '拥有者',
    `created`        INT(11)       NOT NULL DEFAULT '0',
    `updated`        INT(11)       NOT NULL DEFAULT '0',
    `is_deleted`     TINYINT(4)    NOT NULL DEFAULT '0' COMMENT '0正常 1删除',
    PRIMARY KEY (`id`),
    KEY `idx_send_channel` (`send_channel`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='渠道账号信息';

CREATE TABLE `in_app_message`
(
    `id`                  BIGINT(20)    NOT NULL AUTO_INCREMENT,
    `receiver`            VARCHAR(128)  NOT NULL COMMENT '接收用户标识',
    `title`               VARCHAR(200)  NOT NULL DEFAULT '' COMMENT '站内信标题',
    `content`             VARCHAR(4096) NOT NULL DEFAULT '' COMMENT '站内信正文',
    `url`                 VARCHAR(1024)          DEFAULT NULL COMMENT '可选业务跳转链接',
    `message_template_id` BIGINT(20)             DEFAULT NULL,
    `business_id`         BIGINT(20)             DEFAULT NULL,
    `message_id`          VARCHAR(64)   NOT NULL COMMENT '发送链路消息ID',
    `is_read`             TINYINT(4)    NOT NULL DEFAULT '0' COMMENT '0未读 1已读',
    `read_at`             INT(11)                DEFAULT NULL COMMENT '已读时间（秒）',
    `is_deleted`          TINYINT(4)    NOT NULL DEFAULT '0' COMMENT '0正常 1删除',
    `created`             INT(11)       NOT NULL DEFAULT '0',
    `updated`             INT(11)       NOT NULL DEFAULT '0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_in_app_message_receiver` (`message_id`, `receiver`),
    KEY `idx_in_app_receiver_created` (`receiver`, `is_deleted`, `created`),
    KEY `idx_in_app_receiver_unread` (`receiver`, `is_deleted`, `is_read`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='IM站内信';

CREATE TABLE `sys_user`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `username`      VARCHAR(64)  NOT NULL,
    `password_hash` VARCHAR(100) NOT NULL,
    `display_name`  VARCHAR(64)  NOT NULL,
    `role`          VARCHAR(32)  NOT NULL DEFAULT 'ADMIN',
    `enabled`       BIT          NOT NULL DEFAULT 1,
    `created`       INT          NOT NULL,
    `updated`       INT          NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_user_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='Austin用户';
