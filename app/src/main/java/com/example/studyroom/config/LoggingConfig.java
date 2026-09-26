package com.example.studyroom.config;

import com.example.studyroom.logging.RequestIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

// Day29 — RequestIdFilter를 서블릿 컨테이너 필터 체인의 가장 앞(HIGHEST_PRECEDENCE)에 등록한다.
// SecurityConfig.addFilterBefore()로 넣으면 "Spring Security 체인 안에서" 순서만 정해지는데,
// Security 체인 자체(springSecurityFilterChain)도 서블릿 필터 하나일 뿐이라 그보다 앞에 있어야
// 인증 실패(401/403) 응답에도 requestId 헤더·로그가 남는다.
@Configuration
public class LoggingConfig {

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(new RequestIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
