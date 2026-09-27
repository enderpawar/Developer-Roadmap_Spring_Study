# Day24 (10/4, Week D D3) 용어

주제: `SecurityFilterChain`과 JWT 인증 필터, 401/403 분리

| 용어 | 한줄뜻 | 오늘 코드와 관찰 |
|---|---|---|
| `SecurityFilterChain` | 이 애플리케이션에 적용할 Security 필터들의 순서 있는 목록을 정의하는 Bean | `SecurityConfig.securityFilterChain()`이 반환 |
| `FilterChainProxy` | 서블릿 `FilterChain` 안에서 실제 `SecurityFilterChain`으로 위임하는 단일 진입점(스타터가 자동 등록, 직접 구현 안 함) | `spring-boot-starter-security` 추가만으로 등록됨 |
| `OncePerRequestFilter` | 요청당 정확히 한 번만 실행되도록 보장하는 서블릿 필터 베이스 클래스 | `JwtAuthenticationFilter extends OncePerRequestFilter` |
| `SecurityContext`/`SecurityContextHolder` | 현재 요청의 인증 정보(`Authentication`)를 담는 저장소와 그 접근 창구 | `JwtAuthenticationFilter`가 `setAuthentication()`/`clearContext()` 호출 |
| `AuthorizationFilter` | 경로별 인가 규칙(`authorizeHttpRequests`)을 실제로 판정하는 필터 | `.hasRole("ADMIN")`, `.anyRequest().authenticated()` |
| `ExceptionTranslationFilter` | 필터 체인 안에서 발생한 인증/인가 예외를 401/403 응답으로 바꾸는 필터 | `AuthenticationEntryPoint`·`AccessDeniedHandler` 호출 주체 |
| `AuthenticationEntryPoint` | 인증 자체가 안 된 요청을 처리하는 콜백(401) | `CustomAuthenticationEntryPoint.commence()` |
| `AccessDeniedHandler` | 인증은 됐지만 권한이 부족한 요청을 처리하는 콜백(403) | `CustomAccessDeniedHandler.handle()` |
| `SessionCreationPolicy.STATELESS` | 서버가 세션을 생성·사용하지 않도록 하는 설정 | `.sessionManagement(session -> session.sessionCreationPolicy(STATELESS))` |
| `addFilterBefore` | 지정한 필터 앞에 커스텀 필터를 끼워 넣는 설정 메서드 | `addFilterBefore(new JwtAuthenticationFilter(...), UsernamePasswordAuthenticationFilter.class)` |

## 핵심 호출 경로

```text
정상 (토큰 있음 + 역할 충족)
Client → JwtAuthenticationFilter: parseSubject() 성공 → SecurityContext에 Authentication 설정
→ AuthorizationFilter: 경로 규칙 통과(authenticated 또는 hasRole 충족)
→ Controller 실행 → 200 OK
```

```text
인증 실패 (토큰 없음/위조/만료)
Client → JwtAuthenticationFilter: 헤더 없음 또는 JwtException → SecurityContext 비어있음
→ AuthorizationFilter: .anyRequest().authenticated() 불충족
→ ExceptionTranslationFilter → CustomAuthenticationEntryPoint.commence() → 401
```

```text
인가 실패 (인증 O, 역할 부족)
Client → JwtAuthenticationFilter: 인증 정보 채움(ROLE_USER)
→ AuthorizationFilter: POST /reservations/cancel/** + hasRole("ADMIN") 불충족
→ ExceptionTranslationFilter → CustomAccessDeniedHandler.handle() → 403
```
