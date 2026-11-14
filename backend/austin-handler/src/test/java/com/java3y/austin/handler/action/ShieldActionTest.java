package com.java3y.austin.handler.action;

import com.alibaba.fastjson2.JSON;
import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.enums.ShieldType;
import com.java3y.austin.common.pipeline.ProcessContext;
import com.java3y.austin.handler.delivery.QuotaDeliveryService;
import com.java3y.austin.support.utils.LogUtils;
import com.java3y.austin.support.utils.RedisUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ShieldActionTest {

    @Test
    void shouldCreateStableDeferredAttemptAndPreventSecondNightDeferral() {
        Clock nightClock = Clock.fixed(Instant.parse("2026-08-30T01:00:00Z"), ZoneOffset.UTC);
        ShieldAction action = new ShieldAction(nightClock);
        RedisUtils redisUtils = mock(RedisUtils.class);
        ReflectionTestUtils.setField(action, "redisUtils", redisUtils);
        ReflectionTestUtils.setField(action, "logUtils", mock(LogUtils.class));
        ReflectionTestUtils.setField(action, "quotaDeliveryService", mock(QuotaDeliveryService.class));
        TaskInfo taskInfo = TaskInfo.builder().tenantId(7L).messageId("msg-1")
                .shieldType(ShieldType.NIGHT_SHIELD_BUT_NEXT_DAY_SEND.getCode())
                .receiver(Collections.singleton("receiver-a")).build();
        ProcessContext<TaskInfo> context = ProcessContext.<TaskInfo>builder()
                .processModel(taskInfo).needBreak(false).build();

        action.process(context);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(redisUtils).lPush(org.mockito.ArgumentMatchers.eq("night_shield_send"),
                payload.capture(), org.mockito.ArgumentMatchers.eq(86400L));
        TaskInfo deferred = JSON.parseObject(payload.getValue(), TaskInfo.class);
        assertTrue(context.getNeedBreak());
        assertNotNull(deferred.getDeliveryAttemptId());
        assertEquals(ShieldType.NIGHT_NO_SHIELD.getCode(), deferred.getShieldType());
    }
}
