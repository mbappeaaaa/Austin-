package com.java3y.austin.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java3y.austin.auth.security.AccountStateValidationFilter;
import com.java3y.austin.auth.security.AuthPermission;
import com.java3y.austin.common.vo.BasicResultVO;
import com.java3y.austin.auth.security.AustinUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextPersistenceFilter;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.CookieSerializer;

import javax.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class AuthSecurityConfig extends WebSecurityConfigurerAdapter {
    private final AustinUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;
    private final AccountStateValidationFilter accountStateValidationFilter;

    public AuthSecurityConfig(AustinUserDetailsService userDetailsService, ObjectMapper objectMapper,
                              AccountStateValidationFilter accountStateValidationFilter) {
        this.userDetailsService = userDetailsService;
        this.objectMapper = objectMapper;
        this.accountStateValidationFilter = accountStateValidationFilter;
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder());
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.cors().and().csrf().disable()
                .formLogin().disable().httpBasic().disable()
                .authorizeRequests()
                .antMatchers("/auth/login", "/officialAccount/receipt", "/actuator/health",
                        "/billing/payments/webhooks/**", "/billing/payments/mock/**",
                        "/swagger-ui/**", "/swagger-resources/**", "/v2/api-docs",
                        "/v3/api-docs", "/v3/api-docs/**", "/webjars/**").permitAll()
                .antMatchers("/auth/me", "/auth/logout").authenticated()
                .antMatchers("/auth/users/**").hasAuthority(AuthPermission.USER_MANAGE.name())
                .antMatchers("/admin/payment-channels/**").hasAuthority(AuthPermission.SYSTEM_ADMIN.name())
                .antMatchers("/admin/mq/inbox/**")
                .hasAuthority(AuthPermission.MQ_DEAD_LETTER_MANAGE.name())
                .antMatchers("/admin/billing/outbox/**")
                .hasAuthority(AuthPermission.BILLING_OUTBOX_MANAGE.name())
                .antMatchers("/admin/reports/**")
                .hasAuthority(AuthPermission.REPORT_MANAGE.name())
                .antMatchers(HttpMethod.GET, "/billing/reports/**")
                .hasAuthority(AuthPermission.REPORT_READ.name())
                .antMatchers(HttpMethod.POST, "/billing/reports/monthly")
                .hasAuthority(AuthPermission.REPORT_GENERATE.name())
                .antMatchers(HttpMethod.GET, "/billing/quota", "/billing/quota/ledger")
                .hasAuthority(AuthPermission.QUOTA_READ.name())
                .antMatchers(HttpMethod.PUT, "/billing/quota/warning-threshold")
                .hasAuthority(AuthPermission.QUOTA_MANAGE.name())
                .antMatchers(HttpMethod.GET, "/billing/packages", "/billing/payments/**")
                .hasAuthority(AuthPermission.PAYMENT_READ.name())
                .antMatchers(HttpMethod.POST, "/billing/payments/checkout")
                .hasAuthority(AuthPermission.PAYMENT_PURCHASE.name())
                .antMatchers("/actuator/**")
                .hasAuthority(AuthPermission.SYSTEM_ADMIN.name())
                .antMatchers("/inbox/**").hasAuthority(AuthPermission.INBOX_ACCESS.name())
                .antMatchers(HttpMethod.GET, "/channel/**").hasAuthority(AuthPermission.CHANNEL_READ.name())
                .antMatchers(HttpMethod.GET, "/account/**").hasAuthority(AuthPermission.ACCOUNT_READ.name())
                .antMatchers(HttpMethod.POST, "/account/**").hasAuthority(AuthPermission.ACCOUNT_WRITE.name())
                .antMatchers(HttpMethod.DELETE, "/account/**").hasAuthority(AuthPermission.ACCOUNT_WRITE.name())
                .antMatchers(HttpMethod.GET, "/messageTemplate/**").hasAuthority(AuthPermission.TEMPLATE_READ.name())
                .antMatchers(HttpMethod.POST, "/messageTemplate/test", "/messageTemplate/test/**",
                        "/messageTemplate/recall/**", "/messageTemplate/start/**", "/messageTemplate/stop/**")
                .hasAuthority(AuthPermission.TEMPLATE_EXECUTE.name())
                .antMatchers(HttpMethod.POST, "/messageTemplate/**").hasAuthority(AuthPermission.TEMPLATE_WRITE.name())
                .antMatchers(HttpMethod.DELETE, "/messageTemplate/**").hasAuthority(AuthPermission.TEMPLATE_WRITE.name())
                .antMatchers("/trace/**").hasAuthority(AuthPermission.TRACE_READ.name())
                .antMatchers(HttpMethod.GET, "/dashboard/**").hasAuthority(AuthPermission.TRACE_READ.name())
                .antMatchers("/material/**").hasAuthority(AuthPermission.MATERIAL_WRITE.name())
                .antMatchers("/send", "/batchSend", "/recall").hasAuthority(AuthPermission.MESSAGE_SEND.name())
                .antMatchers("/email/integration-test/**", "/feishu/robot/integration-test/**")
                .hasAuthority(AuthPermission.INTEGRATION_TEST.name())
                .antMatchers(HttpMethod.GET, "/miniProgram/template/**", "/alipayMiniProgram/template/**",
                        "/officialAccount/template/**").hasAuthority(AuthPermission.ACCOUNT_READ.name())
                .antMatchers("/miniProgram/**", "/alipayMiniProgram/**", "/officialAccount/**", "/refresh")
                .hasAuthority(AuthPermission.ACCOUNT_WRITE.name())
                .anyRequest().denyAll()
                .and().exceptionHandling()
                .authenticationEntryPoint((request, response, exception) -> writeUnauthorized(response))
                .accessDeniedHandler((request, response, exception) -> writeForbidden(response));
    }

    private void writeUnauthorized(HttpServletResponse response) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), BasicResultVO.fail("登录状态已失效，请重新登录"));
    }

    private void writeForbidden(HttpServletResponse response) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), BasicResultVO.fail("当前角色无权执行此操作"));
    }

    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("AUSTIN_SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        return serializer;
    }
}
