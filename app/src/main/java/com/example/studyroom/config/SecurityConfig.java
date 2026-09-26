package com.example.studyroom.config;

import com.example.studyroom.repository.MemberRepository;
import com.example.studyroom.security.CustomAccessDeniedHandler;
import com.example.studyroom.security.CustomAuthenticationEntryPoint;
import com.example.studyroom.security.JwtAuthenticationFilter;
import com.example.studyroom.security.JwtProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// Day24 — 필터체인 전체. 이 학습 프로젝트는 세션·쿠키를 쓰지 않는 REST API라
// 로그인 폼도, 브라우저 세션도 없다 — 그래서 CSRF(세션 쿠키를 노리는 공격)를 끌 수 있다.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final MemberRepository memberRepository;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtProvider jwtProvider,
                           MemberRepository memberRepository,
                           CustomAuthenticationEntryPoint authenticationEntryPoint,
                           CustomAccessDeniedHandler accessDeniedHandler) {
        this.jwtProvider = jwtProvider;
        this.memberRepository = memberRepository;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // JWT는 stateless다 — 서버가 세션 쿠키를 발급/저장하지 않으니 CSRF 토큰이 막아야 할
                // "쿠키를 훔쳐서 자동 전송되는 요청 위조"라는 전제 자체가 성립하지 않는다.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/hello", "/health", "/bye").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        // 취소는 관리자만 — "누구 예약이든 취소 가능" 대신 owner 허용까지는
                        // 이번 학습 범위 밖으로 남긴다(day24.md 남은 한계 참고).
                        .requestMatchers(HttpMethod.POST, "/reservations/cancel/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .headers(headers -> headers.frameOptions(frame -> frame.disable())) // h2-console이 iframe을 씀
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider, memberRepository),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
