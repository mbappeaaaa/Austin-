package com.java3y.austin.web.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.java3y.austin.auth.domain.SysUser;
import com.java3y.austin.auth.repository.SysUserRepository;
import com.java3y.austin.billing.outbox.OutboxEventHandler;
import com.java3y.austin.billing.outbox.OutboxMessage;
import com.java3y.austin.billing.payment.service.PaymentWebhookProcessor;
import com.java3y.austin.support.service.InAppMessageService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class PaymentSucceededNotificationHandler implements OutboxEventHandler {
    private final SysUserRepository userRepository;
    private final InAppMessageService inAppMessageService;

    public PaymentSucceededNotificationHandler(SysUserRepository userRepository,
                                               InAppMessageService inAppMessageService) {
        this.userRepository = userRepository;
        this.inAppMessageService = inAppMessageService;
    }

    @Override
    public String eventType() {
        return PaymentWebhookProcessor.PAYMENT_SUCCEEDED_EVENT;
    }

    @Override
    public void handle(OutboxMessage message) {
        JSONObject payload = JSON.parseObject(message.getPayload());
        Long tenantId = payload.getLong("tenantId");
        String orderNo = payload.getString("orderNo");
        Long amountMinor = payload.getLong("amountMinor");
        Long quotaAmount = payload.getLong("quotaAmount");
        String currency = payload.getString("currency");
        if (!Objects.equals(message.getTenantId(), tenantId)
                || !Objects.equals(message.getAggregateId(), orderNo)
                || amountMinor == null || amountMinor < 0
                || quotaAmount == null || quotaAmount <= 0) {
            throw new IllegalArgumentException("支付成功Outbox载荷与事件信封不一致");
        }
        List<String> receivers = userRepository
                .findAllByTenantIdAndRoleAndEnabledTrueOrderByIdAsc(tenantId, "ADMIN")
                .stream().map(SysUser::getUsername).collect(Collectors.toList());
        if (receivers.isEmpty()) {
            throw new IllegalStateException("租户没有可接收支付通知的管理员");
        }
        String title = "消息额度购买成功";
        String content = "订单 " + orderNo + " 已支付成功，支付金额 "
                + formatMoney(amountMinor, currency) + "，已增加消息额度 " + quotaAmount + "。";
        inAppMessageService.storeSystemMessage(tenantId, receivers, title, content,
                "/billing/payments/" + orderNo, message.getEventKey());
    }

    private String formatMoney(long amountMinor, String currencyCode) {
        Currency currency = Currency.getInstance(currencyCode);
        int scale = Math.max(currency.getDefaultFractionDigits(), 0);
        return currencyCode + " " + BigDecimal.valueOf(amountMinor, scale).toPlainString();
    }
}
