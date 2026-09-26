package com.example.studyroom.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

// Day29 — 요청 하나를 여러 로그 줄에서 추적하기 위한 상관관계 ID(correlation id).
// 클라이언트가 X-Request-Id를 이미 들고 있으면(여러 서비스를 거쳐 온 요청 등) 그대로 쓰고,
// 없으면 서버가 새로 발급한다. MDC(스레드 로컬 저장소)에 넣으면 이 요청을 처리하는 스레드가
// 찍는 모든 로그 줄에 logback 패턴(%X{requestId})으로 자동으로 붙는다.
//
// Spring Security의 필터체인(springSecurityFilterChain)보다 먼저 실행되어야 인증 실패(401/403)
// 로그에도 requestId가 남는다 — 그래서 SecurityConfig.addFilterBefore()가 아니라
// LoggingConfig에서 FilterRegistrationBean으로 서블릿 컨테이너 최상단에 등록한다.
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        try {
            MDC.put(MDC_KEY, requestId);
            response.setHeader(REQUEST_ID_HEADER, requestId);

            // 절대 로그에 남기면 안 되는 것: 비밀번호, JWT 원문(Authorization 헤더). 여기서는
            // 요청이 들어온 순간(어떤 메서드·경로·인증 헤더 유무)만 남기고, Authorization은 마스킹한다.
            log.info("{} {} Authorization={}", request.getMethod(), request.getRequestURI(),
                    maskAuthorization(request.getHeader("Authorization")));

            filterChain.doFilter(request, response);
        } finally {
            // 서블릿 컨테이너는 스레드를 재사용한다 — 여기서 안 지우면 다음 요청의 로그 줄에
            // 이번 요청의 requestId가 그대로 새어나간다(스레드 로컬 오염).
            MDC.remove(MDC_KEY);
        }
    }

    // 토큰 원문은 그대로 노출하면 안 되는 비밀값이다 — 존재 유무와 스킴(Bearer)만 남긴다.
    static String maskAuthorization(String header) {
        if (header == null || header.isBlank()) {
            return "-";
        }
        return "Bearer ***";
    }
}
