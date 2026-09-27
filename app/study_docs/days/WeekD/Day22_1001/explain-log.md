# Day22 (10/1, Week D D1) 예측→실행→차이 기록

주제: BCrypt 비밀번호 해싱, 회원가입 API

## 실험 1 — 같은 원문의 해시가 매번 다른가

- 코드: `PasswordEncoderTest.sameRawPasswordProducesDifferentHashesEachTime()`
  ```java
  String firstHash = passwordEncoder.encode(rawPassword);
  String secondHash = passwordEncoder.encode(rawPassword);
  assertNotEquals(firstHash, secondHash);
  ```
- 예측: salt가 매번 랜덤이므로 두 해시는 다를 것
- 실행: `assertNotEquals` 통과, 실제로 서로 다른 문자열이 나옴
- 교정 필요했던 부분: 없음 — 예측과 일치

## 실험 2 — 해시 문자열의 접두어

- 코드: `assertTrue(hash.startsWith("$2a$10$"))`
- 예측: 버전(`2a`) + strength 기본값(`10`)이 앞에 붙을 것
- 실행: 통과
- 짚고 넘어간 것: `new BCryptPasswordEncoder()`처럼 인자 없이 생성하면 strength가 코드에 안 보이는데, 이 기본값이 10이라는 건 Javadoc에서 확인했지 코드만 봐서는 알 수 없었다.

## 실험 3 — 회원가입 API의 중복·검증 경로

- 코드: `AuthControllerHttpTest.signupReturns409WhenLoginIdAlreadyExists`, `signupReturns400WhenPasswordTooShort`
- 예측: 같은 `loginId`로 두 번 가입하면 두 번째는 409, 8자 미만 비밀번호는 400
- 실행: 둘 다 예측과 일치. `DuplicateLoginIdException`은 `GlobalExceptionHandler`의 신규 핸들러가 409로, `@Size(min = 8)` 위반은 Spring 기본 검증 핸들러가 400으로 매핑

## 판단 로직 교정 과정

- 1차 판단: "로그인 계정 컬럼(`login_id`, `password`)은 `NOT NULL`이어야 안전하다"
- 교정: 기존 픽스처(`new Member(name)`, 예: `NPlusOneTest`)가 로그인 계정 없이 저장되는 경로가 이미 코드베이스에 있다. `NOT NULL`로 걸면 그 테스트들이 전부 깨진다. Day19에서 `default` 메서드로 대조군 구현체를 안 건드렸던 것과 같은 방향으로, 이번엔 스키마를 nullable로 남겨 기존 호환을 우선했다. 실제 서비스라면 회원 정보(Member)와 로그인 계정(Account)을 분리하는 설계가 더 맞겠지만, 이 트랙 범위에서는 한 테이블에 얹는 절충을 택했다.

## 검증 근거

- `src/test/java/com/example/studyroom/config/PasswordEncoderTest.java`
- `src/test/java/com/example/studyroom/controller/AuthControllerHttpTest.java`
- `./gradlew test` 전체 스위트, BUILD SUCCESSFUL 36/36 (커밋 `be3a973`)

## [직접 작성] 오늘 배운 것을 내 문장으로

<!-- 아래는 학습자가 직접 채운다. 비워두지 말 것. -->

- BCrypt가 같은 원문으로도 매번 다른 해시를 내는 이유:
- 비밀번호를 암호화가 아니라 해싱해야 하는 이유:

## 다음 시작점

Week D D2 — JWT 발급·검증, `POST /auth/login`.
