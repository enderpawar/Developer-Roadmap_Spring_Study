# [Spring Study Day 18] Association Mapping과 Lazy Loading — Hibernate Proxy의 초기화 시점

Day17까지 `Reservation`은 예약자를 `String requesterName`으로 저장했다. 회원 정보를 확장할 방법이 없고, 이름이 곧 식별자 역할을 겸하고 있었다. 오늘은 `Member`라는 별도 Entity를 만들어 `Reservation`이 그 Entity를 참조하도록 바꾸고, 그 연관 필드가 실제로 언제 DB를 조회하는지 확인했다.

> `Reservation`에 `@ManyToOne(fetch = FetchType.LAZY)`로 `Member`를 연결한 뒤, `entityManager.clear()`로 1차 캐시를 비우고 다시 조회했다. `getMember()`를 호출한 시점과 `getMember().getName()`을 호출한 시점 사이에 `member` 테이블 SELECT가 끼어 있는 것을 `show-sql` 로그로 확인했다. `getMember()` 자체는 초기화를 일으키지 않았다.

> **오늘의 흐름** `Member 도입 필요성 → @ManyToOne + FetchType.LAZY 매핑 → getClass()로 Proxy 확인 → getName()에서 SELECT 발생 확인`
>
> 이전 Day: self-invocation과 Transaction Boundary의 프록시 우회 (Day16)
> 다음 Day: N+1 확인과 fetch join (Week C D5)

![시퀀스 다이어그램. 참여자는 테스트, ReservationRepository, Reservation.member(Member$HibernateProxy)다. entityManager.clear() 이후 findById(id)를 호출하면 ReservationRepository가 reservation 테이블만 SELECT하고 member_id 컬럼값을 포함해 반환하며, member 필드는 아직 초기화 안 된 프록시다. 이어서 getMember().getClass()를 호출하면 SELECT 없이 즉시 Member$HibernateProxy를 반환한다. 마지막으로 getMember().getName()을 호출하는 순간에만 member 테이블에 대한 SELECT가 실행되어 초기화되고 "진우"가 반환된다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day18-lazy-proxy-init.png)

## 1. 개념 설명

### 1) Association Mapping의 필요성

> **Association Mapping** = 두 Entity 사이의 관계(FK)를 자바 객체 참조로 표현하는 매핑

우리 코드에서는 `Reservation`이 예약자를 문자열이 아니라 `Member` 참조로 갖도록 바꿨다.

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "member_id")
private Member member;
```

문자열 하나로는 "같은 사람"을 판단할 기준이 없다. 이름이 같은 두 예약이 같은 회원인지, 오타로 갈린 다른 사람인지 DB가 알 수 없다. `member_id`라는 FK 컬럼과 그걸 가리키는 객체 참조로 관계를 표현하면, DB가 무결성 제약(FK constraint)으로 "존재하지 않는 회원"을 막아준다.

```text
Reservation 저장
→ member 필드에 담긴 Member의 id를 member_id 컬럼에 저장
→ FK 제약이 존재하지 않는 member_id를 거부
```

### 2) `@ManyToOne`과 Fetch Type

> **Fetch Type** = 연관 필드를 언제 SELECT할지 정하는 전략. `EAGER`(즉시) / `LAZY`(지연)

`@ManyToOne`의 기본값은 `EAGER`다. 명시하지 않으면 `Reservation`을 조회하는 순간 `member`도 즉시 SELECT된다. 오늘은 이를 뒤집어 `FetchType.LAZY`를 명시했다.

```text
EAGER (기본값)
Reservation 조회 → 즉시 member 테이블 JOIN 또는 SELECT

LAZY (명시)
Reservation 조회 → member 필드는 프록시로만 채움
→ 실제 접근(getName() 등) 시점에 SELECT
```

`@OneToMany`/`@ManyToMany`의 기본값은 반대로 `LAZY`다. "컬렉션은 기본이 지연, 단일 참조는 기본이 즉시"라는 비대칭을 모르면 `@ManyToOne`에 LAZY를 안 붙이고도 지연 로딩이 될 거라 착각하기 쉽다.

### 3) Hibernate Proxy의 초기화 시점

> **Proxy(Hibernate)** = 실제 Entity 대신 들어가는 빈 껍데기 대리 객체. 식별자만 가진 채 시작해 실제 접근 시점에 초기화된다

Hibernate는 이 프록시를 매번 새로 발명하지 않는다. 대상 Entity 클래스를 상속한 서브클래스를 바이트코드로 만들고, 그 서브클래스가 `HibernateProxy` 인터페이스를 구현하게 한다. 우리 코드에서는 재조회한 `Reservation`의 `member` 필드가 `Member`가 아니라 그 서브클래스인 `Member$HibernateProxy`였다.

```java
Reservation found = reservationRepository.findById(id).orElseThrow();
found.getMember().getClass();  // class ...Member$HibernateProxy, SELECT 없음
found.getMember().getName();   // 이 시점에 select ... from member 실행
```

동작 순서는 위 다이어그램과 같다.

```text
findById() → reservation 테이블 SELECT (member_id 컬럼값만 확보)
→ member 필드 = Member$HibernateProxy(id만 보유, 미초기화)
→ getMember() 호출 — 프록시 참조를 그대로 반환, SELECT 없음
→ getMember().getName() 호출 — 프록시가 member 테이블 SELECT 실행, 필드 채움(초기화)
→ 이후 접근은 채워진 값 재사용, 추가 SELECT 없음
```

`entityManager.clear()`는 SELECT를 줄이는 장치가 아니라 1차 캐시(First-Level Cache)를 비워 **다음 조회가 캐시를 못 쓰고 DB로 다시 가게** 만드는 장치다(Day11). 그래서 `reservation` 테이블 SELECT는 즉시 나가지만, `member` 테이블 SELECT는 별개로 지연된다.

### 4) AOP Proxy와의 구분

> **AOP Proxy** = Bean의 메서드 호출을 가로채 부가 기능을 적용하는 대리 객체(Day16). **Hibernate Proxy**(3절) = Entity의 필드 접근을 가로채 SELECT를 지연시키는, 이름만 같은 별개의 장치

| 구분 | AOP Proxy | Hibernate Proxy |
|---|---|---|
| 가로채는 대상 | Bean의 메서드 호출 | Entity의 필드 접근 |
| 목적 | 트랜잭션 등 부가 기능 삽입 | SELECT 지연(Lazy Loading) |
| 확인한 클래스명 | `ReservationService$$SpringCGLIB$$0` | `Member$HibernateProxy` |

AOP Proxy는 `service.inner()`처럼 메서드를 호출하는 순간 가로챈다. Hibernate Proxy는 메서드 호출(`getMember()`) 자체가 아니라, 그 반환값에서 **실제 필드 값을 요구하는 순간**(`getName()`)에 가로챈다. 같은 "프록시"라는 이름이 두 가지 다른 메커니즘을 가리킨다.

### 5) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| Association Mapping | 두 Entity 사이의 관계를 객체 참조로 표현하는 매핑 |
| Fetch Type | 연관 필드를 즉시 조회할지 지연 조회할지 정하는 전략 |
| Proxy(Hibernate) | 실제 데이터 접근 시점까지 SELECT를 미루는 대리 객체 |
| Initialization | 프록시가 실제 값을 채우기 위해 SELECT를 실행하는 순간 |
| First-Level Cache | 같은 트랜잭션 안에서 같은 id 재조회를 캐시로 대체하는 영속성 컨텍스트의 저장소 |

> **더 볼 것**
> - [Hibernate ORM User Guide — Association Mappings](https://docs.hibernate.org/orm/6.6/userguide/html_single/Hibernate_User_Guide.html): `@ManyToOne`/`@OneToMany`를 포함한 연관관계 매핑 전체 챕터
> - [Jakarta Persistence Specification — FetchType](https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html): Fetch Type이 힌트이지 강제가 아니라는 명세 조항
> - [How does a JPA Proxy work and how to unproxy it with Hibernate — Vlad Mihalcea](https://vladmihalcea.com/how-does-a-jpa-proxy-work-and-how-to-unproxy-it-with-hibernate/): 프록시가 엔티티 클래스를 상속한 서브클래스이고 `HibernateProxy` 인터페이스를 구현한다는 근거

## 2. 코드 구현

### 1) `Member` Entity와 연관관계 필드

```java
@Entity
public class Member {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    protected Member() {}
    public Member(String name) { this.name = name; }
}
```

**한 줄씩 보기**

- `protected Member()`: JPA가 프록시·Entity를 생성할 때 쓰는 기본 생성자.
- `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "member_id")`(`Reservation.java`): FK 컬럼과 지연 로딩 전략을 함께 선언.

새 컬럼(`member_id`)은 `V4__member.sql`로 추가했다.

```sql
ALTER TABLE reservation ADD member_id BIGINT;

ALTER TABLE reservation
    ADD CONSTRAINT fk_reservation_member
    FOREIGN KEY (member_id) REFERENCES member(id);
```

`@JoinColumn(name = "member_id")`은 자바 쪽 참조 이름일 뿐이고, "존재하지 않는 `member_id`를 거부한다"는 실제 보장은 이 `FOREIGN KEY` 제약이 한다. `ddl-auto: validate`라 Hibernate가 스키마를 직접 만들지 않고, Flyway 마이그레이션과 Entity 매핑이 일치하는지만 검사한다(Week B에서 정한 원칙 그대로).

### 2) LAZY 초기화 시점 테스트

```java
entityManager.flush();
entityManager.clear();

Reservation found = reservationRepository.findById(reservation.getId()).orElseThrow();
found.getMember().getClass();  // 초기화 전
found.getMember().getName();   // 이 호출이 초기화를 유발
```

**한 줄씩 보기**

- `entityManager.clear()`: 1차 캐시를 비워 다음 조회가 실제 SELECT를 내보내게 강제.
- `getMember().getClass()`: 프록시 껍데기만 반환, SELECT 없음.
- `getMember().getName()`: 프록시가 이 시점에 `member` 테이블을 SELECT.

### 3) 겪은 오류와 자동 검증 결과

처음엔 `member` 필드에 `@ManyToOne`/`@JoinColumn`을 빼먹고 필드만 추가했고, `org.hibernate.type.descriptor.java.spi.JdbcTypeRecommendationException`이 났다 — 애노테이션이 없으면 Hibernate가 `Member`를 연관관계가 아니라 일반 컬럼 값으로 취급하기 때문이다. 이어서 `@ManyToOne`을 `@ManyToMany`로 잘못 써 `org.hibernate.AnnotationException`(`CollectionBinder`)이 났다 — `@ManyToMany`는 컬렉션 타입을 기대하는데 필드가 단일 `Member`라 타입이 안 맞았다.

| 검증 항목 | 방법 | 결과 |
|---|---|---|
| 기존 23개 테스트 회귀 | `./gradlew test` | 두 오류 교정 후 BUILD SUCCESSFUL, 23/23 |
| LAZY 초기화 시점 | `MemberLazyProxyTest` + `show-sql` 로그 | `getClass()`/`getName()` 사이에 SELECT 위치 확인 |

## 3. 스스로 답한 질문

### 1) `clear()` 이후 재조회 시점의 SELECT 발생 여부

**질문.** `entityManager.clear()` 직후 `findById()`가 `reservation` 테이블에 SELECT를 보내는가?

**A1.** 처음에는 "안 나간다, `clear()` 했으니까"라고 답했다. `clear()`를 SELECT를 막는 장치로 착각한 것이다. 실제로는 `clear()`가 1차 캐시를 비워 **다음 조회가 캐시를 못 쓰고 DB로 다시 가게** 만드는 장치라, `reservation` 테이블 SELECT는 오히려 다시 나간다. `member` 테이블 SELECT가 지연되는 건 별개로 `FetchType.LAZY` 때문이다.

### 2) `getMember().getClass()`의 반환 타입

**질문.** 재조회 직후 `getMember().getClass()`는 정확히 `Member`로 나오는가?

**A2.** 처음에는 "정확히 Member로 나올 것 — `findById().orElseThrow()`를 했으니까"라고 답했다. Entity를 성공적으로 조회한 것과 그 연관 필드가 이미 초기화된 것을 같은 것으로 혼동한 것이다. `getMember()` 호출 자체는 초기화를 일으키지 않으므로, 결과는 `Member$HibernateProxy`였다.

## 4. 학습 정리와 다음 범위

### 1) 이해의 변화와 남은 것

`Reservation`이 예약자를 문자열이 아니라 참조로 갖게 되면서, "연관관계를 맺는다"는 게 FK 컬럼 하나를 추가하는 것 이상이라는 걸 확인했다. Hibernate는 그 참조 자리에 실제 객체 대신 프록시를 넣어두고, 진짜 값이 필요한 순간까지 SELECT를 미룬다. Day16의 AOP Proxy와 이름은 같지만 가로채는 대상(메서드 호출 vs 필드 접근)이 다르다는 것도 로그로 직접 갈랐다.

**아직 남은 것**은 두 가지다. ① 반대 방향(`Member`에서 `List<Reservation>`을 `mappedBy`로 꺼내는 것)은 이번 세션에서 빈칸만 제시하고 코드로 옮기지 못했다 — **나중에 고칠 것(다음 세션, Week C D4 이어서)**. ② detached 상태(트랜잭션 종료 후)에서 LAZY 필드에 접근하면 `LazyInitializationException`이 난다는 건 이번 실험 범위에서 재현하지 않았다 — **고치지 않을 것(이번 실험 범위 밖, 필요해지면 별도 검증)**.

면접에서 다시 답해볼 항목을 남긴다.

- `@ManyToOne`의 기본 Fetch Type이 `EAGER`인 반면 `@OneToMany`는 기본이 `LAZY`인 이유
- Hibernate Proxy가 detached 상태에서 초기화될 수 없는 이유

---

오늘 공부한 소스코드: `app/src/main/java/com/example/studyroom/domain/Member.java`, `app/src/main/java/com/example/studyroom/domain/Reservation.java`, `app/src/test/java/com/example/studyroom/repository/MemberLazyProxyTest.java`
