# Day23 (10/2, Week D D2) 용어

주제: JWT(HS256) 발급·검증, 로그인 API

| 용어 | 한줄뜻 | 오늘 코드와 관찰 |
|---|---|---|
| JWT(JSON Web Token) | 헤더·페이로드·서명 세 부분을 점(`.`)으로 이어 붙인, 서버가 서명해서 클라이언트에 내려주는 자기완결적 토큰 | `JwtProvider.issue()`가 생성 |
| HS256 | HMAC-SHA256 — 서버 혼자만 아는 비밀키 하나로 서명·검증을 모두 수행하는 대칭키 알고리즘 | `Keys.hmacShaKeyFor(secret.getBytes(...))` |
| Subject(`sub`) 클레임 | "이 토큰이 누구 것인가"를 나타내는 표준 클레임 | `issue()`에 `loginId`를 subject로 담음 |
| `SecretKey` | 서명·검증에 쓰는 대칭키 객체 | `JwtProvider` 생성자에서 1회 생성해 필드로 보관 |
| RFC 7518 3.2 | HMAC-SHA 계열 키는 해시 출력 크기 이상(HS256은 256비트=32바이트) 이어야 한다는 JWA 명세 규칙 | 오늘 `WeakKeyException`의 근거 조항 |
| Stateless 인증 | 서버가 세션을 따로 저장하지 않고, 매 요청의 토큰 자체로 신원을 확인하는 방식 | 로그인 성공 시 토큰만 발급, 서버 세션 저장소 없음 |
| 계정 열거(user enumeration) 방지 | 아이디 존재 여부와 비밀번호 오류를 구분해 응답하면 공격자가 존재하는 아이디를 추측할 수 있어, 두 실패를 같은 메시지로 응답하는 방어 | `AuthService.login()`이 두 실패 모두 `InvalidCredentialsException`(401, 동일 메시지) |
| `expiration` 클레임 | 토큰의 만료 시각 | `issuedAt`+`jwt.expiration`(1시간)으로 계산 |

## 핵심 호출 경로

```text
POST /auth/login
→ AuthService.login(loginId, rawPassword)
→ memberRepository.findByLoginId(loginId) — 없으면 InvalidCredentialsException(401)
→ passwordEncoder.matches(rawPassword, member.getPassword()) — 틀리면 같은 예외(401)
→ jwtProvider.issue(member.getLoginId()) — subject=loginId로 서명된 토큰 생성
→ LoginResponse(token) 반환
```
