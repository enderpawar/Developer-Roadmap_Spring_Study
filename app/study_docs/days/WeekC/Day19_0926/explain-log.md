# Day19 (9/26, Week C D5) 예측→실행→차이 기록

주제: N+1 문제 확인 + fetch join

## 실험 1 — N+1 재현

- 코드: `NPlusOneTest.findAllTriggersNPlusOneSelects()` — Member 3명 + Reservation 3건 저장 → `flush()`/`clear()` → `reservationRepository.findAll()` → 순회하며 `getMember().getName()`
- 예측: 최초 "3 / 4 / 7"(오답) → 힌트 제공 후에도 "잘 모르겠다" → 메커니즘 직접 설명 후 실행으로 확인
- 실행 결과(Gradle 테스트 리포트 Standard output 그대로):

```text
===== findAll() 호출 시작 =====
Hibernate:
    select r1_0.id, r1_0.cancel_reason, r1_0.confirmed, r1_0.member_id, r1_0.requester_name, r1_0.room_name
    from reservation r1_0
===== findAll() 끝, 지금부터 순회 시작 =====
Hibernate:
    select m1_0.id, m1_0.name from member m1_0 where m1_0.id=?
진우
Hibernate:
    select m1_0.id, m1_0.name from member m1_0 where m1_0.id=?
철수
Hibernate:
    select m1_0.id, m1_0.name from member m1_0 where m1_0.id=?
영희
===== 순회 끝 =====
```

- 판정: `findAll()` 구간 0번(FK값만 결과에 딸려오고 member 프록시는 아직 초기화 안 됨), 순회 구간 3번(getter 호출마다 1번씩), 총 4번. N+1 재현 확인.

## 실험 2 — fetch join으로 해결

- 코드 추가: `SpringDataReservationRepository.findAllWithMember()`(`@Query("select r from Reservation r join fetch r.member")`), `ReservationRepository`·`JpaReservationRepository`에 대응 메서드
- 예측: "이제 1번?" → "1번 찍히고 0번" — 정답
- 실행 결과:

```text
===== findAllWithMember() 호출 시작 =====
Hibernate:
    select r1_0.id, r1_0.cancel_reason, r1_0.confirmed, m1_0.id, m1_0.name, r1_0.requester_name, r1_0.room_name
    from reservation r1_0
    join member m1_0 on m1_0.id=r1_0.member_id
===== findAllWithMember() 끝, 지금부터 순회 시작 =====
진우
철수
영희
===== 순회 끝 =====
```

- 판정: 호출 구간 1번(JOIN 한 방으로 reservation+member 동시 채움), 순회 구간 0번. 예측 그대로 확인 — LAZY와 정반대로 초기화 시점이 조회 시점으로 당겨짐.

## 코드 작성 중 겪은 오류

### 오류 1 — `@Query`를 메서드가 아니라 인터페이스에 붙임

```java
@Query("select r from Reservation r join  fetch r.member")
public interface SpringDataReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findAllWithMember();
}
```

`@Query`는 메서드 전용 애노테이션인데 인터페이스 선언 자체에 붙여서 어떤 메서드에 적용되는지 불명확해짐. 메서드 위로 옮겨서 교정.

### 오류 2 — 인터페이스 확장이 대조군 구현체를 깨뜨림

`ReservationRepository`에 `findAllWithMember()`를 추상 메서드로 추가하자 컴파일 에러:

```
InMemoryReservationRepository is not abstract and does not override abstract method findAllWithMember() in ReservationRepository
JdbcReservationRepository is not abstract and does not override abstract method findAllWithMember() in ReservationRepository
```

두 클래스는 Week B D2 이후 "대조군"으로만 남아 있는 미사용 구현체(`@Repository` 없음, Spring Bean 아님)인데도, 인터페이스에 메서드를 추가하면 그 인터페이스의 **모든** 구현체가 전파 대상이 된다는 걸 실제로 겪음. `default` 메서드로 전환해 해결(아래 판단 참고).

### 오류 3 — 제네릭 문법과 반환 타입 혼동

```java
default <Reservation> findAllWithMember(){
    throw new UnsupportedOperationException();
};
```

`<Reservation>`을 반환 타입 자리에 썼는데, 이건 제네릭 타입 파라미터 선언 문법(`<T>`)이다. 실제로 쓰려던 반환 타입은 `List<Reservation>`이었다. `default List<Reservation> findAllWithMember()`로 교정 후 컴파일 통과.

## 설계 판단 — default 메서드 몸통을 뭘로 채울까

옵션 A(`return findAll();`로 폴백) vs 옵션 B(`throw new UnsupportedOperationException()`) 중 B를 선택.

- 1차 근거: "대조군이라 어차피 안 불린다" — 현재 상태만 본 근거
- 교정 후 근거: 미래에 실수로 `InMemoryReservationRepository`가 다시 연결되는 시나리오까지 고려하면, A는 "조용히 최적화 없는 결과"를 내서 문제를 숨기고 B는 "즉시 실패"로 오용을 바로 드러낸다 — fail fast

## 별도로 발견·수정한 인프라 문제 (오늘 개념과는 무관, 사용자 명시적 승인 하에 예외적으로 AI가 직접 수정)

1. **테스트 격리 버그**: `app/build.gradle.kts:45`가 테스트 실행 시 datasource를 이름이 고정된 인메모리 DB(`jdbc:h2:mem:testdb;...;DB_CLOSE_DELAY=-1`)로 강제하는데, `ReservationControllerHttpTest`만 `@Transactional`이 빠져 있어 그 클래스가 MockMvc로 만든 Reservation(멤버 없음)이 커밋된 채 같은 테스트 프로세스 안에 계속 남아 있었다. 나중에 도는 `NPlusOneTest`의 `findAll()`이 이 유령 데이터를 주워 `getMember()`가 `null`이 되어 `NullPointerException` 발생. `ReservationControllerHttpTest`에 `@Transactional`을 추가해 해결, 전체 테스트 25/25 통과 확인. **사용자가 절대 규칙("학습자 코드를 대신 짜지 않는다") 예외를 명시적으로 승인**했기 때문에 AI가 직접 수정했다 — 오늘 개념(N+1)이 아니라 별개의 기존 버그였기 때문.
2. **IntelliJ 테스트 실행이 Gradle 환경변수 오버라이드를 우회하는 문제**: Windows 시스템 환경변수에 다른 프로젝트용 실제 프로덕션 Postgres(Neon) 접속 정보(`SPRING_PROFILES_ACTIVE=prod`, `SPRING_DATASOURCE_URL` 등)가 남아 있었다. CLI `./gradlew test`는 `build.gradle.kts`의 태스크 레벨 오버라이드로 격리되지만, IntelliJ의 기본 테스트 실행 방식은 이 오버라이드를 안 거쳐 실제 프로덕션 DB에 붙을 위험이 있었다. `app/.idea/gradle.xml`에 `<option name="testRunner" value="GRADLE" />`를 추가해 IntelliJ도 Gradle `:test` 태스크를 그대로 타도록 교정. 근본 원인인 시스템 환경변수 자체는 다른 프로젝트가 쓸 수 있어 삭제하지 않음(사용자 판단 필요 시 별도 처리).
3. (환경 부채, 개념과 무관) Gradle `compileTestJava`가 UP-TO-DATE로 잘못 캐시돼 실제로는 `NPlusOneTest.class`가 컴파일 결과물에 없었던 적이 있었다(OneDrive 동기화로 타임스탬프가 꼬인 것으로 추정). `./gradlew clean test`로 해결 — 재발 시 `clean`부터 의심할 것.

## 검증 근거

- `src/test/java/com/example/studyroom/repository/NPlusOneTest.java` (신규, 테스트 2건 모두 학습자 타이핑)
- `src/main/java/com/example/studyroom/repository/SpringDataReservationRepository.java`, `ReservationRepository.java`, `JpaReservationRepository.java`
- `src/test/java/com/example/studyroom/controller/ReservationControllerHttpTest.java:16` (`@Transactional` 추가, AI가 사용자 승인 하에 직접 수정)
- `app/.idea/gradle.xml` (`testRunner=GRADLE` 추가, AI가 직접 수정)
- `./gradlew clean test` 최종 BUILD SUCCESSFUL, 25/25 — `NPlusOneTest` 두 테스트 모두 통과, SQL 로그로 4번→1번 실측 확인

## [직접 작성] 오늘 배운 것을 내 문장으로

<!-- 아래는 학습자가 직접 채운다. 비워두지 말 것. -->

- N+1 문제가 정확히 어느 시점(코드 줄)에서 발생하는지:
- fetch join이 LAZY와 정반대로 동작하는 지점:
- 인터페이스에 메서드를 추가할 때 왜 대조군 코드까지 신경 써야 하는지:

## 다음 시작점

Day19 완성예제(①)까지 완료. ②빈칸예제·③독립 변형·④인출(노트 덮고 재작성)·⑥복습큐 등록(신규 항목 일부만)은 다음 세션으로 이월. Velog 포스트는 사용자가 별도 세션(Opus)에서 작성 예정.

## 이월분 완료 — println에서 Statistics 단언으로 교체 (9/27)

- 코드: `NPlusOneTest`에 `@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")` 추가, `statistics()` 헬퍼로 `entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics()`를 꺼냄
- 예측: `println` 로그로 셌던 값(N+1 4번, fetch join 1번)이 `Statistics.getPrepareStatementCount()` 단언으로도 그대로 나올 것이다
- 실행 결과: `findAllTriggersNPlusOneSelects` → `statistics.clear()` 이후 `getPrepareStatementCount()` == `4`(findAll 1 + member 지연로딩 3). `findAllWithMemberUsesSingleJoinQuery` → `1`
- 판정: 예측과 일치. Day19 세션에서 println으로 관찰했던 "4번→1번"과 동일한 실측치가 숫자 단언으로 고정됐다

## 겪은 것 — `SessionFactory`를 Bean으로 바로 주입하지 않은 이유

`@Autowired SessionFactory`를 시도하지 않고 처음부터 `entityManager.getEntityManagerFactory().unwrap(SessionFactory.class)`로 꺼냈다. Spring Boot의 JPA 자동구성이 `org.hibernate.SessionFactory` 자체를 Bean으로 노출하지 않는다는 걸 사전에 알고 있었기 때문에, `NoSuchBeanDefinitionException`을 밟지 않고 한 번에 통과했다. 이번 유닛은 새로운 컴파일·런타임 오류 없이 끝났다 — 오류를 지어내지 않고 사실대로 남긴다.

## 검증 근거(이월분)

- `src/test/java/com/example/studyroom/repository/NPlusOneTest.java:18, 30-36, 52-61, 78-86`
- `./gradlew test --console=plain` BUILD SUCCESSFUL, 27/27 (커밋 [`7a9626d`](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/7a9626dcf62c0df95a8459fa6cb9472acbf0cbce))
