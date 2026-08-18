package com.asbg.outboxlab.infrastructure.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 워크숍 UI는 S3/CloudFront에 별도 오리진으로 호스팅되고, 브라우저에서 이 API를 직접 호출한다.
 * 이 API는 쿠키/세션 같은 자격증명을 쓰지 않는 순수 REST라서 오리진을 넓게 허용해도 안전하다.
 * 워크숍이 끝난 뒤 운영으로 넘어가면 CORS_ALLOWED_ORIGINS 환경변수를 CloudFront 도메인
 * 하나로 좁히면 된다 — 코드는 그대로 두고 설정값만 바꾸면 되도록 분리해뒀다.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOriginPatterns;

    public CorsConfig(@Value("${app.cors.allowed-origins:*}") String allowedOrigins) {
        this.allowedOriginPatterns = allowedOrigins.split(",");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
