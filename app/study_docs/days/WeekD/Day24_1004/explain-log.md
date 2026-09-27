# Day24 (10/4, Week D D3) 예측→실행→차이 기록

주제: `SecurityFilterChain` 도입, JWT 인증 필터, 커스텀 401/403 핸들러

## 실험 1 — Security 도입이 기존 테스트에 미치는 영향

- 코드: `spring-boot-starter-security` 추가 + `SecurityConfig.securityFilterChain()`에서 `.anyRequest().authenticated()`
- 예측: 토큰 없이 요청을 보내는 기존 `ReservationControllerHttpTest` 5개가 전부 401로 깨질 것
- 실행: `./gradlew test` → 5개 전부 `expected:<200/400/404> but was:<401>`로 실패, 원문은 `quiz.md` 참고
- 확인된 것: 400/404를 검증하려던 테스트까지 401로 나왔다는 것은 필터 단계가 컨트롤러의 Bean Validation·NotFound 로직보다 먼저 실행된다는 증거다

## 실험 2 — 토큰 발급 후 재시도

- 변경: `@BeforeEach`에서 `jwtProvider.issue()`로 `userToken`/`adminToken`을 발급해 모든 요청에 `Authorization: Bearer ...` 부여
- 예측: 401이 사라지고 원래 기대하던 200/400/404가 다시 나올 것
- 실행: 43개 중 43개 통과 (`build/test-results/test/*.xml` 전수 확인)
- 부작용 없음 확인: `cancel` 두 테스트만 `adminToken`으로 바꿔 `hasRole("ADMIN")` 경로를 통과시켰다

## 판단 로직 교정 과정 — owner 허용을 넣지 않은 이유

- 1차 답: "`AccessDeniedException`을 서비스에서 던지면 `CustomAccessDeniedHandler`가 처리해줄 것" — Security 예외라는 이름만 보고 처리 주체를 잘못 추정
- 교정: 그 예외는 `DispatcherServlet` 안쪽(MVC 핸들러 실행 중)에서 발생한다. `ExceptionTranslationFilter`(403을 만드는 주체)는 필터 체인 쪽이라 `DispatcherServlet`보다 앞에 있고, 그 뒤쪽에서 난 예외는 보지 못한다 — 대신 `GlobalExceptionHandler`의 `@ExceptionHandler(Exception.class)` catch-all이 먼저 잡아 500으로 바꿔버릴 위험이 있다
- 결정: `.requestMatchers(HttpMethod.POST, "/reservations/cancel/**").hasRole("ADMIN")` 같은 경로 기반 규칙은 `AuthorizationFilter`(필터 체인 안, `DispatcherServlet`보다 앞)에서 걸리므로 `CustomAccessDeniedHandler`가 확실히 동작한다 — 이번 범위는 이 방식만 구현하고 owner 허용은 남은 한계로 남겼다

## 검증 근거

- `src/test/java/com/example/studyroom/controller/ReservationControllerHttpTest.java`
- `src/main/java/com/example/studyroom/config/SecurityConfig.java`
- `./gradlew test` 전체 스위트, BUILD SUCCESSFUL 43/43 (커밋 `a617422` 시점)

## [직접 작성] 오늘 배운 것을 내 문장으로

- 401과 403이 서로 다른 컴포넌트(EntryPoint/AccessDeniedHandler)로 분리된 이유:
- `ExceptionTranslationFilter`와 `GlobalExceptionHandler`가 서로 못 보는 예외 범위가 갈리는 지점:

## 다음 시작점

Week D D4 — 인증 실패 케이스(만료·위조·헤더 누락·형식 오류) 테스트, CSRF/CORS/로그아웃 한계.
