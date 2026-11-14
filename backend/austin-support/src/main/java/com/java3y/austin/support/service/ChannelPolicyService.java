package com.java3y.austin.support.service;

import cn.hutool.core.text.CharSequenceUtil;
import com.java3y.austin.common.enums.ChannelType;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 个人开发者版渠道开关。配置值使用既有渠道编码，新增渠道不会改变历史编码。
 */
@Service
public class ChannelPolicyService {

    public static final String ENABLED_CHANNELS_PROPERTY = "austin.channels.enabled";
    public static final String ENABLED_SMS_SCRIPTS_PROPERTY = "austin.channels.sms.enabled-scripts";
    private static final String DEFAULT_ENABLED_CHANNELS = "10,30,40,110,130";
    private static final String DEFAULT_ENABLED_SMS_SCRIPTS = ChannelAccountAvailabilityService.MOCK_SMS_SCRIPT;

    private final Environment environment;

    public ChannelPolicyService(Environment environment) {
        this.environment = environment;
    }

    public boolean isEnabled(Integer channelCode) {
        return channelCode != null && getEnabledChannelCodes().contains(channelCode);
    }

    public boolean isSmsScriptEnabled(String scriptName) {
        return CharSequenceUtil.isNotBlank(scriptName) && getEnabledSmsScripts().contains(scriptName);
    }

    public Set<Integer> getEnabledChannelCodes() {
        String configured = environment.getProperty(ENABLED_CHANNELS_PROPERTY, DEFAULT_ENABLED_CHANNELS);
        Set<Integer> result = new LinkedHashSet<>();
        for (String value : split(configured)) {
            try {
                result.add(Integer.valueOf(value));
            } catch (NumberFormatException ignored) {
                // 忽略无效编码，避免误开启渠道。
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public Set<String> getEnabledSmsScripts() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(split(
                environment.getProperty(ENABLED_SMS_SCRIPTS_PROPERTY, DEFAULT_ENABLED_SMS_SCRIPTS))));
    }

    public boolean requiresAccount(Integer channelCode) {
        return !ChannelType.IM.getCode().equals(channelCode);
    }

    private java.util.List<String> split(String value) {
        if (CharSequenceUtil.isBlank(value)) {
            return Collections.emptyList();
        }
        return Arrays.asList(value.replace(" ", "").split(","));
    }
}
