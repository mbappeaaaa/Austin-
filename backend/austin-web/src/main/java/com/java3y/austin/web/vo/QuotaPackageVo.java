package com.java3y.austin.web.vo;

import com.java3y.austin.billing.payment.domain.QuotaPackage;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class QuotaPackageVo {
    String packageCode;
    String displayName;
    String currency;
    Long amountMinor;
    Long quotaAmount;

    public static QuotaPackageVo from(QuotaPackage quotaPackage) {
        return QuotaPackageVo.builder().packageCode(quotaPackage.getPackageCode())
                .displayName(quotaPackage.getDisplayName()).currency(quotaPackage.getCurrency())
                .amountMinor(quotaPackage.getAmountMinor()).quotaAmount(quotaPackage.getQuotaAmount())
                .build();
    }
}
