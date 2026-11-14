package com.java3y.austin.billing.payment.service;

public class PaymentOperationConflictException extends RuntimeException {
    public PaymentOperationConflictException(String message) {
        super(message);
    }
}
