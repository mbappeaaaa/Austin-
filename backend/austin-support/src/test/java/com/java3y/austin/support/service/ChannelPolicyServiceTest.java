package com.java3y.austin.support.service;

import com.java3y.austin.common.enums.ChannelType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelPolicyServiceTest {

    @Test
    void shouldUsePersonalDeveloperDefaults() {
        ChannelPolicyService policy = new ChannelPolicyService(new MockEnvironment());

        assertTrue(policy.isEnabled(ChannelType.IM.getCode()));
        assertTrue(policy.isEnabled(ChannelType.SMS.getCode()));
        assertTrue(policy.isEnabled(ChannelType.EMAIL.getCode()));
        assertTrue(policy.isEnabled(ChannelType.FEI_SHU_ROBOT.getCode()));
        assertTrue(policy.isEnabled(ChannelType.WEBHOOK.getCode()));
        assertFalse(policy.isEnabled(ChannelType.PUSH.getCode()));
        assertFalse(policy.isEnabled(ChannelType.OFFICIAL_ACCOUNT.getCode()));
        assertFalse(policy.isEnabled(ChannelType.MINI_PROGRAM.getCode()));
        assertFalse(policy.isEnabled(ChannelType.ENTERPRISE_WE_CHAT.getCode()));
        assertFalse(policy.isEnabled(ChannelType.DING_DING_WORK_NOTICE.getCode()));
        assertFalse(policy.isEnabled(ChannelType.ALIPAY_MINI_PROGRAM.getCode()));
        assertTrue(policy.isSmsScriptEnabled(ChannelAccountAvailabilityService.MOCK_SMS_SCRIPT));
        assertFalse(policy.isSmsScriptEnabled(ChannelAccountAvailabilityService.TENCENT_SMS_SCRIPT));
    }
}
