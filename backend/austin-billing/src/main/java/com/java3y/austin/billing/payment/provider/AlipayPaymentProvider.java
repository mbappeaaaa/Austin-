package com.java3y.austin.billing.payment.provider;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.java3y.austin.billing.payment.domain.PaymentProviderType;
import com.java3y.austin.billing.payment.domain.VerifiedPaymentStatus;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 支付宝沙箱支付提供方（电脑网站支付 alipay.trade.page.pay）。
 * 异步通知复用 /billing/payments/webhooks/alipay，验签使用支付宝公钥。
 */
public class AlipayPaymentProvider implements PaymentProvider {

    private static final String CHARSET = "UTF-8";
    private static final String SIGN_TYPE = "RSA2";
    private static final String PRODUCT_CODE = "FAST_INSTANT_TRADE_PAY";
    private static final String CURRENCY_CNY = "CNY";
    private static final DateTimeFormatter ALIPAY_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId ALIPAY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String REDIRECT_PATH = "/billing/payments/alipay/redirect/";

    private final AlipayClient alipayClient;
    private final String appId;
    private final String alipayPublicKey;
    private final String notifyUrl;
    private final String checkoutBaseUrl;

    public AlipayPaymentProvider(AlipayClient alipayClient, String appId, String alipayPublicKey,
                                 String notifyUrl, String checkoutBaseUrl) {
        if (isBlank(appId) || isBlank(alipayPublicKey) || isBlank(notifyUrl)
                || isBlank(checkoutBaseUrl)) {
            throw new IllegalStateException("支付宝沙箱配置(appId/支付宝公钥/异步通知地址/跳转基础地址)必须由运行时注入");
        }
        this.alipayClient = alipayClient;
        this.appId = appId;
        this.alipayPublicKey = alipayPublicKey;
        this.notifyUrl = notifyUrl;
        this.checkoutBaseUrl = checkoutBaseUrl;
    }

    @Override
    public PaymentProviderType type() {
        return PaymentProviderType.ALIPAY;
    }

    @Override
    public PaymentCheckoutResult createCheckout(PaymentCheckoutRequest request) {
        if (!CURRENCY_CNY.equalsIgnoreCase(request.getCurrency())) {
            throw new PaymentProviderException("支付宝沙箱仅支持CNY额度包");
        }
        // pageExecute为本地签名构建表单，此处预生成一次用于尽早暴露密钥/签名配置错误
        buildPagePayForm(request.getOrderNo(), request.getPackageName(),
                request.getAmountMinor(), request.getSuccessUrl());
        return new PaymentCheckoutResult(null,
                checkoutBaseUrl + REDIRECT_PATH + request.getOrderNo());
    }

    @Override
    public VerifiedPaymentEvent verifyWebhook(String payload, String signature) {
        // 支付宝异步通知为form表单提交，签名信息(sign/sign_type)包含在payload内
        Map<String, String> params = HttpUtil.decodeParamMap(payload, StandardCharsets.UTF_8);
        boolean signVerified;
        try {
            signVerified = AlipaySignature.rsaCheckV1(params, alipayPublicKey, CHARSET, SIGN_TYPE);
        } catch (AlipayApiException exception) {
            throw new PaymentProviderException("支付宝异步通知验签异常", exception);
        }
        if (!signVerified) {
            throw new PaymentProviderException("支付宝异步通知验签失败");
        }
        if (!appId.equals(params.get("app_id"))) {
            throw new PaymentProviderException("支付宝异步通知appId与配置不一致");
        }
        String tradeStatus = params.getOrDefault("trade_status", "");
        return VerifiedPaymentEvent.builder()
                .provider(type())
                .providerEventId(resolveEventId(params, payload))
                .providerOrderId(params.get("trade_no"))
                .orderNo(params.get("out_trade_no"))
                .eventType("alipay.trade.notify." + tradeStatus)
                .status(mapStatus(tradeStatus))
                .amountMinor(toMinor(params.get("total_amount")))
                .currency(CURRENCY_CNY)
                .providerCreatedAt(toEpochSecond(params.get("gmt_payment")))
                .liveMode(false)
                .payloadHash(DigestUtil.sha256Hex(payload))
                .build();
    }

    /**
     * 生成电脑网站支付的自动提交表单（pageExecute为本地签名，不发起网络请求）。
     */
    public String buildPagePayForm(String orderNo, String subject, long amountMinor, String returnUrl) {
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setReturnUrl(returnUrl);
        request.setNotifyUrl(notifyUrl);
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", orderNo);
        bizContent.put("total_amount", toYuan(amountMinor));
        bizContent.put("subject", subject);
        bizContent.put("product_code", PRODUCT_CODE);
        request.setBizContent(bizContent.toString());
        try {
            return alipayClient.pageExecute(request).getBody();
        } catch (AlipayApiException exception) {
            throw new PaymentProviderException("支付宝支付表单生成失败", exception);
        }
    }

    /**
     * 按商户订单号发起退款（支持部分退款，累计不超过原订单金额）。
     */
    public AlipayTradeRefundResponse refund(String orderNo, String refundAmountYuan, String refundReason) {
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        AlipayTradeRefundModel model = new AlipayTradeRefundModel();
        model.setOutTradeNo(orderNo);
        model.setRefundAmount(refundAmountYuan);
        model.setRefundReason(refundReason);
        request.setBizModel(model);
        try {
            return alipayClient.execute(request);
        } catch (AlipayApiException exception) {
            throw new PaymentProviderException("支付宝退款请求失败", exception);
        }
    }

    private VerifiedPaymentStatus mapStatus(String tradeStatus) {
        if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
            return VerifiedPaymentStatus.SUCCEEDED;
        }
        if ("TRADE_CLOSED".equals(tradeStatus)) {
            return VerifiedPaymentStatus.FAILED;
        }
        // WAIT_BUYER_PAY 等中间状态暂不处理，等待后续通知
        return VerifiedPaymentStatus.IRRELEVANT;
    }

    private String resolveEventId(Map<String, String> params, String payload) {
        String notifyId = params.get("notify_id");
        return isBlank(notifyId) ? "notify:" + DigestUtil.sha256Hex(payload) : notifyId;
    }

    private Long toMinor(String totalAmountYuan) {
        if (isBlank(totalAmountYuan)) {
            return null;
        }
        try {
            return new BigDecimal(totalAmountYuan).movePointRight(2).longValueExact();
        } catch (NumberFormatException exception) {
            throw new PaymentProviderException("支付宝异步通知金额格式非法", exception);
        }
    }

    private Integer toEpochSecond(String gmtTime) {
        if (isBlank(gmtTime)) {
            return null;
        }
        try {
            return Math.toIntExact(LocalDateTime.parse(gmtTime, ALIPAY_TIME_FORMATTER)
                    .atZone(ALIPAY_ZONE).toEpochSecond());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String toYuan(long amountMinor) {
        return BigDecimal.valueOf(amountMinor).movePointLeft(2).toPlainString();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
