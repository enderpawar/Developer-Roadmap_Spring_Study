# [Spring Study Day 26] 테스트 범위와 Mock 전략 — Slice Test의 Security 자동 설정 충돌

Day22~25에서 만든 테스트는 크게 두 극단이었다. Spring 컨테이너 없이 클래스를 직접 `new`하는 테스트이거나, `@SpringBootTest`로 전체 애플리케이션을 띄워 실제 H2와 Security 필터까지 전부 통과시키는 테스트였다. 오늘은 지금까지 쌓인 17개 테스트 클래스를 Unit/Slice/Integration 세 계층으로 처음 분류하고, 비어 있던 중간 지점(Controller만, Repository만)에 `@WebMvcTest`·`@DataJpaTest`를 새로 추가했다. Testcontainers 같은 실물 인프라 통합은 Week E 범위다.

> Controller 계층만 띄우는 `@WebMvcTest`에 `GET /reservations`가 200을 반환할 것으로 기대했는데 401이 났다. 원인은 `spring-boot-starter-security`가 클래스패스에 있으면, 우리 `SecurityFilterChain`을 이 슬라이스가 못 가져와도 Spring Boot가 더 엄격한 기본 보안을 대신 적용하기 때문이었다. `@AutoConfigureMockMvc(addFilters = false)`로 필터 자체를 꺼서 해결했다. `@DataJpaTest`는 Flyway 스키마 그대로 첫 실행부터 통과했다. 기존 17개 클래스를 재분류하고 신규 2개를 더해 총 51개 테스트가 전부 통과한다.

> **오늘의 흐름** `기존 17개 클래스 분류 → @WebMvcTest 작성(401 충돌) → addFilters=false로 해결 → @DataJpaTest 작성(1회 통과) → 51/51`
>
> 이전 Day: JWT 인증 실패 케이스 401 테스트 (Day25)
> 다음 Day: Week D D6 — 누적시험 A~D

![Test Pyramid 삼각형 다이어그램. 맨 아래층 Unit Tests가 가장 넓고(고립·빠름), 중간층 Service Tests, 맨 위층 UI Tests가 가장 좁다(통합·느림). 왼쪽 축은 "more isolation, faster", 오른쪽 축은 "more integration, slower"로 표시된다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day26-web-test-pyramid.png)

*출처: [The Practical Test Pyramid](https://martinfowler.com/articles/practical-test-pyramid.html) — Ham Vocke, martinfowler.com(ThoughtWorks)*

## 1. 개념 설명

### 1) Test Pyramid와 세 계층의 구분

> **Test Pyramid** = 빠르고 고립된 테스트를 가장 많이, 여러 컴포넌트가 실제로 맞물리는 느린 테스트를 가장 적게 두라는 비율 지침

우리 코드에서는 오늘 처음으로 17개 클래스, 51개 테스트를 이 기준에 맞춰 세어봤다.

| 계층 | 기준 | 클래스 수 | 테스트 수 | 예시 |
|---|---|---|---|---|
| Unit | Spring 컨테이너 없음, `new`로 직접 생성 | 4 | 9 | `PasswordEncoderTest`, `ReservationServiceTest` |
| Slice | 관련 계층 Bean만 좁혀 로드 | 2(신규) | 4 | `ReservationControllerWebMvcTest`, `MemberRepositoryDataJpaTest` |
| Integration | `@SpringBootTest`로 전체 컨텍스트 로드 | 11 | 38 | `TransactionPropagationTest`, `ReservationControllerHttpTest` |

```text
Unit(가장 많고 빠름이 이상적) → Slice(중간) → Integration(가장 적고 느림이 이상적)
```


이 기준으로 다시 세어 보니 이 프로젝트는 Integration이 11/17(65%)로 압도적으로 많다 — Test Pyramid의 이상적 비율과 반대다. 이유는 H2가 파일이 아니라 인메모리라 "진짜로 연결하는" 비용이 이 프로젝트 규모에서는 낮았고, 지금까지는 Mock으로 대체할 유인이 크지 않았기 때문으로 보인다. 이 비대칭 자체를 오늘 당장 고치지는 않는다(4절 참고).

### 2) Slice Test와 Security 자동 설정 충돌

> **Slice Test** = 전체 `ApplicationContext`가 아니라 한 계층에 관련된 Bean만 골라 띄우는 테스트

우리 코드에서는 `ReservationControllerWebMvcTest`가 이 계층을 처음 추가한다.

```java
@WebMvcTest(ReservationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReservationControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ReservationService reservationService;
    // ...
}
```

처음에는 `@AutoConfigureMockMvc(addFilters = false)` 없이 돌렸다. `GET /reservations`가 200을 반환할 것으로 예상했다 — `@WebMvcTest`는 공식 문서 기준으로 `@Controller`·`@ControllerAdvice`·필터·컨버터 같은 "MVC 관련" Bean만 스캔하고, 우리 `SecurityConfig`(일반 `@Configuration`)는 스캔 대상이 아니기 때문이다. 결과는 반대였다.

```text
java.lang.AssertionError: Status expected:<200> but was:<401>
    at ReservationControllerWebMvcTest.listReturnsServiceResultAsJson(ReservationControllerWebMvcTest.java:37)
```

```text
@WebMvcTest 컨텍스트 로드 → 우리 SecurityFilterChain Bean 없음
→ spring-boot-starter-security가 클래스패스에 존재
→ Spring Boot가 "모든 요청 인증 필요"인 기본 보안 자동 설정을 대신 적용
→ Authorization 헤더 없는 요청 → 401
```

"우리 설정이 없다"와 "보안이 없다"는 다른 문장이었다. 클래스패스에 Security 의존성이 있다는 사실만으로 더 엄격한 기본값이 대신 채워졌다. `@AutoConfigureMockMvc(addFilters = false)`로 서블릿 필터 자체를 껐다 — 이 슬라이스는 "MVC 매핑이 맞는가"만 보고 싶다는 목적에 맞춘 것이고, 그 대가로 이 테스트는 인증/인가 동작을 전혀 검증하지 못한다(그건 Day24·25의 `@SpringBootTest` 테스트들의 몫이다).

### 3) `@DataJpaTest`와 실제 스키마

> **`@DataJpaTest`** = JPA 관련 Bean(Repository·EntityManager)만 스캔하고, 각 테스트를 트랜잭션으로 감싸 끝나면 롤백하는 슬라이스

```java
@DataJpaTest
class MemberRepositoryDataJpaTest {
    @Autowired private MemberRepository memberRepository;
    // existsByLoginId, findByLoginId 검증
}
```

`@SpringBootTest`보다 가볍지만, Flyway가 만든 실제 스키마는 그대로 쓴다. 임베디드 DB로 바뀌어도(`spring.test.database.replace=any`) Flyway가 그 DB에도 처음부터 마이그레이션을 다시 실행해주기 때문이다. 실제로 `login_id`·`role` 컬럼까지 포함한 쿼리가 첫 실행부터 통과했다.

| 구분 | `@WebMvcTest` | `@DataJpaTest` |
|---|---|---|
| 스캔 범위 | Controller·MVC 관련 Bean | Repository·EntityManager |
| 실제 DB 사용 | 안 함(Service를 Mock 처리) | 함(Flyway 스키마 그대로) |
| Security 영향 | 자동 설정과 충돌 가능(`addFilters=false`로 회피) | 웹 계층 자체가 컨텍스트에 없어 무관 |

### 4) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| Unit Test | Spring 컨테이너 없이 클래스를 직접 생성해 검증하는 테스트 |
| Slice Test | 관련 계층 Bean만 좁혀 띄우는 테스트 |
| Integration Test | 전체 `ApplicationContext`를 띄워 검증하는 테스트 |
| `@MockitoBean` | 슬라이스 컨텍스트 안의 특정 Bean을 Mockito 대역으로 교체하는 애노테이션 |
| `@AutoConfigureMockMvc(addFilters = false)` | 서블릿 필터 자체를 끄는 MockMvc 옵션 |

> **더 볼 것**
> - [Testing Spring Boot Applications](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html): `@WebMvcTest`·`@DataJpaTest`를 포함한 슬라이스 테스트 전체 목록
> - [The Practical Test Pyramid](https://martinfowler.com/articles/practical-test-pyramid.html): Unit/Service/UI 3계층 비율과 각 계층의 트레이드오프

## 2. 코드 구현

### 1) `@WebMvcTest` + `@MockitoBean`으로 Controller만 검증

```java
@WebMvcTest(ReservationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReservationControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ReservationService reservationService;

    @Test
    void listReturnsServiceResultAsJson() throws Exception {
        given(reservationService.findAllSummaries())
                .willReturn(List.of(new ReservationSummary("A-101", "민지", null)));

        mockMvc.perform(get("/reservations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roomName").value("A-101"));
    }
}
```

**한 줄씩 보기**

- `@WebMvcTest(ReservationController.class)`: 이 Controller와 MVC 관련 Bean만 컨텍스트에 올림. Service·Repository는 올라오지 않는다.
- `@AutoConfigureMockMvc(addFilters = false)`: 서블릿 필터(Security 포함)를 아예 체인에서 뺀다.
- `@MockitoBean private ReservationService`: 실제 Service 대신 Mockito 대역을 주입. `given(...).willReturn(...)`으로 반환값을 직접 정한다.
- `mockMvc.perform(get(...))`: 실제 HTTP 서버 없이 `DispatcherServlet` 디스패치만 재현.

### 2) `@DataJpaTest`로 쿼리 메서드 검증과 자동 검증 결과

```java
@DataJpaTest
class MemberRepositoryDataJpaTest {

    @Autowired private MemberRepository memberRepository;

    @Test
    void existsByLoginIdReflectsSavedMember() {
        memberRepository.save(new Member("진우", "datajpa-test-01", "hashed"));

        assertTrue(memberRepository.existsByLoginId("datajpa-test-01"));
        assertFalse(memberRepository.existsByLoginId("no-such-login-id"));
    }
}
```

**한 줄씩 보기**

- `@DataJpaTest`: Repository·EntityManager 관련 Bean만 로드하고, 각 테스트를 트랜잭션으로 감싸 끝나면 롤백한다.
- `memberRepository.save(...)`: 실제 H2에 INSERT가 나간다(Mock 아님).
- `existsByLoginId(...)`: Spring Data가 메서드 이름에서 파생한 `EXISTS` 쿼리 — 저장한 로그인 아이디는 `true`, 없는 아이디는 `false`.

**자동 검증 결과**

| 확인한 것 | 방법 | 결과 |
|---|---|---|
| `@WebMvcTest`가 Security 없이는 401을 낸다 | 자동 — `ReservationControllerWebMvcTest`(수정 전 재현) | `expected:<200> but was:<401>` |
| `addFilters=false`로 MVC 매핑만 검증된다 | 자동 — 같은 클래스(수정 후) | 200, `roomName` JSON 값 일치 |
| `@DataJpaTest`가 Flyway 스키마를 그대로 쓴다 | 자동 — `MemberRepositoryDataJpaTest` 3건 | 전부 통과 |
| 전체 회귀 | `./gradlew test --console=plain` | BUILD SUCCESSFUL, 51/51 |

커밋: [126880f](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/126880f49ddeca042b43df1c0e1a6f768f9c71c1)

## 3. 스스로 답한 질문

### 1) `@WebMvcTest`에서 Security가 끼어드는 이유

**질문.** `@WebMvcTest`가 우리 `SecurityConfig`를 스캔하지 않는데도 왜 401이 나는가?

**A1.** 처음에는 "우리 설정이 없으니 보안 없이 통과할 것"이라고 예측했다. 실제로는 `spring-boot-starter-security`가 클래스패스에 있다는 사실만으로 Spring Boot가 "모든 요청 인증 필요"인 더 엄격한 기본 보안을 대신 채워 넣었다. "우리 설정이 없다"와 "보안 자체가 없다"를 같은 말로 여긴 게 예측이 틀린 지점이었다.

## 4. 학습 정리와 다음 범위

### 1) 이해의 변화와 남은 것

지금까지는 테스트를 "빠르다/느리다"로만 구분했다. 오늘 Unit/Slice/Integration을 컨텍스트 범위 기준으로 나누면서, "실제 DB를 쓰는가"가 아니라 "얼마나 좁게 로드하는가"가 분류 기준이라는 걸 `@DataJpaTest`(실제 DB를 쓰면서도 Slice)로 확인했다. `@WebMvcTest`의 401 충돌은 슬라이스가 "우리 설정 없음"과 "보안 없음"을 혼동하기 쉽다는 걸 보여준 사례였다.

**아직 남은 것**은 두 가지다. ① `POST /reservations`, `POST /reservations/cancel/{id}` 경로에는 슬라이스 테스트가 없다 — **나중에 고칠 것(다음 버퍼, Day28)**. ② `@DataJpaTest`가 기본으로 켜는 `spring.test.database.replace=any`가 왜 우리 `application.yml`의 파일 기반 H2 설정을 무시해도 괜찮은지(Flyway가 새 DB에도 스키마를 다시 만들어주기 때문)는 짧게만 언급했다 — **고치지 않을 것**(범위 밖).

면접에서 다시 답해볼 항목을 남긴다.

- 이 프로젝트가 Test Pyramid의 이상적 비율과 반대로 Integration이 많은 이유와, 그것이 항상 문제인지
- `addFilters=false`로 얻는 것과 포기하는 것을 한 문장씩으로 구분하기

---

오늘 공부한 소스코드: `app/src/test/java/com/example/studyroom/controller/ReservationControllerWebMvcTest.java`, `app/src/test/java/com/example/studyroom/repository/MemberRepositoryDataJpaTest.java`
