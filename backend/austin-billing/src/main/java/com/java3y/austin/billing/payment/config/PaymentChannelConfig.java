package com.java3y.austin.billing.payment.config;

import lombok.Getter;
import lombok.Setter;
import javax.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "billing_payment_channel_config")
@Getter @Setter
public class PaymentChannelConfig {
    @Id private String provider;
    private boolean enabled;
    private BigDecimal feeRate = BigDecimal.ZERO;
    private String remark = "";
    @Column(columnDefinition = "TEXT") private String encryptedConfig;
    private String updatedBy;
    private Instant updatedAt;
    @Version private Long version;
}
