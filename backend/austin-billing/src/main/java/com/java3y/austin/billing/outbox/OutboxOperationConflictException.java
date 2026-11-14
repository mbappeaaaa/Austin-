package com.java3y.austin.billing.outbox;

public class OutboxOperationConflictException extends RuntimeException {
    public OutboxOperationConflictException(String message) {
        super(message);
    }
}
