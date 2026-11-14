package com.java3y.austin.web.controller;

import com.java3y.austin.auth.service.CurrentUserService;
import com.java3y.austin.billing.domain.QuotaAccount;
import com.java3y.austin.billing.service.QuotaService;
import com.java3y.austin.web.vo.QuotaThresholdUpdateVo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuotaControllerTest {
    @Test
    void shouldReadTenantFromPrincipalAndUpdateOnlyItsThreshold() {
        QuotaService quotas = mock(QuotaService.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.tenantId()).thenReturn(7L);
        when(quotas.updateWarningThreshold(7L, 100L)).thenReturn(account());
        QuotaThresholdUpdateVo request = new QuotaThresholdUpdateVo();
        request.setWarningThreshold(100L);

        assertEquals(100L, new QuotaController(quotas, currentUser)
                .updateWarningThreshold(request).getWarningThreshold());

        verify(quotas).updateWarningThreshold(7L, 100L);
    }

    private QuotaAccount account() {
        return QuotaAccount.builder().tenantId(7L).availableQuota(200L)
                .reservedQuota(5L).warningThreshold(100L).build();
    }
}
