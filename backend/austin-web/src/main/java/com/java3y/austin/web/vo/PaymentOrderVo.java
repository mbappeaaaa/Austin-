package com.java3y.austin.web.vo;

import com.java3y.austin.billing.payment.domain.PaymentOrder;
import com.java3y.austin.billing.payment.provider.PaymentCheckoutResult;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PaymentOrderVo {
    String orderNo;
    String provider;
    String packageCode;
    String packageName;
    String currency;
    Long amountMinor;
    Long quotaAmount;
    String status;
    String checkoutUrl;
    String publishableKey;
    String clientSecret;
    String displayMode;
    Integer paidAt;
    Integer created;

    public static PaymentOrderVo from(PaymentOrder order) {
        return from(order, null);
    }

    public static PaymentOrderVo from(PaymentOrder order, PaymentCheckoutResult result) {
        String checkoutUrl = order.getCheckoutUrl();
        String publishableKey = null;
        String clientSecret = null;
        if (result != null) {
            if (result.getCheckoutUrl() != null) {
                checkoutUrl = result.getCheckoutUrl();
            }
            publishableKey = result.getPublishableKey();
            clientSecret = result.getClientSecret();
        }
        String displayMode = clientSecret != null ? "CLIENT_SECRET"
                : (checkoutUrl != null ? "REDIRECT_URL" : null);
        return PaymentOrderVo.builder().orderNo(order.getOrderNo())
                .provider(order.getProvider().name()).packageCode(order.getPackageCode())
                .packageName(order.getPackageNameSnapshot()).currency(order.getCurrency())
                .amountMinor(order.getAmountMinor()).quotaAmount(order.getQuotaAmount())
                .status(order.getStatus().name()).checkoutUrl(checkoutUrl)
                .publishableKey(publishableKey).clientSecret(clientSecret).displayMode(displayMode)
                .paidAt(order.getPaidAt()).created(order.getCreated()).build();
    }
}
