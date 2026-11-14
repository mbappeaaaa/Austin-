package com.java3y.austin.web.vo;

import com.java3y.austin.billing.domain.QuotaLedgerEntry;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class QuotaLedgerEntryVo {
    Long id;
    Integer created;
    String entryType;
    Long availableDelta;
    Long reservedDelta;
    Long availableAfter;
    String businessType;
    String businessId;

    public static QuotaLedgerEntryVo from(QuotaLedgerEntry entry) {
        return QuotaLedgerEntryVo.builder().id(entry.getId()).created(entry.getCreated())
                .entryType(entry.getEntryType().name()).availableDelta(entry.getAvailableDelta())
                .reservedDelta(entry.getReservedDelta()).availableAfter(entry.getAvailableAfter())
                .businessType(entry.getBusinessType()).businessId(entry.getBusinessId()).build();
    }
}
