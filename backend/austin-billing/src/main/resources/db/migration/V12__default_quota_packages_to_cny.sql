-- Default sandbox packages use CNY for both Alipay and Stripe.
-- Existing payment orders retain their immutable currency and amount snapshots.
ALTER TABLE billing_quota_package MODIFY COLUMN currency CHAR(3) NOT NULL DEFAULT 'CNY';
UPDATE billing_quota_package SET currency = 'CNY', updated = UNIX_TIMESTAMP(), version = version + 1
WHERE sandbox_only = TRUE AND currency <> 'CNY';
