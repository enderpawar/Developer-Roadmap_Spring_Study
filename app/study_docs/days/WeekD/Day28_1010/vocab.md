# Day28 (10/10, Week D D7 버퍼) 용어

주제: 401/403 오류 응답 형식 통일(`code`·`timestamp` 필드 추가)

| 용어 | 한줄뜻 | 오늘 코드와 관찰 |
|---|---|---|
| 오류 응답 일관성 | 오류가 나는 위치(필터 단계 vs MVC 예외 처리 단계)와 무관하게 클라이언트가 같은 모양의 JSON을 받게 하는 것 | `GlobalExceptionHandler`(401)와 `CustomAuthenticationEntryPoint`(401)가 서로 다른 모양이었던 문제 |
| `code` 필드 | HTTP 상태와 별개로, 클라이언트가 분기 처리하기 위한 안정적인 문자열 식별자 | `"UNAUTHORIZED"`, `"FORBIDDEN"` |
| `timestamp` 필드 | 오류가 발생한 시각(UTC, ISO-8601) | `Instant.now().toString()` |
| `LinkedHashMap` | 삽입 순서를 보존하는 `Map` 구현체 | `handleInvalidCredentials()`의 응답 바디 조립 |
| 문자열 직접 조합 응답 | `HttpServletResponse.getWriter()`로 JSON 문자열을 직접 만들어 쓰는 방식(Jackson을 거치지 않음) | `CustomAuthenticationEntryPoint`·`CustomAccessDeniedHandler`(Day24부터 유지된 설계) |

## 오늘 통일한 응답 비교

| 위치 | 변경 전 | 변경 후 |
|---|---|---|
| `GlobalExceptionHandler`(401, 자격 증명 오류) | `{"error": "..."}` | `{"error": "...", "code": "UNAUTHORIZED", "timestamp": "..."}` |
| `CustomAuthenticationEntryPoint`(401, 인증 자체 안 됨) | `{"error": "인증이 필요합니다."}` | `{"error": "인증이 필요합니다.", "code": "UNAUTHORIZED", "timestamp": "..."}` |
| `CustomAccessDeniedHandler`(403, 권한 부족) | `{"error": "권한이 없습니다."}` | `{"error": "권한이 없습니다.", "code": "FORBIDDEN", "timestamp": "..."}` |
