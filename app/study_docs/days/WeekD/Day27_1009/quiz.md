# Day27 (10/9, Week D D6) 누적시험 A~D 기록

범위: Week A(웹 계층) + Week B(JDBC·JPA·영속성 컨텍스트) + Week C(트랜잭션 경계·프록시·N+1) + Week D D1~D5(BCrypt·JWT·Security 필터체인·인증 실패 케이스·테스트 분류)

| # | 질문 | 학습자 답 | 판정 | 교정 |
|---|---|---|---|---|
| 1 | DTO(record)와 Domain을 왜 분리하나 | "DTO는 경계를 넘는 불변 데이터라 record, Domain은 상태 변경이 필요해서 가변" | ✅ | Day13·Day20에서 교정한 인과 순서 그대로 재현 |
| 2 | 같은 트랜잭션에서 같은 id를 두 번 조회하면 SELECT는 몇 번인가 | "1차 캐시에 걸려서 1번, 같은 참조" | ✅ | 정확 |
| 3 | self-invocation에서 `this.inner()`가 프록시를 다시 통과하지 않는 근거 | "`this`는 항상 호출을 시작한 객체 자기 자신을 가리킨다. `target.inner()`처럼 외부에서 부를 때만 그 호출이 프록시 인스턴스에서 시작되고, target 내부의 `this.inner()`는 애초에 프록시에서 시작된 호출이 아니다" | ✅ | Day20까지 세 차례 근거가 무너졌다가 Day22 재시험에서 처음 안정적으로 통과한 문항인데, 이번에도 힌트 없이 그대로 재현됐다 |
| 4 | fetch join을 한 번 쓰면 매핑 자체가 바뀌는가 | "아니오, 그 쿼리 메서드 하나에만 적용되는 쿼리 수준 지시. `findAll()`은 여전히 N+1을 낸다" | ✅ | 정확 |
| 5 | `BCryptPasswordEncoder.encode()`를 같은 원문으로 두 번 호출하면 결과가 같은가 | "다르다. salt가 매번 랜덤이라서" | ✅ | 정확 |
| 6 | HS256 서명 키는 최소 몇 비트 이상이어야 하는가 | "128비트 정도면 충분하지 않아요?" | ❌ | RFC 7518 3.2 기준 256비트(32바이트) 이상. Day23에서 160비트 키로 `WeakKeyException`을 직접 봤는데, 정확한 하한선(256) 자체는 다시 인출하지 못했다 |
| 7 | 인증 안 된 요청(401)과 권한 부족 요청(403)은 각각 무엇이 만드는가 | "둘 다 `SecurityConfig`가 직접 만들어서 응답하는 거 아닌가요?" | ❌ | `SecurityConfig`는 `exceptionHandling(...)`으로 두 핸들러를 등록만 한다. 실제로 401은 `CustomAuthenticationEntryPoint`, 403은 `CustomAccessDeniedHandler`가 각각 만들고, 그 앞단인 `ExceptionTranslationFilter`가 인증 여부로 둘을 가른다 |
| 8 | `@WebMvcTest`에서 `addFilters=false` 없이 돌리면 왜 401이 나는가 | "Security 관련 Bean이 하나도 없어서... 근데 왜 401이 나는지는 잘 모르겠다" | ⚠️ 힌트 후 통과 | "클래스패스에 무엇이 있으면 Spring Boot가 기본값을 대신 채우는가"라는 힌트 후, "`spring-boot-starter-security`가 있으면 기본 보안이 자동 적용된다"까지 도달 |

## 총평

8문항 중 힌트 없이 통과 6개, 힌트 후 통과 1개, 오답 2개(6번·7번). Week A·B·C는 안정적으로 유지됐고, self-invocation(3번)은 Day22에서 처음 안정된 이후 이번에도 흔들림 없이 재현됐다. 흔들린 두 문항은 전부 Week D의 "정확한 수치·주체"를 묻는 질문이었다 — 결론(로그인이 안 되면 401, 서명이 짧으면 위험하다)은 알아도 근거가 되는 숫자·클래스명은 다시 헷갈렸다.

## 복습큐 반영(제안)

| 개념 | 판정 | 다음 도래일 |
|---|---|---|
| HS256 서명 키의 최소 길이(256비트, RFC 7518 3.2) | ❌ | +1 (10/10) |
| 401(`AuthenticationEntryPoint`)과 403(`AccessDeniedHandler`)을 만드는 주체 구분 | ❌ | +1 (10/10) |
| `@WebMvcTest` + Security 클래스패스 자동 설정 | ⚠️ 힌트 필요 | +2 (10/11) |
| self-invocation 근거(`this`는 항상 자기 자신을 가리킴) | ✅ (Day22 이후 지속 안정) | +7 (10/16) |
