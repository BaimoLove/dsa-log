package com.baimo.dsalogserver.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 仅开发期需要：Vite dev server 在 5173，直连 8080 时浏览器会拦。
 * 生产由 nginx 同域反代，不需要 CORS。
 *
 * <p>配成空串即关闭（prod profile 就是这么干的）：
 * {@code allowedOrigins("")} 会在启动时抛异常，而「允许所有来源」又太宽，
 * 干脆让空值表示不注册这条规则。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Trace-Id")
                .maxAge(3600);
    }
}
