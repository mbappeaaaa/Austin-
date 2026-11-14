package com.java3y.austin.auth.config;

import com.java3y.austin.auth.domain.SysUser;
import com.java3y.austin.auth.repository.SysUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthDataInitializer implements ApplicationRunner {
    private final SysUserRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Value("${austin.auth.bootstrap.enabled:false}")
    private boolean bootstrapEnabled;
    @Value("${austin.auth.bootstrap.username:admin}")
    private String username;
    @Value("${austin.auth.bootstrap.password:}")
    private String password;
    @Value("${austin.auth.bootstrap.display-name:Austin 管理员}")
    private String displayName;
    @Value("${austin.auth.bootstrap.tenant-id:1}")
    private Long tenantId;

    public AuthDataInitializer(SysUserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!bootstrapEnabled || repository.findByUsername(username).isPresent()) {
            return;
        }
        if (password == null || password.length() < 8) {
            throw new IllegalStateException("AUSTIN_AUTH_BOOTSTRAP_PASSWORD 至少需要 8 位");
        }
        int now = (int) Instant.now().getEpochSecond();
        repository.save(SysUser.builder().tenantId(tenantId).username(username)
                .passwordHash(passwordEncoder.encode(password))
                .displayName(displayName).role("ADMIN").enabled(true).created(now).updated(now).build());
        log.warn("Created local bootstrap administrator '{}'; disable bootstrap after first startup", username);
    }
}
