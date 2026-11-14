-- 支付宝沙箱仅支持CNY结算，补充人民币沙箱额度包（定价对齐SANDBOX_STARTER：1元/1000额度）
INSERT INTO `billing_quota_package`
    (`package_code`, `display_name`, `currency`, `amount_minor`, `quota_amount`,
     `sandbox_only`, `active`, `created`, `updated`, `version`)
VALUES ('SANDBOX_CNY_STARTER', '沙箱入门包', 'CNY', 100, 1000,
        1, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP(), 0);
