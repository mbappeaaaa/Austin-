package com.java3y.austin.handler.delivery;

import lombok.Getter;

@Getter
public class NonRetryableDeliveryException extends RuntimeException {

    private final String errorCode;

    public NonRetryableDeliveryException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public NonRetryableDeliveryException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
