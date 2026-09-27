# Day24 (10/4, Week D D3) 인출 기록

## 1. 세션 예측과 교정 — 필터체인과 401/403

| # | 질문 | 학습자 답 | 판정 | 교정 |
|---|---|---|---|---|
| P1 | Security를 추가한 뒤 기존 `ReservationControllerHttpTest` 5개를 그대로 돌리면 결과는? | "401로 다 깨질 것 같다. 토큰을 안 보내니까 `anyRequest().authenticated()`에 걸릴 것" | ✅ | 정확 — 실제로 5개 전부 `expected:<200/400/404> but was:<401>`로 실패 |
| P2 | `hasRole("ADMIN")` 경로에 일반 사용자 토큰으로 접근하면? | "403" | ✅ | 정확 — `CustomAccessDeniedHandler`가 응답 |
| P3 | `Authorization` 헤더 자체가 없으면? | "401" | ✅ | 정확 — `CustomAuthenticationEntryPoint`가 응답 |
| P4 | cancel의 "owner도 허용"을 서비스 코드에서 `AccessDeniedException`을 직접 던지는 방식으로 넣으면, `CustomAccessDeniedHandler`가 그 예외를 잡아 403을 만들어줄까? | "그럴 것 같다. `AccessDeniedException`은 Security 예외니까 결국 `CustomAccessDeniedHandler`가 처리하지 않을까" | ❌ | 그 예외는 `DispatcherServlet` 안쪽(MVC 핸들러 실행 중)에서 발생해 `GlobalExceptionHandler`의 `@ExceptionHandler(Exception.class)` catch-all이 먼저 잡아 500으로 바꾼다. `ExceptionTranslationFilter`는 필터 체인 쪽(`DispatcherServlet`보다 바깥)이라 이미 처리된 예외는 못 본다 — 그래서 이번 범위는 `AuthorizationFilter` 단계에서 걸리는 `hasRole("ADMIN")` 경로 규칙으로 좁혔다 |

## 2. 디버깅 실측 (전체 스위트 실행 중 발견)

Security를 넣고 `./gradlew test`를 돌리자 `ReservationControllerHttpTest`의 기존 5개가 전부 401로 깨졌다(`build/test-results/test/TEST-...ReservationControllerHttpTest.xml` 원문):

```text
reserveReturnsSuccessResponseForValidBody() FAILED
    java.lang.AssertionError: Status expected:<200> but was:<401>
listReturnsReservationsWithMemberNameOrNull() FAILED
    java.lang.AssertionError: Status expected:<200> but was:<401>
cancelReturns404WhenReservationDoesNotExist() FAILED
    java.lang.AssertionError: Status expected:<404> but was:<401>
```

400/404를 검증하려던 테스트까지 전부 401로 나온 게 핵심 증거였다 — Bean Validation이나 NotFound 로직에 도달하기 전에 필터 단계에서 이미 막힌 것이다. `@BeforeEach`에서 `jwtProvider.issue()`로 토큰을 미리 발급해 모든 요청에 `Authorization` 헤더를 붙이는 것으로 고쳤다.

## 3. 다음 복습 질문

1. `AuthorizationFilter`에서 걸리는 예외와 `DispatcherServlet` 안에서 직접 던진 예외가 왜 서로 다른 핸들러(`CustomAccessDeniedHandler` vs `GlobalExceptionHandler`)로 가는지 설명하기
2. 401과 403을 가르는 기준(신원 확인 여부)을 한 문장으로 말하기
3. `OncePerRequestFilter`가 보장하는 것과 `addFilterBefore`의 위치가 왜 `UsernamePasswordAuthenticationFilter` 앞이어야 하는지

## 4. 복습 일정

Day24 완료일 10/4 기준, 오늘 새로 등록한 4개 항목(`SecurityFilterChain` 인증/인가 분기, `AuthenticationEntryPoint` vs `AccessDeniedHandler`, `ExceptionTranslationFilter`와 `DispatcherServlet` 예외처리 경계 차이, JWT 필터의 요청당 DB 조회)은 +2일 10/6에 먼저 인출한다.
