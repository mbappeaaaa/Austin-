package com.java3y.austin.billing.payment.provider;

import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.java3y.austin.billing.payment.domain.VerifiedPaymentStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlipayPaymentProviderTest {
    @Test void pagePayPreservesOfficialProductAndAmount() throws Exception {
        AlipayClient client = mock(AlipayClient.class);
        AlipayTradePagePayResponse response = new AlipayTradePagePayResponse();
        response.setBody("<form>signed</form>");
        when(client.pageExecute(any(AlipayTradePagePayRequest.class))).thenReturn(response);
        AlipayPaymentProvider provider = new AlipayPaymentProvider(client, "2026000000000000", "test-public-key",
                "https://merchant.example/notify", "http://localhost:3000/api");
        assertEquals("<form>signed</form>", provider.buildPagePayForm("pay_1", "人民币额度包", 100L, "https://merchant.example/return"));
        ArgumentCaptor<AlipayTradePagePayRequest> captor = ArgumentCaptor.forClass(AlipayTradePagePayRequest.class);
        verify(client).pageExecute(captor.capture());
        com.alibaba.fastjson2.JSONObject content = com.alibaba.fastjson2.JSON.parseObject(captor.getValue().getBizContent());
        assertEquals("FAST_INSTANT_TRADE_PAY", content.getString("product_code"));
        assertEquals("1.00", content.getString("total_amount"));
        assertEquals("https://merchant.example/notify", captor.getValue().getNotifyUrl());
    }
    @Test void verifiesRsa2NotificationAndRejectsTampering() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        String privateKey = Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded());
        String publicKey = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        AlipayPaymentProvider provider = new AlipayPaymentProvider(mock(AlipayClient.class), "2026000000000000", publicKey,
                "https://merchant.example/notify", "http://localhost:3000/api");
        Map<String, String> signed = new HashMap<>();
        signed.put("app_id", "2026000000000000");
        signed.put("trade_status", "TRADE_SUCCESS");
        signed.put("total_amount", "1.00");
        signed.put("trade_no", "trade_1");
        signed.put("out_trade_no", "pay_1");
        signed.put("notify_id", "notify_1");
        Map<String, String> params = new TreeMap<>(signed);
        params.put("sign", AlipaySignature.rsaSign(AlipaySignature.getSignContent(params), privateKey, "UTF-8", "RSA2"));
        params.put("sign_type", "RSA2");
        VerifiedPaymentEvent event = provider.verifyWebhook(encode(params), null);
        assertEquals(VerifiedPaymentStatus.SUCCEEDED, event.getStatus());
        assertEquals(100L, event.getAmountMinor());
        assertEquals("CNY", event.getCurrency());
        params.put("total_amount", "99.99");
        assertThrows(PaymentProviderException.class, () -> provider.verifyWebhook(encode(params), null));
    }
    private String encode(Map<String, String> params) throws Exception {
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (query.length() > 0) query.append('&');
            query.append(e.getKey()).append('=').append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }
        return query.toString();
    }
}
