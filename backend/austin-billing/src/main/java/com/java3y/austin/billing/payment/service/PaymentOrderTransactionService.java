package com.java3y.austin.billing.payment.service;

import com.java3y.austin.billing.payment.domain.PaymentOrder;
import com.java3y.austin.billing.payment.domain.PaymentOrderStatus;
import com.java3y.austin.billing.payment.domain.PaymentProviderType;
import com.java3y.austin.billing.payment.domain.QuotaPackage;
import com.java3y.austin.billing.payment.provider.PaymentCheckoutResult;
import com.java3y.austin.billing.payment.repository.PaymentOrderRepository;
import com.java3y.austin.billing.payment.repository.QuotaPackageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class PaymentOrderTransactionService {
    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentOrderRepository orderRepository;
    private final QuotaPackageRepository packageRepository;

    public PaymentOrderTransactionService(PaymentOrderRepository orderRepository,
                                          QuotaPackageRepository packageRepository) {
        this.orderRepository = orderRepository;
        this.packageRepository = packageRepository;
    }

    @Transactional(readOnly = true)
    public List<QuotaPackage> listActivePackages() {
        return packageRepository.findAllByActiveTrueOrderByAmountMinorAsc();
    }

    @Transactional(readOnly = true)
    public Page<PaymentOrder> listTenantOrders(Long tenantId, PaymentOrderStatus status,
                                               int page, int pageSize) {
        requireTenant(tenantId);
        if (page <= 0 || pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("分页参数无效，每页最多100条");
        }
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize);
        if (status == null) {
            return orderRepository.findAllByTenantIdOrderByCreatedDescIdDesc(
                    tenantId, pageRequest);
        }
        return orderRepository.findAllByTenantIdAndStatusOrderByCreatedDescIdDesc(
                tenantId, status, pageRequest);
    }

    @Transactional
    public PaymentOrder findOrCreate(Long tenantId, String packageCode,
                                     PaymentProviderType provider, String idempotencyKey) {
        requireTenant(tenantId);
        requireText(packageCode, 64, "额度包编码不能为空或过长");
        requireText(idempotencyKey, 128, "支付幂等键不能为空或过长");
        QuotaPackage quotaPackage = packageRepository.findByPackageCodeAndActiveTrue(packageCode)
                .orElseThrow(() -> new IllegalArgumentException("额度包不存在或已下架"));
        if (!Boolean.TRUE.equals(quotaPackage.getSandboxOnly())) {
            throw new IllegalStateException("Sandbox版本只能购买沙箱额度包");
        }
        if (provider == PaymentProviderType.ALIPAY && !"CNY".equalsIgnoreCase(quotaPackage.getCurrency())) {
            throw new IllegalArgumentException("支付宝仅支持人民币（CNY）套餐，请选择人民币额度包");
        }
        String orderNo = "pay_" + UUID.randomUUID().toString().replace("-", "");
        int now = now();
        orderRepository.insertIgnore(tenantId, orderNo, idempotencyKey, provider.name(),
                quotaPackage.getPackageCode(), quotaPackage.getDisplayName(),
                quotaPackage.getCurrency(), quotaPackage.getAmountMinor(),
                quotaPackage.getQuotaAmount(), now);
        PaymentOrder order = orderRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("支付订单创建后无法读取"));
        if (order.getProvider() != provider
                || !Objects.equals(order.getPackageCode(), packageCode)) {
            throw new PaymentOperationConflictException("相同幂等键对应不同支付请求");
        }
        return order;
    }

    @Transactional
    public PaymentOrder attachCheckout(String orderNo, PaymentCheckoutResult checkout) {
        PaymentOrder order = orderRepository.findLockedByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("支付订单不存在"));
        if (order.getProviderOrderId() != null) {
            if (!Objects.equals(order.getProviderOrderId(), checkout.getProviderOrderId())) {
                throw new PaymentOperationConflictException("支付订单已关联其他提供方订单");
            }
            return order;
        }
        order.setProviderOrderId(checkout.getProviderOrderId());
        order.setCheckoutUrl(checkout.getCheckoutUrl());
        if (order.getStatus() == PaymentOrderStatus.CREATED) {
            order.setStatus(PaymentOrderStatus.PENDING);
        }
        order.setUpdated(now());
        return order;
    }

    @Transactional(readOnly = true)
    public PaymentOrder requireTenantOrder(Long tenantId, String orderNo) {
        requireTenant(tenantId);
        return orderRepository.findByTenantIdAndOrderNo(tenantId, orderNo)
                .orElseThrow(() -> new IllegalArgumentException("支付订单不存在"));
    }

    private void requireTenant(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("租户ID不能为空");
        }
    }

    private void requireText(String value, int maxLength, String message) {
        if (value == null || value.trim().isEmpty() || value.length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
    }

    private int now() {
        return Math.toIntExact(Instant.now().getEpochSecond());
    }
}
