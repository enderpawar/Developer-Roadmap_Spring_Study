# 학습 세션 재개 상태

최종 갱신: 2026-10-21

## 세션 목적

**5주 트랙(Week A~E)이 전 구간 완료됐다.** 이 문서의 아래 절들은 재복습~Week C 진입 시점의 세션 기록으로 이력 보존한다. 다음 세션의 목적은 새 Day 진행이 아니라 **종료 후 복습(주 2회 20분)**이다 — `app/study_docs/복습큐.md`의 도래분과, 졸업 루브릭 보완 2건(JWT stateless·로그아웃 한계 / Unit·Slice·Integration 구분)의 +7 재확인을 우선 처리한다.

## 현재 위치

- Week A~E D1~D7 전 구간: 완료 (공식 기록: `app/study_docs/FUNDAMENTALS_ROADMAP.md`)
- 졸업 루브릭(§5) 자가평가: 12개 중 10개 통과, 2개 보완(Day35, [Day35 기록](app/study_docs/days/WeekE/Day35_1021/))
- 다음 시작점: 종료 후 복습 — 새 Day 없음

이하는 재복습 이후 Week C 진입까지의 세션 상태이며, `app/study_docs/FUNDAMENTALS_ROADMAP.md`의 공식 완료 체크 상태와 동기화한다.

## 오늘 다시 연결한 내용

### Week A — 웹 계층

- 정상 반환한 Controller 응답은 기본적으로 `200 OK`이며, `ResponseEntity`로 상태코드와 본문을 명시할 수 있다.
- DTO는 경계를 넘는 데이터 형식이고, Domain은 상태와 상태 변경 규칙을 가진다.
- Java `record` 접근자는 `getRoomName()`이 아니라 `roomName()`이다.
- `@NotBlank`가 제약을 선언하고 `@Valid`가 요청 DTO 검증을 실행한다.
- 검증 실패 시 Service를 호출하지 않고 전역 예외 처리기가 400 응답으로 변환한다.
- Controller는 HTTP, Service는 업무 흐름, Domain은 상태 변경, Repository는 저장·조회를 담당한다.
- IoC는 객체 조립 제어권을 Spring이 갖는 것이고, DI는 의존 객체를 외부에서 전달받는 것이다.
- 생성자 DI를 사용하면 Repository 구현을 JPA·메모리·mock으로 교체하기 쉽다.
- 기본 Singleton Bean은 여러 요청이 공유하므로 Service의 요청별 값은 필드가 아니라 매개변수·지역변수에 둔다.

### Week B D1 — Flyway·DB 제약

- Flyway는 버전 SQL을 순서대로 적용하고 이력과 체크섬을 DB 장부에 저장한다.
- 이미 적용된 마이그레이션은 수정하지 않고 다음 버전 파일을 추가한다.
- `NOT NULL`은 `NULL`만 막고 빈 문자열 `''`은 막지 못한다.
- 앱의 `@NotBlank`는 빠르고 친절한 요청 검증, DB 제약은 모든 저장 경로에 대한 무결성 방어다.
- PK는 연속성이 아니라 유일성을 보장하므로 실패·롤백으로 ID가 건너뛸 수 있다.

### Week B D2 — 순수 JDBC 도입부

- `DataSource`에서 Connection을 빌리고, `PreparedStatement`의 `?`에 값을 바인딩하고, `ResultSet`을 읽은 뒤 자원을 반납한다.
- JPA를 사용해도 커넥션 풀은 사라지지 않는다.
- `ResultSet.next()`는 다음 행으로 커서를 이동하고, `getXxx()`는 현재 행의 값을 읽는다.

### Week B D2~D5·D7 — JDBC에서 JPA, 영속성

- try-with-resources는 `AutoCloseable` 자원을 정상·예외 경로 모두에서 자동으로 닫는다.
- `PreparedStatement`는 SQL 구조와 사용자 값을 분리하여 입력을 SQL 문법이 아닌 데이터로 처리한다.
- JPA는 ORM 표준 명세, Hibernate는 구현체, Spring Data JPA는 Repository 편의 계층이다.
- 신규 Entity의 `Long id`는 저장 전 `null`이고 `IDENTITY` INSERT 뒤 DB 생성값이 들어간다.
- 조회 결과 없음은 `null`이 아니라 `Optional.empty()`다.
- 같은 트랜잭션의 1차 캐시는 `(Entity 타입, id)`를 키로 같은 객체 참조를 반환한다.
- 관리 상태 Entity를 변경하면 `flush()` 때 변경 감지가 UPDATE를 만들며, flush는 commit이 아니다.
- `ddl-auto: validate`는 Entity와 DB 스키마의 일치만 검사하고 스키마 변경은 Flyway가 담당한다.

## 실제 오답과 교정

1. 실제 ID 조회 책임을 Service라고 답했다.
   - 교정: 실제 저장·조회는 Repository, 업무 순서 조율은 Service다.
2. 조회 결과가 없을 때 `ReservationNotFoundException`을 발생시키는 책임을 Repository라고 답했다.
   - 교정: Repository는 `Optional.empty()`를 반환하고, Service가 이를 비즈니스 예외로 변환한다.
3. 위 두 항목은 교정 문제에서 올바르게 구분해 통과했다.

세부 최초 답변과 교정 기록은 `app/study_docs/reviews/2026-09-19-week-ab-refresh.md`에 있다.

## Week C D1에서 확인한 내용

- `ReservationService.cancel()` 전체에 `@Transactional`을 적용하고 명시적 `save()`를 제거했다.
- 정상 반환 경로에서 변경 감지 `UPDATE`와 commit 후 재조회 값을 확인했다.
- 테스트용 트랜잭션 Bean에서 강제 `flush()` 뒤 `RuntimeException`을 발생시켜 `UPDATE` 후 rollback을 확인했다.
- `readOnly=true`를 쓰기 권한 제어로 오해했으나, 조회 의도·최적화 힌트이며 절대적인 쓰기 차단을 보장하지 않는다고 교정했다.

## Week C D2에서 확인한 내용

- `ReservationService` Bean의 실제 타입이 `ReservationService$$SpringCGLIB$$0`임을 확인했다.
- `getBean()`은 임시 객체 생성이 아니라 컨테이너가 준비한 같은 Singleton 프록시의 조회다.
- 외부 `inner()` 호출은 프록시를 통과해 트랜잭션 활성, `outer()`의 self-invocation은 프록시를 우회해 비활성임을 확인했다.
- `transactionalOuter()`에서는 바깥 경계가 트랜잭션을 먼저 시작하므로 내부 `inner()`가 활성 상태에서 실행됐다.
- Service·프록시는 ApplicationContext, JPA Entity는 영속성 컨텍스트의 관리 대상이다.

## 다음 기기에서 시작할 지점

(이력) 이 시점에는 Week C D3 트랜잭션 전파가 다음 시작점이었다. **현재는 Week C D3 이후 Week C~E 전 구간이 완료돼(2026-10-21) 새 시작점이 없다.** 다음 세션은 종료 후 복습(주 2회 20분)이다.

## 검증 상태(이 세션 시점 — Week C D2까지)

- `ReservationService.cancel()`에 `@Transactional`을 적용하고 명시적 `save()`를 제거했다.
- `ReservationServiceTransactionTest`에서 commit·rollback 통합 테스트를 추가했다.
- Spring AOP 프록시 판별 테스트와 self-invocation 호출 경로 테스트 3개를 추가했다.
- 전체 테스트 22개, failures 0, errors 0을 확인했다.
- IntelliJ 연결 Gradle 프로젝트 JVM을 `temurin-24`로 고정했다.
- Gradle 8.14.5가 Java 24.0.2로 기동됨을 확인했다.
- 1차 캐시 동일성 테스트를 실행해 `BUILD SUCCESSFUL`, INSERT 1회·SELECT 1회를 확인했다.
- 공식 로드맵 체크박스와 복습큐는 변경하지 않았다.

