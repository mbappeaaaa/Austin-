package com.java3y.austin.web.controller;

import com.java3y.austin.auth.service.CurrentUserService;
import com.java3y.austin.billing.payment.config.ManagedPaymentProviders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentChannelAdminControllerTest {
    private final ManagedPaymentProviders service = mock(ManagedPaymentProviders.class);
    private final PaymentChannelAdminController controller = new PaymentChannelAdminController(service,
            mock(CurrentUserService.class), "http://localhost:3000");
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void authenticate(String permission) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, java.util.Collections.singletonList(new SimpleGrantedAuthority(permission))));
    }
    @Test void tenantPaymentPermissionCannotReadSecretsConfiguration() {
        authenticate("PAYMENT_PURCHASE");
        assertThrows(ResponseStatusException.class, () -> controller.list(new MockHttpServletResponse()));
        verifyNoInteractions(service);
    }
    @Test void crossOriginAndMissingOriginWritesAreRejected() {
        authenticate("SYSTEM_ADMIN");
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThrows(ResponseStatusException.class, () -> controller.save("STRIPE", new ManagedPaymentProviders.Update(), request, new MockHttpServletResponse()));
        request.addHeader("Origin", "https://untrusted.invalid");
        assertThrows(ResponseStatusException.class, () -> controller.save("STRIPE", new ManagedPaymentProviders.Update(), request, new MockHttpServletResponse()));
        verifyNoInteractions(service);
    }
    @Test void adminCanReadNonCacheableConfiguration() {
        authenticate("SYSTEM_ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.list(response);
        assertEquals("no-store", response.getHeader("Cache-Control"));
        verify(service).list();
    }
}
