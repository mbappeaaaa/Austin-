package com.java3y.austin.web.controller;

import cn.hutool.core.text.CharSequenceUtil;
import com.java3y.austin.auth.service.CurrentUserService;
import com.java3y.austin.common.enums.ChannelType;
import com.java3y.austin.support.domain.ChannelAvailability;
import com.java3y.austin.support.service.ChannelAccountAvailabilityService;
import com.java3y.austin.support.service.ChannelPolicyService;
import com.java3y.austin.web.annotation.AustinAspect;
import com.java3y.austin.web.annotation.AustinResult;
import com.java3y.austin.web.vo.ChannelDefinitionVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@AustinAspect
@AustinResult
@RestController
@RequestMapping("/channel")
@Api("渠道启用配置")
public class ChannelController {

    private final ChannelPolicyService channelPolicyService;
    private final ChannelAccountAvailabilityService availabilityService;
    private final CurrentUserService currentUserService;

    public ChannelController(ChannelPolicyService channelPolicyService,
                             ChannelAccountAvailabilityService availabilityService,
                             CurrentUserService currentUserService) {
        this.channelPolicyService = channelPolicyService;
        this.availabilityService = availabilityService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/list")
    @ApiOperation("前端渠道列表；默认只返回已启用渠道")
    public List<ChannelDefinitionVo> list(@RequestParam(defaultValue = "false") boolean includeDisabled,
                                          String creator) {
        String accountOwner = currentUserService.isAdmin() && CharSequenceUtil.isNotBlank(creator)
                ? creator : currentUserService.username();
        List<ChannelDefinitionVo> result = new ArrayList<>();
        for (ChannelType channel : ChannelType.values()) {
            boolean enabled = channelPolicyService.isEnabled(channel.getCode());
            if (!enabled && !includeDisabled) {
                continue;
            }
            ChannelAvailability availability = availabilityService.checkChannel(channel.getCode(), accountOwner);
            result.add(ChannelDefinitionVo.builder().code(channel.getCode()).codeEn(channel.getCodeEn())
                    .name(channel.getDescription()).enabled(enabled)
                    .requiresAccount(channelPolicyService.requiresAccount(channel.getCode()))
                    .availabilityStatus(availability.getStatus()).availabilityMessage(availability.getMessage())
                    .enabledSmsScripts(ChannelType.SMS.equals(channel)
                            ? channelPolicyService.getEnabledSmsScripts() : null).build());
        }
        return result;
    }
}
