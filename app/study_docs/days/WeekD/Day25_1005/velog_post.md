# [Spring Study Day 25] JWT 인증 실패 케이스 — CSRF·CORS·로그아웃의 Stateless 한계

Day24에서 만든 `JwtAuthenticationFilter`는 성공 경로(유효한 토큰)만 테스트돼 있었다. 오늘은 그 필터의 실패 경로 — 만료된 토큰, 위조된 서명, 헤더 자체가 없는 경우, `Bearer ` 접두사가 없는 형식 오류 — 를 한 파일에 모아 전부 401로 떨어지는지 확인했다. 새 코드는 이 테스트 파일 하나뿐이라, 나머지는 이미 stateless로 설계된 이 인증 방식이 CSRF·CORS·로그아웃과 어떻게 관계 맺는지를 코드 변경 없이 관찰만 했다.

> 4개의 인증 실패 테스트(만료·위조·헤더 누락·형식 오류)를 새로 만들었고, 전부 첫 실행부터 통과했다. Day24에서 만든 `catch (JwtException e)` 분기가 이미 이 경우들을 포괄하도록 설계돼 있었기 때문으로 보이며, 실패를 지어내지 않고 그대로 기록한다. CSRF·CORS·stateless 로그아웃은 코드 변경 없이 현재 설계의 방어 범위를 개념적으로 확인했다.

> **오늘의 흐름** `인증 실패 4종 테스트 작성 → 전부 401로 첫 실행 통과 → stateless 설계가 CSRF를 무력화하는 이유 확인 → CORS·로그아웃 한계는 관찰만`
>
> 이전 Day: `SecurityFilterChain` 구성과 커스텀 401·403 핸들러 (Day24)
> 다음 Day: 단위·통합·HTTP 테스트 분류 (Day26)

## 1. 개념 설명

### 1) JwtException 계층과 눈사태 효과

> **눈사태 효과(avalanche effect)** = 해시·서명 입력을 한 글자만 바꿔도 출력이 완전히 달라지는 성질

우리 코드에서는 서명 위조 테스트가 이 성질을 그대로 이용한다.

```java
String tamperedToken = token.substring(0, token.length() - 1)
        + (token.charAt(token.length() - 1) == 'A' ? 'B' : 'A');
```

`JwtProvider.parseSubject()`는 서명이 위조됐으면 `SignatureException`, 만료됐으면 `ExpiredJwtException`을 던진다. 둘 다 `JwtException`의 하위타입이라 `JwtAuthenticationFilter`의 `catch (JwtException e)` 한 줄이 둘 다 잡는다.

```text
서명 위조 → parseSubject() → SignatureException(JwtException) → clearContext() → 401
토큰 만료 → parseSubject() → ExpiredJwtException(JwtException) → clearContext() → 401
```

### 2) CSRF와 stateless 인증의 무관성

> **CSRF** = 피해자의 브라우저가 세션 쿠키를 자동으로 실어 보내는 성질을 악용해, 다른 사이트가 피해자 대신 요청을 위조하는 공격

![CSRF 공격 흐름. 피해자가 공격자의 페이지를 열면 숨겨진 form이 은행 사이트로 자동 POST되고, 브라우저가 피해자의 세션 쿠키를 자동으로 첨부해 은행 서버가 이를 정상 요청처럼 처리한다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day25-web-csrf.png)

*출처: [Cross-site request forgery (CSRF) - Security | MDN](https://developer.mozilla.org/en-US/docs/Web/Security/Attacks/CSRF) — MDN Web Docs contributors, CC BY-SA 2.5*

이 공격이 성립하려면 "브라우저가 인증 정보를 자동으로 첨부한다"는 전제가 있어야 한다. `SecurityConfig`가 `.sessionManagement(... STATELESS)`로 세션 쿠키 자체를 안 쓰고, JWT는 클라이언트 코드가 `Authorization` 헤더에 매번 직접 실어야 한다 — 자동 첨부 경로가 없으니 이 공격의 전제가 깨진다. `.csrf(csrf -> csrf.disable())`를 Day24에서 끈 근거가 이 그림으로 다시 확인된다. 다만 이건 "쿠키 기반 세션"에 한정된 관찰이고, 토큰을 `localStorage`에 저장했을 때 생기는 XSS 경로의 위험은 별개 주제라 오늘 다루지 않는다.

### 3) CORS preflight 개요

> **CORS preflight** = 브라우저가 실제 요청 전에 `OPTIONS`로 서버의 허용 여부를 먼저 확인하는 절차

![CORS preflight 시퀀스. 브라우저가 Access-Control-Request-Method/-Headers를 담은 OPTIONS를 먼저 보내고, 서버가 204와 Access-Control-Allow-* 헤더로 응답한 뒤에야 실제 POST 요청이 전송되고 200이 돌아온다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day25-web-cors-preflight.png)

*출처: [Cross-Origin Resource Sharing (CORS) - HTTP | MDN](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS) — MDN Web Docs contributors, CC BY-SA 2.5*

이 프로젝트는 아직 별도 CORS 설정이 없다 — 지금까지의 테스트는 전부 같은 서버 안의 MockMvc 호출이라 브라우저의 오리진 비교 자체가 일어나지 않는다. 실제 프런트엔드가 다른 오리진에서 이 API를 호출하면 이 preflight를 통과해야 하는데, 그 설정은 오늘 범위 밖이라 미검증으로 남긴다.

### 4) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| SignatureException | 서명이 발급 시점과 다를 때 던져지는 JwtException 하위타입 |
| ExpiredJwtException | 만료 시각이 지났을 때 던져지는 JwtException 하위타입 |
| CSRF | 자동 첨부되는 쿠키를 악용해 요청을 위조하는 공격 |
| CORS preflight | 실제 요청 전에 OPTIONS로 허용 여부를 먼저 확인하는 절차 |

> **더 볼 것**
> - [Cross-site request forgery (CSRF) - MDN](https://developer.mozilla.org/en-US/docs/Web/Security/Attacks/CSRF): 공격 흐름과 방어 수단
> - [CORS - MDN](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS): preflight가 필요한 조건과 헤더 구성

## 2. 코드 구현

### 1) 인증 실패 4종과 자동 검증 결과

| 검증 항목 | 방법 | 결과 |
|---|---|---|
| 헤더 없음·형식 오류·위조·만료 | `JwtAuthenticationFailureTest` 4개 | 첫 실행부터 4/4 통과, 전부 401 |
| 회귀 | `./gradlew test --console=plain` | BUILD SUCCESSFUL, 47/47 |

새로 짠 코드가 첫 실행부터 통과한 이유는 Day24에서 `catch (JwtException e)`를 서명 위조·만료 모두를 포괄하도록 이미 넓게 잡아뒀기 때문으로 보인다. 실패를 지어내지 않고 이 사실을 그대로 남긴다.

커밋: [f765bbf](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/f765bbfd28922cb7acd7d60012d0642a7e9400f6)

## 3. 스스로 답한 질문

### 1) stateless 로그아웃의 즉시 무효화 한계

**질문.** 로그아웃 API를 만들면 그 순간 발급했던 토큰을 서버가 즉시 무효화할 수 있는가?

**A1.** 처음에는 "가능하다. 로그아웃 API에서 그 토큰을 지우면 될 것 같다"고 답했다.

실제로는 서버가 토큰의 상태를 별도로 저장하지 않는 stateless 구조라 "지운다"는 개념 자체가 없다. 클라이언트가 들고 있는 토큰 문자열은 서명이 유효한 한 만료 시각까지 그대로 유효하다. 즉시 무효화하려면 블랙리스트 같은 별도 상태 저장이 필요한데, 이는 지금의 stateless 설계와 상충한다 — 오늘은 이 한계를 관찰만 하고 구현하지 않았다.

## 4. 학습 정리와 다음 범위

### 1) 이해의 변화와 남은 것

Day24에서는 인증이 성공하는 경로만 확인했다. 오늘은 실패 경로 4종을 모아 전부 같은 401로 수렴한다는 것을 테스트로 굳혔고, 그 위에서 CSRF·CORS·로그아웃이 이 stateless 설계와 어떤 관계인지를 코드 없이 개념으로 정리했다.

**아직 남은 것**은 CORS 설정 자체가 없다는 점이다 — **나중에 고칠 것**(실제 프런트엔드 연동 Day에서 `CorsConfigurationSource`를 추가). stateless 로그아웃의 즉시 무효화는 블랙리스트 저장소가 필요한 구조적 트레이드오프라 **고치지 않을 것**으로 남긴다(이 트랙 범위 밖).

면접에서 다시 답해볼 항목을 남긴다.

- `Thread.sleep` 기반 만료 테스트를 결정론적으로 바꾸는 방법
- 토큰을 `localStorage`에 저장했을 때 CSRF 대신 커지는 위험

---

오늘 공부한 소스코드: `app/src/test/java/com/example/studyroom/security/JwtAuthenticationFailureTest.java`, `app/src/main/java/com/example/studyroom/security/JwtAuthenticationFilter.java`, `app/src/main/java/com/example/studyroom/config/SecurityConfig.java`
