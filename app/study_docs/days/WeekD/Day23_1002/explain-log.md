# Day23 (10/2, Week D D2) 예측→실행→차이 기록

주제: JWT(HS256) 발급·검증, `POST /auth/login`

## 실험 1 — 짧은 서명 키로 `JwtProvider` 생성

- 코드: `new JwtProvider("studyroom-secret-key", 3600000L)` (20바이트)
- 예측: "에러가 날 것 같다"는 막연한 감만 있었고, 컴파일 시점인지 실행 시점인지는 몰랐다
- 실행: `JwtProviderTest` 두 테스트 모두 즉시 실패, 아래 예외가 그대로 찍혔다
  ```text
  io.jsonwebtoken.security.WeakKeyException: The specified key byte array is 160 bits which is not
  secure enough for any JWT HMAC-SHA algorithm. ... keys used with HMAC-SHA algorithms MUST have a
  size >= 256 bits ...
      at app//io.jsonwebtoken.security.Keys.hmacShaKeyFor(Keys.java:83)
      at app//com.example.studyroom.security.JwtProvider.<init>(JwtProvider.java:22)
  ```
- 교정: RFC 7518 3.2 기준 256비트(32바이트) 이상으로 secret을 늘려야 했다. `application.yml`과 테스트 생성자 인자를 `studyroom-secret-key-must-be-at-least-32-bytes-long`으로 바꿔 해결했다.

## 실험 2 — 발급한 토큰을 그대로 검증

- 코드:
  ```java
  String token = jwtProvider.issue("jinwoo01");
  String subject = jwtProvider.parseSubject(token);
  assertEquals("jinwoo01", subject);
  ```
- 예측: `issue()`에 넣은 subject가 `parseSubject()`에서 그대로 나올 것
- 실행: 통과 — 서명이 유효하면 `parseSignedClaims()`가 페이로드를 그대로 복원

## 실험 3 — 로그인 실패 두 종류를 같은 응답으로 합치기

- 코드:
  ```java
  Member member = memberRepository.findByLoginId(loginId)
          .orElseThrow(InvalidCredentialsException::new);
  if (!passwordEncoder.matches(rawPassword, member.getPassword())) {
      throw new InvalidCredentialsException();
  }
  ```
- 예측: 아이디가 없으면 404, 비밀번호가 틀리면 401일 것이라고 답했다
- 실행: `AuthControllerHttpTest.loginReturns401WhenLoginIdDoesNotExist`, `loginReturns401ForWrongPassword` 둘 다 401 + 같은 메시지
- 교정: 처음엔 "존재하지 않는 리소스니 404가 맞다"고 생각했는데, 그렇게 응답을 나누면 공격자가 존재하는 아이디를 하나씩 추측(user enumeration)할 수 있다는 설명을 듣고 방향을 바꿨다. `findByLoginId`가 비어 있어도, 비밀번호가 틀려도 같은 `InvalidCredentialsException`(401)으로 합친다.

## 판단 로직 교정 과정

- 1차 판단: "404/401을 나누는 게 REST 원칙에 더 맞다"
- 교정: 보안이 REST 관습보다 우선하는 지점이 있다는 걸 처음 마주했다. 리소스 유무를 정확히 알려주는 게 항상 좋은 설계는 아니고, 여기서는 정보를 의도적으로 덜 주는 쪽(같은 메시지)이 맞는 판단이었다.

## 검증 근거

- `src/test/java/com/example/studyroom/security/JwtProviderTest.java`
- `src/test/java/com/example/studyroom/controller/AuthControllerHttpTest.java`
- `./gradlew test` 전체 스위트, BUILD SUCCESSFUL 41/41 (커밋 `8832809`)

## [직접 작성] 오늘 배운 것을 내 문장으로

<!-- 아래는 학습자가 직접 채운다. 비워두지 말 것. -->

- HS256 키 길이 제한이 존재하는 이유:
- 로그인 실패 메시지를 하나로 합쳐야 하는 이유:

## 다음 시작점

Week D D3 — `SecurityFilterChain`으로 토큰을 실제 요청 인가에 연결 (Day24).
