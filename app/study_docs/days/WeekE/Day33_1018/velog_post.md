# [Spring Study Day 33] 예약 시간대 중복 방지 — 구간 겹침 판정과 하위 호환 확장

이 글이 다루는 코드는 Day04부터 있던 `Reservation`에 시간대(`startAt`/`endAt`) 개념을 처음 추가하고, 같은 방(room)에 겹치는 시간대 예약을 막는 독립 과제다. 마이그레이션·엔티티·DTO·서비스·예외 처리·리포지토리 3종·테스트까지 전 계층을 관통해서 새로 만들었다. 동시 요청이 동시에 겹침 검사를 통과하는 레이스 컨디션과 DB 수준 제약(exclusion constraint)은 이 글의 범위가 아니다.

> `startAt`/`endAt`을 선택 필드로 추가하고, 같은 방·활성 예약(`confirmed = true`) 사이에서 `start < otherEnd && otherStart < end` 공식으로 겹침을 검사했다. 경계가 정확히 맞닿는 구간(10~11시, 11~12시)은 겹침이 아니게 통과시켰고, 겹치면 409, 시간 순서가 뒤바뀌면 400을 반환한다. 기존 61개 테스트는 무수정으로 통과했고, 신규 10개(Unit 6 + Integration 4)를 포함해 `./gradlew test` 71개 전부 통과했다.

> **오늘의 흐름** `V7 마이그레이션(선택 컬럼) → 겹침 판정 공식(부등호) → 3중 구현체(JPA/InMemory) 동기화 → 예외 매핑(400/409) → 기존 61개 무수정 통과`
>
> 이전 Day: GitHub Actions CI에 Docker 통합 실행 잡 추가 (Day32)
> 다음 Day: 최종 인출 시험(전 범위) + 루브릭 자가평가 (Week E D6)

![두 구간을 나타내는 두 개의 수평선. 위쪽 구간이 먼저 시작해 진행되다가, 끝나기 전에 아래쪽 구간이 시작되어 두 구간이 부분적으로 겹치는 Allen의 구간 대수 "overlaps" 관계](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day33-web-allen-overlap.png)

*출처: [Allen's interval algebra](https://en.wikipedia.org/wiki/Allen%27s_interval_algebra) — Weisserd~commonswiki, Wikimedia Commons, GNU FDL 1.2 이상 / CC BY-SA 3.0*

## 1. 개념 설명

### 1) 구간 겹침의 판정 공식과 경계 조건

> **구간 겹침(Interval Overlap)** = 두 시간 구간이 공통된 시간을 하나라도 공유하는 상태

우리 코드에서는 `SpringDataReservationRepository.findOverlapping()`이 이 판정을 그대로 SQL로 옮긴다.

```java
@Query("select r from Reservation r " +
        "where r.roomName = :roomName and r.confirmed = true " +
        "and r.startAt is not null and r.endAt is not null " +
        "and r.startAt < :endAt and :startAt < r.endAt")
List<Reservation> findOverlapping(String roomName, LocalDateTime startAt, LocalDateTime endAt);
```

공식은 "내 시작이 상대 끝보다 앞서고, 상대 시작이 내 끝보다 앞선다"는 두 조건을 **AND**로 묶은 것이다. 두 구간 중 하나라도 완전히 앞이나 뒤에 있으면 두 부등호 중 하나가 거짓이 되어 겹침이 아니라고 판정한다.

```text
겹침 판정 → existing.startAt < new.endAt  AND  new.startAt < existing.endAt
→ 둘 다 참이면 겹침 → ReservationOverlapException(409)
→ 하나라도 거짓이면 겹침 아님 → 저장 진행
```

핵심은 **등호를 쓰지 않았다**는 것이다. 기존 예약이 10~11시이고 신규 예약이 11~12시라면, `existing.startAt(10) < new.endAt(12)`는 참이지만 `new.startAt(11) < existing.endAt(11)`은 거짓(11은 11보다 작지 않음)이라 겹침이 아니다. `reserveAllowsTouchingIntervalsInSameRoom` 테스트로 이 경계를 직접 확인했다.

이 관계는 Allen의 구간 대수(Interval Algebra)가 분류하는 13가지 관계 중 "overlaps"(위 그림)와 "meets"(정확히 맞닿음)의 차이와 같다. 아래는 같은 이론에서 "before"(간격을 두고 완전히 떨어진 경우)를 나타낸 그림이다. 정확히는 우리 코드의 맞닿는 경우는 간격이 0인 "meets"이고 이 그림의 "before"는 간격이 있는 경우라 완전히 같은 사례는 아니지만, 둘 다 "겹치지 않음"으로 판정된다는 결론은 같다.

![두 구간을 나타내는 두 개의 수평선. 위쪽 구간이 완전히 끝난 뒤 간격을 두고 아래쪽 구간이 시작되는 Allen의 구간 대수 "before" 관계](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day33-web-allen-before.png)

*출처: [Allen's interval algebra](https://en.wikipedia.org/wiki/Allen%27s_interval_algebra) — Weisserd~commonswiki, Wikimedia Commons, GNU FDL 1.2 이상 / CC BY-SA 3.0*

### 2) 하위 호환을 지키는 확장 방식

> **하위 호환 확장** = 기존 호출부·테스트 결과를 그대로 유지한 채 새 동작을 추가하는 설계

우리 코드에서는 `startAt`/`endAt`을 `@NotNull` 없이 선택 항목으로 뒀다.

```java
// ReservationService.java
public void reserve(String roomName, String requesterName) {
    reserve(roomName, requesterName, null, null); // 기존 시그니처는 그대로, 새 로직엔 null 전달
}

public void reserve(String roomName, String requesterName, LocalDateTime startAt, LocalDateTime endAt) {
    if (startAt != null && endAt != null) { // 둘 다 있을 때만 검증·중복검사
        ...
    }
    ...
}
```

| 구분 | 시간대 없음(기존 호출) | 시간대 있음(신규 호출) |
|---|---|---|
| 검증 | 스킵 | `endAt`이 `startAt`보다 뒤인지 확인(아니면 400) |
| 중복 검사 | 스킵 | `findOverlapping()` 호출(겹치면 409) |
| 기존 테스트 영향 | 없음(61개 무수정 통과) | 신규 10개로만 검증 |

처음에는 `startAt`/`endAt`에 `@NotNull`을 걸 생각이었다. 실제로 걸어보니 시간대 없이 예약하던 기존 테스트 8개가 전부 400으로 깨졌다. 매핑을 "필수"가 아니라 "선택, 둘 다 있을 때만 검사"로 바꿔 하위 호환을 지켰다.

### 3) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| Interval Overlap | 두 시간 구간이 공통 시간을 공유하는 상태 |
| Allen's Interval Algebra | 두 구간의 관계를 overlaps·meets·before 등으로 분류하는 이론 |
| Exclusion Constraint | DB가 겹치는 행의 저장 자체를 거부하도록 강제하는 제약 |
| Additive Migration | 기존 컬럼·행을 건드리지 않고 컬럼만 추가하는 마이그레이션 |

> **더 볼 것**
> - [Allen's interval algebra — Wikipedia](https://en.wikipedia.org/wiki/Allen%27s_interval_algebra): overlaps/meets/before 등 13가지 구간 관계의 정의
> - [PostgreSQL — Exclusion Constraints](https://www.postgresql.org/docs/current/ddl-constraints.html#DDL-CONSTRAINTS-EXCLUSION): DB 수준에서 겹침을 막는 제약의 예시
> - [Spring Data JPA Reference — Query Methods](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html): `@Query`로 직접 JPQL을 지정하는 방법

## 2. 코드 구현

### 1) 3중 구현체에서 같은 판정 로직 유지하기

`ReservationRepository`(port)는 세 구현체(`JpaReservationRepository`, `InMemoryReservationRepository`, `JdbcReservationRepository`)를 갖는다. Day19에서는 새 쿼리 메서드를 `default + throw`로 스텁 처리해 대조군을 건드리지 않았지만, 이번엔 `InMemoryReservationRepository`가 Unit 테스트에서 DB 없이 같은 판정을 내려야 해서 실제 구현이 필요했다.

```java
// InMemoryReservationRepository.java
public List<Reservation> findOverlapping(String roomName, LocalDateTime startAt, LocalDateTime endAt) {
    return store.stream()
            .filter(r -> r.getRoomName().equals(roomName))
            .filter(Reservation::isConfirmed)
            .filter(r -> r.getStartAt() != null && r.getEndAt() != null)
            .filter(r -> r.getStartAt().isBefore(endAt) && startAt.isBefore(r.getEndAt()))
            .toList();
}
```

**한 줄씩 보기**

- `filter(Reservation::isConfirmed)`: 취소된 예약(`confirmed = false`)은 애초에 후보에서 제외.
- `isBefore(endAt) && startAt.isBefore(...)`: JPQL의 `<` 부등호를 자바 `LocalDateTime.isBefore()`로 그대로 옮김 — 같은 판정 공식을 SQL과 순수 자바 양쪽에 중복 구현.
- `JpaReservationRepository`는 `delegate.findOverlapping(...)`으로 `SpringDataReservationRepository`의 `@Query`에 위임.

같은 판정 공식을 두 곳(JPQL, 자바 스트림)에 따로 적어야 했던 건 이 구조의 트레이드오프다. 한쪽만 고치면 두 구현체의 결과가 갈릴 위험이 생긴다 — 현재는 각 구현체별 테스트로만 커버하고, 공식 자체를 한 곳으로 합치는 리팩터링은 하지 않았다.

### 2) 예외 매핑과 자동 검증 결과

```java
if (!endAt.isAfter(startAt)) {
    throw new InvalidReservationTimeException(); // 400
}
if (!reservationRepository.findOverlapping(roomName, startAt, endAt).isEmpty()) {
    throw new ReservationOverlapException(); // 409
}
```

두 예외는 기존 `GlobalExceptionHandler`에 핸들러만 추가해 처리했다(Day03·Day07에서 확립한 "도메인 예외 + 전역 처리기" 패턴 재사용).

| 검증 항목 | 방법 | 결과 |
|---|---|---|
| 겹치면 409 | `ReservationOverlapHttpTest.secondReservationOverlappingSameRoomReturns409` | 통과 |
| 맞닿으면 200(둘 다 저장) | `touchingIntervalsInSameRoomAreBothAccepted` | 통과 |
| 순서 역전이면 400 | `endAtBeforeStartAtReturns400` | 통과 |

전체 `./gradlew test --console=plain` 71개 전부 통과. 커밋 [11763ea](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/11763ea1bd113e9b9ec7a5e7b14dbe440ec9466e).

## 3. 스스로 답한 질문

### 1) 취소된 예약을 겹침 검사에서 제외하는 근거

**질문.** 브리프의 겹침 공식에는 "취소 여부"가 없는데, 왜 `confirmed = true` 조건을 넣었는가?

**A1.** 처음 근거는 "브리프 문구에 없는 조건이라 굳이 안 넣어도 되지 않나"였다. 결론이 아니라 판단 자체를 건너뛸 뻔했다.

`cancel()`이 예약을 `confirmed = false`로 되돌리는 게 이 도메인의 기존 규칙이다. 취소된 예약이 계속 새 예약을 막는다면, 취소라는 동작 자체가 의미를 잃는다. "브리프에 없다"는 그 조건이 필요 없다는 근거가 아니라 브리프가 구체화를 요구하지 않았을 뿐이라는 걸, 기존 코드의 규칙과 대조한 뒤에야 알았다.

### 2) 시간대 필드를 선택으로 설계한 이유

**질문.** `startAt`/`endAt`을 왜 필수(`@NotNull`)가 아니라 선택 항목으로 뒀는가?

**A2.** 처음 근거는 "필수로 걸면 검증이 간단하다"였다. 실제로 걸어보고서야 시간대 없이 예약하던 기존 컨트롤러 테스트 8개가 전부 400으로 깨지는 걸 확인했다.

교정된 근거는 "간단함"이 아니라 "무엇을 안 건드릴 것인가"였다. 기존 API 계약(시간대 없는 예약)을 유지하려면 필드를 선택으로 두고, 검증·중복검사를 "둘 다 있을 때만" 실행하는 가드로 옮겨야 했다.

## 4. 학습 정리와 다음 범위

### 1) 이해의 변화와 남은 것

Week C에서 fetch join을 "이 쿼리 한 개에만 적용되는 예외"로 정리했던 판단 기준이, 오늘은 "새 컬럼을 추가할 때 기존 계약을 어디까지 지킬 것인가"라는 더 넓은 질문으로 확장됐다. 이번 과제는 프레임워크 기능 하나를 배우는 게 아니라, 계층을 관통하는 새 규칙을 기존 코드와 충돌 없이 얹는 연습이었다.

**아직 남은 것**은 두 가지다. ① `startAt`만 있고 `endAt`이 없는 요청은 "둘 다 없음"과 똑같이 검증을 건너뛰어 조용히 저장된다 — **나중에 고칠 것**(하나만 있으면 400으로 막아야 함). ② 같은 방·같은 시간대에 대한 동시 요청은 애플리케이션 레벨 검사라 이론적으로 막지 못한다(DB `exclusion constraint` 없음) — 이 트랙 범위(H2·학습용) 밖으로 **고치지 않을 것**.

면접에서 다시 답해볼 항목을 남긴다.

- 애플리케이션 레벨 겹침 검사와 DB 레벨 exclusion constraint 중 하나를 고르는 기준
- 같은 판정 공식을 SQL과 자바 양쪽에 중복 구현했을 때의 유지보수 위험을 줄이는 방법

---

오늘 공부한 소스코드: `app/src/main/java/com/example/studyroom/service/ReservationService.java`, `app/src/main/java/com/example/studyroom/repository/SpringDataReservationRepository.java`, `app/src/main/java/com/example/studyroom/repository/InMemoryReservationRepository.java`, `app/src/test/java/com/example/studyroom/service/ReservationOverlapTest.java`, `app/src/test/java/com/example/studyroom/controller/ReservationOverlapHttpTest.java`
