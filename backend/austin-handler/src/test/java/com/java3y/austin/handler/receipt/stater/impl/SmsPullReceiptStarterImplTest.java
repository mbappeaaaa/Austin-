package com.java3y.austin.handler.receipt.stater.impl;

import com.java3y.austin.common.constant.CommonConstant;
import com.java3y.austin.common.enums.ChannelType;
import com.java3y.austin.handler.script.SmsScript;
import com.java3y.austin.support.dao.ChannelAccountDao;
import com.java3y.austin.support.dao.SmsRecordDao;
import com.java3y.austin.support.domain.ChannelAccount;
import com.java3y.austin.support.domain.SmsRecord;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmsPullReceiptStarterImplTest {

    @Test
    void shouldPersistPulledReceiptWithAccountTenant() {
        ChannelAccountDao accountDao = mock(ChannelAccountDao.class);
        SmsRecordDao recordDao = mock(SmsRecordDao.class);
        SmsScript script = mock(SmsScript.class);
        ChannelAccount account = ChannelAccount.builder().id(12L).tenantId(7L)
                .sendChannel(ChannelType.SMS.getCode()).isDeleted(CommonConstant.FALSE)
                .accountConfig("{\"scriptName\":\"MockSmsScript\"}").build();
        SmsRecord record = SmsRecord.builder().seriesId("receipt-1").build();
        when(accountDao.findAllByIsDeletedEqualsAndSendChannelEquals(
                CommonConstant.FALSE, ChannelType.SMS.getCode()))
                .thenReturn(Collections.singletonList(account));
        when(script.pull(12)).thenReturn(Collections.singletonList(record));

        SmsPullReceiptStarterImpl starter = new SmsPullReceiptStarterImpl();
        ReflectionTestUtils.setField(starter, "channelAccountDao", accountDao);
        ReflectionTestUtils.setField(starter, "scriptMap", Collections.singletonMap("MockSmsScript", script));
        ReflectionTestUtils.setField(starter, "smsRecordDao", recordDao);

        starter.start();

        assertEquals(7L, record.getTenantId());
        verify(recordDao).saveAll(Collections.singletonList(record));
    }
}
