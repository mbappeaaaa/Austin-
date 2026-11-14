package com.java3y.austin.billing.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class QuotaReservationResult {
    private final QuotaReservation reservation;
    private final boolean created;
}
