# [Spring Study Day 11 ~ Day 14] 2주차 마무리 시험

Week B는 저장을 메모리에서 DB로 내리는 주였다. D1~D3에서 Flyway로 스키마를 만들고 JDBC를 거쳐 Spring Data JPA 어댑터까지 갈아끼웠고, 남은 D4~D7은 그 위에서 영속성 컨텍스트를 열어본 뒤 한 주를 시험과 부채 상환으로 닫는 순서였다. 이 글은 그 네 날을 한 편으로 묶는다.

> 아침 인출로 밀린 오답재시험 5문항을 먼저 풀고(4개 통과), 같은 `id`를 두 번 조회해 SQL 횟수와 참조 동일성을 예측·대조했다. `save()` 없이 필드만 바꿔도 `UPDATE`가 나가는 것을 로그로 확인했고, 누적시험 8문항은 모두 통과했지만 절반은 힌트가 필요했다. D7에서는 `ddl-auto`를 `validate`로 올리고 `CHECK` 제약으로 Day08의 부채를 갚았으며, 독립과제로 `cancel_reason` 컬럼을 마이그레이션부터 Controller까지 관통시켰다. 최종 테스트 16개 통과.

## 1. 시험 범위와 진행 방식

이 네 날은 원래 나눠 진행할 계획이었고 실제로 한 번 진행했다. 그 기록이 노트북에 있었고, 커밋하지 않은 상태에서 데이터를 잃었다.

규칙대로 처리했다. 이 저장소는 Day 완료를 `vocab.md`·`quiz.md`·`explain-log.md`와 코드·테스트로 판정한다. 산출물이 없으면 완료가 아니고, 기억으로 완료 표시만 채우는 건 복구가 아니라 규칙 우회다. 체크리스트의 재개 지점을 그대로 D4에 두고 네 날을 다시 했다.

먼저 복습큐에서 도래한 오답재시험 5건을 뽑았다. 밀린 분량이 많을 때는 오답재시험을 예외 없이 먼저 넣는 규칙이 있어서다.

| 인출 항목 | 이전 상태 | 오늘 |
|---|---|---|
| `final`은 JVM 변수 재대입만 막고 DB 행과 무관 | 2회 오답 | 통과 |
| 싱글톤 Service의 인스턴스 필드는 요청끼리 섞인다 | 오답(반대로 이해) | 통과 |
| 같은 객체를 두 번 `save()`하면 항목은 1개 | 오답(2로 예측) | 통과 |
| 예외 상세는 로그로, 응답에는 일반 메시지만 | 미답 | 통과(힌트 후) |
| Entity의 `final` 필드 + 빈 기본 생성자 | 오답 | **재확인 필요** |

마지막 항목은 "불변이라 못 다룬다"는 방향까지만 갔고 정확한 메커니즘에는 못 닿았다. 이건 Hibernate가 런타임에 못 다루는 문제가 아니라 **컴파일러가 초기화되지 않은 생성자 경로를 거부**하는 문제다. 첫 항목과 같은 `final` 키워드인데 한쪽은 굳었고 한쪽은 아직 아니다.

## 2. D4·D5 — 예측과 로그의 차이

| 용어 | 한줄뜻 | 코드 모습 |
|---|---|---|
| 영속성 컨텍스트 | `EntityManager`가 트랜잭션 동안 관리 중인 Entity를 붙잡아두는 공간 | `@Transactional` 메서드 하나 = 컨텍스트 하나 |
| 1차 캐시 | 그 공간에서 `(타입, id)`로 Entity를 찾는 식별자 맵 | `findById()`가 SQL 전에 들르는 곳 |
| 변경 감지 | 로드 시점 스냅샷과 현재 값을 비교해 `UPDATE`를 만드는 동작 | `save()` 없이 필드만 변경 |
| `flush()` | 대기 중인 변경을 실제 SQL로 DB에 내보내는 시점 | `entityManager.flush()` |

`clear()`는 이 공간을 통째로 비우고, `flush()`는 비우지 않고 내보내기만 한다. 두 메서드를 Day10에 이미 썼지만 그때는 검증 지점을 고정하려는 용도였고 무엇을 하는지는 설명하지 못했다.

### 1) 같은 `id`를 두 번 조회하면 SELECT는 몇 번인가

테스트를 쓰기 전에 답을 적었다. 내 예측은 **SELECT 한 번, `first == second`는 참**이었다. `save()` → `flush()`로 `id`를 확보한 뒤 같은 `id`로 `findById()`를 두 번 부르는 구조다.

로그에 찍힌 건 `insert` 한 줄이 전부였다. `select`가 **0번**이다.

`save()`가 반환한 객체는 그 순간부터 영속성 컨텍스트 안에 있다. 그래서 첫 번째 조회부터 1차 캐시에 걸렸고 DB까지 갈 일이 없었다. "한 번"이라고 답한 건 첫 조회는 당연히 DB에서 읽을 거라고 본 것인데, 그 전제가 틀렸다.

`flush()` 뒤에 `clear()`를 한 줄 넣고 다시 예측했다. SELECT 한 번, 두 번째 조회는 SQL 없음, 동일성은 여전히 참 — 이번엔 로그가 그대로였다.

```sql
insert into reservation (confirmed, requester_name, room_name, id) values (?, ?, ?, default)
select r1_0.id, r1_0.confirmed, r1_0.requester_name, r1_0.room_name from reservation r1_0 where r1_0.id=?
```

![시퀀스 다이어그램. 테스트가 save(reservation)를 호출하면 Repository가 영속성 컨텍스트에 영속 상태로 등록하고, flush() 시점에 컨텍스트가 H2로 INSERT를 보낸다. 이후 alt 프레임이 두 갈래로 갈린다. clear()를 호출하지 않은 갈래에서는 findById(id) 두 번이 모두 캐시 조회에서 끝나고 인스턴스 r을 돌려주며, H2 생명선에는 화살표가 하나도 닿지 않는다. clear()를 호출한 갈래에서는 첫 findById(id)만 컨텍스트가 H2로 SELECT를 보내 행 1건을 받아오고, 두 번째 findById(id)는 다시 캐시 조회에서 끝나 같은 인스턴스 r을 돌려준다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day11-first-level-cache.png)

`clear()`는 조회를 막는 장치가 아니라 **"처음 조회하는 상태"로 되돌리는** 장치였다. 그리고 두 경우 모두 두 조회가 같은 객체를 돌려줬다. 1차 캐시가 보장하는 건 값이 같다는 게 아니라 같은 트랜잭션·같은 `id`면 **객체가 하나**라는 것이다.

첫 단언은 `assertEquals`로 썼다가 `assertSame`으로 바꿨다. `Reservation`에는 `equals()`를 재정의하지 않아 `assertEquals`도 우연히 통과하지만, 나중에 누가 `equals()`를 필드 비교로 재정의하면 서로 다른 인스턴스여도 계속 초록불이다.

### 2) `save()` 없이 `UPDATE`가 나가는가

D5의 예측은 "커밋될 것 같다, dirty checking 때문에"였고 결과는 맞았다.

```java
Reservation managed = repository.findById(id).orElseThrow();
managed.cancel(cancelReason);          // save() 호출 없음
entityManager.flush();                 // 여기서 UPDATE가 만들어진다
```

코드 어디에도 `repository.save(managed)`가 없는데 `update reservation set ... where id=?`가 나갔다. JPA에서 "저장"은 `save()` 호출이 아니라 관리 중인 객체의 필드를 바꾸는 행위 자체였다.

다만 답을 맞힌 것과 설명할 수 있는 것은 달랐다. "무엇을 언제 비교하는가"는 로그를 보고서야 정리했고, 그 사이에 테스트를 두 번 잘못 짰다.

1차 시도는 `confirm()` 없이 저장하고 `cancel()`을 불렀다. 저장 시점에도 `confirmed`가 `false`, 변경 후에도 `false`다. 2차 시도는 조회 후 `confirm()`을 부르고 곧바로 `cancel()`을 불렀다. 로드 스냅샷이 `false`, 최종값도 `false`다. **둘 다 통과하지만, dirty checking이 동작했든 안 했든 통과한다.** 값이 바뀌지 않았으니 검증할 변경 자체가 없었다.

3차에서 `confirm()`을 최초 저장 이전으로 옮겨 DB에 `true`로 넣어두고, 트랜잭션 안에서는 `cancel()`만 한 번 불렀다. 그제서야 스냅샷(`true`)과 최종값(`false`)이 달라지면서 `UPDATE`가 관찰됐다. 이 시행착오가 곧 변경 감지의 정의다 — Hibernate가 보는 건 "지금 값이 무엇인가"가 아니라 **"로드 시점 값과 지금 값이 다른가"**다.

> **더 볼 것**
> - [Hibernate ORM User Guide](https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html): 영속성 컨텍스트와 Entity 상태 전이
> - 아직 안 본 것 — `@Transactional`이 트랜잭션 경계를 만드는 메커니즘(Week C D1)

## 3. D6 누적시험에서 틀린 문제

Week A 전체와 Week B D1~D5 범위로 8문항을 봤다. 최종적으로는 다 통과했지만 힌트 없이 답한 건 절반이다. 아래는 실제로 틀렸거나 막힌 것만 골랐다.

| 문항 | 최초 답변 | 판정 |
|---|---|---|
| DTO와 Domain을 왜 분리하나 | "domain은 immutable하지 않으므로 상태 변경 로직을 수행함" | 인과 역전 |
| 생성자 주입을 쓰는 이유 3가지 | "생성자 주입이 뭔지 기억이 안 난다" | 개념 재설명 필요 |
| 적용된 Flyway 파일을 왜 고치면 안 되나 | "Dirty Checking시 탈락한다" | 용어 혼동 |

**DTO/Domain 분리**는 방향은 맞았는데 순서가 거꾸로였다. "불변이 아니라서 상태 변경 로직이 있다"가 아니라, **상태 변경이 필요한 객체라서 애초에 `record`로 만들 수 없다**가 맞다. 원인과 결과를 바꿔 외우면 "그럼 Domain을 record로 만들면 안 되나"라는 질문에 답할 수 없다.

**Flyway 문항**이 이번 시험에서 가장 볼 만한 오답이다. 체크섬이라고 답해야 할 자리에 그날 배운 "dirty checking"을 넣었다. 두 장치가 "뭔가 바뀐 걸 감지한다"로 겹쳐 보였기 때문인데, 감지 대상도 시점도 다르다.

| 장치 | 무엇을 언제 비교하나 | 다르면 |
|---|---|---|
| Flyway 체크섬 | 마이그레이션 **파일**의 해시를 기동 시점에 | 기동 거부 |
| Hibernate dirty checking | Entity **필드** 값과 로드 스냅샷을 flush 시점에 | `UPDATE` 발행 |

새로 배운 개념이 기존 개념 자리를 덮어쓴 경우라 복습큐에 따로 등록했다.

## 4. D7 — 시험에서 고친 판단을 코드에 넣기

### 1) 틀린 문항이 그대로 이번 주 코드 규칙이 됐다

D7에는 스키마를 두 번 더 바꿔야 했다. `CHECK` 제약을 걸고, 독립과제로 컬럼을 하나 추가하는 일이다. 몇 시간 전 시험에서 틀린 게 정확히 **"적용된 마이그레이션은 고치지 않는다"**였으므로, 두 번 다 `V1__init.sql`을 열지 않고 `V2`·`V3`를 새로 쌓았다.

Flyway는 SQL을 해석하지 않고 파일 전체의 해시만 대조한다. "주석 한 줄이라 의미 없음" 같은 판단을 아예 하지 않는다는 걸 Day08에 기동 실패로 겪었고, 오늘 시험에서는 그 이유를 dirty checking으로 잘못 댔다. 판단 기준을 고친 직후에 그 기준을 쓸 자리가 두 번 나온 셈이다.

### 2) `ddl-auto: none` → `validate`

예측은 "Entity 필드와 실제 DB 컬럼의 타입·이름을 비교해서 안 맞으면 기동 실패"였고 맞았다. `none`과 `validate`는 둘 다 Hibernate가 DDL을 만들지 않는다는 점에서 같고, `validate`는 거기에 **불일치 감시**를 더한다. 스키마를 만드는 권한은 끝까지 Flyway에만 있다.

### 3) `NOT NULL`이 못 막는 구멍을 `CHECK`로

Day08에 확인해둔 부채가 있었다. `NOT NULL`은 `NULL`만 막고 `''`는 통과시킨다 — `''`는 값이 없는 게 아니라 길이 0인 값이 있는 상태이기 때문이다.

```sql
ALTER TABLE reservation
    ADD CONSTRAINT room_name CHECK (room_name <> '');
```

여기까지 세 번 걸렸다. 처음엔 `CHECK NOT BLANK`로 썼다 — `@NotBlank`는 자바 애노테이션이고 SQL에는 그런 키워드가 없다. 다음엔 `CHECK <> ''`로 비교 대상을 빼먹었고, 그다음엔 `ALTER TABLE reservation( ... )`처럼 `CREATE TABLE`의 괄호를 끌어왔다. 앱 검증과 DB 제약이 "층이 다르다"는 걸 개념으로는 정리해뒀는데 문법 단계에서 다시 섞였다.

검증은 `entityManager.createNativeQuery()`로 Bean Validation을 완전히 우회해 빈 문자열을 직접 INSERT하고 `PersistenceException`이 나는지 봤다. `@NotBlank`가 보지 못하는 경로로 넣어야 DB 제약만 순수하게 테스트할 수 있다.

### 4) 독립과제 — `cancel_reason` 한 컬럼이 지나간 자리

컬럼 하나인데 다섯 파일이 바뀌었다. `V3`에서 NULL 허용 여부를 정하는 게 첫 판단이었다. `NOT NULL`을 걸면 아직 취소되지 않은 예약도 사유를 가져야 한다. `CHECK`로 조건부 제약을 걸까 하다가, **아무 제약도 안 걸면 그 컬럼은 원래 nullable**이라는 데서 멈췄다.

그다음은 `cancel()`의 시그니처였다. 오버로드로 `cancel(String)`을 추가하면 기존 호출부를 안 건드려도 되지만, 취소 경로가 둘로 갈려 어떤 취소는 사유가 남고 어떤 취소는 안 남는다. "취소엔 항상 사유가 있어야 한다"고 보고 시그니처 자체를 바꿨고, 그 대가를 컴파일러가 하나씩 알려줬다.

```text
ReservationService.java:25: error: method cancel in class Reservation cannot be applied to given types;
        reservation.cancel();
  required: String
  found:    no arguments
```

Service를 고치니 Controller가, Controller를 고치니 테스트 4곳이 걸렸다. 그다음엔 `@PathVariable`로 선언한 `cancelReason`이 문제였다 — URL 템플릿에는 `{id}` 자리 하나뿐인데 경로 변수로 받으려 했고, 거기 붙인 `@Positive`는 숫자용이라 `String`에 맞지 않았다. `@RequestParam` + `@NotBlank`로 바꾸자 이번엔 기존 MockMvc 테스트 2개가 깨졌다. 쿼리 파라미터가 필수가 됐는데 요청에 안 넣고 있었으니, 기대하던 404·400이 아니라 **다른 이유의 400**이 돌아온 것이다.

어디가 영향받는지 외우고 있을 필요는 없었고, 컴파일 에러를 따라가면 됐다.

### 5) 같은 함정을 하루에 두 번

독립과제 마지막 검증 테스트에서 D5와 **같은 실수를 다시 했다.** 취소 사유를 저장 전과 후에 똑같은 문자열로 넣은 것이다. 로드 스냅샷과 최종값이 같으니 dirty checking이 동작하든 말든 통과한다.

몇 시간 전에 세 번이나 다시 짠 함정인데 도메인이 바뀌자 못 알아봤다. 개념을 이해한 것과 그 개념을 새 코드에서 알아보는 것은 다른 능력이라는 게 이번 주에서 가장 실질적인 수확이었다.

## 5. 검증 범위와 다음 시작점

| 확인한 것 | 방법 | 결과 |
|---|---|---|
| 1차 캐시 — `clear()` 유무에 따른 SELECT 횟수와 참조 동일성 | 자동 — 통합 테스트 + Hibernate 로그 | 0회 / 1회, `assertSame` 통과 |
| 변경 감지 — `save()` 없이 `UPDATE` | 자동 — flush 후 `clear()` + 재조회 | `UPDATE` 관찰, 값 반영 확인 |
| `CHECK` 제약이 `''`를 거부 | 자동 — 네이티브 쿼리로 앱 검증 우회 | `PersistenceException` |

전체 `./gradlew test` 16개 통과. 코드는 [9e3dfc3](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/9e3dfc3a3956d03e68588499e7a54772a7a6d599)과 [2f870cf](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/2f870cf97f51e956885b914505c09d54fe1b7ca3)에 있다.

**미검증 범위**를 구분해둔다. 오늘 확인한 동일성과 변경 감지는 전부 하나의 `@Transactional` 안에서 관찰했고, 트랜잭션이 다를 때의 동작은 보지 않았다. `ddl-auto: validate`도 컬럼의 존재와 타입을 볼 뿐 `CHECK` 제약까지 검사하지는 않는다.

Week B를 시작할 때 JPA를 "SQL을 대신 써주는 것"으로 알고 있었다. D3까지는 그 설명이 버텼는데 D4·D5에서 무너졌다. `findById()`가 SQL을 안 내보내고 `save()`를 안 불러도 `UPDATE`가 나간다면, JPA가 관리하는 건 SQL이 아니라 **트랜잭션 동안 객체가 어떤 상태에 있는가**다. SQL은 그 상태 관리의 결과로 나온다.

**아직 남은 것**은 두 가지다. `@Transactional`은 이번 주 내내 "주어진 래퍼"로만 썼고 경계를 어떻게 만드는지는 설명하지 못한다 — 처음부터 **Week C D1**에 배정해둔 범위다. 오류 응답에 오류 코드·타임스탬프·요청 식별자가 없는 문제는 여전히 **나중에 고칠 것**(Week D D5 또는 Week E D1)으로 남아 있다.

다음 시작점은 **Week C D1 — 트랜잭션 경계와 커밋·롤백**이다. 이번 주에 닫아둔 상자를 여는 첫 날이다.

면접에서 받으면 다시 정리해봐야 할 질문을 남긴다.

- 같은 트랜잭션에서 같은 `id`를 조회한 두 객체가 다른 인스턴스라면 어떤 버그가 가능해지는가
- 변경 감지가 있는데도 `save()`를 명시적으로 호출하는 코드가 흔한 이유는 무엇인가

<!-- 선택 복습 메모: 게시 화면에는 노출하지 않는다.
[직접 작성] 1차 캐시가 "값이 같아서"가 아니라 "같은 트랜잭션·같은 id라서" 같은 참조를 돌려준다는 것이 왜 중요한지, 그리고 같은 함정(증명하지 못하는 테스트)을 하루에 두 번 밟은 이유를 자기 문장으로 적는다.
-->

---

오늘 공부한 소스코드: [8week_Spring_Study/app](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/tree/master/app)
