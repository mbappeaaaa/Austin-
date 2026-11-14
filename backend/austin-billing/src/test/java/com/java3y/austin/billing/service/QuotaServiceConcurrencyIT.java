package com.java3y.austin.billing.service;

import com.java3y.austin.billing.domain.QuotaAccount;
import com.java3y.austin.billing.repository.QuotaAccountRepository;
import com.java3y.austin.billing.repository.QuotaLedgerRepository;
import com.java3y.austin.billing.repository.QuotaReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@SpringBootTest(classes = QuotaServiceConcurrencyIT.TestApplication.class,
        properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false"})
class QuotaServiceConcurrencyIT {

    private static final Long TENANT_ID = 99L;

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:5.7.44")
            .withDatabaseName("austin_billing_test");

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
    private QuotaReservationRepository reservationRepository;
    @Autowired
    private QuotaLedgerRepository ledgerRepository;

    @Test
    void shouldNeverOverReserveWhenRequestsRaceForLastQuota() throws Exception {
        quotaService.initializeAccount(TENANT_ID, 0);
        quotaService.credit(TENANT_ID, 1, "seed-credit", "TEST", "seed");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> first = reservationAttempt("race-1", ready, start);
            Callable<Boolean> second = reservationAttempt("race-2", ready, start);
            Future<Boolean> firstResult = executor.submit(first);
            Future<Boolean> secondResult = executor.submit(second);
            ready.await();
            start.countDown();
            int success = (firstResult.get() ? 1 : 0) + (secondResult.get() ? 1 : 0);

            QuotaAccount account = accountRepository.findByTenantId(TENANT_ID).orElseThrow(AssertionError::new);
            assertEquals(1, success);
            assertEquals(0L, account.getAvailableQuota());
            assertEquals(1L, account.getReservedQuota());
            assertEquals(1L, reservationRepository.count());
            assertEquals(2L, ledgerRepository.count());
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Boolean> reservationAttempt(String key, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await();
            try {
                quotaService.reserve(TENANT_ID, 1, key, "MESSAGE", key);
                return true;
            } catch (com.java3y.austin.billing.exception.InsufficientQuotaException exception) {
                return false;
            }
        };
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.java3y.austin.billing.domain")
    @EnableJpaRepositories(basePackages = "com.java3y.austin.billing.repository")
    static class TestApplication {
        @Bean
        QuotaService quotaService(QuotaAccountRepository accountRepository,
                                  QuotaReservationRepository reservationRepository,
                                  QuotaLedgerRepository ledgerRepository,
                                  QuotaAlertOutboxAppender alertOutboxAppender) {
            return new QuotaService(accountRepository, reservationRepository,
                    ledgerRepository, alertOutboxAppender);
        }

        @Bean
        QuotaAlertOutboxAppender quotaAlertOutboxAppender() {
            return org.mockito.Mockito.mock(QuotaAlertOutboxAppender.class);
        }
    }
}
