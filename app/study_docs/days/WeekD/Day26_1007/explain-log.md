# Day26 (10/7, Week D D5) 예측→실행→차이 기록

주제: `@WebMvcTest`·`@DataJpaTest` 슬라이스 추가, 17개 테스트 클래스 재분류

## 실험 1 — `@WebMvcTest` + Security 자동 설정 충돌

- 코드: `ReservationControllerWebMvcTest.listReturnsServiceResultAsJson()` — `@WebMvcTest(ReservationController.class)`만 두고 `GET /reservations`가 200을 반환하는지 확인
- 예측: `@WebMvcTest`는 `SecurityConfig`(일반 `@Configuration`)를 스캔 대상으로 가져오지 않으니, 이 슬라이스에는 보안 자체가 없어 그냥 200이 나올 것
- 실행 결과: `Status expected:<200> but was:<401>` (원문은 `day26.md` 참고)
- 왜 틀렸나: "우리 `SecurityConfig`가 없다"까지는 맞았지만, `spring-boot-starter-security`가 클래스패스에 존재하는 것만으로 Spring Boot가 대신 "모든 요청 인증 필요"라는 기본 보안 자동 설정을 적용한다는 사실을 놓쳤다. 슬라이스가 "우리 설정 없음"과 "보안 없음"을 같은 뜻으로 오해한 것이다.
- 수정: `@AutoConfigureMockMvc(addFilters = false)`로 서블릿 필터 자체를 꺼서, 이 슬라이스의 목적("MVC 매핑이 맞는가")에 맞게 범위를 좁혔다.

## 실험 2 — `@DataJpaTest`와 실제 스키마

- 코드: `MemberRepositoryDataJpaTest`의 `existsByLoginIdReflectsSavedMember()` 등 3개
- 예측: `@DataJpaTest`도 Flyway가 만든 실제 스키마를 그대로 쓸 것이다(임베디드 DB로 바뀌어도 마이그레이션은 다시 실행됨)
- 실행 결과: 3개 테스트 모두 첫 실행부터 통과, `login_id`/`role` 컬럼까지 정상 조회
- 판정: 예측과 일치. 오류를 지어내지 않고, 실제로 안 났다는 사실을 그대로 남긴다.

## 판단 로직 교정 과정 — Slice의 분류 기준

- 1차 생각: "실제 DB나 실제 Bean을 쓰면 Integration에 가깝다"
- 교정: 분류 기준은 "무엇을 실제로 쓰는가"가 아니라 "컨텍스트가 얼마나 좁게 로드되는가"다. `@DataJpaTest`는 실제 H2·Flyway를 쓰면서도 Slice다 — JPA 관련 Bean만 로드하기 때문. `@SpringBootTest`는 Mock을 하나도 안 써도 Integration이다 — 전체 컨텍스트를 로드하기 때문.

## 검증 근거

- `app/src/test/java/com/example/studyroom/controller/ReservationControllerWebMvcTest.java`
- `app/src/test/java/com/example/studyroom/repository/MemberRepositoryDataJpaTest.java`
- `./gradlew test --console=plain` BUILD SUCCESSFUL, 51/51 (커밋 [`126880f`](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/126880f49ddeca042b43df1c0e1a6f768f9c71c1))

## [직접 작성] 오늘 배운 것을 내 문장으로

<!-- 아래는 학습자가 직접 채운다. 비워두지 말 것. -->

- `@WebMvcTest`가 우리 `SecurityConfig`를 안 가져오는 것과 "보안이 없다"가 다른 이유:
- Slice Test의 분류 기준이 "실제 연결 여부"가 아니라 "컨텍스트 범위"인 이유:

## 다음 시작점

Week D D6 — 누적시험 A~D.
