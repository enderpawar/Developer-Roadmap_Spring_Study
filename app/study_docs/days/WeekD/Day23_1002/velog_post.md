# [Spring Study Day 23] JWT 발급·검증 — HS256 서명과 로그인 API

Day22에서 `BCryptPasswordEncoder`로 비밀번호를 해시해서 저장했다. 오늘은 그 해시로 신원을 확인한 뒤 **무엇을 클라이언트에 돌려줄지**를 다룬다. `JwtProvider`가 HS256으로 서명한 JWT를 발급·검증하고, `POST /auth/login`이 실제 로그인 시 토큰을 내려준다. 아직 필터체인은 없어 발급된 토큰을 요청 인가에 실제로 쓰지는 않는다 — 그건 Day24(`SecurityFilterChain`)의 범위다.

> `JwtProvider.issue()`/`parseSubject()`로 토큰을 발급·검증하고, `AuthService.login()`이 아이디·비밀번호를 확인해 토큰을 내려주게 했다. 서명 키를 160비트로 짧게 잡았다가 `WeakKeyException`을 실제로 만났고, RFC 7518 규정대로 256비트 이상으로 늘려 해결했다. 아이디가 없는 경우와 비밀번호가 틀린 경우를 같은 401 응답으로 합쳐 계정 열거를 막았다. `./gradlew test` 41/41 통과까지 확인했다.

> **오늘의 흐름** `짧은 secret → WeakKeyException → 32바이트 이상 secret → JwtProvider.issue()/parseSubject() → POST /auth/login → 401 응답 통합`
>
> 이전 Day: BCrypt 비밀번호 저장, 회원가입 API (Day22)
> 다음 Day: `SecurityFilterChain`으로 토큰을 요청 인가에 연결 (Day24)

![인코딩된 JWT 문자열을 빨강(header).보라(payload).파랑(signature) 3부분으로 색으로 구분해 보여주는 표준 다이어그램. 점(.)으로 구분된 세 부분이 각각 알고리즘 정보, 클레임, 서명임을 나타낸다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day23-web-jwt-structure.png)

*출처: [JSON Web Tokens — jwt.io Introduction](https://www.jwt.io/introduction) — Auth0(Okta 소유)*

## 1. 개념 설명

### 1) JWT의 구조와 서명 검증

> **JWT** = 헤더(alg·타입)·페이로드(클레임)·서명 세 부분을 점(`.`)으로 이어 붙인, 서버가 서명해서 클라이언트에 내려주는 자기완결적 토큰.

우리 코드에서는 `JwtProvider`가 이 세 부분을 만들고 확인하는 유일한 지점이다.

```java
public String issue(String subject) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + expirationMillis);
    return Jwts.builder()
            .subject(subject)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact();
}
```

```text
issue(loginId) 호출
→ header(alg=HS256) + payload(sub=loginId, iat, exp) 조립
→ key로 서명(signWith) → header.payload.signature 문자열 1개(compact) 반환
→ 클라이언트가 이후 요청에 이 문자열을 그대로 들고 다님(Day24에서 실제 사용)
```

검증은 반대 방향이다. `parseSubject()`가 서명을 다시 계산해 토큰에 붙은 서명과 비교하고, 일치하지 않으면 페이로드를 읽지 않고 예외를 던진다.

```java
public String parseSubject(String token) {
    return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getSubject();
}
```

서명이 위조됐으면 `SignatureException`, 만료됐으면 `ExpiredJwtException`이 던져진다. 둘 다 `JwtException`의 하위타입이라 호출부는 `JwtException` 하나만 잡으면 된다 — 이건 소스 주석에 남긴 관찰이고, 오늘 두 예외를 직접 발생시켜 검증하지는 않았다(미검증).

### 2) HS256 키 길이 요구사항과 WeakKeyException

> **HS256(HMAC-SHA256)** = 서버 혼자만 아는 비밀키 하나로 서명·검증을 모두 수행하는 대칭키 서명 알고리즘. RFC 7518 3.2에 따라 키 크기가 해시 출력 크기(256비트) 이상이어야 한다.

`jwt.secret`을 처음에 `studyroom-secret-key`(20바이트=160비트)로 짧게 잡고 `JwtProviderTest`를 돌렸더니 실제로 아래 예외가 났다. 지어낸 오류가 아니라 `build/test-results/test/TEST-...JwtProviderTest.xml`에 그대로 남은 원문이다.

```text
io.jsonwebtoken.security.WeakKeyException: The specified key byte array is 160 bits which is not
secure enough for any JWT HMAC-SHA algorithm.  The JWT JWA Specification (RFC 7518, Section 3.2)
states that keys used with HMAC-SHA algorithms MUST have a size >= 256 bits (the key size must be
greater than or equal to the hash output size).  Consider using the Jwts.SIG.HS256.key() builder
(or HS384.key() or HS512.key()) to create a key guaranteed to be secure enough for your preferred
HMAC-SHA algorithm.  See https://tools.ietf.org/html/rfc7518#section-3.2 for more information.
    at app//io.jsonwebtoken.security.Keys.hmacShaKeyFor(Keys.java:83)
    at app//com.example.studyroom.security.JwtProvider.<init>(JwtProvider.java:22)
    at app//com.example.studyroom.security.JwtProviderTest.<init>(JwtProviderTest.java:11)
```

```text
JwtProvider 생성자 호출
→ Keys.hmacShaKeyFor(secret.getBytes(UTF-8)) — 바이트 배열 길이를 먼저 검사
→ 256비트 미만이면 SecretKey를 만들지 않고 즉시 WeakKeyException
→ 통과하면 SecretKey를 필드에 보관, 이후 서명·검증에 재사용
```

키 검사가 서명·검증 시점이 아니라 **생성자 시점**에 일어난다는 게 오늘 관찰이다. 짧은 키로는 애초에 `JwtProvider` 객체 자체가 만들어지지 않는다. `application.yml`의 `jwt.secret`을 `studyroom-secret-key-must-be-at-least-32-bytes-long`(32바이트 이상)으로 늘려 해결했다. HS384·HS512는 키 요구 크기가 더 크다는 것까지가 예외 메시지로 확인한 범위이고, 실제로 그 두 알고리즘을 써보지는 않았다(미검증).

### 3) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| JWT | header.payload.signature 세 부분으로 구성된 자기완결적 토큰 |
| HS256 | 대칭키 하나로 서명·검증을 모두 수행하는 HMAC-SHA256 알고리즘 |
| Subject(`sub`) 클레임 | 토큰이 누구 것인지 나타내는 표준 클레임 |
| Stateless 인증 | 서버가 세션을 저장하지 않고 토큰 자체로 매 요청의 신원을 확인하는 방식 |

> **더 볼 것**
> - [RFC 7518 — JSON Web Algorithms, Section 3.2](https://tools.ietf.org/html/rfc7518#section-3.2): HMAC-SHA 키 크기 요구사항 원문
> - [jjwt 프로젝트 문서 — JWT Creation](https://github.com/jwtk/jjwt#jwt-create): `Jwts.builder()`/`Jwts.parser()` API

## 2. 코드 구현

### 1) 로그인 서비스 — 조회·검증·발급

`AuthService.login()`이 아이디 조회 → 비밀번호 검증 → 토큰 발급을 순서대로 수행한다.

```java
public String login(String loginId, String rawPassword) {
    Member member = memberRepository.findByLoginId(loginId)
            .orElseThrow(InvalidCredentialsException::new);
    if (!passwordEncoder.matches(rawPassword, member.getPassword())) {
        throw new InvalidCredentialsException();
    }
    return jwtProvider.issue(member.getLoginId());
}
```

**한 줄씩 보기**

- `findByLoginId(loginId).orElseThrow(...)`: 아이디가 없으면 여기서 바로 `InvalidCredentialsException`.
- `passwordEncoder.matches(rawPassword, member.getPassword())`: Day22의 해시와 원문을 비교 — 원문을 다시 해시해서 문자열로 비교하지 않는다(BCrypt의 salt가 매번 달라 단순 비교가 불가능하므로 `matches()`가 내부에서 salt를 재사용해 비교).
- `jwtProvider.issue(member.getLoginId())`: 검증을 통과한 뒤에만 토큰을 만든다.

아래 그림은 일반적인 토큰 기반 인증의 흐름이다. 우리 프로젝트는 별도의 인가 서버(Authorization Server) 없이 **같은 서버가 로그인 시 토큰을 발급**한다는 점이 다르다 — 그림의 ①②(Auth0 인가 서버 왕복)가 우리 코드에서는 `AuthService.login()` 한 번의 호출로 합쳐져 있다.

![클라이언트가 인가 서버에 토큰을 요청해 받은 뒤(①②), 그 토큰을 들고 API(리소스 서버)에 요청하는(③) 토큰 기반 인증의 일반 흐름 다이어그램.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day23-web-jwt-flow.png)

*출처: [JSON Web Tokens — jwt.io Introduction](https://www.jwt.io/introduction) — Auth0(Okta 소유)*

③(API가 토큰을 제시받아 검증하는 단계)은 아직 우리 코드에 없다 — Day24에서 `SecurityFilterChain`이 이 역할을 맡는다.

### 2) 로그인 실패 응답을 하나로 합치기

```java
Member member = memberRepository.findByLoginId(loginId)
        .orElseThrow(InvalidCredentialsException::new);
if (!passwordEncoder.matches(rawPassword, member.getPassword())) {
    throw new InvalidCredentialsException();
}
```

아이디가 없는 경우와 비밀번호가 틀린 경우가 **같은 예외, 같은 메시지("아이디 또는 비밀번호가 올바르지 않습니다")**로 401을 응답한다. 어느 쪽이 틀렸는지 알려주면 공격자가 존재하는 아이디를 하나씩 추측(계정 열거, user enumeration)할 수 있기 때문이다.

### 3) 자동 검증 결과

| 검증 항목 | 방법 | 결과 |
|---|---|---|
| JWT 발급·검증(subject 왕복) | `JwtProviderTest` 2건(신규) | 2/2 통과 |
| 로그인 API(성공/틀린 비밀번호/없는 아이디 401) | `AuthControllerHttpTest` 3건(신규) | 3/3 통과 |
| 회귀 | `./gradlew test` | BUILD SUCCESSFUL, 41/41(Day22 종료 시점 36 + 신규 5) |

커밋: [8832809](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/88328090ae47e8d28f812de9b6b7bff9e862671f)

## 3. 스스로 답한 질문

### 1) 로그인 실패를 404/401로 나누지 않은 이유

**질문.** 아이디가 없는 건 "리소스가 없다"는 뜻인데 왜 404가 아니라 401로 응답하고, 비밀번호 오류와 구분도 안 하는가?

**A1.** 처음엔 "존재하지 않는 리소스니 404가 REST 원칙에 더 맞다"고 답했다. 그런데 그렇게 나누면 공격자가 아이디만 바꿔가며 요청해서 404/401 차이로 "이 아이디는 실제로 존재한다"는 정보를 얻을 수 있다는 설명을 듣고 방향을 바꿨다. 리소스 유무를 정확히 알려주는 게 항상 좋은 설계는 아니고, 인증 실패처럼 정보 자체가 공격 표면이 되는 지점에서는 의도적으로 구분을 없애는 쪽이 맞았다.

## 4. 학습 정리와 다음 범위

### 1) 이해의 변화와 남은 것

Day22에서는 "저장하지 말아야 할 값을 어떻게 저장하는가"를 봤다. 오늘은 "신원을 확인한 결과를 어떤 형태로 클라이언트에 넘기는가"로 넘어갔다. JWT가 서버 세션 없이도 요청마다 신원을 증명할 수 있는 이유는 서명이 위조·변조를 막아주기 때문이고, 그 서명의 안전성이 키 길이 하나에 달려 있다는 걸 `WeakKeyException`으로 직접 겪었다.

**아직 남은 것**은 두 가지다. ① 토큰에 role(권한) 클레임이 없다 — **바로 고칠 것(Day24)**, 필터가 DB를 다시 조회할지 클레임에 실을지는 그때 정한다. ② refresh token과 로그아웃(토큰 무효화)은 다루지 않았다 — **고치지 않을 것**(이 5주 트랙 범위 밖, 로드맵에도 개념으로만 다루게 되어 있음).

면접에서 다시 답해볼 항목을 남긴다.

- HS256(대칭키)과 RS256(비대칭키) 중 여러 서비스가 같은 토큰을 검증해야 할 때 어느 쪽이 적합한지
- stateless 인증에서 로그아웃(토큰 무효화)이 왜 근본적으로 어려운지

---

오늘 공부한 소스코드: `app/src/main/java/com/example/studyroom/security/JwtProvider.java`, `app/src/main/java/com/example/studyroom/service/AuthService.java`, `app/src/main/java/com/example/studyroom/controller/AuthController.java`, `app/src/test/java/com/example/studyroom/security/JwtProviderTest.java`
