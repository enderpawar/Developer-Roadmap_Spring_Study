# Day29 (10/12, Week E D1) 용어

주제: 로깅(Request ID·MDC·민감정보 마스킹) + 설정관리(profile·env·secret)

| 용어 | 한줄뜻 | 오늘 코드와 관찰 |
|---|---|---|
| MDC(Mapped Diagnostic Context) | 스레드 로컬에 key-value를 저장해, 그 스레드가 찍는 모든 로그 줄에 자동으로 붙게 하는 SLF4J 저장소 | `MDC.put("requestId", ...)` → 로그 패턴 `%X{requestId}`가 값을 채움 |
| 상관관계 ID(Correlation ID) | 하나의 요청을 여러 로그 줄·서비스에 걸쳐 추적하기 위해 부여하는 고유 식별자 | `X-Request-Id` 헤더 — 클라이언트가 보내면 재사용, 없으면 `UUID.randomUUID()`로 발급 |
| `OncePerRequestFilter` | 요청당 정확히 한 번만 실행되도록 보장하는 Spring의 서블릿 필터 베이스 클래스 | `RequestIdFilter extends OncePerRequestFilter` |
| `FilterRegistrationBean` | 서블릿 컨테이너 필터 체인에 필터를 등록하며 순서(`order`)·URL 패턴을 지정하는 Spring Boot 컴포넌트 | `LoggingConfig.requestIdFilter()` — `Ordered.HIGHEST_PRECEDENCE` |
| 스레드 재사용과 MDC 오염 | 서블릿 컨테이너가 스레드를 풀링해 재사용하므로, 요청 처리 후 MDC를 안 지우면 다음 요청 로그에 이전 값이 새어나가는 문제 | `finally { MDC.remove(MDC_KEY); }` |
| 민감정보 마스킹 | 로그에 비밀번호·토큰 원문이 남지 않도록 존재 유무나 일부만 남기고 나머지를 가리는 처리 | `maskAuthorization()` — `Bearer ***`만 남김 |
| Spring Profile | `application-{profile}.yml`로 환경(local/test/prod)별 설정을 분리하고 `spring.profiles.active`로 선택하는 메커니즘 | `application-local.yml`/`-test.yml`/`-prod.yml` |
| Externalized Configuration | 비밀값·환경별 값을 소스코드가 아니라 외부(환경변수 등)에서 주입받는 설정 방식 | `application-prod.yml`의 `${DB_URL}` 등 |
| Placeholder 미해결 예외 | `${VAR}` 형태의 값에 대응하는 환경변수가 없을 때 Spring이 기동을 막는 `IllegalArgumentException` 계열 오류 | prod 프로필은 기본값 없이 전부 `${...}`만 사용 — 값 없으면 기동 실패 |
| `LoggingApplicationListener` | `ApplicationContext` 기동 과정에서 `Environment`를 읽어 logback 설정(패턴 등)을 재구성하는 Spring Boot 리스너 | 컨테이너 없이 필터만 직접 호출한 테스트에는 커스텀 패턴이 적용되지 않음(아래 참고) |

## 핵심 흐름

```text
요청 도착 → RequestIdFilter(HIGHEST_PRECEDENCE)
→ X-Request-Id 있으면 재사용, 없으면 UUID 발급
→ MDC.put("requestId", id) → 응답 헤더에도 echo
→ 요청 로그 한 줄(Authorization은 마스킹) → filterChain.doFilter()
→ (컨트롤러~서비스 전체 로그에 requestId 자동 첨부)
→ finally: MDC.remove("requestId")
```

## 프로필 분리 관찰

`application.yml`(공통) → `active: local` 기본값. `build.gradle.kts`의 Test 태스크가 `SPRING_PROFILES_ACTIVE=test` 환경변수를 강제해 테스트는 이 기본값을 덮는다. prod 프로필은 `${DB_URL}` 등 기본값 없는 placeholder만 써서, 값이 없으면 기동 자체가 막힌다(비밀값 없이 조용히 뜨는 것보다 안전).
