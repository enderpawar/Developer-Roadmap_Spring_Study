# Day25 (10/5, Week D D4) 예측→실행→차이 기록

주제: 인증 실패 케이스(만료·위조·헤더 누락·형식 오류) 테스트, stateless 설계와 CSRF·CORS·로그아웃의 관계

## 실험 1 — 서명 위조

- 코드: `JwtAuthenticationFailureTest.tamperedSignatureReturns401()` — 발급된 토큰의 마지막 글자 하나만 바꿔 서명을 깨뜨림
- 예측: 해시 기반 서명은 입력이 조금만 달라져도 결과가 완전히 달라진다(눈사태 효과) → 검증 실패 → 401
- 실행: 첫 실행부터 통과, 401 확인
- 확인된 것: `parseSubject()`가 `SignatureException`(`JwtException` 하위타입)을 던지고, `JwtAuthenticationFilter`의 `catch (JwtException e)`가 이미 이를 포괄하고 있었다

## 실험 2 — 토큰 만료

- 코드: 이 테스트 클래스에서만 `@SpringBootTest(properties = "jwt.expiration=1")`로 만료시간을 1ms로 단축, 발급 직후 `Thread.sleep(20)`
- 예측: 20ms는 1ms보다 훨씬 크므로 반드시 만료 상태가 된다 → 401
- 실행: 첫 실행부터 통과
- 남는 의문: `Thread.sleep` 기반 시간 테스트는 느린 환경(CI 등)에서 이론적으로 불안정할 수 있다 — `Clock`을 주입 가능하게 바꾸면 결정론적으로 만들 수 있지만, 이번 범위에서는 "짧은 만료시간 + sleep"으로 단순화했다

## 실험 3 — 헤더 없음 / Bearer 접두사 없음

- 코드: `missingAuthorizationHeaderReturns401`, `malformedBearerPrefixReturns401`
- 예측: 헤더가 없으면 `header != null` 검사에서 걸리고, 접두사가 없으면 `startsWith("Bearer ")` 검사에서 걸려 — 두 경우 다 토큰을 꺼내려는 시도 자체를 안 한다 → 인증 정보가 안 채워져 401
- 실행: 둘 다 통과. SQL 로그로 간접 확인 — `memberRepository.findByLoginId()` 호출 자체가 없어 "필터 단계에서 이미 걸렀다"는 것이 로그의 부재로 드러난다

## 판단 교정 — stateless 로그아웃의 한계

- 1차 답: "로그아웃 API에서 토큰을 지우면 그 순간 무효화된다"
- 교정: 서버가 토큰의 상태를 별도로 저장하지 않는 stateless 구조에서는 "지운다"는 개념 자체가 없다. 클라이언트가 들고 있는 토큰 문자열은 서명이 유효한 한 만료 시각까지 그대로 유효하다 — 즉시 무효화하려면 블랙리스트 같은 상태 저장이 필요한데, 이는 지금 설계와 상충한다. 오늘은 구현하지 않고 한계로만 남긴다

## 검증 근거

- `src/test/java/com/example/studyroom/security/JwtAuthenticationFailureTest.java`
- `./gradlew test` 전체 스위트, BUILD SUCCESSFUL 47/47 (커밋 `f765bbf` 시점)

## [직접 작성] 오늘 배운 것을 내 문장으로

- 서명 위조와 만료가 같은 `catch` 절에서 처리돼도 되는 이유:
- stateless 설계가 CSRF 방어를 자연히 불필요하게 만드는 이유:

## 다음 시작점

Week D D5 — 단위·통합·HTTP 테스트 분류.
