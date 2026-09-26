package com.example.studyroom.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

// Day24 — 인증 자체가 안 된 요청(토큰 없음/위조/만료)이 인가가 필요한 경로에 닿으면 여기로 온다.
// 기존 GlobalExceptionHandler(@RestControllerAdvice)는 DispatcherServlet 안쪽만 잡을 수 있는데,
// 이 401은 필터 단계(그보다 바깥)에서 나기 때문에 별도 컴포넌트가 필요하다.
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"인증이 필요합니다.\"}");
    }
}
