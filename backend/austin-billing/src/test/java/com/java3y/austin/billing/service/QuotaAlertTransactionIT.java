package com.java3y.austin.billing.service;

import com.java3y.austin.billing.domain.QuotaAccount;
import com.java3y.austin.billing.exception.InsufficientQuotaException;
import com.java3y.austin.billing.payment.repository.OutboxEventRepository;
import com.java3y.austin.billing.repository.QuotaAccountRepository;
import com.java3y.austin.billing.repository.QuotaLedgerRepository;
import com.java3y.austin.billing.repository.QuotaReservationRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(classes = QuotaAlertTransactionIT.TestApplication.class,
        properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false"})
class QuotaAlertTransactionIT {
    private static final Long TENANT_ID = 77L;

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:5.7.44")
            .withDatabaseName("austin_quota_alert_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    private QuotaService quotaService;
    @Autowired
    private QuotaAccountRepository accountRepository;
    @Autowired
    private OutboxEventRepository outboxRepository;

    @Test
    void shouldCommitOneAlertPerCycleEvenWhenInsufficientReservationThrows() {
        quotaService.initializeAccount(TENANT_ID, 2L);
        quotaService.credit(TENANT_ID, 3L, "seed", "TEST", "seed");

        quotaService.reserve(TENANT_ID, 1L, "threshold", "MESSAGE", "1001:0");
        assertEquals(1L, outboxRepository.count());

        assertThrows(InsufficientQuotaException.class,
                () -> quotaService.reserve(TENANT_ID, 3L, "insufficient-1", "MESSAGE", "1001:1"));
        assertThrows(InsufficientQuotaException.class,
                () -> quotaService.reserve(TENANT_ID, 3L, "insufficient-2", "MESSAGE", "1001:2"));

        QuotaAccount alerted = accountRepository.findByTenantId(TENANT_ID)
                .orElseThrow(AssertionError::new);
        assertTrue(alerted.getThresholdAlertActive());
        assertTrue(alerted.getInsufficientAlertActive());
        assertEquals(2L, outboxRepository.count());

        quotaService.credit(TENANT_ID, 10L, "recover", "PAYMENT", "pay-1");
        QuotaAccount recovered = accountRepository.findByTenantId(TENANT_ID)
                .orElseThrow(AssertionError::new);
        assertEquals(1L, recovered.getAlertCycle());
        assertFalse(recovered.getThresholdAlertActive());
        assertFalse(recovered.getInsufficientAlertActive());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.java3y.austin.billing")
    @EnableJpaRepositories(basePackages = "com.java3y.austin.billing")
    static class TestApplication {
        @Bean
        QuotaAlertOutboxAppender quotaAlertOutboxAppender(OutboxEventRepository outboxRepository) {
            return new QuotaAlertOutboxAppender(outboxRepository);
        }

        @Bean
        QuotaService quotaService(QuotaAccountRepository accountRepository,
                                  QuotaReservationRepository reservationRepository,
                                  QuotaLedgerRepository ledgerRepository,
                                  QuotaAlertOutboxAppender alertOutboxAppender) {
            return new QuotaService(accountRepository, reservationRepository,
                    ledgerRepository, alertOutboxAppender);
        }
    }
}
