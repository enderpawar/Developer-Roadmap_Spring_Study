# [Spring Study Day 10] ORM — Entity 매핑과 Spring Data JPA 기본 CRUD

Day9에 만든 `JdbcReservationRepository`는 `save()`에 INSERT만 있어서, 취소 흐름에서 기존 예약을 저장하면 새 행이 하나 더 생겼다. 오늘은 그 자리를 Spring Data JPA 구현으로 바꾸고 같은 흐름을 다시 검증했다. 영속성 컨텍스트와 flush 시점은 다음 Day의 주제라 여기서는 다루지 않고, Entity 매핑과 기본 CRUD까지만 본다.

> `Reservation`에 매핑 애노테이션을 붙이고, 기존 `ReservationRepository`를 구현하는 어댑터를 통해 Spring Data에 위임했다. Service와 Controller는 고치지 않았다. 통합 테스트 2개로 신규 저장·단건 조회와 기존 ID 갱신을 확인했고, 기존 ID를 저장했을 때 행이 늘지 않고 `UPDATE`가 나가는 것까지 봤다. 다만 실제 취소 요청이 몇 번의 SQL을 내는지는 측정하지 못했다.

> **오늘의 흐름** `JDBC 반복 제거 → Entity 매핑 → Repository 런타임 구현 → 어댑터 위임 → save() 분기 관찰 → 스키마 소유권 확인`
>
> 이전 Day: 순수 JDBC로 `JdbcReservationRepository`를 직접 구현하고 INSERT만 있는 `save()`의 한계를 확인 (Day9)
> 다음 Day: Persistence Context와 First-Level Cache로 같은 id 조회의 SELECT 횟수와 Entity 동일성을 확인 (Day11)

## 1. 개념 설명

### 1) JDBC 반복 코드와 계층 구분

> **ORM** = 객체와 관계형 DB 사이의 왕복 규칙을 애노테이션으로 선언하고, SQL 생성과 행→객체 변환을 구현체에 맡기는 방식

Day9의 JDBC 구현에서 `store.add()` 한 줄은 SQL 작성, 자원 개폐, 파라미터 바인딩, 키 회수, 예외 변환, 행→객체 매핑으로 흩어졌다. 그중 비즈니스 로직은 한 줄도 없었다.

```text
store.add() 호출
→ SQL 작성
→ 자원 개폐(Connection/PreparedStatement)
→ 파라미터 바인딩
→ 키 회수(생성된 id)
→ 예외 변환
→ 행 → 객체 매핑
```

더 큰 문제는 빠뜨린 코드였다. `save()`의 갱신 분기를 손으로 써야 했는데 쓰지 않았고, 테스트 10개가 통과한 채로 취소할 때마다 복제 행이 생겼다. 객체와 행 사이의 왕복을 매번 손으로 쓰면 이런 누락도 매번 생길 수 있다.

ORM은 이 왕복 규칙을 애노테이션으로 한 번 선언하고, SQL 생성과 행→객체 변환을 구현체에 맡기는 방식이다. 로드맵의 표현대로 JPA는 커넥션 풀을 없애는 것이 아니라 **직접 관리와 매핑을 추상화**한다. DB 왕복 자체는 그대로 일어난다.

세 이름(JPA·Hibernate·Spring Data JPA)은 같은 층이 아니다. JPA는 규칙만 정하고 실행하지 않는다. 그 규칙대로 SQL을 만들어 보내는 것은 Hibernate다. Spring Data JPA는 그 위에서 Repository 구현을 대신 만들어준다.

| 층 | 역할 | 패키지 |
|---|---|---|
| JPA | 명세 — 어떤 애노테이션이 무엇을 뜻하는가 | `jakarta.persistence` |
| Hibernate | 구현 — 명세를 읽고 SQL을 실행 | `org.hibernate` |
| Spring Data JPA | 편의층 — Repository 인터페이스의 구현 생성 | `org.springframework.data.jpa` |

Day9의 구조와 나란히 놓으면 이해가 쉽다. JDBC는 표준 인터페이스이고 H2 드라이버가 구현이었다. JPA와 Hibernate도 같은 명세↔구현 관계이고, 그 위에 편의층이 하나 더 얹혀 있다.

Day9 잔여 과제였던 `JdbcTemplate`/`JdbcClient`는 여기서 한 줄로 정리했다. 둘 다 반복 코드를 줄여주지만 SQL과 `RowMapper`는 여전히 개발자가 쓰는 "SQL은 내가 쓴다"의 세계다. JPA는 SQL 생성까지 구현체에 맡긴다는 점이 다르다.

> **보장 범위** — JDBC 반복 코드의 문제와 JPA·Hibernate·Spring Data JPA의 층 구분까지 확인했다. `JdbcTemplate`/`JdbcClient`와 JPA 두 도구를 코드로 비교하지는 않았다(미검증).

### 2) Entity 매핑 규칙과 IDENTITY 전략

> **@Entity** = 클래스를 JPA가 관리하는 영속 객체로 표시하는 애노테이션

`Reservation`에 `@Entity`, 필드의 `@Id`, `@GeneratedValue(strategy = GenerationType.IDENTITY)`를 붙였다. 이 상태로 새 예약을 저장하자 Hibernate가 다음 SQL을 보냈다.

```sql
insert into reservation (confirmed, requester_name, room_name, id) values (?, ?, ?, default)
```

`id` 자리에 값이 아니라 `default`가 들어간다. `IDENTITY`가 "번호는 DB가 정한다"는 뜻이라 값을 보내지 않고, 번호를 만드는 것은 Day8에 쓴 `AUTO_INCREMENT`다. `requesterName`이 `requester_name`으로 바뀐 것은 Day8에서 예상한 Hibernate 네이밍 전략이 실제로 동작한 결과다. 컬럼 순서도 소스의 필드 순서와 다르다.

조회 방향의 동작 순서는 다음과 같다.

```text
findById(id) 호출
→ Hibernate가 SELECT 실행
→ protected 기본 생성자로 빈 Reservation 생성
→ @Id가 필드에 있으므로 필드에 컬럼 값을 직접 채움
→ 채워진 객체 반환
```

Day9의 `mapRow`가 `new` → `assignId()` → `confirm()` 세 단계로 캡슐화된 생성자를 우회하던 일이 통째로 사라진 이유가 이것이다. 대신 `confirm()`·`cancel()`로만 상태를 바꾸게 해둔 규칙을 Hibernate는 지나가지 않는다는 뜻이기도 하다. 필드 접근은 도메인 메서드를 호출하지 않는다.

기본 생성자는 `protected`로 뒀다. Hibernate에는 객체를 만들 진입점을 주면서, 애플리케이션 코드가 방 이름도 신청자도 없는 예약을 `new Reservation()`으로 아무 데서나 만들지는 못하게 하는 선택이다. 이 생성자를 추가하면서 `final` 필드와 충돌한 과정은 3절 1)에 적었다.

> **보장 범위** — `insert`가 `default`로 id를 비워 보내고 `findById`가 필드에 값을 직접 채우는 것까지 로그로 확인했다. `@Entity`·`@Id`는 SQL을 쓰는 코드가 아니라 Hibernate가 SQL을 만들 때 읽는 규칙이며, 필드 접근은 생성자·메서드를 거치지 않는다는 것도 이 범위에서 확인했다. `IDENTITY`는 DB가 INSERT를 실행해야 번호를 알 수 있어 INSERT 시점을 미룰 수 없다는 성질이 있는데, 그 시점을 오늘 테스트로 따로 측정하지는 않았다(미검증).

### 3) 비어 있는 Repository 인터페이스의 런타임 구현

> **Repository Proxy** = 개발자가 구현체를 작성하지 않아도 Spring Data가 인터페이스를 스캔해 런타임에 만들어 Bean으로 등록하는 구현 객체

오늘 추가한 파일 중 하나는 본문이 비어 있다. 우리 코드에서는 `SpringDataReservationRepository`가 이 상태 그대로 테스트에서 호출된다.

```java
public interface SpringDataReservationRepository extends JpaRepository<Reservation, Long> {
}
```

이 인터페이스에는 `save`도 `findById`도 없는데 테스트는 그 메서드들을 호출한다. 구현을 소스에서 찾으려고 하면 못 찾는다.

```text
애플리케이션 기동
→ Spring Data가 JpaRepository를 상속한 인터페이스를 스캔
→ 로그: Found 1 JPA repository interface.
→ 인터페이스의 구현 객체를 만들어 Bean으로 등록
→ JpaReservationRepository의 생성자에 주입
```

`JpaRepository<Reservation, Long>`의 두 타입 인자는 어떤 Entity를, 어떤 타입의 id로 다루는지를 알려준다.

![클래스 다이어그램. ReservationService가 «interface» ReservationRepository를 생성자 주입으로 참조하고, «@Repository» JpaReservationRepository가 그 인터페이스를 «realize»한다. JpaReservationRepository는 delegate 필드로 «interface» SpringDataReservationRepository를 주입받고, 그 인터페이스는 오퍼레이션 칸이 비어 있는 채로 JpaRepository<Reservation, Long>를 상속한다. 왼쪽 아래의 «@Entity» Reservation은 roomName·requesterName·confirmed·id가 모두 private이고 id에 «@Id, IDENTITY»가 붙어 있으며, 생성자 Reservation()은 protected다. 노트는 Spring Data가 기동 시 이 인터페이스를 찾아 구현 객체를 만들어 Bean으로 등록한다는 것과 기동 로그의 "Found 1 JPA repository interface."를 가리킨다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day10-jpa-adapter.png)

> **보장 범위** — 기동 로그의 "Found 1 JPA repository interface."와 테스트 통과로 구현체가 등록되고 동작한다는 것까지 확인했다. 구현 객체의 내부 구조는 오늘 열어보지 않았다(미검증).

### 4) 어댑터와 의존성 방향

> **Adapter** = 기존 인터페이스를 구현하면서 실제 처리는 다른 구현체에 위임하는 객체

Spring Data 인터페이스를 `ReservationService`에 바로 주입할 수도 있었다. 하지만 그러면 Service가 `JpaRepository`라는 저장 기술의 타입을 알게 된다.

그래서 기존 `ReservationRepository`를 구현하는 `JpaReservationRepository`를 두고, 그 안에서 `save`·`findById`·`findAll`을 한 줄씩 Spring Data에 위임했다.

```java
@Repository
public class JpaReservationRepository implements ReservationRepository {
    private final SpringDataReservationRepository delegate;

    @Override
    public Reservation save(Reservation reservation) {
        return delegate.save(reservation); // 실제 저장은 Spring Data 구현체가 수행
    }
}
```

```text
ReservationService 호출
→ ReservationRepository(인터페이스)
→ JpaReservationRepository(어댑터)
→ SpringDataReservationRepository(delegate)
→ Hibernate
```

Service는 여전히 Day4에 뽑은 인터페이스만 의존한다. 상위 계층이 추상에 의존하고 저장 기술이 그 추상을 구현하는 DIP 구조다.

| 교체 전후 | Service가 의존하는 타입 | 실행되는 구현 |
|---|---|---|
| Day9 | `ReservationRepository` | `JdbcReservationRepository` |
| Day10 | `ReservationRepository` | `JpaReservationRepository` → Spring Data 구현 |

실제로 `ReservationService`와 `ReservationController`는 이번 Day에 한 글자도 바뀌지 않았다. 대신 `JdbcReservationRepository`에서 `@Repository`를 뗐다. 같은 타입의 Bean이 둘이면 생성자 주입이 고를 근거가 없어 기동이 실패한다는 것을 Day9의 `NoUniqueBeanDefinitionException`으로 이미 겪었다. JDBC 코드는 대조군으로 파일에 남겼지만 실행 경로에서는 빠졌다.

> **보장 범위** — `ReservationService`·`ReservationController`가 이번 Day에 수정되지 않았다는 것과, `JdbcReservationRepository`에서 `@Repository`를 떼 Bean 후보에서 뺀 것까지 확인했다. 저장 기술을 바꿔도 Service가 그대로인 것은 Service가 구체 클래스가 아니라 `ReservationRepository` 인터페이스에 의존하기 때문이다.

### 5) save()의 신규·갱신 분기와 SQL 관찰

> **Persist/Merge Dispatch** = Spring Data JPA의 `save()`가 식별자 유무로 새 Entity(INSERT)와 기존 Entity(UPDATE)를 갈라 처리하는 방식

Day9의 결함은 `save()`에 갱신 분기가 없던 것이었다. Spring Data의 `save()`는 이 분기를 스스로 수행한다. 오늘 관찰한 범위는 다음과 같다.

```text
save(reservation) 호출
→ id 없음 → insert ... values (?, ?, ?, default)
→ id 있음 → update reservation set ... where id=?
```

| 호출 | 관찰한 SQL |
|---|---|
| `save()` — id 없는 새 예약 | `insert ... values (?, ?, ?, default)` |
| `findById(id)` | `select ... where r1_0.id=?` |
| `save()` — id 있는 기존 예약 | `update reservation set confirmed=?, requester_name=?, room_name=? where id=?` |

기존 예약을 취소하고 저장하면 어떤 SQL이 나가는지 미리 답했다. 내 예측은 `SELECT` 1개와 `UPDATE` 1개였고, 근거는 "`findById`로 꺼낸 객체는 `id`가 이미 있으므로 신규가 아니다"였다. 판정은 정답이었고 실제 로그에도 `UPDATE`가 있었다.

그런데 테스트 로그의 `update` 앞에는 `SELECT`가 없었다. 내가 예측한 SELECT는 `findById`가 낼 것으로 본 것인데, 테스트는 방금 저장한 객체를 그대로 다시 저장하므로 조회 단계가 없다. 답은 맞았지만 근거가 되는 실행 경로는 내가 생각한 것과 달랐다.

실제 취소 흐름은 `findById` → `cancel()` → `save()`이고, 이 시점 `ReservationService`에는 `@Transactional`이 없다. 테스트 클래스에는 붙어 있으니 두 경우의 조건이 같지 않다.

> **보장 범위** — id 없는 저장에서 INSERT, id 있는 저장에서 UPDATE가 나간다는 것과, 테스트의 `update` 앞에 SELECT가 없다는 것은 로그로 확인했다. 실제 취소 흐름에서 `@Transactional` 유무가 SQL 횟수를 바꾸는지는 측정하지 않았다(미검증).

### 6) 스키마의 주인 — Flyway와 ddl-auto: none

> **ddl-auto: none** = Hibernate가 DDL(테이블 생성·변경)을 실행하지 못하게 막아 스키마의 주인을 다른 도구에 고정하는 설정

`spring.jpa.hibernate.ddl-auto: none`으로 두어 Hibernate가 테이블을 만들거나 고치지 못하게 했다. 스키마의 주인은 Day8에 만든 Flyway `V1__init.sql`이다.

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: none # Hibernate는 스키마를 건드리지 않는다. 주인은 Flyway.
```

```text
통합 테스트 기동
→ Flyway가 V1__init.sql 적용, 스키마 version 1
→ Hibernate EntityManagerFactory 초기화
→ Hibernate가 DDL을 생성했다는 로그 없음
```

DB는 `jdbc:h2:file:./data/studyroom`처럼 파일에 남고, Flyway 장부까지 찍혀 있다. 여기서 Hibernate가 테이블을 새로 만들면 Flyway 장부에 없는 변경이 생기는 "두 주인" 문제가 된다.

| 도구 | 현재 역할 |
|---|---|
| Flyway | 스키마 생성·변경과 버전 이력 |
| JPA/Hibernate | 이미 있는 테이블과 Entity 사이의 데이터 왕복 |
| Spring Data JPA | Repository 구현 생성 |

`none`은 Entity와 스키마가 어긋나도 기동에서 잡아주지 않는다는 한계가 있다. Hibernate가 변경은 하지 않고 일치 여부만 검사하는 `validate`로 올리는 일은 Week B D7에 배정했다.

> **보장 범위** — Flyway가 `V1__init.sql`을 적용하고 Hibernate가 DDL을 생성했다는 로그가 없다는 것까지 확인했다. `none`이 Entity-스키마 불일치를 실제로 잡아주지 않는지는 불일치를 재현해 확인하지 않았다(미검증).

### 7) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| ORM | 객체와 관계형 DB의 왕복 규칙을 선언적으로 정의하고 SQL 생성·매핑을 구현체에 맡기는 방식 |
| Entity | 식별자를 가지고 영속 계층이 관리하는 도메인 객체 |
| Identifier Generation Strategy | 식별자 값을 누가·언제 만드는지 정하는 규칙(예: DB 자동 증가에 위임) |
| Repository | 영속 계층 접근을 하나의 인터페이스로 추상화한 경계 |
| Adapter | 기존 인터페이스를 구현하면서 실제 처리는 다른 구현체에 위임하는 구조 |
| Schema Migration | 스키마의 생성·변경 이력을 버전으로 관리하는 절차 |

> **더 볼 것**
> - [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/reference/): 비어 있는 인터페이스가 구현으로 바뀌는 지점
> - [Hibernate ORM User Guide](https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html): 매핑 애노테이션이 SQL로 번역되는 규칙
> - 아직 안 본 것 — 영속성 컨텍스트, 1차 캐시, 변경 감지

## 2. 코드 구현

### 1) `Reservation` — 애노테이션 세 개와 기본 생성자

```java
@Entity
public class Reservation {
    private String roomName;
    private String requesterName;
    private boolean confirmed;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    protected Reservation() {
    }
}
```

**한 줄씩 보기**
- `@Entity` — 이 클래스를 JPA가 관리하는 영속 객체로 등록한다.
- `@Id` + `@GeneratedValue(IDENTITY)` — 식별자를 DB의 `AUTO_INCREMENT`에 위임한다.
- `protected Reservation()` — Hibernate가 조회 결과로 객체를 만들 때 쓰는 진입점이며, 애플리케이션 코드가 임의로 빈 예약을 만드는 것은 막는다.

도메인 생성자 `Reservation(roomName, requesterName)`과 `confirm()`·`cancel()`은 그대로 두고, 두 문자열 필드의 `final`을 떼고 JPA용 기본 생성자를 추가했다.

### 2) 어댑터 — 구현만 교체하고 Service는 유지

```java
@Repository
public class JpaReservationRepository implements ReservationRepository {
    private final SpringDataReservationRepository delegate;

    @Override
    public Reservation save(Reservation reservation) {
        return delegate.save(reservation);
    }
    // findById, findAll도 한 줄 위임
}
```

어댑터 하나를 두는 값으로 Day4에 정한 경계를 유지했다. `build.gradle.kts`에는 `spring-boot-starter-data-jpa`를 추가하되 `starter-jdbc`는 Day9 대조군을 위해 남겼다.

### 3) 기존 ID 저장의 중복 행 검증

`savingExistingReservationUpdatesWithoutAddingDuplicate()`는 새 예약을 저장·`flush()`한 뒤 같은 객체를 `cancel()`해 다시 저장·`flush()`하고, `clear()` 후 `findAll()`로 결과를 확인한다. 저장 전보다 전체 행 수가 한 건만 늘었는지, 같은 id의 행이 하나인지, 그 행의 `confirmed`가 `false`인지를 검사한다.

두 테스트 모두 `save()` 뒤에 `EntityManager.flush()`를 명시했다. `save()`를 호출한 줄과 SQL이 실제로 나가는 시점이 다를 수 있는데, 오늘은 그 이유를 설명할 수 없어서 검증 지점을 고정하려고 SQL을 강제로 밀어냈다. `clear()`도 함께 썼다. 비운 뒤 다시 조회하면 방금 저장한 객체가 아니라 DB에서 새로 읽은 값으로 검사하게 된다. 왜 이렇게 해야 하는지, 같은 id를 두 번 조회하면 같은 객체가 되는지는 Day11 영속성 컨텍스트의 주제다.

Day9 JDBC 구현은 같은 흐름에서 무조건 INSERT하여 복제 행을 만들었다. JPA 어댑터에서는 두 번 저장해도 행이 한 건만 늘었고 기존 id의 상태가 갱신됐다.

### 4) 자동 검증 결과

| 확인한 것 | 방법 | 결과 |
|---|---|---|
| 신규 저장과 단건 조회 | 자동 — `savesAndFindsReservationThroughJpaAdapter()` | `id` 생성, `insert` 뒤 `select ... where r1_0.id=?` |
| 기존 ID 저장 시 중복 행 | 자동 — 저장 전후 행 수와 같은 `id` 행 개수 비교 | 행 수 +1, 같은 `id` 한 행, `confirmed=false` |
| 실제 취소 요청의 SQL 횟수 | **미검증** — 테스트 안에서만 관찰 | 1절 `save()` 소절 |

전체 `./gradlew clean test`는 12개 통과한다. 코드는 [ff795f9](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/ff795f9214a74ab37391c80f4a2bc6216c3d6e2a), 그림은 [3b398d1](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/3b398d1a50cc78412b44ec5016756705ac8bc83c)에 있다.

## 3. 스스로 답한 질문

### 1) `final` 필드와 빈 기본 생성자의 컴파일 조건

**질문.** `roomName`, `requesterName`이 `final`인 상태에서 인자 없는 `protected Reservation() {}`를 추가하면 컴파일되는가?

**A1.** 처음에는 "된다"라고 답했다. 두 문자열 필드가 기존 생성자에서 초기화되고 있으니 문제될 게 없다고 봤는데, 컴파일이 안 됐다.

```text
Reservation.java:13: error: variable roomName might not have been initialized
```

`final` 인스턴스 필드는 생성자 하나가 아니라 **모든 생성자 경로에서** 초기화되어야 한다. 인자 없는 생성자에는 방 이름과 신청자 이름을 넣을 방법이 없으니 그 경로가 비고, 컴파일러가 거기서 막는다. 내가 본 건 기존 생성자 하나였고 컴파일러가 본 건 경로 전체였다.

두 필드의 `final`을 떼고 기본 생성자를 `protected`로 뒀다. 이번 주에 `final`로 틀린 게 처음이 아니다. Day8에는 필드가 `final`이면 DB 값이 안 바뀔 거라고 봤다가 `UPDATE`가 그냥 실행되는 걸 봤다. `final`은 자바 변수의 재대입을 컴파일 시점에 막는 장치일 뿐, 그 바깥에는 권한이 없다.

### 2) `@Transactional` 테스트의 전체 실행 실패

**질문.** 단독으로 돌릴 때는 통과하던 테스트가 `clean test` 전체 실행에서만 실패한 이유는 무엇인가?

**A2.** 처음 쓴 단언이 `assertEquals(1, reservations.size())`였다. 내 테스트가 한 건 저장했으니 전체가 한 건이라고 본 것이다.

원인은 다른 테스트가 남긴 행이었다. `ReservationControllerHttpTest`의 예약 성공 테스트에는 `@Transactional`이 없어서 `POST /reservations`로 만든 예약이 커밋된다. 테스트용 DB는 `build.gradle.kts`에 `jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1`로 고정해둔 상태라 JVM이 살아 있는 동안 유지되고, 그 행이 내 `findAll()`에 같이 잡혔다.

여기서 깨진 건 `@Transactional`에 대한 오해다. 롤백되는 것은 그 테스트가 쓴 것뿐이고, 남이 이미 커밋한 행은 지워주지 않는다. 절대값 단언은 사실 실행 순서에 기대고 있었다.

```java
int countBeforeSave = repository.findAll().size();
assertEquals(countBeforeSave + 1, reservations.size());
```

`+1`은 앞에 몇 건이 있든 성립한다. 애초에 확인하려던 것도 "전체가 한 건"이 아니라 "두 번 저장했는데 행은 한 개만 늘었다"였다. 기능 결함이 아니라 테스트 격리 가정의 결함이었고, 단언이 의도에 맞게 좁혀졌다.

## 4. 학습 정리와 다음 범위

### 1) 전체 흐름 다시 보기

Repository 호출 한 번이 Spring Data JPA → JPA(명세) → Hibernate(구현) → JDBC를 차례로 지나 DB에 닿는 전체 층 구조를 한 장으로 보면 다음과 같다.

![계층도. 맨 위 Application에서 두 경로가 내려온다. 초록 화살표 "Repository 사용"은 Spring Data JPA(Repository) 층으로, 빨간 화살표 "Raw JPA 사용(e.g. EntityManager 사용)"은 그 아래 JPA 층으로 바로 들어간다. Spring Data JPA와 JPA는 초록 테두리로 함께 묶여 있고, JPA 아래에 Hibernate, 그 아래에 JDBC가 쌓이며, JDBC가 맨 아래 Relational Database와 양방향으로 연결된다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day10-overview-jpa-stack.png)

*출처: [JPA, Hibernate, 그리고 Spring Data JPA의 차이점](https://suhwan.dev/2019/02/24/jpa-vs-hibernate-vs-spring-data-jpa/) — suhwan.dev. 저작권은 원저작자에게 있습니다.*

### 2) 이해의 변화와 남은 것

JPA로 바꾼 결과를 "코드가 줄었다"로 정리하려다 말았다. 줄어든 건 SQL 작성과 행-객체 변환 같은 JDBC 반복 코드이고, `insert`·`select`·`update`는 로그에 그대로 찍힌다. DB 작업이 사라진 게 아니라 그 일을 하는 주체가 Hibernate로 바뀐 것이다.

테스트에 대한 생각도 한 번 더 고쳐졌다. Day9에는 초록불 열 개를 켜놓고 중복 행 버그를 안고 있었고, 오늘은 통과하던 테스트가 실행 순서 때문에 깨졌다. 무엇을 검사하느냐만큼 그 검사가 무엇을 가정하느냐도 봐야 했다.

**아직 남은 것**은 두 가지다. `JdbcReservationRepository`의 중복 행 결함은 코드에 그대로 있지만 Bean 후보에서 빠져 실행되지 않으므로 **고치지 않을 것**(대조군 보존)으로 둔다. `ddl-auto`는 아직 `none`이라 Entity와 스키마가 어긋나도 기동에서 걸리지 않는데, 이건 **나중에 고칠 것**으로 Week B D7에 `validate` 전환을 배정했다. 다음 Day는 Day11 영속성 컨텍스트와 1차 캐시다.

면접에서 다시 답해볼 항목을 남긴다.

- Repository 구현을 JDBC에서 JPA로 바꿨을 때 Service가 수정되지 않은 이유의 의존성 방향 설명
- `@Transactional`이 붙은 테스트에서도 격리가 깨지는 조건과 그 결함이 드러나는 실행 순서

---

오늘 공부한 소스코드: `app/src/main/java/com/example/studyroom/domain/Reservation.java`, `app/src/main/java/com/example/studyroom/repository/JpaReservationRepository.java`, `app/src/main/java/com/example/studyroom/repository/SpringDataReservationRepository.java`, `app/src/test/java/com/example/studyroom/repository/JpaReservationRepositoryTest.java`
