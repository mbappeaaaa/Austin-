package com.java3y.austin.billing.payment.repository;

public interface CurrencyFeeProjection {
    String getCurrency();

    Long getAmountMinor();

    Long getOrderCount();
}
