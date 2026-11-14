package com.java3y.austin.billing.payment.service;

import com.alibaba.fastjson2.JSON;
import com.java3y.austin.billing.payment.domain.OutboxEvent;
import com.java3y.austin.billing.payment.domain.OutboxStatus;
import com.java3y.austin.billing.payment.domain.PaymentEvent;
import com.java3y.austin.billing.payment.domain.PaymentEventStatus;
import com.java3y.austin.billing.payment.domain.PaymentOrder;
import com.java3y.austin.billing.payment.domain.PaymentOrderStatus;
import com.java3y.austin.billing.payment.domain.VerifiedPaymentStatus;
import com.java3y.austin.billing.payment.provider.VerifiedPaymentEvent;
import com.java3y.austin.billing.payment.repository.OutboxEventRepository;
import com.java3y.austin.billing.payment.repository.PaymentEventRepository;
import com.java3y.austin.billing.payment.repository.PaymentOrderRepository;
import com.java3y.austin.billing.service.QuotaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class PaymentWebhookProcessor {
    public static final String PAYMENT_SUCCEEDED_EVENT = "billing.payment.succeeded.v1";

    private final PaymentEventRepository eventRepository;
    private final PaymentOrderRepository orderRepository;
    private final OutboxEventRepository outboxRepository;
    private final QuotaService quotaService;

    public PaymentWebhookProcessor(PaymentEventRepository eventRepository,
                                   PaymentOrderRepository orderRepository,
                                   OutboxEventRepository outboxRepository,
                                   QuotaService quotaService) {
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.quotaService = quotaService;
    }

    @Transactional
    public PaymentWebhookResult process(VerifiedPaymentEvent verified) {
        if (verified.isLiveMode()) {
            throw new PaymentOperationConflictException("Sandbox版本拒绝处理Live支付回调");
        }
        int now = now();
        int inserted = eventRepository.insertIgnore(verified.getProvider().name(),
                verified.getProviderEventId(), verified.getProviderOrderId(), verified.getOrderNo(),
                verified.getEventType(), verified.getPayloadHash(), verified.getProviderCreatedAt(), now);
        PaymentEvent event = eventRepository.findByProviderAndProviderEventId(
                        verified.getProvider(), verified.getProviderEventId())
                .orElseThrow(() -> new IllegalStateException("支付事件登记后无法读取"));
        if (inserted == 0) {
            if (!Objects.equals(event.getPayloadHash(), verified.getPayloadHash())) {
                throw new PaymentOperationConflictException("相同支付事件ID对应不同回调内容");
            }
            return new PaymentWebhookResult(true, false, event.getOrderNo());
        }
        if (verified.getStatus() == VerifiedPaymentStatus.IRRELEVANT) {
            finishEvent(event, PaymentEventStatus.IGNORED, now);
            return new PaymentWebhookResult(false, false, verified.getOrderNo());
        }

        PaymentOrder order = findOrder(verified);
        validateOrder(order, verified);
        attachProviderOrderIfNecessary(order, verified);
        boolean changed = applyState(order, verified, now);
        if (changed && order.getStatus() == PaymentOrderStatus.SUCCEEDED) {
            quotaService.credit(order.getTenantId(), order.getQuotaAmount(),
                    "payment-credit:" + order.getOrderNo(), "PAYMENT", order.getOrderNo());
            appendPaymentSucceededOutbox(order, now);
        }
        finishEvent(event, changed ? PaymentEventStatus.PROCESSED : PaymentEventStatus.IGNORED, now);
        return new PaymentWebhookResult(false, changed, order.getOrderNo());
    }

    private PaymentOrder findOrder(VerifiedPaymentEvent verified) {
        PaymentOrder order = null;
        if (verified.getOrderNo() != null && !verified.getOrderNo().trim().isEmpty()) {
            order = orderRepository.findLockedByOrderNo(verified.getOrderNo()).orElse(null);
        }
        if (order == null && verified.getProviderOrderId() != null) {
            order = orderRepository.findLockedByProviderAndProviderOrderId(
                    verified.getProvider(), verified.getProviderOrderId()).orElse(null);
        }
        if (order == null) {
            throw new IllegalStateException("支付回调暂未匹配到本地订单，请提供方稍后重试");
        }
        return order;
    }

    private void validateOrder(PaymentOrder order, VerifiedPaymentEvent verified) {
        if (order.getProvider() != verified.getProvider()) {
            throw new PaymentOperationConflictException("支付回调提供方与订单不一致");
        }
        if (verified.getOrderNo() != null && !Objects.equals(order.getOrderNo(), verified.getOrderNo())) {
            throw new PaymentOperationConflictException("支付回调商户订单号不一致");
        }
        if (order.getProviderOrderId() != null && verified.getProviderOrderId() != null
                && !Objects.equals(order.getProviderOrderId(), verified.getProviderOrderId())) {
            throw new PaymentOperationConflictException("支付回调提供方订单号不一致");
        }
        if (verified.getStatus() == VerifiedPaymentStatus.SUCCEEDED) {
            if (!Objects.equals(order.getAmountMinor(), verified.getAmountMinor())
                    || !Objects.equals(order.getCurrency(), verified.getCurrency())) {
                throw new PaymentOperationConflictException("支付成功金额或币种与订单不一致");
            }
        }
    }

    private void attachProviderOrderIfNecessary(PaymentOrder order, VerifiedPaymentEvent verified) {
        if (order.getProviderOrderId() == null && verified.getProviderOrderId() != null) {
            order.setProviderOrderId(verified.getProviderOrderId());
        }
    }

    private boolean applyState(PaymentOrder order, VerifiedPaymentEvent verified, int now) {
        PaymentOrderStatus current = order.getStatus();
        if (verified.getStatus() == VerifiedPaymentStatus.SUCCEEDED) {
            if (current == PaymentOrderStatus.SUCCEEDED) {
                return false;
            }
            order.setStatus(PaymentOrderStatus.SUCCEEDED);
            order.setPaidAt(verified.getProviderCreatedAt() == null
                    ? now : verified.getProviderCreatedAt());
        } else if (verified.getStatus() == VerifiedPaymentStatus.FAILED) {
            if (current != PaymentOrderStatus.CREATED && current != PaymentOrderStatus.PENDING) {
                return false;
            }
            order.setStatus(PaymentOrderStatus.FAILED);
        } else if (verified.getStatus() == VerifiedPaymentStatus.EXPIRED) {
            if (current != PaymentOrderStatus.CREATED && current != PaymentOrderStatus.PENDING) {
                return false;
            }
            order.setStatus(PaymentOrderStatus.EXPIRED);
        } else {
            return false;
        }
        order.setUpdated(now);
        return true;
    }

    private void appendPaymentSucceededOutbox(PaymentOrder order, int now) {
        String eventKey = "payment-succeeded:" + order.getOrderNo();
        if (outboxRepository.findByEventKey(eventKey).isPresent()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", order.getTenantId());
        payload.put("orderNo", order.getOrderNo());
        payload.put("provider", order.getProvider().name());
        payload.put("amountMinor", order.getAmountMinor());
        payload.put("currency", order.getCurrency());
        payload.put("quotaAmount", order.getQuotaAmount());
        payload.put("paidAt", order.getPaidAt());
        outboxRepository.save(OutboxEvent.builder().tenantId(order.getTenantId())
                .aggregateType("PAYMENT_ORDER").aggregateId(order.getOrderNo())
                .eventType(PAYMENT_SUCCEEDED_EVENT).eventKey(eventKey)
                .payload(JSON.toJSONString(payload)).status(OutboxStatus.PENDING)
                .attemptCount(0).maxAttempts(5).replayCount(0)
                .created(now).updated(now).build());
    }

    private void finishEvent(PaymentEvent event, PaymentEventStatus status, int now) {
        event.setEventStatus(status);
        event.setProcessedAt(now);
    }

    private int now() {
        return Math.toIntExact(Instant.now().getEpochSecond());
    }
}
