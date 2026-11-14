CREATE TABLE billing_payment_channel_config (
    provider VARCHAR(32) NOT NULL PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    fee_rate DECIMAL(7,4) NOT NULL DEFAULT 0,
    remark VARCHAR(255) NOT NULL DEFAULT '',
    encrypted_config TEXT NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);
