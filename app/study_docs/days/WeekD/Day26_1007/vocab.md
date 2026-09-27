# Day26 (10/7, Week D D5) 용어

주제: 테스트 분류(Unit/Slice/Integration) — `@WebMvcTest`·`@DataJpaTest` 슬라이스 추가

| 용어 | 한줄뜻 | 오늘 코드와 관찰 |
|---|---|---|
| Unit Test | Spring 컨테이너 없이 클래스를 `new`로 직접 만들어 검증하는 가장 좁은 범위 테스트 | `PasswordEncoderTest`·`ReservationServiceTest` 등 기존 4개 클래스 |
| Slice Test | 전체 `ApplicationContext`가 아니라 한 계층에 관련된 Bean만 골라 띄우는 테스트 | `ReservationControllerWebMvcTest`·`MemberRepositoryDataJpaTest`(신규 2개) |
| Integration Test | `@SpringBootTest`로 전체 `ApplicationContext`를 띄워 여러 계층이 실제로 맞물리는지 확인하는 테스트 | 기존 11개 클래스(H2까지 실제로 사용) |
| `@WebMvcTest` | MVC 관련 Bean(Controller·`@ControllerAdvice`·필터·컨버터)만 스캔하는 슬라이스 | `ReservationControllerWebMvcTest` |
| `@MockitoBean` | 슬라이스 컨텍스트 안에서 특정 Bean을 Mockito 대역으로 교체 | `ReservationService`를 Mock 처리 |
| `@AutoConfigureMockMvc(addFilters = false)` | 서블릿 필터(Security 포함) 자체를 꺼서 순수 MVC 매핑만 검증 | Security 자동 설정 충돌 회피 |
| `@DataJpaTest` | JPA 관련 Bean(Repository·EntityManager)만 스캔하고, 각 테스트를 트랜잭션으로 감싸 끝나면 롤백하는 슬라이스 | `MemberRepositoryDataJpaTest` |
| Test Pyramid | 빠르고 고립된 테스트를 많이(기반), 느리고 통합적인 테스트를 적게(꼭대기) 두라는 비율 지침 | 아래 그림(velog_post 참고) |
| Spring Boot 기본 보안 자동 설정 | `spring-boot-starter-security`가 클래스패스에 있으면, 슬라이스가 우리 `SecurityFilterChain`을 못 가져와도 "모든 요청 인증 필요"라는 더 엄격한 기본값이 대신 적용되는 동작 | `@WebMvcTest`에서 200 기대 → 401 실제 |

## 오늘 다시 분류한 기존 클래스(발췌)

| 클래스 | 분류 | 근거 |
|---|---|---|
| `TransactionPropagationTest` | Integration | `@SpringBootTest`, 실제 H2로 커넥션 2개 동시 사용까지 관찰 |
| `ReservationServiceTest` | Unit | Spring 컨테이너 없이 `InMemoryReservationRepository`(순수 자바 컬렉션)로 직접 생성 |
| `JwtProviderTest` | Unit | Spring 컨테이너 없이 `JwtProvider`를 `new`로 직접 생성 |
