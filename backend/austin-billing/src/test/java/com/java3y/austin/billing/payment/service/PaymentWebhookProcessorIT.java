package com.java3y.austin.billing.payment.service;

import com.java3y.austin.billing.domain.QuotaAccount;
import com.java3y.austin.billing.payment.domain.PaymentOrder;
import com.java3y.austin.billing.payment.domain.PaymentOrderStatus;
import com.java3y.austin.billing.payment.domain.PaymentProviderType;
import com.java3y.austin.billing.payment.domain.VerifiedPaymentStatus;
import com.java3y.austin.billing.payment.provider.VerifiedPaymentEvent;
import com.java3y.austin.billing.payment.repository.OutboxEventRepository;
import com.java3y.austin.billing.payment.repository.PaymentEventRepository;
import com.java3y.austin.billing.payment.repository.PaymentOrderRepository;
import com.java3y.austin.billing.repository.QuotaAccountRepository;
import com.java3y.austin.billing.repository.QuotaLedgerRepository;
import com.java3y.austin.billing.repository.QuotaReservationRepository;
import com.java3y.austin.billing.service.QuotaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@SpringBootTest(classes = PaymentWebhookProcessorIT.TestApplication.class,
        properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false"})
class PaymentWebhookProcessorIT {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:5.7.44")
            .withDatabaseName("austin_payment_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    private PaymentWebhookProcessor processor;
    @Autowired
    private PaymentOrderRepository orderRepository;
    @Autowired
    private PaymentEventRepository eventRepository;
    @Autowired
    private OutboxEventRepository outboxRepository;
    @Autowired
    private QuotaAccountRepository accountRepository;
    @Autowired
    private QuotaLedgerRepository ledgerRepository;
    @Autowired
    private QuotaService quotaService;

    @Test
    void shouldCommitOrderQuotaLedgerAndOutboxOnce() {
        long tenantId = 901L;
        quotaService.initializeAccount(tenantId, 0);
        orderRepository.saveAndFlush(order(tenantId, "pay-it-1"));

        processor.process(success(tenantId, "pay-it-1", "evt-it-1"));
        processor.process(success(tenantId, "pay-it-1", "evt-it-1"));

        PaymentOrder stored = orderRepository.findByOrderNo("pay-it-1")
                .orElseThrow(AssertionError::new);
        QuotaAccount account = accountRepository.findByTenantId(tenantId)
                .orElseThrow(AssertionError::new);
        assertEquals(PaymentOrderStatus.SUCCEEDED, stored.getStatus());
        assertEquals(1000L, account.getAvailableQuota());
        assertEquals(1L, ledgerRepository.count());
        assertEquals(1L, eventRepository.count());
        assertEquals(1L, outboxRepository.count());
    }

    @Test
    void shouldRollbackEventAndOrderWhenQuotaCreditFails() {
        long tenantId = 902L;
        orderRepository.saveAndFlush(order(tenantId, "pay-it-2"));

        assertThrows(IllegalStateException.class,
                () -> processor.process(success(tenantId, "pay-it-2", "evt-it-2")));

        PaymentOrder stored = orderRepository.findByOrderNo("pay-it-2")
                .orElseThrow(AssertionError::new);
        assertEquals(PaymentOrderStatus.PENDING, stored.getStatus());
        assertEquals(0L, eventRepository.findByProviderAndProviderEventId(
                PaymentProviderType.MOCK, "evt-it-2").isPresent() ? 1L : 0L);
        assertEquals(0L, outboxRepository.count());
    }

    private PaymentOrder order(long tenantId, String orderNo) {
        int now = Math.toIntExact(Instant.now().getEpochSecond());
        return PaymentOrder.builder().tenantId(tenantId).orderNo(orderNo)
                .idempotencyKey("idem-" + orderNo).provider(PaymentProviderType.MOCK)
                .providerOrderId("session-" + orderNo).packageCode("SANDBOX_STARTER")
                .packageNameSnapshot("Sandbox Starter").currency("USD")
                .amountMinor(100L).quotaAmount(1000L).status(PaymentOrderStatus.PENDING)
                .created(now).updated(now).build();
    }

    private VerifiedPaymentEvent success(long tenantId, String orderNo, String eventId) {
        return VerifiedPaymentEvent.builder().provider(PaymentProviderType.MOCK)
                .providerEventId(eventId).providerOrderId("session-" + orderNo)
                .orderNo(orderNo).eventType("mock.checkout.succeeded")
                .status(VerifiedPaymentStatus.SUCCEEDED).amountMinor(100L).currency("USD")
                .providerCreatedAt(Math.toIntExact(Instant.now().getEpochSecond()))
                .liveMode(false).payloadHash("hash-" + eventId).build();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.java3y.austin.billing")
    @EnableJpaRepositories(basePackages = "com.java3y.austin.billing")
    static class TestApplication {
        @Bean
        QuotaService quotaService(QuotaAccountRepository accountRepository,
                                  QuotaReservationRepository reservationRepository,
                                  QuotaLedgerRepository ledgerRepository,
                                  com.java3y.austin.billing.service.QuotaAlertOutboxAppender alertOutboxAppender) {
            return new QuotaService(accountRepository, reservationRepository,
                    ledgerRepository, alertOutboxAppender);
        }

        @Bean
        com.java3y.austin.billing.service.QuotaAlertOutboxAppender quotaAlertOutboxAppender(
                OutboxEventRepository outboxRepository) {
            return new com.java3y.austin.billing.service.QuotaAlertOutboxAppender(outboxRepository);
        }

        @Bean
        PaymentWebhookProcessor paymentWebhookProcessor(PaymentEventRepository eventRepository,
                                                         PaymentOrderRepository orderRepository,
                                                         OutboxEventRepository outboxRepository,
                                                         QuotaService quotaService) {
            return new PaymentWebhookProcessor(eventRepository, orderRepository,
                    outboxRepository, quotaService);
        }
    }
}
