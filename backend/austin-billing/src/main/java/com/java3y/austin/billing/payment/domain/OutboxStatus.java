package com.java3y.austin.billing.payment.domain;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    RETRY_WAIT,
    DEAD
}
