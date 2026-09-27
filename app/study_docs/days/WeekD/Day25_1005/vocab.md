# Day25 (10/5, Week D D4) 용어

주제: 인증 실패 케이스 테스트, CSRF·CORS·로그아웃의 stateless JWT 한계

| 용어 | 한줄뜻 | 오늘 코드와 관찰 |
|---|---|---|
| `SignatureException` | JWT 서명이 발급 시점의 서명과 일치하지 않을 때 던져지는 `JwtException` 하위타입 | `tamperedSignatureReturns401` — 토큰 마지막 글자 하나만 바꿔 재현 |
| `ExpiredJwtException` | 토큰의 `exp` 클레임이 현재 시각보다 과거일 때 던져지는 `JwtException` 하위타입 | `expiredTokenReturns401` — `jwt.expiration=1`(1ms) + `Thread.sleep(20)` |
| 눈사태 효과(avalanche effect) | 해시·서명 입력을 한 글자만 바꿔도 출력이 완전히 달라지는 성질 | 서명 한 글자 위조만으로도 검증이 확실히 실패 |
| `@SpringBootTest(properties = "...")` | 그 테스트 클래스에서만 특정 설정값을 오버라이드 | `JwtAuthenticationFailureTest`에만 `jwt.expiration=1` 적용, 전역엔 영향 없음 |
| CSRF (Cross-Site Request Forgery) | 피해자의 브라우저가 자동으로 실어 보내는 세션 쿠키를 이용해, 다른 사이트가 피해자 대신 요청을 위조하는 공격 | 이 앱은 세션 쿠키가 없어(stateless) 그 전제가 성립하지 않음 → `.csrf(csrf -> csrf.disable())` |
| CORS preflight | 브라우저가 실제 요청 전에 `OPTIONS`로 서버의 허용 여부를 먼저 확인하는 절차 | 이 프로젝트는 별도 CORS 설정이 없어 브라우저 기반 다른 오리진 호출은 아직 다루지 않음(관찰만) |
| stateless 로그아웃의 한계 | 서버가 발급한 토큰의 상태를 따로 저장하지 않으므로, 로그아웃해도 그 토큰은 만료 시각까지 여전히 유효하다 | 별도 블랙리스트·저장소 없이는 즉시 무효화 수단이 없음(오늘 관찰 범위, 구현 안 함) |

## 핵심 호출 경로

```text
인증 실패 4종 — 전부 JwtAuthenticationFilter 단계에서 걸려 401
헤더 없음      → header == null                        → 인증 정보 안 채움 → 401
Bearer 접두사 없음 → header.startsWith("Bearer ") 실패      → 인증 정보 안 채움 → 401
서명 위조      → parseSubject()가 SignatureException      → clearContext() → 401
토큰 만료      → parseSubject()가 ExpiredJwtException      → clearContext() → 401
```

```text
CSRF가 성립하려면(오늘 관찰)
공격 사이트의 폼/스크립트 → 피해자 브라우저가 세션 쿠키를 자동 첨부 → 서버가 쿠키만으로 인증
→ 이 앱은 세션 쿠키 자체가 없다(JWT를 Authorization 헤더로 수동 첨부) → 자동 첨부 경로가 없어 전제가 깨짐
```
