package com.java3y.austin.web.controller;

import com.java3y.austin.auth.service.CurrentUserService;
import com.java3y.austin.billing.payment.config.ManagedPaymentProviders;
import com.java3y.austin.billing.payment.domain.PaymentProviderType;
import com.java3y.austin.common.vo.BasicResultVO;
import com.java3y.austin.web.annotation.AustinResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/** Deliberately not annotated AustinAspect: request bodies contain credentials. */
@AustinResult
@RestController
@RequestMapping("/admin/payment-channels")
public class PaymentChannelAdminController {
    private final ManagedPaymentProviders service;
    private final CurrentUserService currentUser;
    private final String adminOrigin;
    public PaymentChannelAdminController(ManagedPaymentProviders service, CurrentUserService currentUser,
            @Value("${austin.admin.origin:http://localhost:3000}") String adminOrigin) {
        this.service = service;
        this.currentUser = currentUser;
        this.adminOrigin = adminOrigin;
    }

    private void requirePlatformAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream().noneMatch(a -> "SYSTEM_ADMIN".equals(a.getAuthority())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    @GetMapping
    public List<ManagedPaymentProviders.View> list(HttpServletResponse response) {
        requirePlatformAdmin();
        response.setHeader("Cache-Control", "no-store");
        return service.list();
    }

    @PutMapping("/{provider}")
    public ManagedPaymentProviders.View save(@PathVariable String provider,
            @RequestBody ManagedPaymentProviders.Update update, HttpServletRequest request, HttpServletResponse response) {
        requirePlatformAdmin();
        // Session-based secret administration must reject cross-site writes even when global CSRF is disabled.
        if (!adminOrigin.equals(request.getHeader("Origin"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        response.setHeader("Cache-Control", "no-store");
        return service.save(PaymentProviderType.parse(provider), update, currentUser.username());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BasicResultVO<Void> invalid(IllegalArgumentException e) { return BasicResultVO.fail(e.getMessage()); }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public BasicResultVO<Void> forbidden() { return BasicResultVO.fail("仅平台管理员可以从受信任的管理端修改支付配置"); }
}
