# Day26 (10/7, Week D D5) 인출 기록

## 1. 세션 예측과 교정 — 테스트 슬라이스와 실제 연결 여부

| # | 질문 | 학습자 답 | 판정 | 교정 |
|---|---|---|---|---|
| Q1 | `@WebMvcTest(ReservationController.class)`만 쓰고 `@AutoConfigureMockMvc(addFilters=false)`를 안 붙이면 `GET /reservations`는 몇 번을 응답하는가 | "200. `SecurityConfig`를 이 슬라이스가 안 가져오니까 보안 없이 통과할 것" | ❌ | 실제는 401. `@WebMvcTest`가 우리 `SecurityConfig`(일반 `@Configuration`)를 안 가져오는 건 맞지만, `spring-boot-starter-security`가 클래스패스에 있으면 Spring Boot가 대신 "모든 요청 인증 필요"라는 더 엄격한 기본 보안을 자동 적용한다 |
| Q2 | `MemberRepositoryDataJpaTest`는 Flyway 마이그레이션이 적용된 상태로 도는가 | "적용된 상태일 것. Repository만 테스트하는 거지 스키마를 새로 만드는 건 아니니까" | ✅ | 실제로 첫 실행부터 통과, `login_id`/`role` 컬럼까지 그대로 조회됨 |
| Q3 | 기존 `TransactionPropagationTest`(진짜 H2를 직접 씀, Mock 없음)는 Unit/Slice/Integration 중 무엇인가 | "Slice 같음. DB를 실제로 쓰니까 Unit은 아니고, Controller까지는 안 걷치니까" | ❌ | 기준은 "실제 DB를 쓰는가"가 아니라 "컨텍스트 범위"다. `@SpringBootTest`는 전체 `ApplicationContext`를 띄우므로 Integration이 맞다. Slice는 "관련 계층 Bean만" 좁혀 띄운 경우를 가리킨다 |
| Q4 | `ReservationServiceTest`(`InMemoryReservationRepository` 사용)는 무엇인가 | "Unit. Spring 컨테이너 자체가 없으니까" | ✅ | 정확 |
| Q5 | 오늘 만든 두 슬라이스 테스트가 인증/인가 동작까지 검증하는가 | "아니요. `ReservationControllerWebMvcTest`는 `addFilters=false`로 필터 자체를 꺼서 Security가 아예 안 낌" | ✅ | 정확. 인증/인가는 Day24·25의 `@SpringBootTest` 테스트들의 몫으로 남아있다 |

## 2. 다음 복습 질문

1. Slice Test의 분류 기준이 "실제 DB 사용 여부"가 아니라 "컨텍스트 범위"인 이유
2. `@WebMvcTest`에 Security가 클래스패스에 있을 때 기본으로 적용되는 규칙
3. `@DataJpaTest`가 각 테스트를 트랜잭션으로 감싸 롤백하는 것과 `@SpringBootTest`+`@Transactional`의 차이

## 3. 복습 일정

Day26 완료일 10/7 기준, Q1(Security 자동 설정)과 Q3(분류 기준)은 오답이라 +1일(10/8)에 먼저 인출한다. Q2·Q4·Q5는 +2일(10/9)로 복귀한다(상세는 `복습큐.md` 참고 — 본 세션에서는 신규 등록만 제안하고 실제 반영은 리드가 처리).
