package com.java3y.austin.billing.payment.config;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentChannelRepository extends JpaRepository<PaymentChannelConfig, String> { }
