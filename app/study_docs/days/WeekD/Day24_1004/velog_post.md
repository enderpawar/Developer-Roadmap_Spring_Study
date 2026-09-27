# [Spring Study Day 24] Servlet Filter 인증·인가 경계 — SecurityFilterChain과 커스텀 401/403 핸들러

Day23에서 로그인 성공 시 JWT를 발급·검증하는 `JwtProvider`를 만들었다. 오늘은 그 토큰을 매 요청마다 검사하는 자리를 실제 Servlet Filter 체인 안에 넣었다 — `spring-boot-starter-security`로 `SecurityFilterChain`을 구성하고, `JwtAuthenticationFilter`가 `Authorization` 헤더를 읽어 `SecurityContext`를 채우고, 인증·인가 실패는 커스텀 `AuthenticationEntryPoint`(401)·`AccessDeniedHandler`(403)가 JSON으로 응답하게 했다. Refresh Token 재발급, CSRF·CORS 세부는 이 글에서 다루지 않는다(Day25).

> Security를 추가하고 전체 테스트를 돌리자 기존 `ReservationControllerHttpTest`의 5개가 전부 401로 깨졌다. 원인은 `.anyRequest().authenticated()`가 토큰 없는 기존 요청을 필터 단계에서 먼저 막아, 400·404를 검증하려던 테스트까지 401로 나온 것이었다. `@BeforeEach`에서 토큰을 미리 발급해 모든 요청에 붙이는 것으로 고쳤고, 신규 401·403 테스트 2개를 추가해 43개 전부 통과를 확인했다.

> **오늘의 흐름** `spring-boot-starter-security 추가 → SecurityFilterChain 구성 → 기존 테스트 5개 401로 깨짐 → 토큰 발급으로 수정 → 신규 401/403 테스트 추가`
>
> 이전 Day: JWT 발급과 검증 — `JwtProvider.issue()`/`parseSubject()` (Day23)
> 다음 Day: 인증 실패 케이스(만료·위조·헤더 누락·형식 오류) 테스트, CSRF·CORS 한계 (Day25)

![시퀀스 다이어그램. Client가 Authorization 헤더를 들고 JwtAuthenticationFilter에 요청하면, 토큰이 유효하면 SecurityContext에 인증 정보가 채워지고 AuthorizationFilter를 통과해 Controller가 200을 반환한다. alt 프레임은 두 실패 경로를 보여준다 — 토큰이 없거나 위조·만료됐으면 ExceptionTranslationFilter가 CustomAuthenticationEntryPoint.commence()를 호출해 401을 반환하고, 인증은 됐지만 hasRole(ADMIN)을 만족하지 못하면 CustomAccessDeniedHandler.handle()이 403을 반환한다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day24-securityfilterchain-flow.png)

## 1. 개념 설명

### 1) Servlet FilterChain과 SecurityFilterChain

> **SecurityFilterChain** = Spring Security가 특정 요청 패턴에 적용할 Security 필터들을 순서대로 묶어놓은 Bean

우리 코드에서는 `SecurityConfig.securityFilterChain()`이 이 Bean을 만든다.

```java
http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**", "/hello", "/health", "/bye").permitAll()
                .requestMatchers(HttpMethod.POST, "/reservations/cancel/**").hasRole("ADMIN")
                .anyRequest().authenticated())
        .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler))
        .addFilterBefore(new JwtAuthenticationFilter(jwtProvider, memberRepository),
                UsernamePasswordAuthenticationFilter.class);
```

Spring Security는 서블릿 표준 `FilterChain`을 통째로 차지하지 않는다. `DelegatingFilterProxy`라는 서블릿 필터 하나가 `FilterChainProxy`(Spring Bean)로 위임하고, `FilterChainProxy`가 URL 패턴에 맞는 `SecurityFilterChain`을 골라 그 안의 필터들을 순서대로 실행한다.

![FilterChain 구조. Client 요청이 Filter0, Filter1, Filter2를 순서대로 거쳐 Servlet(대상)에 도달하고, 응답은 역순으로 돌아온다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day24-web-filterchain.png)

*출처: [Architecture :: Spring Security](https://docs.spring.io/spring-security/reference/servlet/architecture.html) — Broadcom Inc., docs.spring.io*

우리가 등록한 `SecurityFilterChain` 내부에는 `JwtAuthenticationFilter`(직접 추가), `AuthorizationFilter`(경로 규칙 판정), `ExceptionTranslationFilter`(예외를 401·403으로 변환) 등이 정해진 순서로 들어간다.

![SecurityFilterChain 내부 구조. FilterChainProxy가 하나의 SecurityFilterChain을 골라, 그 안의 Security Filter0부터 Filtern까지 순서대로 호출한다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day24-web-securityfilterchain.png)

*출처: [Architecture :: Spring Security](https://docs.spring.io/spring-security/reference/servlet/architecture.html) — Broadcom Inc., docs.spring.io*

```text
Client 요청 → DelegatingFilterProxy → FilterChainProxy
→ 이 앱에 등록된 SecurityFilterChain 하나 선택
→ JwtAuthenticationFilter → ... → AuthorizationFilter → DispatcherServlet
```

우리는 JWT를 쓰는 REST API라 세션 쿠키가 없다. `.sessionManagement(... STATELESS)`로 서버가 세션을 만들지 않게 하고, `.csrf(csrf -> csrf.disable())`로 CSRF 보호를 껐다 — CSRF가 막는 대상은 "쿠키가 브라우저에 의해 자동으로 실려 가는" 상황인데, 세션 쿠키 자체가 없으니 그 전제가 성립하지 않는다(CSRF의 실제 공격 형태는 Day25에서 다룬다).

### 2) JwtAuthenticationFilter의 SecurityContext 채우기

> **SecurityContext** = 현재 요청을 처리 중인 스레드가 "누구로 인증됐는지"를 담아두는 저장소, `SecurityContextHolder`로 접근한다

우리 코드에서는 `OncePerRequestFilter`를 상속해 요청당 정확히 한 번 이 저장소를 채운다.

```java
if (header != null && header.startsWith(BEARER_PREFIX)) {
    String token = header.substring(BEARER_PREFIX.length());
    try {
        String loginId = jwtProvider.parseSubject(token);
        memberRepository.findByLoginId(loginId).ifPresent(this::authenticate);
    } catch (JwtException e) {
        SecurityContextHolder.clearContext();
    }
}
filterChain.doFilter(request, response);
```

**한 줄씩 보기**

- `header.startsWith(BEARER_PREFIX)`: `Bearer ` 접두사가 없으면 토큰을 꺼내려 시도조차 안 함
- `jwtProvider.parseSubject(token)`: 서명 검증과 만료 확인을 한 번에 수행, 실패하면 `JwtException` 하위타입을 던짐
- `catch (JwtException e)`: 위조(`SignatureException`)·만료(`ExpiredJwtException`) 전부 이 한 줄에서 잡힘, 여기서 401을 직접 만들지 않고 인증 없는 상태로 흘려보냄
- `filterChain.doFilter(...)`: 성공하든 실패하든 항상 다음 필터로 진행 — 이 필터 자신은 요청을 막지 않는다

이 필터는 401을 스스로 만들지 않는다. `SecurityContext`를 비워두면, 뒤따르는 `AuthorizationFilter`의 `.anyRequest().authenticated()`가 "인증 안 됨"으로 판정해 예외를 던지고, 그 예외를 `ExceptionTranslationFilter`가 401로 바꾼다. 인증 여부를 판정하는 책임과, 그 판정 실패를 HTTP 응답으로 바꾸는 책임이 서로 다른 필터에 나뉘어 있다.

### 3) ExceptionTranslationFilter의 401/403 분기

> **ExceptionTranslationFilter** = 필터 체인에서 발생한 인증·인가 관련 예외를 잡아, 상황에 따라 401 또는 403 응답으로 바꾸는 필터

```text
AuthorizationFilter에서 인가 판정
→ 인증 자체가 안 됨(AuthenticationException) → AuthenticationEntryPoint.commence() → 401
→ 인증은 됐지만 권한 부족(AccessDeniedException) → AccessDeniedHandler.handle() → 403
```

![ExceptionTranslationFilter의 분기. 정상 처리 흐름과, SecurityException을 잡아 Start Authentication(SecurityContextHolder·RequestCache·AuthenticationEntryPoint, 401) 또는 Access Denied(AccessDeniedHandler, 403)로 갈라지는 결정 흐름을 보여준다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day24-web-exceptiontranslationfilter.png)

*출처: [Architecture :: Spring Security](https://docs.spring.io/spring-security/reference/servlet/architecture.html) — Broadcom Inc., docs.spring.io*

우리 코드의 `CustomAuthenticationEntryPoint`는 `{"error":"인증이 필요합니다."}`를, `CustomAccessDeniedHandler`는 `{"error":"권한이 없습니다."}`를 반환한다. 401은 "누구인지조차 모른다", 403은 "누군지는 알지만 이 작업을 할 권한이 없다"는 차이다. 기존 `GlobalExceptionHandler`(`@RestControllerAdvice`)는 이 두 예외를 잡지 못한다 — `ExceptionTranslationFilter`는 `DispatcherServlet`보다 앞(필터 체인 쪽)에서 동작하고, `@RestControllerAdvice`는 `DispatcherServlet` 안쪽의 MVC 예외만 본다. 그래서 Security 전용 컴포넌트가 따로 필요했다.

### 4) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| SecurityFilterChain | 특정 요청 패턴에 적용할 Security 필터들을 순서대로 묶은 Bean |
| FilterChainProxy | 서블릿 FilterChain 안에서 SecurityFilterChain으로 위임하는 단일 진입점 |
| SecurityContext | 현재 요청의 인증 정보를 담는 저장소 |
| AuthorizationFilter | 경로별 인가 규칙을 판정하는 필터 |
| ExceptionTranslationFilter | 인증·인가 예외를 401·403 응답으로 바꾸는 필터 |

> **더 볼 것**
> - [Architecture :: Spring Security](https://docs.spring.io/spring-security/reference/servlet/architecture.html): FilterChainProxy·SecurityFilterChain·ExceptionTranslationFilter 전체 구조
> - [Exploring the Spring Security Filter Chain — reflectoring.io](https://reflectoring.io/spring-security-understand-filter-chain/): 필터 순서와 각 필터의 역할을 코드 관점에서 설명

## 2. 코드 구현

### 1) 겪은 오류 — 기존 테스트 5개의 401 회귀

Security를 넣고 바로 전체 테스트를 돌리자 `ReservationControllerHttpTest`의 기존 5개가 전부 401로 실패했다(`build/test-results/test/TEST-...ReservationControllerHttpTest.xml` 원문):

```text
reserveReturnsSuccessResponseForValidBody() FAILED
    java.lang.AssertionError: Status expected:<200> but was:<401>
cancelReturns404WhenReservationDoesNotExist() FAILED
    java.lang.AssertionError: Status expected:<404> but was:<401>
cancelReturns400WhenIdIsNotPositive() FAILED
    java.lang.AssertionError: Status expected:<400> but was:<401>

41 tests completed, 5 failed
```

400과 404를 검증하려던 테스트까지 401로 나온 게 원인을 가리켰다 — Bean Validation이나 NotFound 로직에 도달하기도 전에 `.anyRequest().authenticated()`가 필터 단계에서 이미 막은 것이다. `@BeforeEach`에서 `jwtProvider.issue()`로 `userToken`/`adminToken`을 미리 발급해 모든 `/reservations` 요청에 `Authorization: Bearer ...`를 붙이는 것으로 고쳤다.

### 2) cancel 권한을 ROLE_ADMIN 전용으로 좁힌 판단

브리프 원안은 "ROLE_ADMIN 또는 owner(본인 예약)"였다. owner 판정을 서비스 코드에서 `AccessDeniedException`을 직접 던지는 방식으로 구현하면, 그 예외는 `DispatcherServlet` 안쪽에서 발생해 `GlobalExceptionHandler`의 `@ExceptionHandler(Exception.class)` catch-all이 먼저 잡아 500으로 바꿀 위험이 있다. 이 판단에 이르는 과정은 3절에 적었다. 이번 범위는 `.hasRole("ADMIN")` 경로 규칙만 구현하고, owner 허용은 4절의 남은 한계에 남겼다.

### 3) 자동 검증 결과

| 검증 항목 | 방법 | 결과 |
|---|---|---|
| 기존 테스트 회귀 | `./gradlew test --console=plain` | BUILD SUCCESSFUL, 43/43, 0 failures |
| 신규 403 | `cancelReturns403WhenCallerIsNotAdmin` | `{"error":"권한이 없습니다."}` |
| 신규 401 | `reserveReturns401WhenAuthorizationHeaderIsMissing` | `{"error":"인증이 필요합니다."}` |

`h2-console`은 `permitAll` + `frameOptions().disable()`만 해뒀고 실제 접속은 확인하지 않았다(수동 확인 필요).

커밋: [a617422](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/a6174224f5e86cd438be6d0937bbee2e6dc0c176)

## 3. 스스로 답한 질문

### 1) owner 허용 대신 ROLE_ADMIN 전용으로 좁힌 이유

**질문.** cancel 권한에 "owner도 허용"을 서비스 코드에서 `AccessDeniedException`을 직접 던지는 방식으로 넣으면, `CustomAccessDeniedHandler`가 그 예외를 잡아 403을 만들어줄까?

**A1.** 처음에는 "그럴 것 같다. `AccessDeniedException`은 Security 예외니까 결국 `CustomAccessDeniedHandler`가 처리하지 않을까"라고 답했다. 예외 이름만 보고 처리 주체를 추정한 것이다.

실제는 반대였다. 그 예외는 `DispatcherServlet` 안쪽(MVC 핸들러 실행 중)에서 발생하는데, `ExceptionTranslationFilter`(403을 만드는 주체)는 필터 체인 쪽이라 `DispatcherServlet`보다 앞에 있다 — 뒤쪽에서 난 예외는 볼 수 없다. 대신 `GlobalExceptionHandler`의 `@ExceptionHandler(Exception.class)` catch-all이 먼저 잡아 500으로 바꿔버릴 위험이 있다. 반면 `.hasRole("ADMIN")` 같은 경로 규칙은 `AuthorizationFilter`(필터 체인 안, `DispatcherServlet`보다 앞)에서 걸리므로 `CustomAccessDeniedHandler`가 확실히 동작한다. "어떤 예외인가"가 아니라 "어디서 발생하는가"가 처리 주체를 가른다는 걸 다시 확인했다.

## 4. 학습 정리와 다음 범위

### 1) 이해의 변화와 남은 것

Day23까지는 JWT를 발급·검증하는 도구(`JwtProvider`)만 있었다. 오늘 그 도구를 실제 요청 경로에 끼워 넣으면서, 인증(누구인지)과 인가(무엇을 할 수 있는지)가 서로 다른 필터·다른 예외·다른 HTTP 코드로 분리된다는 것을 401·403 두 응답으로 확인했다. 특히 "같은 이름의 예외라도 발생 위치(필터 체인 vs `DispatcherServlet`)에 따라 처리 주체가 갈린다"는 것이 이번 판단의 핵심이었다.

**아직 남은 것**은 두 가지다. ① cancel의 owner 허용은 구현하지 않았다 — **나중에 고칠 것**. 넣으려면 `GlobalExceptionHandler`에 `@ExceptionHandler(AccessDeniedException.class)`를 추가해야 하는데, 그러면 403 응답이 `CustomAccessDeniedHandler`가 아니라 `GlobalExceptionHandler`에서 나가 두 메커니즘이 뒤섞인다. ② 401·403 JSON이 `{"error": "..."}` 하나뿐이라 기존 `GlobalExceptionHandler`의 다른 응답과 필드 구성이 다르다 — 바로 고칠 것은 아니고 **Day28 버퍼에서 `code`·`timestamp`를 맞출 계획**이다. `h2-console` 수동 접속 확인은 **고치지 않을 것**(우선순위 낮음)으로 남긴다.

면접에서 다시 답해볼 항목을 남긴다.

- `FilterChainProxy`가 여러 `SecurityFilterChain` 중 하나를 고르는 기준
- `OncePerRequestFilter`가 없다면 어떤 상황에서 필터가 중복 실행될 수 있는지

---

오늘 공부한 소스코드: `app/src/main/java/com/example/studyroom/config/SecurityConfig.java`, `app/src/main/java/com/example/studyroom/security/JwtAuthenticationFilter.java`, `app/src/main/java/com/example/studyroom/security/CustomAuthenticationEntryPoint.java`, `app/src/main/java/com/example/studyroom/security/CustomAccessDeniedHandler.java`, `app/src/test/java/com/example/studyroom/controller/ReservationControllerHttpTest.java`
