package com.java3y.austin.handler.delivery;

public class RetryableDeliveryException extends RuntimeException {

    public RetryableDeliveryException(String message) {
        super(message);
    }
}
