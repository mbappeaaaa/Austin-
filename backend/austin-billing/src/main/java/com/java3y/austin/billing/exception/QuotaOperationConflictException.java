package com.java3y.austin.billing.exception;

public class QuotaOperationConflictException extends RuntimeException {
    public QuotaOperationConflictException(String message) {
        super(message);
    }
}
