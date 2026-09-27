# 백엔드 기본기 로드맵 (5주 · 확정본 v2)

> 확정일 2026-07-24 · 실행 **2026-07-25(금) ~ 2026-08-28(목)** · 종료 후 복습 ~9/11
> 대상: 컴퓨터공학과 3학년, 백엔드/Spring 최초. CS 이론(자료구조·OS·네트워크·DB) 보유.
> 목표: **5주 전 범위를 훑어 1회독 멘탈모델을 만든다.** 유창한 독립구현은 이 단계 목표가 아니며 **공모전 프로젝트**에서 채운다. 단 인증·테스트·디버깅은 졸업 루브릭 필수라 밀려도 스킵하지 않는다.
> 성격: 상용 백엔드 강의(예: 인프런 스프링 로드맵, 약 116시간)가 쓰는 **"기반 → 추상화" 원칙을 선택적으로 압축 적용**한 능동 학습 트랙이다. 상용과 *동일 순서·동일 범위*가 아니라 핵심만 골라 빠르게 도는 버전이다.
> 이 문서는 Claude 초안 → codex(gpt-5.6) 2회 교차검증을 반영한 확정본이다. 이 갱신 저장소에서 사용하는 유일한 학습 계획이다.

---

## 0. 핵심 방법 3줄

1. **비계 점감**: 개념마다 완성예제 → 빈칸 → 독립. 초보자에게 "직접 재현부터"는 금지.
2. **인출 + 간격반복**: 안 보고 다시 쓰고, +2/+7/+14일에 다시 인출. 다시 읽기·형광펜은 효과 없다.
3. **CS 연결**: 모든 개념을 이미 아는 CS 이론에 붙인다(트랜잭션=ACID, 인덱스=B-tree, 해시=BCrypt).

## 1. 학습 단위: 티어링된 유닛

### Full 루프 (메커니즘/하드 개념)
1. 개념 + 완성예제 (AI가 CS에 연결해 설명 + 주석 달린 도는 코드; 학습자는 타이핑·실행)
2. 빈칸 예제 (핵심 2~4줄 채움)
3. 독립 변형 (살짝 다른 요구로 혼자)
4. 인출 (코드/노트 덮고 재작성 + 퀴즈 → **정답 대조 → 오답 교정**)
5. 예측→실행→차이설명 (동작을 먼저 예측 → 실행 → 예측과 실제 차이 설명)
6. 복습큐 등록 (+2/+7/+14일)

### Light 루프 (용어/작은 개념)
개념 + 짧은 예제 → 인출 1문항 → 복습큐 등록.

> **하루 부하 상한(중요): Full 루프는 하루 최대 1개.** 나머지는 Light 또는 관찰(observation)로 둔다. 이 상한이 과부하를 막는 핵심 장치다.

## 2. 간격반복 엔진

| 장치 | 주기 | 규칙 |
|---|---|---|
| 아침 인출 워밍업 | 매일 10분 | 복습큐에서 도래한 질문을 코드 안 보고 답 |
| 오답 재시험 | +1일 | 틀린 항목은 맞을 때까지 다음날 재시험 → 맞으면 정상 간격 복귀 |
| 개념별 간격 | +2 / +7 / +14일 | 각 개념 재인출 |
| 주간 누적시험 | 매주 6일차 | 그 주까지 전 범위 섞어서(교차연습) |
| **종료 후 복습** | 8/29~9/11 | Week D·E 개념의 +7/+14일이 8/28 이후 도래 → **주 2회 20분** 짧은 인출 세션으로 마무리 |
| 최종 시험 | 8/27 | 전 범위 (직전 Week E 신규 내용은 종료 후 복습에서 한 번 더) |

## 3. 주간 리듬

**주 5일 학습 + 6일차 누적시험 + 7일차 버퍼.** 밀리면 자르는 순서: Week E 실배포·Testcontainers → 버퍼 미화. **JPA·트랜잭션·검증·오류·인증·테스트·디버깅은 안 자른다.**

**공식 '벽'은 Week C(트랜잭션·프록시)와 Week D(인증·테스트) 둘.** 이 두 주는 느려도 되고, 버퍼일을 지연 흡수에 우선 쓴다. Week B는 쉽지 않지만 5주 분리로 완화됐다.

## 4. 5주 로드맵 (하루 = Full 1 + 지원 Light/관찰)

### 세션 재개용 진행 체크리스트

> **모든 새 학습 세션은 날짜표보다 이 체크리스트를 먼저 확인한다.** 달력상 다음 주차가 되었더라도 미완료 필수 유닛을 건너뛰지 않고, 마지막 `[x]`의 다음 항목부터 재개한다.
>
> `[x]`는 코드·테스트와 해당 Day 산출물(`vocab.md`, `quiz.md`, `explain-log.md`, 필요 시 `progress.md`)로 완료가 확인된 경우에만 표시한다. 시작했거나 설명만 들은 항목은 완료로 표시하지 않는다. 세션 종료 시 체크 상태와 **다음 시작점**을 함께 갱신한다.

**현재 확인 시점: 2026-10-21 — 5주 트랙 종료.** Week A~E D1~D7 전 구간이 코드·테스트와 Day 산출물로 완료 확인됐다. 졸업 루브릭(§5) 자가평가는 12개 중 10개 통과, 2개는 보완 표시로 남아 있다(JWT stateless·로그아웃 무효화 한계 / Unit·Slice·Integration 구분 — 둘 다 Day35 재시험에서 힌트 없이 통과했으나 +7 재확인 전이라 보완으로 유지한다). **다음 세션은 새 Day 진행이 아니라 종료 후 복습(주 2회 20분, §2 간격반복 엔진 참고)이다** — [복습큐.md](복습큐.md)에 남은 도래분과 위 보완 2건의 +7 재확인을 우선 처리한다. 상세는 [Day35 기록](days/WeekE/Day35_1021/).
> **Week C D3 완료 근거(2026-09-25)**: `TransactionPropagationTest`에서 ① `REQUIRED`(기본값): `PropagationOuterService.reserveThenFail()`이 예외를 던지면 `PropagationInnerService.reserve()`가 저장한 예약도 같은 트랜잭션에 합류해 함께 롤백됨을 확인, ② `REQUIRES_NEW`로 전환 후 같은 실패 상황에서도 예약이 독립 트랜잭션으로 이미 커밋되어 살아남음을 확인했다. 전체 테스트 23개가 통과했다. 전체 스위트 실행 중 `findAll().isEmpty()`가 다른 테스트의 커밋 데이터 때문에 실패하는 테스트 격리 문제를 발견해 `stream().noneMatch/anyMatch`로 특정 레코드만 검증하도록 교정했다. 상세는 [Day17 기록](days/WeekC/Day17_0925/).
> **Week C D2 완료 근거(2026-09-20)**: `ReservationService` Bean이 `ReservationService$$SpringCGLIB$$0` 타입의 AOP 프록시임을 `AopUtils`로 확인했다. 테스트용 `SelfInvocationService`에서 외부 `inner()` 호출은 트랜잭션 활성 `true`, 비트랜잭션 `outer()`의 내부 호출은 `false`, `@Transactional transactionalOuter()`의 내부 호출은 바깥 경계의 효과로 `true`임을 검증했다. 전체 테스트 22개가 통과했다. 상세는 [Day16 기록](days/WeekC/Day16_0920/).
> **Week C D1 완료 근거(2026-09-20)**: `ReservationService.cancel()` 전체에 `@Transactional`을 적용하고 명시적 `save()`를 제거했다. `ReservationServiceTransactionTest`에서 ① 정상 반환 시 변경 감지 `UPDATE`와 commit 후 취소 상태 유지, ② 명시적 `flush()`로 `UPDATE`를 실행한 뒤 `RuntimeException`으로 rollback되어 기존 `confirmed=true`, `cancelReason=null`이 유지되는 것을 H2 통합 테스트로 확인했다. 전체 테스트 18개가 통과했다. 상세는 [Day15 기록](days/WeekC/Day15_0920/).
> **D4·D5 완료 근거(2026-08-22, 데이터 유실 복구 후 재학습)**: `JpaReservationRepositoryTest`에 통합 테스트 2개를 추가했다. ① 같은 트랜잭션 안에서 같은 id를 2회 `findById()`하면 SELECT는 0번(이미 캐시), `clear()` 추가 시 SELECT 1번으로 바뀌고 `first == second`는 두 조건 모두 `true` — 1차 캐시가 값이 아니라 트랜잭션·id 기준으로 참조를 재사용함을 확인했다(`JpaReservationRepositoryTest.java:65-81`). ② `confirmed=true`로 저장한 예약을 재조회해 `cancel()`만 호출하고 `save()`는 호출하지 않았는데도 `flush()` 시점에 `UPDATE`가 자동 실행됨을 로그로 확인했다(`JpaReservationRepositoryTest.java:83-110`, dirty checking). 상세는 [Day11 기록](days/WeekB/Day11_0822/), [Day12 기록](days/WeekB/Day12_0822/).
> **D3 완료 근거(2026-08-09)**: `Reservation` Entity 매핑, Spring Data JPA 어댑터 CRUD, JDBC Bean 후보 제거를 완료했다. 통합 테스트 2개로 신규 저장·단건 조회와 기존 ID 갱신·중복 방지를 검증했고 Hibernate 로그에서 `INSERT`·`SELECT`·`UPDATE`를 확인했다. 상세는 [Day10 진행 기록](days/WeekB/Day10_0807/progress.md).
> **D6 시작**: Week A+B 범위 누적시험. 오답은 그 자리에서 복습큐에 등록한다.
> **밀린 인출분(2026-08-09 Day10 등록 전 기준 실측)**: 기존 복습큐 38행 중 **36행이 도래**했다(미래 도래는 `@Repository` 8/19, `Optional`→도메인예외 8/12 둘뿐). 내역 — 8/3 1건 / 8/4 18건 / **8/6 오답재시험 4건** / 8/7 8건(Day08 계열) / 8/8 4건 / 8/9 1건. 여기에 Day09 `quiz.md` §1 6문항(8/7 출제 예정이었으나 미실시)과 Day10 신규 7건이 별도로 있다.
> **한 번에 따라잡지 않는다.** 아침 10분에는 복습큐 「밀렸을 때 규칙」대로 **5~7문항만** 뽑고(오답재시험 4건 우선), 나머지는 Week B D7 버퍼에서 몰아 처리한다. 도래일은 뒤로 밀지 않는다 — 지연량이 증거다.
> 환경 부채: 사용자 범위 환경변수 `SPRING_DATASOURCE_*`·`SPRING_PROFILES_ACTIVE=prod`가 남아 있다(다른 프로젝트용). `test` 태스크에서만 격리해둔 상태라 `bootRun`은 여전히 영향을 받는다.
> 일정: 달력상 8/9는 Week C D2이나 실제 진도는 Week B D3이다(**6일 지연**). Week C·D가 「벽」으로 지정된 주라 추가 지연을 전제하고, 자르지 않기로 한 항목(JPA·트랜잭션·검증·오류·인증·테스트·디버깅)을 우선 지킨다.

#### Week A — 웹 계층

- [x] D1 요청→응답 왕복 — [Day01 기록](days/WeekA/Day01_0725/)
- [x] D2 record DTO vs Domain 분리 — [Day02 기록](days/WeekA/Day02_0726/)
- [x] D3 전역 오류처리 + Bean Validation — [Day03 기록](days/WeekA/Day03_0728/)
- [x] D4 Service/Repository 책임 분리 — [Day04 진행 기록](days/WeekA/Day04_0728/progress.md)
- [x] D5 IoC·DI·생성자 주입 — [Day05 진행 기록](days/WeekA/Day05_0729/progress.md)
- [x] **D6 누적시험 A + 오답 재시험** — 2026-08-02 완료 ([Day06 기록](days/WeekA/Day06_0730/))
- [x] **D7 버퍼 / 기술부채 상환** — 2026-08-02 완료 ([Day07 기록](days/WeekA/Day07_0731/))

Week A 통합 Velog — [Spring Study Day 6 & Day 7: 1주차 마무리 시험](velog/week-a-identity-storage-error-boundary.md)

#### Week B — 데이터 접근 기초

- [x] D1 Flyway `V1__init`로 스키마 정의 — 2026-08-05 완료 ([Day08 기록](days/WeekB/Day08_0801/))
- [x] D2 JDBC 완성예제와 JPA 도입 이유 — 2026-08-07 종료 ([Day09 기록](days/WeekB/Day09_0802/))
  - 잔여 ① `JdbcReservationRepository.save()`의 UPDATE 분기는 **JDBC 구현에 쓰지 않았다.** Spring Data `save()`가 같은 분기(ID 없으면 INSERT, 있으면 UPDATE)를 수행하므로 대체된 것으로 처리한다. **JDBC 구현에는 중복 행 버그가 그대로 남아 있고 이를 검증하는 테스트도 없다** — 대조군으로만 보존한다.
  - 잔여 ②③(`JdbcTemplate`/`JdbcClient` 비교, JPA·Hibernate·Spring Data 구분)은 D3 도입부에서 흡수했다.
- [x] D3 Entity 매핑 + 기본 CRUD — 2026-08-09 완료 ([Day10 기록](days/WeekB/Day10_0807/))
- [x] D4 영속성 컨텍스트·1차 캐시·동일성 — 2026-08-22 완료 ([Day11 기록](days/WeekB/Day11_0822/))
- [x] D5 변경 감지·flush 시점 — 2026-08-22 완료 ([Day12 기록](days/WeekB/Day12_0822/))
- [x] D6 누적시험 A+B — 2026-08-22 완료, 8/8 통과 ([Day13 기록](days/WeekB/Day13_0822/))
- [x] D7 버퍼 / `ddl-auto: validate` — 2026-08-22 완료: ①②(ddl-auto: validate 전환, CHECK 제약 부채 상환) + ③독립과제(`cancel_reason` 컬럼, V3 마이그레이션+Entity+Service+Controller 전 계층 관통) ([Day14 기록](days/WeekB/Day14_0822/)). **패턴 승격 완료** — `CODE_PATTERNS.md`에 P18~P21 append, P10~P17 근거 줄번호 재감사(P13·P16·P17·P11 정정), `PATTERN_DRILLS.md`에 묶음 7(D18~D21, Loan 도메인) 추가.

Week B 통합 Velog — [Spring Study Day 11 ~ Day 14: 2주차 마무리 시험](velog/week-b-persistence-context-and-dirty-checking.md)
> D4~D7이 하루에 진행돼 처음에는 네 날을 한 편으로 합쳤다. 2026-09-23 사용자 요청으로 Day별 글로 다시 분할했다 — [Day11](days/WeekB/Day11_0822/velog_post.md) · [Day12](days/WeekB/Day12_0822/velog_post.md) · [Day13](days/WeekB/Day13_0822/velog_post.md) · [Day14](days/WeekB/Day14_0822/velog_post.md). 통합 글은 이력으로 보존한다.

Week B 전체와 Week C D1~D3 완료. 다음은 Week C D4 — 연관관계 + Hibernate LAZY 프록시.

#### Week C — 트랜잭션·프록시·성능

- [x] D1 트랜잭션 경계 / 커밋·롤백 — 2026-09-20 완료 ([Day15 기록](days/WeekC/Day15_0920/))
- [x] D2 Spring AOP 프록시 / self-invocation 관찰 — 2026-09-20 완료 ([Day16 기록](days/WeekC/Day16_0920/))
- [x] D3 트랜잭션 전파 — 2026-09-25 완료 ([Day17 기록](days/WeekC/Day17_0925/))
> **Week C D4 완료 근거(2026-09-27)**: `Member.reservations`에 `@OneToMany(mappedBy="member", fetch=LAZY)`를 채우고, `MemberLazyProxyTest.memberReservationsCollectionIsLazyPersistentBag()`으로 컬렉션 방향 LAZY 초기화(`PersistentBag`, `Hibernate.isInitialized()`)를 확인했다. 커밋 `7a9626d`, 27/27 통과.
> **Week C D5 완료 근거(2026-09-27)**: `NPlusOneTest`의 `println` 관찰을 `Statistics.getPrepareStatementCount()` 단언으로 교체(N+1=4, fetch join=1, 기존 실측치와 일치). 커밋 `7a9626d`, 27/27 통과.
> **Week C D6 완료 근거(2026-09-28)**: Week A~C 10문항 인출, self-invocation·AOP프록시 vs Hibernate프록시·fetch join 적용범위 3건 오답 교정. 상세는 [Day20 기록](days/WeekC/Day20_0928/).
> **Week C D7 완료 근거(2026-09-29)**: `FetchJoinInnerVsLeftTest`로 inner join fetch의 member-null 누락을 실증하고 `findAllWithMemberOrNull()`(left join fetch)을 추가, `ReservationService.findAllSummaries()`(`readOnly=true`) + `GET /reservations` 독립과제 완료. 커밋 `094c6b3`, 30/30 통과.
- [x] D4 연관관계 + Hibernate LAZY 프록시 — 2026-09-27 완료(이월분 포함) ([Day18 기록](days/WeekC/Day18_0925/))
- [x] D5 N+1 확인 + fetch join — 2026-09-27 완료(이월분 포함) ([Day19 기록](days/WeekC/Day19_0926/))
- [x] D6 누적시험 A+B+C — 2026-09-28 완료, 10문항 중 7개 통과(6개 힌트없이, 1개 힌트후), 3개 오답 교정 ([Day20 기록](days/WeekC/Day20_0928/))
- [x] D7 버퍼 — 2026-09-29 완료: inner/left join fetch 대조 + `GET /reservations`(readOnly) 독립과제 ([Day21 기록](days/WeekC/Day21_0929/)). **패턴 승격 완료** — `CODE_PATTERNS.md`에 P22~P26 append, `PATTERN_DRILLS.md`에 묶음8(D22~D26, Loan 도메인) 추가.

Week C 통합 Velog — [[Spring Study Day 20 & Day 21] 3주차 마무리 시험](velog/week-c-transaction-proxy-and-fetch-strategy.md)

#### Week D — 인증 + 테스트

> **Week D D1 완료 근거(2026-10-01)**: `Member`에 `loginId`/`password`를 nullable 컬럼으로 추가하고, `BCryptPasswordEncoder`를 `PasswordEncoder` Bean으로 등록해 `POST /auth/signup`이 원문 대신 해시를 저장하게 했다. `PasswordEncoderTest`로 동일 원문의 해시가 매번 다르다는 것과 `$2a$10$` 접두어를 확인했고, `AuthControllerHttpTest`로 성공/중복 409/짧은 비밀번호 400을 확인했다. 전체 테스트 36개가 통과했다. 상세는 [Day22 기록](days/WeekD/Day22_1001/).
> **Week D D2 완료 근거(2026-10-02)**: `JwtProvider`가 HS256으로 토큰을 발급(`issue`)·검증(`parseSubject`)하고, `POST /auth/login`이 아이디·비밀번호 확인 후 토큰을 내려주게 했다. 서명 키를 160비트로 짧게 잡아 `WeakKeyException`을 실제로 재현했고 RFC 7518 기준 256비트 이상으로 교정했다. 아이디 없음/비밀번호 오류를 같은 401 응답으로 합쳐 계정 열거를 방지했다. 전체 테스트 41개가 통과했다. 상세는 [Day23 기록](days/WeekD/Day23_1002/).
> **Week D D3 완료 근거(2026-10-04)**: `SecurityFilterChain`+`JwtAuthenticationFilter`+커스텀 401/403 핸들러를 도입했다. 커밋 `a617422`, `ReservationControllerHttpTest` 43/43 통과(기존 5개 401 회귀 수정 + 신규 2개). 상세는 [Day24 기록](days/WeekD/Day24_1004/).
> **Week D D4 완료 근거(2026-10-05)**: `JwtAuthenticationFailureTest`로 위조·만료·헤더누락·형식오류 4가지 인증 실패 케이스를 모두 401로 확인하고, CSRF/CORS/로그아웃 한계를 정리했다. 커밋 `f765bbf`, 47/47 통과. 상세는 [Day25 기록](days/WeekD/Day25_1005/).
> **Week D D5 완료 근거(2026-10-07)**: 기존 17개 테스트 클래스(51개 테스트)를 Unit(4)/Slice(2, 신규)/Integration(11)으로 분류하고 `ReservationControllerWebMvcTest`(`@WebMvcTest`)·`MemberRepositoryDataJpaTest`(`@DataJpaTest`)를 추가했다. `addFilters=false` 없이 돌렸을 때 Spring Boot 기본 보안 자동 설정으로 401이 나는 걸 직접 재현하고 수정했다. 커밋 `126880f`, 51/51 통과.
> **Week D D6 완료 근거(2026-10-09)**: Week A~D 8문항 인출, self-invocation은 Day22 이후 안정 유지, HS256 키 최소 길이와 401/403을 만드는 주체(`AuthenticationEntryPoint`/`AccessDeniedHandler`) 2건 오답 교정. 상세는 [Day27 기록](days/WeekD/Day27_1009/).
> **Week D D7 완료 근거(2026-10-10)**: D6 문항 7 교정 기준을 적용해 `GlobalExceptionHandler`·`CustomAuthenticationEntryPoint`·`CustomAccessDeniedHandler`의 401/403 응답에 `code`·`timestamp` 필드를 통일 추가(기존 `error` 필드는 유지). 커밋 `d38c973`, 51/51 통과.
- [x] D1 BCrypt 비밀번호 저장 — 2026-10-01 완료 ([Day22 기록](days/WeekD/Day22_1001/))
- [x] D2 JWT 발급·검증 — 2026-10-02 완료 ([Day23 기록](days/WeekD/Day23_1002/))
- [x] D3 Security Filter Chain·SecurityContext — 2026-10-04 완료 ([Day24 기록](days/WeekD/Day24_1004/))
- [x] D4 인증 실패 케이스 테스트 — 2026-10-05 완료 ([Day25 기록](days/WeekD/Day25_1005/))
- [x] D5 테스트 분류 + H2 통합 테스트 — 2026-10-07 완료 ([Day26 기록](days/WeekD/Day26_1007/))
- [x] D6 누적시험 A~D — 2026-10-09 완료, 8문항 중 6개 힌트없이+1개 힌트후 통과, 2개 오답 교정 ([Day27 기록](days/WeekD/Day27_1009/))
- [x] D7 버퍼 — 2026-10-10 완료: 401/403 응답 code·timestamp 필드 통일 ([Day28 기록](days/WeekD/Day28_1010/)). **패턴 승격 완료** — `CODE_PATTERNS.md`에 P27~P32 append, `PATTERN_DRILLS.md`에 묶음9(D27~D32, Loan 도메인) 추가.

Week D 통합 Velog — [[Spring Study Day 27 & Day 28] 4주차 마무리 시험](velog/week-d-authentication-and-test-strategy.md)

#### Week E — 운영·디버깅·통합

> **Week E D1 완료 근거(2026-10-12)**: `RequestIdFilter`(MDC 상관관계 추적)와 profile 분리(local/test/prod) + fail-fast 비밀값 관리를 도입했다. 커밋 `21fd3d5`, `./gradlew test` 60/60, `RequestIdFilterTest`·`RequestIdHttpTest` 9종 신규. 상세는 [Day29 기록](days/WeekE/Day29_1012/).
> **Week E D2 완료 근거(2026-10-13)**: 취소 사유 트리밍 버그를 재현 테스트로 먼저 확정한 뒤 DEBUG 로그로 원인을 좁혀 수정했다. 커밋 `a9d9864`(재현, BUILD FAILED 59/61) → `8a030ea`(수정, BUILD SUCCESSFUL 61/61). 상세는 [Day30 기록](days/WeekE/Day30_1013/).
> **Week E D3 완료 근거(2026-10-15)**: 멀티스테이지 `Dockerfile`과 `compose.yaml`(app+MySQL, healthcheck)을 작성했다. 커밋 `bb5a462`, `./gradlew test` 61/61(로컬 Docker 미설치로 실행 자체는 미검증, Day32로 이월). 상세는 [Day31 기록](days/WeekE/Day31_1015/).
> **Week E D4 완료 근거(2026-10-16)**: GitHub Actions에 `docker` 잡을 추가해 실제 이미지 빌드·컨테이너 기동을 검증했다. 커밋 `9f088d7`+`4b58652`, run `36251196912` test✓/docker✓(1차 run `36250928356`은 MySQL Error 1064로 실패 후 수정). 상세는 [Day32 기록](days/WeekE/Day32_1016/).
> **Week E D5 완료 근거(2026-10-18)**: 예약 시간대 중복 방지(`start < otherEnd && otherStart < end`, 맞닿는 구간 허용)를 마이그레이션(V7)·엔티티·DTO·서비스·예외·리포지토리 3종·테스트 전 계층에 걸쳐 추가했다. 신규 10개 포함 `./gradlew test` 71/71 통과. 커밋 `11763ea`. 상세는 [Day33 기록](days/WeekE/Day33_1018/).
> **Week E D6 완료 근거(2026-10-20)**: Week A~E 14문항 인출, 힌트 없이 통과 11개·오답 3개(JWT stateless, 테스트 슬라이스 범위, Docker 멀티스테이지). 상세는 [Day34 기록](days/WeekE/Day34_1020/).
> **Week E D7 완료 근거(2026-10-21)**: 오답 2개 재시험 힌트 없이 통과, 졸업 루브릭(§5) 12개 중 10개 통과·2개 보완으로 자가평가. 상세는 [Day35 기록](days/WeekE/Day35_1021/).
- [x] D1 로깅 + 설정관리 — 2026-10-12 완료 ([Day29 기록](days/WeekE/Day29_1012/))
- [x] D2 디버깅 실습 — 2026-10-13 완료: 취소 사유 트리밍 버그 재현·수정 ([Day30 기록](days/WeekE/Day30_1013/))
- [x] D3 Docker / Compose — 2026-10-15 완료 ([Day31 기록](days/WeekE/Day31_1015/))
- [x] D4 GitHub Actions CI — 2026-10-16 완료 ([Day32 기록](days/WeekE/Day32_1016/))
- [x] D5 누적 독립과제 — 2026-10-18 완료: 예약 시간대 중복 방지 ([Day33 기록](days/WeekE/Day33_1018/))
- [x] D6 최종 인출 시험 — 2026-10-20 완료, 14문항 중 11개 힌트없이 통과·3개 오답 교정 ([Day34 기록](days/WeekE/Day34_1020/))
- [x] D7 버퍼 / 졸업판정 — 2026-10-21 완료: 오답 2개 재시험 통과, 졸업 루브릭 12개 중 10개 통과·2개 보완 ([Day35 기록](days/WeekE/Day35_1021/)). **패턴 승격 완료** — `CODE_PATTERNS.md`에 P33~P38 append, `PATTERN_DRILLS.md`에 묶음10(D33~D38, Loan 도메인) 추가.

Week E 통합 Velog — [[Spring Study Day 34 & Day 35] 5주차 마무리 시험](velog/week-e-operations-and-graduation.md)

### 사전 (7/25 D1에 포함) — 새 시작점 확인
이 저장소는 **빈 최소 스켈레톤**에서 시작했다(Spring Boot 3.5.3 · Java 17 · web+validation, 롬복 없음). 저장소 루트에서 `./gradlew test`가 green인지 확인했다. 과거 완성 코드는 이 갱신 저장소에 포함하지 않는다. 현재 코드와 Day 기록을 근거로 직접 학습한다.

### Week A (7/25~7/31) — 웹 계층: 요청의 생애
| 일 | Full(1) | 지원 Light/관찰 | 난이도 |
|---|---|---|---|
| 7/25 D1 | 요청→응답 왕복(Controller 하나) | 스켈레톤 빌드·실행 확인 / HTTP·상태코드 | ★★★ |
| 7/26 D2 | record DTO vs Domain 분리 | `@RequestBody`·`@PathVariable` 매핑 | ★★★ |
| 7/27 D3 | 전역 오류처리 `@RestControllerAdvice` | Bean Validation `@Valid`(→400) | ★★★ |
| 7/28 D4 | Service/Repository 책임 분리 | 인터페이스 Repository | ★★★ |
| 7/29 D5 | IoC/DI·생성자 주입 | Bean·싱글톤 무상태(관찰) | ★★★ |
| 7/30 D6 | 누적시험 A + 오답 재시험 | — | — |
| 7/31 D7 | 버퍼 / 독립과제: 엔드포인트 1개 0층부터 | — | — |

### Week B (8/1~8/7) — 데이터 접근 기초 (SQL→JDBC→JPA→영속성)
> **이 주에서 `@Transactional`은 "설명 없이 주어진 래퍼(블랙박스)"로 쓴다.** 경계·프록시 해부는 Week C. 영속성·변경감지 실험은 활성 트랜잭션이 필요하므로 이 래퍼 안에서 관찰한다.

| 일 | Full(1) | 지원 Light/관찰 | 난이도 |
|---|---|---|---|
| 8/1 D1 | **Flyway `V1__init`**로 스키마 정의 | SQL·관계 10분 리마인드 / DB 제약(NN·UNIQUE·FK) vs 앱 검증 | ★★★ |
| 8/2 D2 | **왜 JPA인가 — JDBC 완성예제**(`try-with-resources`+`DataSource`; finally 방식은 옛 반복코드 대조용) | JdbcTemplate/JdbcClient 한 줄 / JPA·Hibernate·Spring Data 차이. *JPA는 커넥션풀을 없애는 게 아니라 직접 관리·매핑을 추상화한다* | ★★★★ |
| 8/3 D3 | Entity 매핑 + 기본 CRUD | (영속성 이론은 다음 날) | ★★★ |
| 8/4 D4 | **영속성 컨텍스트·1차 캐시·동일성** (같은 ID 2회 SELECT 1번, 예측→실행) | 블랙박스 `@Transactional` 안에서 관찰 | ★★★★ |
| 8/5 D5 | **변경 감지·flush 시점** (save 없이 UPDATE, 예측→실행) | — | ★★★★ |
| 8/6 D6 | 누적시험 A+B | — | — |
| 8/7 D7 | 버퍼 / `ddl-auto: validate` 마무리 | — | — |

### Week C (8/8~8/14) — 트랜잭션·프록시·성능 ★벽
> 느려도 되는 주 ①. Week B의 블랙박스를 여기서 연다. **Spring AOP 프록시와 Hibernate LAZY 프록시는 다른 장치**임을 명시(같은 "프록시" 이름이라 혼동 주의).

| 일 | Full(1) | 지원 Light/관찰 | 난이도 |
|---|---|---|---|
| 8/8 D1 | **트랜잭션 경계** / 커밋·롤백 경험 | `readOnly` 소개 | ★★★★ |
| 8/9 D2 | "애노테이션만으로 왜 되지?" → **Spring AOP 프록시 도입** | self-invocation 한계(관찰; 프록시 우회) | ★★★★ |
| 8/10 D3 | **트랜잭션 전파** (`REQUIRED`/`REQUIRES_NEW`의 존재와 위험 수준까지) | 커넥션풀·DataSource 개념 | ★★★ |
| 8/11 D4 | 연관관계 매핑 + **Hibernate LAZY 프록시** (AOP 프록시와 다른 장치) | 지연 초기화 시점(관찰) | ★★★★ |
| 8/12 D5 | **N+1 존재확인 → fetch join 1개** (예측→실행→쿼리수 차이) | Hibernate Statistics로 쿼리 카운트 | ★★★★ |
| 8/13 D6 | 누적시험 A+B+C | — | — |
| 8/14 D7 | 버퍼(지연 흡수 우선) | — | — |

### Week D (8/15~8/21) — 인증 + 테스트 ★벽 (매일 결합)
> 느려도 되는 주 ②. B·C와 동급 벽. Testcontainers 실물 구성은 Docker(Week E) 뒤로 미룸 — 여기 통합 테스트는 H2로.

| 일 | Full(1) | 지원 Light/관찰 | 난이도 |
|---|---|---|---|
| 8/15 D1 | BCrypt로 비밀번호 저장 | 인증 vs 인가 / 해시 vs 암호화 / 단위 테스트 | ★★★ |
| 8/16 D2 | **JWT 발급·검증** | 구조(H/P/S)·Bearer·stateless | ★★★★ |
| 8/17 D3 | **시큐리티 필터체인·SecurityContext** (401·403) | MockMvc 인증 테스트 | ★★★★ |
| 8/18 D4 | 실패 케이스 테스트(만료·위조·누락) | 토큰 저장·만료·로그아웃 한계 / CSRF·CORS(개념 light) | ★★★ |
| 8/19 D5 | 테스트 분류(Unit/Slice/Integration) — 무엇을 mock/실제연결 | H2 통합 테스트 | ★★★ |
| 8/20 D6 | 누적시험 A~D | — | — |
| 8/21 D7 | 버퍼(지연 흡수 우선) | — | — |

### Week E (8/22~8/28) — 운영·디버깅·통합·독립과제
| 일 | Full(1) | 지원 Light/관찰 | 난이도 |
|---|---|---|---|
| 8/22 D1 | 로깅(무엇을 남기고 절대 안 남기나·Request ID) | 설정관리(profile·env·secret) | ★★★ |
| 8/23 D2 | 디버깅 실습(stack trace·breakpoint·SQL/log·최소재현·가설검증) | 버그 심고 추적 | ★★★ |
| 8/24 D3 | Docker / Dockerfile / Compose(app+MySQL) | — | ★★★ |
| 8/25 D4 | GitHub Actions CI(test 자동) | Git branch·PR / *Testcontainers MySQL 1경로 = 스트레치* | ★★★ |
| 8/26 D5 | **누적 독립과제** — 작은 기능 1개 0층부터(검증·오류·테스트·마이그레이션) | *실배포 = 스트레치* | ★★★★ |
| 8/27 D6 | **최종 인출 시험(전 범위)** + 루브릭 자가평가 | — | — |
| 8/28 D7 | 버퍼 / 졸업판정 / explain-log → interview-notes | — | — |

## 5. 졸업 루브릭 (8/27~28 + 종료 후 복습, 코드 없이 말로)

- [x] 요청 하나가 계층을 어떻게 흐르는가 / HTTP 메서드·상태코드 계약 — 통과(Day34 문항1)
- [x] DTO를 왜 분리하나 / Validation·전역 오류 처리 흐름 — 통과(Day34 문항2)
- [x] DI를 쓰는 이유 3가지 / 생성자 주입 / 싱글톤 무상태 — 통과(Day34 문항3)
- [x] **순수 JDBC 대비 JPA가 무엇을 추상화하나** (커넥션풀을 없애는 게 아니라 직접 관리·매핑을 추상화) — 통과(Day34 문항4)
- [x] 영속성 컨텍스트가 `save()` 없이 UPDATE 하는 원리 — 통과(Day34 문항5)
- [x] `@Transactional`이 Spring AOP 프록시로 도는 의미 / self-invocation 한계 — 통과(Day17→20→34, 세 번째 재시험 만에 근거 안정)
- [x] **트랜잭션 전파(`REQUIRED`/`REQUIRES_NEW`)의 존재와 위험** / 커넥션풀이 왜 필요한가 — 통과(Day34 문항7)
- [x] Hibernate LAZY 프록시 초기화 시점 (AOP 프록시와 다른 장치) — 통과(Day34 문항8)
- [x] N+1이 왜 생기고 어떻게 없앴는지(숫자로) — 통과(Day34 문항9, 4→1)
- [ ] JWT stateless 장점과 로그아웃 무효화 문제 / 401 vs 403 — (보완: Day34 오답→Day35 재시험 통과, +7 재확인 전이라 미확정 유지)
- [ ] Unit/Slice/Integration을 각각 언제 쓰나 / 무엇을 mock하나 — (보완: Day34 오답→Day35 재시험 통과, +7 재확인 전이라 미확정 유지)
- [x] 로그로 버그를 추적하는 절차 / 민감정보를 안 남기는 이유 — 통과(Day34 문항12)

## 6. 산출물 (매일 갱신 = 증거)

**주차 > 요일 폴더 구조** — 7/25(Day01)~8/28(Day35), 5주×7일 순차 번호 + 날짜 접미사.
Day 폴더는 소속 주차 폴더(`WeekA`~`WeekE`) 아래에 둔다. Day 번호는 주차를 넘어 이어서 센다(Week B는 Day08부터).

```
study_docs/
├─ days/
│  ├─ WeekA/                                                        ← 주차 폴더 (WeekA~WeekE)
│  │  ├─ Day01_0725/vocab.md, quiz.md, explain-log.md, velog_post.md   ← 일반 개념·구현 Day
│  │  ├─ …
│  │  ├─ Day06_0730/vocab.md, quiz.md, explain-log.md                  ← 주간 D6 누적시험
│  │  └─ Day07_0731/vocab.md, quiz.md, explain-log.md, progress.md     ← 주간 D7 버퍼
│  │     (progress.md는 진행 상태·부채를 남길 필요가 있는 날만 추가)
│  └─ WeekB/
│     ├─ Day08_0801/…
│     └─ Day09_0802/…
├─ velog/                      ← 주 1편 대표글. 심화글 또는 D6·D7 통합 마무리 시험 글.
├─ CODE_PATTERNS.md             ← **참조서**. 패턴별 골격 + 판단 근거 + 실제로 낸 오류. 주차별 append.
├─ PATTERN_DRILLS.md            ← **드릴**. 빈칸 + 독립과제. 손으로 먼저 쓰고 나서 위 파일과 대조한다.
├─ interview-notes.md          ← 면접 문장(기존 파일 유지, 누적)
├─ spring-core-notes.md        ← Week A~C 인출·되설명 워크시트(기존 파일 재사용)
├─ 기술부채.md                  ← 기술 부채 원장(부채·분류·해결 Day·상태) — 단일 추적 위치.
└─ 복습큐.md                    ← 간격반복 대기열(개념·다음도래일) — 유일하게 날짜로 안 쪼갠다.
                                   여러 날에 걸친 도래일을 한눈에 봐야 하는 파일이라 누적 유지.
```

각 Day 폴더의 `vocab.md`/`quiz.md`/`explain-log.md`는 그날 Full/Light 루프 내용만 담는다.

**`CODE_PATTERNS.md`·`PATTERN_DRILLS.md`는 Day 산출물이 아니라 주차를 가로지르는 누적 자산이다.** `vocab.md`가 용어의 뜻(선언적 지식)을 담는다면 이 둘은 코드의 형태(절차적 지식)를 담는다 — 하나로 다른 하나가 채워지지 않는다. 각 Day의 완성예제는 그 주가 끝날 때 `CODE_PATTERNS.md`에 패턴으로 승격시키고, 대응하는 빈칸을 `PATTERN_DRILLS.md`에 추가한다. **사용 순서는 항상 드릴로 먼저 인출 → 틀림 → 그때 참조서 확인이다.** 참조서를 순서대로 읽는 것은 §0의 "다시 읽기는 효과 없다"에 해당한다. `❌ 흔한 실수` 항목은 학습자가 실제로 낸 오류만 쓰고 일반론은 넣지 않는다.

각 Day의 **완료 판정 산출물**은 `vocab.md`, `quiz.md`, `explain-log.md`이며, 진행 상태나 기술 부채를 별도로 남길 필요가 있을 때만 `progress.md`를 작성한다. Day 완료는 코드·테스트와 이 산출물로 판정하며, **Velog 글은 완료 조건으로 사용하지 않는다**(위 「세션 재개용 진행 체크리스트」의 `[x]` 기준과 동일). 일반 Day는 `velog_post.md`를 쓰고, 각 주 D6·D7은 Day별 학습 증거를 유지한 채 `study_docs/velog/`의 주차 마무리 시험 글 한 편으로 합친다. D6에 초안을 열고 D7 완료 후 확정한다.

Velog 글은 [Spring 기본기 기술 블로그 템플릿](VELOg_POST_TEMPLATE.md)을 따른다. 일반 개념·구현 Day는 핵심 메커니즘 또는 설계 판단 중심으로 쓰고, D6·D7 통합 글은 시험 범위·실제 오답·D7 코드 적용·자동 검증·다음 시작점 순서로 유연하게 작성한다.
Day 번호 ↔ Week·요일 매핑: Day(N) = Week A~E 중 `⌈N/7⌉`번째 주, 그 주의 D`((N-1)%7)+1`.
따라서 Day(N)의 저장 경로는 `study_docs/days/Week{A~E 중 ⌈N/7⌉번째}/DayNN_MMDD/`이다.

## 7. 규칙

- **하루 Full 루프 최대 1개.** 나머지는 Light/관찰.
- AI에게 코드를 대신 짜게 하지 않는다. 개념설명·완성예제·문제출제·인출채점·코드리뷰만. 피드백은 AI 의견이 아니라 실제 코드·테스트·공식문서 근거.
- `record`는 DTO에만. Entity/가변 Domain에 쓰지 않는다.
- 기존 프로젝트가 Boot 3면 학습 중 Boot 4로 업그레이드하지 않는다.
- 이 저장소 밖의 과거 코드나 계획에 의존하지 않는다. 실습·테스트·학습 기록은 모두 현재 저장소 안에서 완결한다.
- 상용 강의와 "동일 순서"라고 말하지 않는다 — "기반→추상화 원칙을 선택 압축"이 정확한 표현.
- **Day 플랜이 끝나면 무조건 그날 안에 git commit + push.** 그날 산출물(코드·vocab·quiz·explain-log·복습큐 갱신)이 유실 없이 원격에 남아야 다음날/복습주기에 그대로 이어볼 수 있다.
- **Velog 글은 Day 흐름을 유지하되 D6·D7은 주차 마무리 글로 합친다.** 일반 Day는 소재에 맞춰 쓰고, 각 주의 D6 누적시험과 D7 버퍼는 `[백엔드 기본기 DAY N & DAY N+1] N주차 마무리 시험` 한 편으로 통합한다. 서로 다른 날에 진행하면 D6에 초안을 열고 D7 완료 후 확정한다. Day별 `vocab`·`quiz`·`explain-log` 증거는 분리해서 보존한다. 통합 글은 실제 시험 범위·오답 교정·D7 코드 적용·자동 검증·다음 시작점을 중심으로 쓰며 일반 개념 글의 고정 H2를 강제하지 않는다. 실제로 비교하지 않은 대안이나 수행하지 않은 검증을 만들지 않고 `[직접 작성]`은 덮어쓰지 않는다.
- **기술 부채는 분류해서 원장 한 곳에 모은다.** 바로 고칠 것(다음 실험 결과를 왜곡·빌드 실패·보안 위험) / 나중에 고칠 것(해결 Day 지정) / 고치지 않을 것(이 트랙에서 해결하지 않음, 1회만 명시) 중 하나로 나눠 `기술부채.md`에 기록한다. 같은 한계를 매 글마다 반복해 적지 않는다.
- **각 주 D7은 지연 흡수와 부채 점검 슬롯이다.** ⓪ **밀린 인출 백로그 소화**(아침 10분으로는 못 따라잡은 도래분 — 오답재시험부터) → ① 그 주의 미완료 필수 유닛 처리 → ② 바로 고칠 것이거나 현재까지 배운 범위로 해결 가능한 고우선순위 부채 상환 → ③ 주간 독립과제 순으로 쓴다. 이후 주차의 개념이 필요한 부채는 해결 Day만 배정하고, **부채가 남아 있다는 이유만으로 선행 진도를 중단하지 않는다.**
- **주차 마무리(D7 종료) 시 패턴 승격은 필수 절차다.** 그 주의 완성예제를 `CODE_PATTERNS.md`에 패턴으로, 대응 빈칸을 `PATTERN_DRILLS.md`에 묶음으로 append한다. 골격은 `src/`의 돌아가는 코드에서만 뽑고 미검증분은 `⚠️ 미검증`으로 표시하며, `❌ 흔한 실수`는 그 주 `explain-log.md`의 실제 오류만 원문 인용한다. 드릴에 정답은 넣지 않는다. 상세 절차는 [`../CLAUDE.md`](../CLAUDE.md) 참고.

## 8. 새 세션 시작 체크리스트

1. 이 문서의 **세션 재개용 진행 체크리스트**에서 마지막 `[x]`와 첫 미완료 필수 Day를 확인한다.
2. `study_docs/복습큐.md`에서 오늘 도래한 항목과 +1일 오답 재시험을 확인한다.
3. 해당 `study_docs/days/WeekX/DayNN_MMDD/`와 현재 코드·테스트를 대조한다.
4. 저장소 루트에서 `./gradlew test`로 시작 상태를 확인한다.
5. Day 완료 후 산출물·복습큐·진행 체크리스트·다음 시작점을 함께 갱신한다.
