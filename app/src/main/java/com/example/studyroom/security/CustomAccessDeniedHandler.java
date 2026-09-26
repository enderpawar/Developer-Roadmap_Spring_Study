package com.example.studyroom.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

// Day24 — 인증은 됐지만 권한이 부족한 요청(예: ADMIN 전용 경로에 USER 토큰으로 접근)이 여기로 온다.
// 401(누구인지 모름)과 403(누군지는 알지만 권한 없음)을 분리하는 이유가 이 클래스와 EntryPoint의 차이다.
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // Day28 — CustomAuthenticationEntryPoint(401)와 형식을 맞췄다: "error"는 유지하고
        // "code"·"timestamp"를 추가해서 401/403 모두 같은 모양으로 응답하게 했다.
        response.getWriter().write(
                "{\"error\":\"권한이 없습니다.\",\"code\":\"FORBIDDEN\",\"timestamp\":\""
                        + Instant.now() + "\"}");
    }
}
