package com.java3y.austin.handler.script.impl;

import com.java3y.austin.common.enums.SmsStatus;
import com.java3y.austin.handler.domain.sms.SmsParam;
import com.java3y.austin.support.domain.SmsRecord;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MockSmsScriptTest {

    @Test
    void shouldCreateSuccessfulRecordsWithoutThirdPartyAccount() {
        SmsParam param = SmsParam.builder().phones(new LinkedHashSet<>(Arrays.asList("13800138000", "13900139000")))
                .content("验证码 1234").messageTemplateId(100L).build();

        List<SmsRecord> records = new MockSmsScript().send(param);

        assertEquals(2, records.size());
        assertEquals(SmsStatus.SEND_SUCCESS.getCode(), records.get(0).getStatus());
        assertEquals("Mock短信", records.get(0).getSupplierName());
        assertEquals(Integer.valueOf(0), records.get(0).getChargingNum());
        assertTrue(records.get(0).getSeriesId().startsWith("MOCK-"));
        assertTrue(records.get(0).getReportContent().contains("未真实发送"));
    }

    @Test
    void shouldNotRegisterMockScriptInProductionProfile() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("prod");
        context.register(MockSmsScript.class);
        context.refresh();

        assertFalse(context.containsBean("MockSmsScript"));
        context.close();
    }
}
