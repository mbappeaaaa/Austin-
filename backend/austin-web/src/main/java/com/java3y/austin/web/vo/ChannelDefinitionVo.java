package com.java3y.austin.web.vo;

import com.java3y.austin.common.enums.ChannelAvailabilityStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelDefinitionVo {
    private Integer code;
    private String codeEn;
    private String name;
    private boolean enabled;
    private boolean requiresAccount;
    private ChannelAvailabilityStatus availabilityStatus;
    private String availabilityMessage;
    private Set<String> enabledSmsScripts;
}
