# [Spring Study Day 19] 연관 Entity 조회 전략 — N+1 Problem과 Fetch Join

Day18에서 `Reservation.member`를 `FetchType.LAZY`로 매핑하고, 프록시가 `getName()` 시점에 SELECT를 한 번 보내는 것을 확인했다. 오늘은 그 한 번이 **목록을 순회할 때 건수만큼 반복되는지** 실제 SQL 로그로 세어 보고, JPQL `join fetch`로 같은 목록을 한 번의 SQL로 가져오도록 바꿨다. 컬렉션(`@OneToMany`) fetch join과 페이징은 이 글의 범위가 아니다.

> Member 3명과 Reservation 3건을 저장하고 1차 캐시를 비운 뒤, `findAll()` 결과를 순회하며 `getMember().getName()`을 호출했다. `findAll()` 구간에는 member SELECT가 없었고, 순회 구간에서 3번이 나가 총 4번(1+N)이었다. `join fetch` 쿼리 메서드로 바꾸자 JOIN SQL 1번으로 끝났고 순회 구간에는 SQL이 없었다. 두 결과는 `NPlusOneTest`의 `show-sql` 로그로 확인했다.

> **오늘의 흐름** `LAZY 목록 조회 → 순회 중 건마다 member SELECT(1+N) → @Query join fetch → JOIN SQL 1번`
>
> 이전 Day: Association Mapping과 Hibernate Proxy의 초기화 시점 (Day18)
> 다음 Day: Day19 빈칸예제·독립 변형 이어서, 이후 누적시험 A+B+C (Week C D6)

![시퀀스 다이어그램. 위쪽 프레임은 findAll()이다. 테스트가 ReservationRepository.findAll()을 호출하면 DB에 reservation 테이블 SELECT가 1번 나가고, member는 미초기화 Proxy 3개로 채워진 List가 반환된다. 이어지는 loop [3건] 안에서 getMember().getName()을 호출할 때마다 member 테이블 SELECT가 1번씩 나가 총 SQL은 1+3=4번이다. 아래쪽 프레임은 findAllWithMember()다. 호출 시 reservation과 member를 JOIN한 SQL 1번으로 member까지 초기화된 List가 반환되고, loop 안의 getName() 호출에서는 SQL이 나가지 않아 총 SQL은 1번이다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day19-nplusone-vs-fetch-join.png)

## 1. 개념 설명

### 1) N+1 Problem의 발생 구조

> **N+1 Problem** = 목록 조회 SQL 1번 뒤에, 목록의 각 항목에서 LAZY 연관 필드를 초기화하느라 SQL이 N번 더 나가는 현상

우리 코드에서는 `NPlusOneTest.findAllTriggersNPlusOneSelects()`가 이 상황을 그대로 만든다.

```java
List<Reservation> reservations = reservationRepository.findAll(); // reservation SELECT 1번
for (Reservation r : reservations) {
    System.out.println(r.getMember().getName());  // 여기서 건마다 member SELECT
}
```

Day18의 메커니즘이 목록에 그대로 곱해진 결과다. `findAll()`은 `reservation` 테이블만 읽는다. 결과 행에는 `member_id` 값이 들어 있지만, `member` 필드는 그 id만 가진 미초기화 프록시로 채워진다.

```text
findAll() → select … from reservation (1번)
→ Reservation 3개 생성, 각 member = id만 가진 Proxy
→ 순회 1회차 getName() → 프록시 초기화 → select … from member where id=?
→ 2회차, 3회차도 서로 다른 id라 각각 SELECT
→ 총 1 + 3 = 4번
```

실제 로그에서 `findAll()` 호출 구간과 순회 구간 사이에 경계 문자열을 찍어 SELECT 위치를 갈랐다. `findAll()` 구간에는 `reservation` SELECT만 있었고, `member` SELECT 3개는 전부 순회 구간에서 `진우`·`철수`·`영희` 출력 직전에 한 번씩 나왔다.

같은 패턴은 방향이 반대인 연관관계에서도 나타난다. 아래는 다른 글의 로그로, `Member` 목록을 순회하며 `@OneToMany` 컬렉션 `orders`의 크기를 출력한 결과다. `where orders0_.member_id=?` SELECT가 회원 수만큼 똑같이 반복된다.

![Hibernate SQL 로그 캡처. select … from orders orders0_ where orders0_.member_id=? 쿼리와 order size: 10 출력이 회원마다 한 번씩 계속 반복된다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day19-web-nplusone-log.png)

*출처: [JPA N+1 발생원인과 해결 방법](https://www.popit.kr/jpa-n1-%EB%B0%9C%EC%83%9D%EC%9B%90%EC%9D%B8%EA%B3%BC-%ED%95%B4%EA%B2%B0-%EB%B0%A9%EB%B2%95/) — Yun(cheese10yun), Popit*

우리 코드는 `@ManyToOne` 방향이라 연관 필드가 컬렉션이 아니라 단일 프록시지만, "목록 1번 조회 뒤 건마다 같은 모양의 SELECT가 반복된다"는 구조는 같다.

N+1은 SQL 하나하나가 느린 문제가 아니라 **애플리케이션과 DB 사이 왕복(round trip) 횟수**의 문제다. 각 SELECT가 PK 인덱스를 타더라도, 루프 안에서 원격 호출을 반복하는 구조라 왕복의 고정 비용이 건수만큼 쌓인다. 오늘은 3건이라 4번이었고, 건수에 비례해 늘어나는 구조라는 것까지가 관찰 범위다(응답 시간은 측정하지 않았다).

### 2) Fetch Join의 동작

> **Fetch Join** = JPQL의 `join fetch`로 연관 Entity를 같은 SQL의 JOIN으로 함께 읽어, 조회 시점에 연관 필드까지 초기화하는 방식

우리 코드에서는 Spring Data 인터페이스에 JPQL을 직접 지정했다.

```java
@Query("select r from Reservation r join  fetch r.member")
List<Reservation> findAllWithMember();
```

일반 `join`은 조건 필터에만 쓰이고 SELECT 절에는 `r`만 남는다. `fetch`가 붙으면 Hibernate가 `member`의 컬럼까지 SELECT 절에 넣고, 그 값으로 `member` 필드를 프록시가 아닌 초기화된 객체로 채운다. 실제 SQL은 다음과 같았다.

```text
select r1_0.id, …, m1_0.id, m1_0.name, …
from reservation r1_0
join member m1_0 on m1_0.id=r1_0.member_id
```

```text
findAllWithMember() → reservation + member JOIN SQL 1번
→ 각 행에서 Reservation과 Member를 함께 생성
→ member 필드 = 초기화 완료
→ 순회 중 getName() → 이미 채워진 값 반환, SQL 없음
```

| 구분 | `findAll()` | `findAllWithMember()` |
|---|---|---|
| SQL 개수(3건) | 1 + 3 = 4 | 1 |
| member 초기화 시점 | 순회 중 `getName()` 호출 시 | 조회 시점 |
| 적용 범위 | 매핑의 `FetchType.LAZY` 그대로 | 이 쿼리 한 개에만 적용 |

fetch join은 매핑을 바꾸지 않는다. `Reservation.member`는 여전히 `FetchType.LAZY`이고, 같은 코드베이스에서 `findAll()`은 계속 N+1을 냈다. 기본은 지연으로 두고, 연관 데이터가 확실히 필요한 조회 경로에서만 쿼리 단위로 당겨오는 구조다.

주의할 점은 로그의 `join`이 **inner join**이라는 것이다. 관계대수의 내부 조인처럼 `member_id`가 `null`인 예약은 결과에서 빠진다. 아래 그림에서 A를 `reservation`, B를 `member`로 놓으면, `join fetch`가 돌려주는 범위는 첫 번째 그림의 교집합이다.

![두 원 A와 B의 교집합만 칠해진 벤 다이어그램. A Inner Join B는 양쪽에 짝이 있는 행만 결과에 남긴다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day19-web-inner-join.png)

*출처: [File:SQL Join - 07 A Inner Join B.svg](https://commons.wikimedia.org/wiki/File:SQL_Join_-_07_A_Inner_Join_B.svg) — GermanX, Wikimedia Commons, CC BY-SA 4.0*

![원 A 전체와 교집합이 칠해진 벤 다이어그램. A Left Join B는 B에 짝이 없는 A의 행도 결과에 남긴다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day19-web-left-join.png)

*출처: [File:SQL Join - 01 A Left Join B.svg](https://commons.wikimedia.org/wiki/File:SQL_Join_-_01_A_Left_Join_B.svg) — GermanX, Wikimedia Commons, CC BY-SA 4.0*

지금 HTTP로 만든 예약은 member 없이 저장되므로 A에만 속한 영역에 있다. `findAll()`을 이 메서드로 그대로 바꾸면 그 행들이 목록에서 사라지고, 남기려면 두 번째 그림 범위인 `left join fetch`가 필요하다. 이건 SQL 형태에서 도출한 결론이고 테스트로는 확인하지 않았다. 해결 수단(`@EntityGraph`, batch fetch size 등)이 fetch join만 있는 것도 아니지만, 오늘 비교한 것은 LAZY 그대로와 fetch join 두 가지다.

### 3) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| N+1 Problem | 목록 조회 1번 뒤 항목마다 연관 조회가 N번 더 나가는 현상 |
| Fetch Join | `join fetch`로 연관 Entity를 한 SQL에서 함께 읽어 조회 시점에 초기화하는 방식 |
| JPQL | 테이블이 아니라 Entity와 필드 이름으로 쓰는 JPA 쿼리 언어 |
| Lazy Loading | 연관 필드를 실제 접근 시점까지 프록시로 두고 SELECT를 미루는 전략 |
| Proxy Initialization | 프록시가 실제 값을 채우기 위해 SELECT를 실행하는 순간 |

> **더 볼 것**
> - [Hibernate ORM 6.6 User Guide — Fetching](https://docs.hibernate.org/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#fetching): 매핑 수준 fetch 전략과 쿼리 수준 fetch(JOIN FETCH, entity graph)의 구분
> - [Spring Data JPA Reference — Using @Query](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html#jpa.query-methods.at-query): 쿼리 메서드에 JPQL을 직접 지정하는 방법
> - [N+1 query problem with JPA and Hibernate — Vlad Mihalcea](https://vladmihalcea.com/n-plus-1-query-problem/): `@ManyToOne` LAZY/EAGER 각각에서 N+1이 생기는 경로

## 2. 코드 구현

### 1) Port 인터페이스 확장과 default 메서드

`Service`가 의존하는 것은 Spring Data 인터페이스가 아니라 우리가 정의한 `ReservationRepository`(port)이고, `JpaReservationRepository`가 Spring Data를 감싸 위임한다. 새 쿼리를 쓰려면 세 파일이 같이 움직여야 했다.

```java
// ReservationRepository.java (port)
default List<Reservation> findAllWithMember() {
    throw new UnsupportedOperationException();
}

// JpaReservationRepository.java (유일한 @Repository 구현체)
@Override
public List<Reservation> findAllWithMember() {
    return delegate.findAllWithMember();   // SpringDataReservationRepository의 @Query 메서드
}
```

**한 줄씩 보기**

- `@Query(...)`(`SpringDataReservationRepository`): 메서드 이름 파생 대신 이 JPQL을 실행. 애노테이션은 메서드 위에 붙는다.
- `default … throw new UnsupportedOperationException()`: 구현하지 않은 구현체가 호출되면 즉시 예외.
- `delegate.findAllWithMember()`: JPA 구현체만 실제 fetch join 쿼리로 연결.

처음에는 port에 추상 메서드로 추가했다. 그러자 컴파일이 깨졌다.

```text
InMemoryReservationRepository is not abstract and does not override abstract method findAllWithMember() in ReservationRepository
JdbcReservationRepository is not abstract and does not override abstract method findAllWithMember() in ReservationRepository
```

두 클래스는 Week B에서 JPA 구현체로 교체된 뒤 대조군으로만 남은 구현체이고, `@Repository`가 없어 Spring Bean도 아니다. 그래도 인터페이스를 구현하는 이상 컴파일러에게는 똑같은 구현체다. `default` 메서드로 바꿔 두 클래스를 건드리지 않고 컴파일을 유지했다.

몸통은 `return findAll();`로 조용히 대체하는 방법과 즉시 예외를 던지는 방법 중 후자를 골랐다. 대체 방식은 누군가 메모리 구현체를 다시 연결했을 때 "JOIN 없이 동작하는 결과"를 돌려줘 문제를 숨긴다. 예외 방식은 첫 호출에서 오용을 드러낸다(fail fast). 이 판단에 이르기까지의 과정은 3절 2)에 적었다.

### 2) 겪은 오류와 자동 검증 결과

코드 오류는 두 개가 더 있었다. `@Query`를 메서드가 아니라 인터페이스 선언 위에 붙였고, 반환 타입 자리에 `default <Reservation> findAllWithMember()`처럼 제네릭 타입 파라미터 선언 문법을 썼다. 쓰려던 반환 타입은 `List<Reservation>`이었다.

전체 테스트를 처음 돌렸을 때는 `NPlusOneTest`가 `NullPointerException`으로 실패했다. 테스트 DB가 프로세스 내내 유지되는 인메모리 H2(`DB_CLOSE_DELAY=-1`)인데, `ReservationControllerHttpTest`에만 `@Transactional`이 없어 MockMvc로 만든 member 없는 예약이 커밋된 채 남았다. `findAll()`이 그 행까지 읽어 `getMember()`가 `null`이었다. 테스트 코드에서 해당 행을 걸러내는 대신, 오염의 원인인 `ReservationControllerHttpTest`에 `@Transactional`을 붙여 롤백되게 했다.

| 검증 항목 | 방법 | 결과 |
|---|---|---|
| N+1 재현 | `findAllTriggersNPlusOneSelects()` + `show-sql` | `findAll()` 구간 0번, 순회 구간 3번, 총 4번 |
| fetch join | `findAllWithMemberUsesSingleJoinQuery()` + `show-sql` | 호출 구간 JOIN 1번, 순회 구간 0번 |
| 회귀 | `./gradlew clean test` | BUILD SUCCESSFUL, 25/25 |

두 테스트는 SQL 개수를 assert하지 않는다. 판정 근거는 테스트 통과가 아니라 로그를 사람이 센 결과다.

커밋: [ee61c80](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/ee61c8040300d21fb4926b47074ac7e91a8f4dd1)

## 3. 스스로 답한 질문

### 1) `findAll()` 구간과 순회 구간의 member SELECT 분포

**질문.** `findAll()` 구간, 순회 구간, 전체에서 member 관련 SELECT는 각각 몇 번 나가는가?

**A1.** 처음에는 "3 4 7"이라고 답했다. `findAll()` 구간에서 이미 member SELECT가 3번 나가고 순회에서 또 4번 나간다는 예측이다. LAZY가 무엇을 미루는지 힌트를 받은 뒤에도 "잘 모르겠다"였다.

실제는 0 / 3 / 4(reservation 1번 포함)였다. `findAll()`이 가져오는 건 `member_id` 값뿐이고, 그 값은 프록시의 식별자로만 쓰인다. SELECT는 Day18에서 본 그대로 `getName()`으로 실제 값을 요구하는 순간에 나간다. 그 규칙이 목록의 건마다 한 번씩 적용된 것이 N+1이다. 이후 fetch join 예측에서는 "1번 찍히고 0번"으로 호출 구간과 순회 구간을 나눠 맞혔다.

### 2) default 메서드 몸통의 선택 기준

**질문.** 대조군 구현체를 위한 `default` 몸통은 `findAll()`로 대체해야 하는가, 예외를 던져야 하는가?

**A2.** 처음 근거는 "실제로 호출되지 않는 대조군 코드라 결과를 동작시킬 필요가 없다"였다. 결론(예외)은 맞았지만, 근거가 지금 호출되지 않는다는 현재 상태에만 머물러 있었다.

교정 후 근거는 미래의 오용을 기준으로 삼았다. 나중에 메모리 구현체가 실수로 다시 연결되면, 대체 방식은 JOIN 없는 결과를 조용히 돌려줘 아무도 눈치채지 못하고, 예외 방식은 첫 호출에서 바로 드러난다. "지금 안 불린다"는 어느 몸통이든 통하는 근거라 선택 기준이 되지 못한다.

## 4. 학습 정리와 다음 범위

### 1) 이해의 변화와 남은 것

Day18에서는 프록시 초기화를 한 건의 사건으로 봤다. 오늘 그 사건이 목록 순회 안에서 건수만큼 반복되는 것이 N+1이라는 걸 로그로 셌고, fetch join은 매핑을 바꾸는 게 아니라 한 쿼리에서 초기화 시점을 조회 시점으로 당기는 장치라는 것을 `findAll()`과의 대조로 확인했다.

**아직 남은 것**은 두 가지다. ① `findAllWithMember()`는 inner join이라 member 없는 예약을 결과에서 뺀다. 지금은 테스트에서만 호출하지만, 서비스의 목록 조회를 이 메서드로 바꾸려면 `left join fetch` 여부부터 정해야 한다 — **나중에 고칠 것(Week C D7 버퍼)**. ② 컬렉션 방향(`Member.reservations`) fetch join은 Day18의 `mappedBy` 필드가 아직 없어 다루지 못했다 — **나중에 고칠 것(Week C D7 버퍼, Day18 이월분과 함께)**.

면접에서 다시 답해볼 항목을 남긴다.

- fetch join, `@EntityGraph`, batch fetch size 중 하나를 고르는 기준
- 컬렉션 fetch join과 페이징을 함께 쓸 때 생기는 제약

---

오늘 공부한 소스코드: `app/src/test/java/com/example/studyroom/repository/NPlusOneTest.java`, `app/src/main/java/com/example/studyroom/repository/SpringDataReservationRepository.java`, `app/src/main/java/com/example/studyroom/repository/ReservationRepository.java`, `app/src/main/java/com/example/studyroom/repository/JpaReservationRepository.java`
