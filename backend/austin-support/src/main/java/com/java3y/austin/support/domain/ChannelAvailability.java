package com.java3y.austin.support.domain;

import com.java3y.austin.common.enums.ChannelAvailabilityStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 渠道账号可用性检查结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelAvailability {
    private ChannelAvailabilityStatus status;
    private String message;
    private boolean qualificationRequired;

    public boolean isReady() {
        return ChannelAvailabilityStatus.READY.equals(status);
    }
}
