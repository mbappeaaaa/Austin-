package com.java3y.austin.web.service;

import com.java3y.austin.support.dao.ChannelAccountDao;
import com.java3y.austin.support.domain.ChannelAccount;
import com.java3y.austin.web.exception.CommonException;
import com.java3y.austin.web.service.impl.ChannelAccountServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelAccountServiceImplTest {

    private static final Long TENANT_ID = 7L;
    private final ChannelAccountDao channelAccountDao = mock(ChannelAccountDao.class);
    private final ChannelAccountServiceImpl service = new ChannelAccountServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "channelAccountDao", channelAccountDao);
    }

    @Test
    void shouldScopeExistingAccountLookupByTenant() {
        ChannelAccount request = ChannelAccount.builder().id(99L).creator("alice").sendChannel(40).build();
        when(channelAccountDao.findByIdAndTenantId(99L, TENANT_ID)).thenReturn(Optional.empty());

        assertThrows(CommonException.class, () -> service.prepareForValidation(request, TENANT_ID));

        verify(channelAccountDao).findByIdAndTenantId(99L, TENANT_ID);
    }

    @Test
    void shouldOverrideClientTenantWhenSaving() {
        ChannelAccount request = ChannelAccount.builder().tenantId(999L).creator("alice")
                .sendChannel(40).build();
        when(channelAccountDao.save(request)).thenAnswer(invocation -> invocation.getArgument(0));

        ChannelAccount saved = service.save(request, TENANT_ID);

        assertEquals(TENANT_ID, saved.getTenantId());
        verify(channelAccountDao).save(request);
    }
}
