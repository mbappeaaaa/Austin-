package com.java3y.austin.handler.inbox;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(classes = InboxServiceIT.TestApplication.class,
        properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false"})
class InboxServiceIT {

    private static final Long TENANT_ID = 7L;

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:5.7.44")
            .withDatabaseName("austin_inbox_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.jpa.database-platform",
                () -> "org.hibernate.dialect.MySQL57Dialect");
    }

    @javax.annotation.Resource
    private InboxService service;
    @javax.annotation.Resource
    private InboxMessageRepository repository;

    @Test
    void shouldPersistIdempotentlyClaimWithLockAndExposeDueRetry() {
        InboxRegistrationResult first = service.register(TENANT_ID, "email.notice", "msg-1",
                "{\"messageId\":\"msg-1\"}", 3);
        InboxRegistrationResult duplicate = service.register(TENANT_ID, "email.notice", "msg-1",
                "{\"messageId\":\"msg-1\"}", 3);

        assertTrue(first.isCreated());
        assertFalse(duplicate.isCreated());
        assertEquals(first.getMessage().getId(), duplicate.getMessage().getId());
        assertThrows(InboxOperationConflictException.class,
                () -> service.register(TENANT_ID, "email.notice", "msg-1", "different", 3));

        InboxMessage claimed = service.claim(TENANT_ID, first.getMessage().getId(), "worker-a", 30)
                .orElseThrow(AssertionError::new);
        assertEquals(InboxStatus.PROCESSING, claimed.getStatus());
        service.fail(TENANT_ID, claimed.getId(), "worker-a", "TEMPORARY", "retry", 1);

        InboxMessage retry = repository.findById(claimed.getId()).orElseThrow(AssertionError::new);
        retry.setNextRetryAt(0L);
        repository.saveAndFlush(retry);
        List<InboxMessage> ready = repository.findReady(InboxStatus.PENDING, InboxStatus.RETRY_WAIT,
                InboxStatus.PROCESSING, Long.MAX_VALUE, PageRequest.of(0, 10));
        assertEquals(1, ready.size());
        assertEquals(claimed.getId(), ready.get(0).getId());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = "com.dtp.starter.apollo.autoconfigure.DtpAutoConfiguration")
    @EntityScan(basePackageClasses = InboxMessage.class)
    @EnableJpaRepositories(basePackageClasses = InboxMessageRepository.class)
    static class TestApplication {

        @Bean
        InboxService inboxService(InboxMessageRepository repository) {
            return new InboxService(repository);
        }
    }
}
