package com.java3y.austin.web.vo;

import com.java3y.austin.common.enums.ChannelAvailabilityStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 渠道账号展示对象。accountConfig 只能放脱敏后的配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelAccountVo {
    private Long id;
    private String name;
    private Integer sendChannel;
    private String accountConfig;
    private String creator;
    private Integer created;
    private Integer updated;
    private ChannelAvailabilityStatus availabilityStatus;
    private String availabilityMessage;
    private boolean qualificationRequired;
}
