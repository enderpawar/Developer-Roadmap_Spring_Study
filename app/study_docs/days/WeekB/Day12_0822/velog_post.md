# [Spring Study Day 12] Dirty Checking — Snapshot과 flush 시점

Day11에서 한 트랜잭션 안의 같은 `id`는 1차 캐시를 통해 같은 인스턴스로 돌아온다는 것을 확인했다. 이번에는 그 인스턴스의 필드만 바꾸고 `save()`를 부르지 않았을 때 DB에 반영되는지, 반영된다면 무엇을 언제 비교해서 `UPDATE`를 만드는지 확인했다. 트랜잭션 경계와 commit·rollback은 Week C D1의 범위로 남긴다.

> 관리 중인 `Reservation`에 `cancel()`만 호출하고 `save()` 없이 `flush()`하자 `update reservation set ...`가 실행됐고, `clear()` 후 재조회한 값도 `confirmed=false`였다. 예측은 맞았지만 비교 대상과 시점은 로그를 본 뒤에야 설명할 수 있었고, 그 사이 테스트를 두 번 잘못 짰다. 두 시도는 로드 시점 스냅샷과 최종값이 같아 변경 감지가 동작하든 말든 통과하는 테스트였다.

> **오늘의 흐름** `필요성 → Snapshot 비교 → 최종 결과 비교 → flush 시점 → flush·commit 구분 → save() 역할 재정의`
>
> 이전 Day: Persistence Context와 First-Level Cache로 같은 id 조회의 SELECT 횟수와 Entity 동일성을 확인 (Day11)
> 다음 Day: Week A·B 범위의 2주차 누적시험과 오답 교정 (Day13)

## 1. 개념 설명

### 1) Dirty Checking의 필요성

> **Dirty Checking** = 관리 중인 Entity의 현재 값을 스냅샷과 비교해 다르면 `UPDATE`를 만드는 동작

Day09의 `JdbcReservationRepository.save()`에는 `INSERT`만 있었다. 그래서 취소 흐름에서 기존 예약을 다시 저장하면 새 행이 하나 더 생겼다. 상태가 바뀔 때마다 어떤 SQL을 보낼지 저장소 코드가 직접 결정해야 했던 구조다.

JPA로 바꾼 뒤에도 "필드를 바꿨으면 반드시 `save()`를 불러야 반영된다"는 규칙이라면 문제가 남는다.

- `save()` 한 줄을 빠뜨리면 상태 변경이 조용히 사라진다.
- 도메인 메서드 `cancel()`은 `confirmed` 필드만 바꾸는데, 호출하는 쪽이 매번 영속화까지 책임져야 한다.

Day11에서 확인했듯 한 트랜잭션 안에서 같은 예약은 인스턴스 하나다. 그렇다면 그 인스턴스의 필드가 바뀌었다는 사실만으로 "이 예약의 상태가 바뀌었다"를 판단할 수 있다. 변경 감지는 이 판단을 영속성 컨텍스트가 하도록 만든 장치다.

상태를 바꾸는 메서드는 `Reservation.cancel()`(`this.confirmed = false`)과 `Reservation.confirm()`이며, 둘 다 JPA를 모르는 평범한 Java 메서드다. 이 변경을 SQL로 옮기는 일은 도메인 메서드가 아니라 영속성 컨텍스트가 한다.

### 2) Snapshot과 비교 시점

> **Snapshot** = Entity가 영속성 컨텍스트에 들어온(로드되거나 저장된) 시점의 필드 값 사본

변경 감지는 "지금 값이 무엇인가"를 보지 않는다. **들어올 때의 값과 지금 값이 다른가**를 본다. 그래서 들어올 때의 값을 따로 보관해야 하고, 그것이 스냅샷이다. 오늘 이 비교를 관찰한 실험 코드는 `JpaReservationRepositoryTest.modifyingManagedEntityWithoutExplicitSaveStillPersistsOnFlush()`다.

```text
Entity가 영속성 컨텍스트에 들어옴 (save 또는 조회)
→ 컨텍스트가 그 시점의 필드 값 사본(스냅샷) 보관
→ 애플리케이션이 도메인 메서드로 필드 변경 (cancel())
   → 이 시점에는 Java 객체만 바뀌고 SQL은 없음
→ flush 시점 도달
→ 컨텍스트가 관리 중인 Entity마다 현재 값과 스냅샷 비교
   ├─ 다름 → UPDATE SQL 생성·실행
   └─ 같음 → 아무 SQL도 만들지 않음
```

주체는 영속성 컨텍스트이고, 비교는 flush 시점에 한 번 일어난다. `cancel()`을 호출한 순간 SQL이 나가는 것이 아니다. `cancel()`은 `this.confirmed = false` 한 줄짜리 평범한 Java 메서드이고, JPA를 전혀 모른다.

오늘 로그에 찍힌 `UPDATE`는 이렇다.

```sql
update reservation set confirmed=?, requester_name=?, room_name=? where id=?
```

바뀐 것은 `confirmed` 하나인데 세 컬럼이 모두 `set` 절에 있다. 적어도 이 설정에서는 Hibernate가 바뀐 컬럼만 골라 쓰지 않고 Entity의 컬럼 전체로 `UPDATE`를 만들었다. 이 방식을 바꾸는 설정은 오늘 다루지 않았다.

> **보장 범위** — 같은 트랜잭션 안에서 관리 중인 Entity의 필드를 바꾸고 flush하면, `save()` 없이 `UPDATE`가 실행되고 재조회 값에 반영됐다. 성립 조건은 Entity가 관리 상태여야 하고, flush 시점의 값이 스냅샷과 달라야 한다는 것이다. 관리 상태가 아닌 객체(컨텍스트 밖으로 나간 객체)의 필드 변경은 오늘 실험하지 않았다(미검증) — Day11처럼 `clear()` 뒤에 남은 참조를 바꾸는 경우가 여기에 해당한다.

### 3) 중간 과정이 아닌 최종 결과의 비교

> **Final-State Comparison** = 트랜잭션 중간에 값이 몇 번 바뀌었는지와 무관하게, flush 시점의 최종값과 스냅샷만 비교하는 변경 감지의 성질

이 정의에서 바로 나오는 결론이 있다. 트랜잭션 안에서 값이 몇 번 바뀌었는지는 상관없다. flush 시점의 최종값이 스냅샷과 같으면 변경이 없는 것으로 본다.

오늘 테스트를 세 번 짠 과정이 정확히 이 결론을 보여줬다.

| 시도 | 스냅샷 → flush 시점 값 | 테스트가 증명하는 것 |
|---|---|---|
| 1차: `confirm()` 없이 저장 → `cancel()` | `false` → `false` | 없음 (변경 자체가 없음) |
| 2차: 조회 후 `confirm()` → 곧바로 `cancel()` | `false` → `false` | 없음 (중간에 `true`를 거쳤지만 최종값이 같음) |
| 3차: 저장 **전** `confirm()` → 조회 후 `cancel()` | `true` → `false` | `save()` 없이 `UPDATE` 발생 |

1·2차 모두 마지막 `assertFalse(reloaded.isConfirmed())`는 통과한다. 하지만 DB에는 처음부터 `false`가 들어 있었으므로, 변경 감지가 동작했든 안 했든 통과한다. **실패할 수 없는 테스트는 아무것도 증명하지 못한다.**

3차에서 `confirm()`을 최초 저장 이전으로 옮겨 DB에 `true`를 넣어두고, 트랜잭션 안에서는 `cancel()`만 한 번 불렀다. 그제서야 스냅샷(`true`)과 최종값(`false`)이 달라졌고 `UPDATE`가 관찰됐다.

![시퀀스 다이어그램. 참여자는 테스트, Repository, r : Reservation, 영속성 컨텍스트, H2다. 테스트가 save(r)을 호출하면 Repository가 r을 영속 상태로 등록하고, flush() 때 컨텍스트가 H2로 INSERT를 보낸다. findById(id)는 캐시 조회에서 같은 인스턴스 r을 managed로 돌려준다. alt 프레임의 첫 갈래는 저장 전 confirm()으로 스냅샷이 confirmed=true인 3차 시도다. managed.cancel()로 confirmed가 false가 되고, flush()에서 컨텍스트가 true와 false가 다름을 확인해 H2로 UPDATE를 보낸다. 둘째 갈래는 스냅샷이 confirmed=false인 1·2차 시도다. cancel() 뒤 flush()에서 false와 false가 같아 UPDATE가 없다. 프레임 뒤에는 clear() 후 findById(id)가 캐시에 없어 H2로 SELECT를 보내 confirmed=false인 새 인스턴스 reloaded를 받는다. 하단 주석은 두 갈래 모두 assertFalse가 통과하고 차이는 UPDATE 발생 여부뿐이라고 적는다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day12-dirty-checking.png)

### 4) flush 시점과 테스트 트랜잭션

> **Test Transaction Rollback** = Spring `@Transactional` 테스트가 기본적으로 종료 시 rollback해, commit 전 자동 flush가 일어나지 않는 성질

오늘 테스트는 `cancel()` 다음 줄에서 `entityManager.flush()`를 직접 불렀다. flush는 대기 중인 변경을 SQL로 내보내는 시점이고, 그 시점이 와야 비교가 일어난다. flush·clear는 테스트에 주입한 `EntityManager`로 직접 호출했고, 트랜잭션은 테스트 클래스의 클래스 수준 `@Transactional`이 감싼다.

명시적으로 부르지 않아도 flush는 일어난다. Hibernate 문서의 기본 `AUTO` 모드는 트랜잭션 commit 전, 그리고 일부 쿼리 실행 전에 flush한다. 문제는 테스트다.

Spring 테스트에서 `@Transactional`로 감싼 테스트 트랜잭션은 **기본적으로 종료 시 rollback된다.** commit이 없으니 commit 직전 flush도 없다. Spring 문서는 이 상태를 ORM 테스트의 false positive로 설명하고, 수동 `flush()`를 권한다.

```text
테스트 트랜잭션 시작
→ managed.cancel() : 메모리 변경
→ entityManager.flush() : 스냅샷 비교 → UPDATE 실행
→ entityManager.clear() → findById() : DB에서 다시 읽어 반영 확인
→ 테스트 종료 → rollback (기본 동작)
```

그래서 이 테스트에서 본 `UPDATE`는 DB에 **실행**됐지만 **확정**되지는 않았다. `flush()` 줄을 빼고 돌려 `UPDATE`가 사라지는지는 실행하지 않았다(미검증).

### 5) flush와 commit의 구분

> **Transaction Boundary** = 트랜잭션이 시작되고 끝나는 경계로, 그 안의 변경을 확정(commit)하거나 취소(rollback)하는 단위

| 동작 | 하는 일 | 되돌릴 수 있는가 |
|---|---|---|
| `flush()` | 컨텍스트의 변경을 SQL로 DB에 실행 | 같은 트랜잭션이 rollback되면 취소됨 |
| commit | 트랜잭션 안에서 실행된 변경을 확정 | 확정 후에는 트랜잭션 rollback 대상이 아님 |

ACID의 원자성 단위는 commit이다. flush는 그 트랜잭션 안에서 SQL을 앞당겨 실행할 뿐, "저장 완료"를 의미하지 않는다. 오늘은 flush만 관찰했고, flush 후 rollback되는 대조군은 이후 Day15에서 실행했다.

> **정리.** flush는 "SQL을 보내는 시점", commit은 "결과를 확정하는 시점"이다. 테스트 트랜잭션은 기본적으로 rollback되므로, 변경 감지를 관찰하려면 flush를 직접 불러야 한다.

### 6) `save()`의 역할 재정의

> **`save()`의 실제 역할** = 새 객체를 영속화하는 편의 메서드. "저장"의 유일한 경로가 아님

오늘 결과로 `save()`를 다시 봤다. 이미 관리 중인 Entity라면 `save()` 없이도 flush 시점에 반영된다. `save()`가 꼭 필요한 곳은 **아직 컨텍스트에 없는 새 객체**를 영속화할 때다.

현재 코드에서 두 경우가 나뉜다.

- `ReservationService.reserve()` — `new Reservation(...)`으로 만든 새 객체이므로 `save()`가 영속화 경로다.
- `ReservationService.cancel()` — `findById()` → `reservation.cancel()` → `reservationRepository.save(reservation)`. 이 시점 서비스에는 `@Transactional`이 없다.

`cancel()`의 `save()`가 불필요한지는 조회한 객체가 `save()` 시점까지 관리 상태로 남아 있는지에 달렸다. 즉 트랜잭션 경계 문제이고, 오늘 테스트는 이것을 확인하지 않았다. 이후 Day15에서 `cancel()`에 `@Transactional`을 붙이면서 이 `save()`를 제거했다.

같은 이유로 Day10의 `savingExistingReservationUpdatesWithoutAddingDuplicate()`도 다시 읽힌다. 그 테스트는 관리 중인 `saved`에 `cancel()` 후 `save(saved)`를 불렀다. 거기서 본 `UPDATE`가 `save()` 호출 때문인지 변경 감지 때문인지는 그 테스트만으로 구분할 수 없다(미검증).

### 7) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| Dirty Checking | 관리 중인 Entity의 현재 값을 스냅샷과 비교해 다르면 `UPDATE`를 만드는 동작 |
| Snapshot | Entity가 영속성 컨텍스트에 들어온 시점의 필드 값 사본 |
| Persistence Context | 관리 중인 Entity와 스냅샷을 함께 보관하는 JPA의 관리 영역 |
| Managed State | Entity가 영속성 컨텍스트에 등록되어 변경 감지 대상이 되는 상태 |
| flush | 영속성 컨텍스트의 변경 사항을 실제 SQL로 DB에 내보내는 시점 |
| Transaction Boundary | 트랜잭션이 시작되고 끝나는 경계로, Entity가 관리 상태로 남는 범위를 결정 |
| Repository | Entity의 저장·조회를 담당하는 컴포넌트로 `save()`는 그 편의 메서드 중 하나 |

> **더 볼 것**
> - [Hibernate ORM User Guide — Flushing](https://docs.hibernate.org/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#flushing): `AUTO` flush 모드와 flush가 일어나는 시점
> - [Spring TestContext — Transaction Management](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/tx.html): 테스트 트랜잭션의 기본 rollback과 ORM 테스트의 수동 flush
> - 아직 안 본 것 — `@Transactional`이 서비스 메서드에 트랜잭션 경계를 만드는 방식(Week C D1)

## 2. 코드 구현

### 1) `save()` 호출 없는 상태 변경 테스트

```java
Reservation reservation = new Reservation("B202", "노은주");
reservation.confirm();                       // 저장 전에 true로 만든다

Reservation saved = repository.save(reservation);
entityManager.flush();
Long id = saved.getId();
Reservation managed = repository.findById(id).orElseThrow();

managed.cancel();                            // save()는 호출하지 않는다
entityManager.flush();                       // 여기서 스냅샷 비교 → UPDATE

entityManager.clear();
Reservation reloaded = repository.findById(id).orElseThrow();
assertFalse(reloaded.isConfirmed());
```

`confirm()`의 위치가 이 테스트의 핵심이다. 저장 전에 호출해야 스냅샷이 `true`가 되고, 트랜잭션 안의 `cancel()` 한 번이 스냅샷과 다른 최종값을 만든다.

`managed`는 Day11의 결론대로 캐시에서 나온 인스턴스다. 소스 주석에도 "캐시에서 나오든 DB에서 나오든 상관없음"이라고 적어 뒀다. 중요한 것은 관리 상태라는 점이지, SELECT가 나갔는지가 아니다.

마지막의 `clear()` → `findById()`는 검증 지점을 DB로 옮기는 장치다. `clear()` 없이 `managed.isConfirmed()`를 확인하면 메모리 값만 보는 것이라, DB 반영 여부를 증명하지 못한다.

`cancel()`은 이날 인자가 없는 메서드였다. 이후 D7 독립과제([2f870cf](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/2f870cf97f51e956885b914505c09d54fe1b7ca3))에서 취소 사유를 받는 `cancel(String)`으로 바뀌었다.

**한 줄씩 보기**

- `reservation.confirm();` — 저장 전에 호출해 스냅샷을 `confirmed=true`로 만든다.
- `entityManager.flush();` (첫 번째) — INSERT를 즉시 내보내 `id`를 확보한다.
- `Reservation managed = repository.findById(id).orElseThrow();` — 같은 트랜잭션이라 1차 캐시에서 스냅샷 `true`인 인스턴스를 관리 상태로 돌려받는다.
- `managed.cancel();` — `save()` 없이 필드만 `false`로 바꾼다. 이 줄에서는 SQL이 없다.
- `entityManager.flush();` (두 번째) — 이 시점에 스냅샷(`true`)과 현재 값(`false`)을 비교해 `UPDATE`를 실행한다.
- `entityManager.clear();` — 1차 캐시를 비워 다음 조회가 DB를 다시 읽게 만든다.
- `assertFalse(reloaded.isConfirmed());` — DB에서 새로 읽은 값으로 반영 여부를 확인한다.

### 2) 자동 검증 결과

`./gradlew test --tests "...JpaReservationRepositoryTest" -i`로 실행해 Hibernate SQL 로그를 터미널에서 확인했다.

```sql
insert into reservation (confirmed, requester_name, room_name, id) values (?, ?, ?, default)
update reservation set confirmed=?, requester_name=?, room_name=? where id=?
select r1_0.id, r1_0.confirmed, r1_0.requester_name, r1_0.room_name from reservation r1_0 where r1_0.id=?
```

| 확인 항목 | 방법 | 결과 |
|---|---|---|
| `save()` 없이 `UPDATE` 실행 | 자동 테스트 + SQL 로그 | `update` 1줄 관찰 |
| DB 반영 | `clear()` 후 재조회 | `assertFalse(reloaded.isConfirmed())` 통과 |
| commit 여부 | 확인하지 않음 | 테스트 트랜잭션은 기본 rollback |

`select`가 한 줄뿐이라는 점도 Day11과 맞는다. `managed`를 얻은 첫 `findById()`는 캐시 적중이었고, SELECT는 `clear()` 뒤 재조회에서만 나갔다. 코드는 [9e3dfc3](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/9e3dfc3a3956d03e68588499e7a54772a7a6d599)에 있다.

## 3. 스스로 답한 질문

### 1) `save()` 없는 필드 변경의 DB 반영

**질문.** 관리 중인 Entity를 `cancel()`만 하고 `save()`는 호출하지 않으면 DB에 반영되는가?

**A1.** 처음 답은 "커밋될 것 같아요, dirty checking 때문에"였다. 결과는 맞았다. 로그에 `save()` 호출 없이 `update`가 찍혔다.

하지만 용어로 맞힌 답이었다. "언제 무엇을 비교하는가"를 이어서 물었을 때 답하지 못하고 넘어갔고, 실행 결과를 본 뒤에야 설명을 재구성했다.

교정된 답은 이렇다. 영속성 컨텍스트가 **flush 시점**에 관리 중인 Entity의 **현재 값과 로드(또는 저장) 시점 스냅샷**을 비교하고, 다르면 `UPDATE`를 만든다. 또 오늘 테스트에서는 트랜잭션이 rollback으로 끝나므로, 정확히는 "커밋된다"가 아니라 "flush 시점에 UPDATE가 실행된다"까지가 관찰한 범위다.

### 2) 증명력이 없는 Dirty Checking 테스트의 구성

**질문.** 처음 두 번의 테스트는 왜 통과했는데도 아무것도 증명하지 못했는가?

**A2.** 1차는 `confirm()` 없이 저장해 스냅샷이 `false`였고, `cancel()` 후에도 `false`였다. 2차는 조회 후 `confirm()`으로 `true`를 만들었다가 곧바로 `cancel()`해 최종값이 다시 `false`가 됐다. 스냅샷도 `false`였다.

두 경우 모두 스냅샷과 최종값이 같았다. 변경 감지 관점에서는 바뀐 것이 없고, DB에도 처음부터 `false`가 있었으므로 `assertFalse`는 무조건 통과한다.

재발 방지 기준은 **"상태를 바꾸는 호출이 로드 시점 스냅샷과 다른 최종값을 만드는가"**를 테스트 작성 전에 확인하는 것이다. 초기 상태를 트랜잭션 밖(최초 저장 이전)에서 반대 값으로 만들어 두고, 트랜잭션 안에서는 변경 호출을 한 번만 남긴다.

## 4. 학습 정리와 다음 범위

### 1) 전체 흐름 다시 보기

오늘 실험은 관리 중인 Entity의 필드를 바꾸고 `save()` 없이 flush했을 때 `UPDATE`가 나가는지 확인하는 것이었다. 아래 그림은 그 변경 감지가 flush 한 번 안에서 어떤 순서로 일어나는지 정리한 것이다.

![영속성 컨텍스트(entityManager) 안의 변경 감지 흐름도. 1차 캐시 표는 @Id, Entity, 스냅샷 세 열로 memberA와 memberB의 엔티티와 스냅샷을 함께 보관한다. 1. flush()가 호출되면 2. 엔티티와 스냅샷을 비교하고, 달라진 memberA에 대해 3. UPDATE SQL을 생성해 쓰기 지연 SQL 저장소에 쌓는다. 4. flush 때 저장소의 UPDATE A가 DB로 전송되고, 5. commit으로 DB에 확정된다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day12-overview-dirty-checking.png)

*출처: [[JPA] 영속성 컨텍스트](https://velog.io/@imcool2551/JPA-%EC%98%81%EC%86%8D%EC%84%B1-%EC%BB%A8%ED%85%8D%EC%8A%A4%ED%8A%B8) — imcool2551 (velog), 원 도식은 인프런 김영한 JPA 로드맵 강의 자료. 저작권은 원저작자에게 있습니다.*

### 2) 이해의 변화와 남은 것

JPA에서 "저장"은 `save()` 호출과 같은 말이 아니었다. 관리 중인 객체의 필드를 바꾸는 행위 자체가 변경이고, 영속성 컨텍스트가 flush 시점에 스냅샷과 비교해 그 변경을 SQL로 바꾼다. `save()`는 새 객체를 컨텍스트에 넣는 입구에 가깝다.

그리고 변경 감지는 과정이 아니라 결과를 비교한다. 이 정의를 알고 있어도 처음 두 테스트는 "값을 바꾸는 호출을 했다"는 사실만 보고 짰다. 실패할 수 없는 테스트를 알아보는 기준을 개념 정의에서 직접 끌어낸 것이 오늘의 수확이었다.

**아직 남은 것**은 두 가지다. `ReservationService.cancel()`의 `save()`가 필요한지는 서비스 트랜잭션 경계에 달렸고, 오늘 테스트는 그것을 확인하지 않았다. **나중에 고칠 것**으로 분류해 Week C D1에서 경계를 정한 뒤 판단한다. 또 오늘 본 `UPDATE`는 rollback되는 테스트 트랜잭션 안의 결과라서, commit까지 이어지는 경로는 같은 Week C 범위에서 확인한다.

면접에서 다시 답해볼 항목을 남긴다.

- 변경 감지가 있는데도 `save()`를 명시적으로 호출하는 코드가 흔한 이유
- 트랜잭션 안에서 값이 바뀌었다가 원래대로 돌아온 Entity에 대한 flush 결과

<!-- 선택 복습 메모: 게시 화면에는 노출하지 않는다.
[직접 작성] 오늘 배운 것을 내 문장으로
- `save()`가 "저장"의 유일한 방법이 아니라는 게 왜 처음엔 낯설게 느껴졌는지:
- 오늘 세 번 실험을 다시 짜야 했던 이유를 내 말로:
-->

---

오늘 공부한 소스코드: `app/src/test/java/com/example/studyroom/repository/JpaReservationRepositoryTest.java`, `app/src/main/java/com/example/studyroom/domain/Reservation.java`
