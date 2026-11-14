package com.java3y.austin.billing.exception;

public class InsufficientQuotaException extends RuntimeException {
    public InsufficientQuotaException(String message) {
        super(message);
    }
}
