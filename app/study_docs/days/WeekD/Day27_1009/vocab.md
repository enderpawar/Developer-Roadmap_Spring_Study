# Day27 (10/9, Week D D6) 용어

주제: 누적시험 A~D — 새 용어 없이 Week A~D 개념을 재정리

| 용어 | 한줄뜻 | 오늘 재확인 |
|---|---|---|
| DTO(record) vs Domain | DTO는 경계를 넘는 불변 데이터, Domain은 상태와 변경 규칙을 가진 가변 객체 | 문항 1 |
| 1차 캐시 / 영속성 컨텍스트 | 같은 트랜잭션·같은 id 조회를 캐시로 대체해 같은 참조를 돌려주는 저장소 | 문항 2 |
| self-invocation | target 내부의 `this.메서드()` 호출은 프록시를 다시 통과하지 않아 `@Transactional`이 적용되지 않는 현상 | 문항 3 — **Day22 이후 근거까지 안정적으로 통과 유지** |
| Fetch Join의 적용 범위 | 매핑이 아니라 그 쿼리 메서드 하나에만 적용되는 쿼리 수준 지시 | 문항 4 |
| BCrypt Salt | `encode()`를 호출할 때마다 무작위로 섞는 값 — 같은 원문도 매번 다른 해시를 만듦 | 문항 5 |
| HS256 서명 키 길이 요건 | RFC 7518 3.2에 따라 HMAC-SHA 알고리즘 키는 256비트(32바이트) 이상이어야 함 | 문항 6 — **오답** |
| `AuthenticationEntryPoint`(401) vs `AccessDeniedHandler`(403) | 전자는 인증 자체가 안 된 요청을, 후자는 인증은 됐지만 권한이 부족한 요청을 처리 — 둘 다 필터 단계(`ExceptionTranslationFilter`)에서 갈린다 | 문항 7 — **오답** |
| Slice Test와 Security 자동 설정 | `@WebMvcTest`는 우리 `SecurityConfig`를 안 가져와도, 클래스패스의 `spring-boot-starter-security`가 더 엄격한 기본 보안을 대신 적용 | 문항 8 — 힌트 후 통과 |

## 이번 시험에서 드러난 패턴

Week A·B·C(1~4번)는 이미 여러 차례 재시험을 거쳐 안정됐다 — self-invocation(3번)은 Day22에서 처음 근거까지 통과한 뒤 이번에도 힌트 없이 재현됐다. 흔들린 항목은 전부 Week D(6·7번)였고, 8번은 이틀 전(Day26)에 막 배운 내용이라 힌트가 필요했다.

> **더 볼 것**
> - [RFC 7518, Section 3.2](https://www.rfc-editor.org/rfc/rfc7518#section-3.2): HMAC-SHA 알고리즘의 최소 키 길이 규정
> - [Exception Translation Filter — Spring Security Reference](https://docs.spring.io/spring-security/reference/servlet/architecture.html#servlet-exceptiontranslationfilter): 401·403이 갈리는 필터 단계의 근거
