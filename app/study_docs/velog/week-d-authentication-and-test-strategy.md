# [Spring Study Day 27 & Day 28] 4주차 마무리 시험

Week D는 Week C에서 열었던 "프레임워크가 알아서 처리한다"는 블랙박스를, "누가 이 요청을 보냈는가"라는 새 축으로 이어간 주였다. D1~D2에서 BCrypt로 비밀번호를 해시해 저장하고 JWT를 발급·검증했고, D3~D4에서 Security 필터체인 전체를 넣어 401·403을 직접 만들고 실패 케이스(만료·위조·헤더 누락)를 테스트했다. D5에서는 지금까지 쌓인 테스트를 Unit/Slice/Integration으로 분류하고 빠졌던 슬라이스 테스트를 추가했다. 이 글은 D6 누적시험과 D7 버퍼를 묶어, 4주차 전체를 인출하고 코드에 반영한 기록이다.

> D6에서는 Week A~D 전체를 8문항으로 인출했다. Day22 이후 안정됐던 self-invocation은 이번에도 힌트 없이 재현됐지만, HS256 서명 키의 최소 길이(256비트)와 401·403을 만드는 주체(`AuthenticationEntryPoint`·`AccessDeniedHandler`)를 `SecurityConfig`와 혼동한 두 문항은 오답이었다. D7에서는 `GlobalExceptionHandler`·`CustomAuthenticationEntryPoint`·`CustomAccessDeniedHandler` 세 곳의 401·403 응답에 `code`·`timestamp` 필드를 추가해 형식을 통일했다. 최종 테스트 51개 통과.

## 1. 시험 범위와 진행 방식

### 1) Week D D1~D5 요약

- **D1(Day22, BCrypt)** — `PasswordEncoderConfig`로 `BCryptPasswordEncoder` Bean을 구성하고 `POST /auth/signup`을 추가. 같은 원문도 매번 다른 해시가 나오는 것을 salt로 확인.
- **D2(Day23, JWT)** — `JwtProvider`로 HS256 토큰을 발급·검증. 160비트 키로 `WeakKeyException`을 겪고 32바이트 이상으로 교정.
- **D3(Day24, 필터체인)** — `spring-boot-starter-security`를 넣고 `JwtAuthenticationFilter`·`CustomAuthenticationEntryPoint`·`CustomAccessDeniedHandler`로 401·403을 직접 응답.
- **D4(Day25, 실패 케이스)** — 만료·위조·헤더 누락·형식 오류 네 가지를 각각 401로 확인.
- **D5(Day26, 테스트 분류)** — 17개 클래스를 Unit/Slice/Integration으로 분류하고 `@WebMvcTest`·`@DataJpaTest`를 추가하다 Security 자동 설정과 충돌.

아래 그림은 D2~D3에서 만든 JWT 인증 흐름을 Spring Security의 공식 구조로 다시 확인한 것이다. `BearerTokenAuthenticationToken`(원시 토큰) → `AuthenticationManager` → `JwtAuthenticationProvider` → `JwtDecoder`가 검증·디코딩 → `JwtAuthenticationConverter`가 권한을 구성 → 최종 `JwtAuthenticationToken`이 나온다. 우리 코드는 `spring-security-oauth2-resource-server`를 쓰지 않고 `JwtAuthenticationFilter`를 직접 만든 더 단순한 경로지만, "토큰 → 디코딩/검증 → 권한 있는 인증 객체"라는 단계 구성은 같다.

![다섯 단계로 이어지는 JWT 인증 흐름. ① BearerTokenAuthenticationToken(원시 토큰)이 AuthenticationManager/ProviderManager로 전달되고 ② JwtAuthenticationProvider가 처리를 맡아 ③ JwtDecoder가 서명을 검증·디코딩하고 ④ JwtAuthenticationConverter가 권한을 구성해 ⑤ 최종 JwtAuthenticationToken(Jwt + 권한)이 반환된다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day28-web-jwt-authentication-provider.png)

*출처: [OAuth 2.0 Resource Server JWT — Spring Security Reference](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) — Broadcom Inc., © Broadcom, All Rights Reserved*

D6(10/9) 출제 범위는 Week A(DTO/Domain), Week B(1차 캐시), Week C(self-invocation, fetch join 적용 범위), Week D D1~D5(BCrypt salt, HS256 키 길이, 401/403 주체 구분, `@WebMvcTest`+Security)였다. 노트를 덮고 먼저 답하고, 틀리거나 막히면 힌트를 받은 뒤 다시 답하는 방식은 Day20과 같다.

### 2) 시험 결과

| 구분 | 문항 | 기록 |
|---|---|---|
| 힌트 없이 통과 | 1 DTO/Domain, 2 1차 캐시, 3 self-invocation, 4 fetch join, 5 BCrypt salt | self-invocation은 Day22 이후 안정된 근거를 이번에도 그대로 재현 |
| 힌트 후 통과 | 8 `@WebMvcTest`+Security 자동 설정 | 이틀 전(Day26)에 막 배운 내용이라 힌트 필요 |
| 오답 후 교정 | 6 HS256 키 최소 길이, 7 401/403을 만드는 주체 | 둘 다 Week D — "결론은 아는데 정확한 근거·주체를 못 대는" 패턴 |

8문항 중 흔들린 건 전부 Week D였다 — 로드맵이 이 주도 "벽"으로 지정한 것과 정확히 일치한다. 아래에서는 6·7번을 다룬다.

## 2. 시험에서 틀린 문제

### 1) 문항 6 — HS256 서명 키의 최소 길이

> **HS256 서명 키 길이 요건** = RFC 7518 3.2에 따라 HMAC-SHA 알고리즘의 키 크기는 해시 출력 크기(HS256은 256비트) 이상이어야 한다는 규정

**질문.** JWT를 HS256으로 서명할 때 키는 최소 몇 비트 이상이어야 하는가?

**최초 답변.** "128비트 정도면 충분하지 않아요?"

**왜 틀렸나.** Day23에서 160비트 키(`studyroom-secret-key`)로 `WeakKeyException`을 직접 겪었는데, 그때 남은 기억은 "짧으면 안 된다"는 결론뿐이었다. 실제로 겪은 오류(에피소드 기억)와 정확한 규정 수치(사실 기억)는 별개로 인출해야 한다는 게 이번 시험의 결론이다.

**교정 기준.** RFC 7518 3.2는 HMAC-SHA 알고리즘의 키 크기가 해시 출력 크기 이상이어야 한다고 규정한다. HS256의 해시 출력은 256비트이므로 키도 256비트(32바이트) 이상이어야 한다. `application.yml`의 `jwt.secret`은 이미 32바이트 이상 문자열로 고쳐져 있다(Day23).

### 2) 문항 7 — 401과 403을 만드는 주체 구분

> **`AuthenticationEntryPoint`** = 인증 자체가 안 된 요청을 처리해 401을 응답. **`AccessDeniedHandler`** = 인증은 됐지만 권한이 부족한 요청을 처리해 403을 응답. 둘 다 `ExceptionTranslationFilter`가 호출한다

**질문.** 401과 403 응답은 각각 무엇이 만드는가?

**최초 답변.** "둘 다 `SecurityConfig`가 직접 만들어서 응답하는 거 아닌가요?"

**왜 틀렸나.** `SecurityConfig.securityFilterChain()`의 `exceptionHandling(ex -> ex.authenticationEntryPoint(...).accessDeniedHandler(...))`은 두 컴포넌트를 **등록**할 뿐이다. "설정 클래스가 다 처리한다"는 뭉뚱그려진 그림과 "설정은 등록만 하고 실행은 별도 컴포넌트가 한다"는 실제 구조를 혼동했다.

아래 그림은 Spring 공식 문서가 그리는 `ExceptionTranslationFilter`의 분기다. 정상 처리 경로와 `SecurityException`을 잡은 뒤의 두 갈래(인증 시작 → 401, 접근 거부 → 403)가 한 다이어그램에 있다.

![ExceptionTranslationFilter의 결정 흐름. 정상 처리 경로와, SecurityException을 잡았을 때 두 갈래로 갈리는 경로를 보여준다. 인증 자체가 안 된 경우 SecurityContextHolder·RequestCache를 거쳐 AuthenticationEntryPoint가 처리해 401을 만들고, 인증은 됐지만 권한이 없는 경우 AccessDeniedHandler가 처리해 403을 만든다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day28-web-exceptiontranslationfilter.png)

*출처: [Architecture — Spring Security Reference](https://docs.spring.io/spring-security/reference/servlet/architecture.html) — Broadcom Inc., © Broadcom, All Rights Reserved*

**교정 기준.** `ExceptionTranslationFilter`가 인증 여부로 두 갈래를 가른다. 인증 자체가 안 됐으면(`AuthenticationException`) `AuthenticationEntryPoint`가, 인증은 됐지만 권한이 부족하면(`AccessDeniedException`) `AccessDeniedHandler`가 응답을 만든다. `SecurityConfig`는 "이 두 컴포넌트를 쓰겠다"는 배선만 담당한다. 이 기준은 곧바로 D7의 코드 적용에 쓰였다(3절 참고).

## 3. D7 코드 적용

Day27에서 이월된 기술부채는 정확히 문항 7에서 교정한 기준이 필요한 자리였다. 지금까지 401은 나는 위치(필터 단계 vs MVC 예외 처리 단계)에 따라 응답 모양이 미묘하게 달랐다.

```text
변경 전 403: {"error":"권한이 없습니다."}
변경 후 403: {"error":"권한이 없습니다.","code":"FORBIDDEN","timestamp":"2026-10-10T05:12:03.123456Z"}
```

교정 기준(문항 7) 그대로, 401을 만드는 두 주체(`GlobalExceptionHandler`의 `handleInvalidCredentials()`, `CustomAuthenticationEntryPoint`)와 403을 만드는 `CustomAccessDeniedHandler`를 하나로 합치지 않고, 각자 위치는 그대로 둔 채 응답 **필드 구성**만 맞췄다.

```java
@ExceptionHandler(InvalidCredentialsException.class)
public ResponseEntity<Map<String, Object>> handleInvalidCredentials(InvalidCredentialsException ex) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("error", ex.getMessage());
    body.put("code", "UNAUTHORIZED");
    body.put("timestamp", Instant.now().toString());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
}
```

`CustomAuthenticationEntryPoint`·`CustomAccessDeniedHandler`는 `ExceptionTranslationFilter` 단계(`DispatcherServlet`보다 바깥)에서 동작해 `@RestControllerAdvice`가 닿지 않는다(Day24에서 확인한 그대로). 그래서 Jackson을 거치지 않고 문자열을 직접 조합하는 기존 방식은 유지하고, 같은 두 필드만 문자열에 추가했다.

```java
response.getWriter().write(
        "{\"error\":\"권한이 없습니다.\",\"code\":\"FORBIDDEN\",\"timestamp\":\""
                + Instant.now() + "\"}");
```

`error` 필드는 지우지 않고 그대로 뒀다 — 필드를 지우면 기존 `jsonPath("$.error")` 단언이 전부 깨지는데, 이번 부채 항목은 "형식 통일"이지 "필드 교체"가 아니기 때문이다. 400(Validation)·404(NotFound)·409(Duplicate)·500(Unexpected) 응답에는 손대지 않았다 — 기술부채 원장에 이번 항목의 범위가 "401/403 일관성"으로 명시돼 있었다.

## 4. 자동 검증 범위

| 확인한 것 | 방법 | 결과 |
|---|---|---|
| 기존 `error` 필드가 그대로 유지된다 | 자동 — 기존 `jsonPath("$.error")` 단언 재확인 | 수정 없이 통과 |
| 403 응답에 `code`("FORBIDDEN")·`timestamp`가 추가됐다 | 자동 — `ReservationControllerHttpTest.cancelReturns403WhenCallerIsNotAdmin` | 통과 |
| 401 응답 두 종류(`GlobalExceptionHandler`·`CustomAuthenticationEntryPoint`)가 같은 필드 구성을 갖는다 | 자동 — `AuthControllerHttpTest.loginReturns401ForWrongPassword`, `ReservationControllerHttpTest.reserveReturns401WhenAuthorizationHeaderIsMissing` | 통과 |

전체 `./gradlew test --console=plain` 51개 통과(테스트 개수 변화 없음, 기존 3개 테스트에 단언만 추가). 코드는 커밋 [d38c973](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/d38c97362738824b5e39dee48f3926d79b37ce42)에 있다.

**미검증 범위**를 구분해둔다. `timestamp`는 항상 UTC(`Instant.now().toString()`)로 찍혀 클라이언트가 로컬 타임존으로 변환해야 하는데, 이 점을 문서화하지 않았다. `CustomAuthenticationEntryPoint`·`CustomAccessDeniedHandler`의 문자열 직접 조합 방식은 메시지에 특수문자가 없어 당장 이스케이핑 위험은 낮지만, Jackson 직렬화만큼 안전하지는 않다.

## 5. 주차 마무리와 다음 시작점

Week D를 시작할 때 인증은 "로그인하면 되는 것"이었다. D1~D5를 거치며 비밀번호가 salt와 함께 해시로 저장되는 과정, JWT가 서명 키의 강도에 실제로 의존한다는 것, 401·403이 서로 다른 필터 컴포넌트에서 나온다는 것을 코드와 로그로 직접 봤다. 이번 시험에서 가장 크게 확인한 건 "오류를 겪어봤다"와 "정확한 근거·주체를 댈 수 있다"는 다르다는 점이다 — HS256 키 길이 오류는 Day23에서 직접 봤지만 정확한 하한선(256비트)은 다시 인출하지 못했고, 401/403의 실제 처리 주체도 "설정 클래스가 다 한다"는 뭉뚱그려진 그림으로 되돌아갔다.

**아직 남은 것**은 두 가지다. HS256 키 길이 규정과 401/403 주체 구분은 복습큐에 다시 올라 **바로 고칠 것(다음 재시험, 10/10)**으로 추적한다. Day26에서 남겨둔 `POST /reservations`·`cancel` 경로의 슬라이스 테스트 부재는 이번 D7에서 다루지 않았다 — **나중에 고칠 것(다음 버퍼)**으로 계속 이월한다.

다음 시작점은 **Week E D1 — 로깅과 설정관리**다. 지금까지는 "누가 요청을 보냈는지"를 다뤘다면, Week E부터는 "무엇을 남기고 어떻게 운영하는가"를 다룬다.

면접에서 다시 답해볼 항목을 남긴다.

- HS256처럼 대칭키 서명을 쓸 때 키 길이가 왜 알고리즘의 해시 출력 크기에 종속되는지
- `SecurityConfig`의 "등록"과 `AuthenticationEntryPoint`/`AccessDeniedHandler`의 "실행"을 분리해서 설명하기

---

오늘 공부한 소스코드: `app/src/main/java/com/example/studyroom/exception/GlobalExceptionHandler.java`, `app/src/main/java/com/example/studyroom/security/CustomAuthenticationEntryPoint.java`, `app/src/main/java/com/example/studyroom/security/CustomAccessDeniedHandler.java`, `app/src/test/java/com/example/studyroom/controller/ReservationControllerHttpTest.java`, `app/src/test/java/com/example/studyroom/controller/AuthControllerHttpTest.java`
