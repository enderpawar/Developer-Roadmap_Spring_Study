# Day23 (10/2, Week D D2) 인출 기록

## 1. 세션 예측과 교정 — JWT

| # | 질문 | 학습자 답 | 판정 | 교정 |
|---|---|---|---|---|
| P1 | HS256 서명 키가 256비트보다 짧으면 어떻게 되는가 | "에러가 날 것 같다. 근데 컴파일 에러인지 실행 에러인지는 모르겠다" | ✅ | 실행 시점 예외(`WeakKeyException`) — `Keys.hmacShaKeyFor()` 내부에서 키 바이트 길이를 검사해서 던짐 |
| P2 | `issue()`로 만든 토큰을 `parseSubject()`에 그대로 넣으면 무엇이 나오는가 | "issue에 넣은 subject 문자열 그대로 나올 것" | ✅ | `parseSubjectReturnsSameSubjectUsedAtIssue`로 확인 |
| P3 | 로그인 시 아이디가 없는 경우와 비밀번호가 틀린 경우, 응답이 서로 다른가 | "아이디가 없으면 404, 비밀번호가 틀리면 401일 것 같다" | ❌ | 둘 다 401 + 동일 메시지("아이디 또는 비밀번호가 올바르지 않습니다") — 어느 쪽이 틀렸는지 구분해 응답하면 존재하는 아이디를 공격자가 추측(user enumeration)할 수 있어서 의도적으로 합쳤다 |
| P4 | secret 키를 32바이트 이상으로만 늘리면 충분한가, 아니면 다른 조건도 있는가 | "일단 32바이트 넘기면 될 것 같다" | ✅ | RFC 7518 3.2 기준을 만족하면 `Keys.hmacShaKeyFor()`가 예외 없이 키를 생성함(추가 조건 없음, 오늘 확인 범위) |

## 2. 실제로 겪은 오류 — WeakKeyException

`jwt.secret`을 `studyroom-secret-key`(160비트)로 처음 잡고 `JwtProviderTest`를 돌렸을 때 실제로 발생한 예외. 예측 없이 처음 실행에서 그대로 맞닥뜨렸다.

```text
io.jsonwebtoken.security.WeakKeyException: The specified key byte array is 160 bits which is not
secure enough for any JWT HMAC-SHA algorithm.  The JWT JWA Specification (RFC 7518, Section 3.2)
states that keys used with HMAC-SHA algorithms MUST have a size >= 256 bits (the key size must be
greater than or equal to the hash output size).
```

원인: HS256은 256비트(32바이트) 이상 키를 요구하는데 처음 고른 문자열은 20바이트뿐이었다. `jwt.secret`을 32바이트 이상 문자열로 늘려 해결했다.

## 3. 다음 복습 질문

1. HS256(대칭키)과 RS256(비대칭키) 중 어느 쪽이 여러 서비스가 같은 토큰을 검증해야 할 때 더 적합한지
2. stateless 인증의 장점과, 로그아웃(토큰 무효화)이 왜 stateless와 상충하는지

## 4. 복습 일정

Day23 완료일 10/2 기준, 오늘 새로 등록한 JWT/HS256/WeakKeyException 항목은 +2일 10/4에 먼저 인출한다.
