package com.java3y.austin.web.service;

import com.java3y.austin.service.api.domain.TraceResponse;
import com.java3y.austin.service.api.service.TraceService;
import com.java3y.austin.support.dao.MessageTemplateDao;
import com.java3y.austin.support.dao.SmsRecordDao;
import com.java3y.austin.support.utils.RedisUtils;
import com.java3y.austin.web.service.impl.DataServiceImpl;
import com.java3y.austin.web.vo.amis.UserTimeLineVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class DataServiceImplTest {

    private static final Long TENANT_ID = 7L;

    private RedisUtils redisUtils;
    private TraceService traceService;
    private DataServiceImpl dataService;

    @BeforeEach
    void setUp() {
        redisUtils = mock(RedisUtils.class);
        traceService = mock(TraceService.class);
        dataService = new DataServiceImpl();
        ReflectionTestUtils.setField(dataService, "redisUtils", redisUtils);
        ReflectionTestUtils.setField(dataService, "traceService", traceService);
        ReflectionTestUtils.setField(dataService, "messageTemplateDao", mock(MessageTemplateDao.class));
        ReflectionTestUtils.setField(dataService, "smsRecordDao", mock(SmsRecordDao.class));
    }

    @Test
    void shouldReturnEmptyImmediatelyWhenBusinessIndexIsMissing() {
        String businessId = "1000000120260827";
        when(redisUtils.lRange(anyString(), anyLong(), anyLong())).thenReturn(Collections.emptyList());
        when(traceService.traceByMessageId(businessId)).thenReturn(new TraceResponse()
                .setData(Collections.emptyList()));

        MessageTemplateDao messageTemplateDao = (MessageTemplateDao) ReflectionTestUtils.getField(
                dataService, "messageTemplateDao");
        when(messageTemplateDao.findByIdAndTenantId(anyLong(), org.mockito.ArgumentMatchers.eq(TENANT_ID)))
                .thenReturn(Optional.of(com.java3y.austin.support.domain.MessageTemplate.builder().build()));

        UserTimeLineVo result = dataService.getTraceUserInfo(businessId, TENANT_ID);

        assertNotNull(result);
        assertTrue(result.getItems().isEmpty());
        verify(redisUtils).lRange(businessId, 0, -1);
        verify(redisUtils).lRange("Austin:BusinessId:" + businessId, 0, -1);
        verifyNoMoreInteractions(redisUtils);
    }

    @Test
    void shouldHideBusinessTraceOwnedByAnotherTenant() {
        String businessId = "1000000120260827";
        MessageTemplateDao messageTemplateDao = (MessageTemplateDao) ReflectionTestUtils.getField(
                dataService, "messageTemplateDao");
        when(messageTemplateDao.findByIdAndTenantId(anyLong(), org.mockito.ArgumentMatchers.eq(TENANT_ID)))
                .thenReturn(Optional.empty());

        UserTimeLineVo result = dataService.getTraceUserInfo(businessId, TENANT_ID);

        assertNotNull(result);
        assertTrue(result.getItems().isEmpty());
        verify(redisUtils).lRange(businessId, 0, -1);
        verifyNoMoreInteractions(redisUtils);
    }
}
