package com.java3y.austin.web.controller;

import com.java3y.austin.auth.service.CurrentUserService;
import com.java3y.austin.billing.payment.domain.PaymentOrder;
import com.java3y.austin.billing.payment.domain.PaymentOrderStatus;
import com.java3y.austin.billing.payment.domain.PaymentProviderType;
import com.java3y.austin.billing.payment.service.PaymentCheckoutOutcome;
import com.java3y.austin.billing.payment.service.PaymentCheckoutService;
import com.java3y.austin.billing.payment.service.PaymentOrderTransactionService;
import com.java3y.austin.web.annotation.AustinAspect;
import com.java3y.austin.web.annotation.AustinResult;
import com.java3y.austin.web.exception.CommonException;
import com.java3y.austin.web.vo.PaymentCheckoutRequestVo;
import com.java3y.austin.web.vo.PaymentOrderPageVo;
import com.java3y.austin.web.vo.PaymentOrderVo;
import com.java3y.austin.web.vo.QuotaPackageVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@AustinAspect
@AustinResult
@RestController
@RequestMapping("/billing")
@Api("支付与额度购买")
public class PaymentController {
    private final PaymentCheckoutService checkoutService;
    private final PaymentOrderTransactionService transactionService;
    private final CurrentUserService currentUserService;

    public PaymentController(PaymentCheckoutService checkoutService,
                             PaymentOrderTransactionService transactionService,
                             CurrentUserService currentUserService) {
        this.checkoutService = checkoutService;
        this.transactionService = transactionService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/packages")
    @ApiOperation("查询当前可购买的固定额度包")
    public List<QuotaPackageVo> packages() {
        return transactionService.listActivePackages().stream()
                .map(QuotaPackageVo::from).collect(Collectors.toList());
    }

    @GetMapping("/payments/providers")
    @ApiOperation("查询已启用的支付渠道")
    public List<PaymentProviderType> providers() {
        return checkoutService.availableProviders();
    }

    @GetMapping("/payments")
    @ApiOperation("分页查询当前租户支付订单")
    public PaymentOrderPageVo payments(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int perPage,
                                       @RequestParam(required = false) String status) {
        Page<PaymentOrder> result = transactionService.listTenantOrders(
                currentUserService.tenantId(), parseStatus(status), page, perPage);
        return PaymentOrderPageVo.builder().count(result.getTotalElements())
                .rows(result.getContent().stream().map(PaymentOrderVo::from)
                        .collect(Collectors.toList())).build();
    }

    @PostMapping("/payments/checkout")
    @ApiOperation("创建沙箱支付Checkout；同一幂等键返回同一订单")
    public PaymentOrderVo checkout(@RequestBody PaymentCheckoutRequestVo request) {
        requireAdmin();
        PaymentCheckoutOutcome outcome = checkoutService.checkout(currentUserService.tenantId(),
                request.getPackageCode(), PaymentProviderType.parse(request.getProvider()),
                request.getIdempotencyKey());
        return PaymentOrderVo.from(outcome.getOrder(), outcome.getResult());
    }

    @GetMapping("/payments/{orderNo}")
    @ApiOperation("查询当前租户支付订单")
    public PaymentOrderVo order(@PathVariable String orderNo) {
        PaymentCheckoutOutcome outcome = checkoutService.describe(
                currentUserService.tenantId(), orderNo);
        return PaymentOrderVo.from(outcome.getOrder(), outcome.getResult());
    }

    private void requireAdmin() {
        if (!currentUserService.isAdmin()) {
            throw new CommonException("仅租户管理员可以购买消息额度");
        }
    }

    private PaymentOrderStatus parseStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return null;
        }
        try {
            return PaymentOrderStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("支付订单状态无效");
        }
    }
}
