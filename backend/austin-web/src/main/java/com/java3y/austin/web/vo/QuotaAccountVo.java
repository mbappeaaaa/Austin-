package com.java3y.austin.web.vo;

import com.java3y.austin.billing.domain.QuotaAccount;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class QuotaAccountVo {
    Long tenantId;
    Long availableQuota;
    Long reservedQuota;
    Long warningThreshold;

    public static QuotaAccountVo from(QuotaAccount account) {
        return QuotaAccountVo.builder().tenantId(account.getTenantId())
                .availableQuota(account.getAvailableQuota())
                .reservedQuota(account.getReservedQuota())
                .warningThreshold(account.getWarningThreshold()).build();
    }
}

