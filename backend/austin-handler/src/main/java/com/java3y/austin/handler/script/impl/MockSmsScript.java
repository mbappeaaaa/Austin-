package com.java3y.austin.handler.script.impl;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import com.java3y.austin.common.enums.SmsStatus;
import com.java3y.austin.handler.domain.sms.SmsParam;
import com.java3y.austin.handler.script.SmsScript;
import com.java3y.austin.support.domain.SmsRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 开发环境短信模拟器。保留标准短信 Handler 和发送记录链路，但不访问任何第三方。
 */
@Slf4j
@Component("MockSmsScript")
@Profile("!prod & !production")
public class MockSmsScript implements SmsScript {

    private static final int MOCK_SUPPLIER_ID = 0;
    private static final String MOCK_SUPPLIER_NAME = "Mock短信";

    @Override
    public List<SmsRecord> send(SmsParam smsParam) {
        if (smsParam == null || smsParam.getPhones() == null) {
            return Collections.emptyList();
        }
        List<SmsRecord> records = new ArrayList<>();
        int now = Math.toIntExact(DateUtil.currentSeconds());
        int sendDate = Integer.parseInt(DateUtil.format(new Date(), DatePattern.PURE_DATE_PATTERN));
        for (String phone : smsParam.getPhones()) {
            try {
                SmsRecord record = SmsRecord.builder()
                        .sendDate(sendDate)
                        .messageTemplateId(smsParam.getMessageTemplateId())
                        .phone(Long.valueOf(phone))
                        .supplierId(MOCK_SUPPLIER_ID)
                        .supplierName(MOCK_SUPPLIER_NAME)
                        .msgContent(smsParam.getContent())
                        .seriesId("MOCK-" + IdUtil.fastSimpleUUID())
                        .chargingNum(0)
                        .status(SmsStatus.SEND_SUCCESS.getCode())
                        .reportContent("模拟短信发送成功（未调用第三方，未真实发送）")
                        .created(now)
                        .updated(now)
                        .build();
                records.add(record);
                log.info("模拟短信发送成功：phone={}, templateId={}, seriesId={}，未调用第三方，未真实发送",
                        phone, smsParam.getMessageTemplateId(), record.getSeriesId());
            } catch (NumberFormatException e) {
                log.error("MockSmsScript 手机号格式错误：phone={}, templateId={}", phone, smsParam.getMessageTemplateId());
            }
        }
        return records;
    }

    @Override
    public List<SmsRecord> pull(Integer id) {
        return Collections.emptyList();
    }
}
