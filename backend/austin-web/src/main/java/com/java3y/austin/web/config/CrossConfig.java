package com.java3y.austin.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置
 */
@Configuration
public class CrossConfig implements WebMvcConfigurer {
    /**
     * 允许的管理台来源，支持逗号分隔多个。
     * 注意 CORS 是字符串精确匹配：localhost 与 127.0.0.1 属于不同 Origin，都要显式列出。
     */
    @Value("${austin.admin.origin:http://localhost:3000,http://127.0.0.1:3000}")
    private java.util.List<String> adminOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(adminOrigins.toArray(new String[0]))
                .allowedMethods("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS")
                .allowCredentials(true)
                .maxAge(3600)
                .allowedHeaders("*");
    }
}
