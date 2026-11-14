package com.java3y.austin.handler.handler;

import lombok.Getter;

@Getter
public final class DeliveryResult {

    private final DeliveryStatus status;
    private final String reasonCode;

    private DeliveryResult(DeliveryStatus status, String reasonCode) {
        this.status = status;
        this.reasonCode = reasonCode;
    }

    public static DeliveryResult success() {
        return new DeliveryResult(DeliveryStatus.SUCCESS, null);
    }

    public static DeliveryResult finalFailure(String reasonCode) {
        return new DeliveryResult(DeliveryStatus.FINAL_FAILURE, reasonCode);
    }

    public static DeliveryResult retryableFailure(String reasonCode) {
        return new DeliveryResult(DeliveryStatus.RETRYABLE_FAILURE, reasonCode);
    }

    public static DeliveryResult unknown(String reasonCode) {
        return new DeliveryResult(DeliveryStatus.UNKNOWN, reasonCode);
    }

    public static DeliveryResult skipped(String reasonCode) {
        return new DeliveryResult(DeliveryStatus.SKIPPED, reasonCode);
    }

    public static DeliveryResult deferred(String reasonCode) {
        return new DeliveryResult(DeliveryStatus.DEFERRED, reasonCode);
    }
}
