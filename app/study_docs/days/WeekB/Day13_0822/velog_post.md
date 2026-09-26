# [Spring Study Day 13] 2주차 누적시험 — 오답 교정

Week B D6은 새 개념을 배우는 날이 아니라, Week A 전체와 Week B D1~D5를 한 번에 인출하는 누적시험 날이다. 범위는 웹 계층(Controller·DTO·DI·예외 처리)과 저장 계층(Flyway·JDBC·JPA·영속성 컨텍스트·변경 감지)이다. 이 글은 실제로 틀렸거나 힌트가 필요했던 문항만 골라, 최초 답변과 교정 기준을 남기고 그 밑에 깔린 개념을 다시 정리한다.

> 8문항을 모두 최종 통과했지만 힌트 없이 답한 것은 절반이었다. DTO와 Domain의 분리 이유는 인과관계를 거꾸로 답했고, 생성자 주입은 개념부터 다시 설명을 들어야 했으며, 적용된 Flyway 파일을 고치면 안 되는 이유에는 그날 배운 "Dirty Checking"을 넣었다. 세 오답을 복습큐에 다시 올렸고, Flyway 기준은 바로 다음 D7에서 `V2`·`V3` 마이그레이션을 새로 쌓는 판단으로 쓰였다.

> **오늘의 흐름** `Day8~12 인출 → 8문항 답변 → 오답 3개 교정 → 복습큐 재등록 → D7 코드 적용`
>
> 이전 Day: 관리 중인 Entity의 변경을 Snapshot과 비교해 `save()` 없이 UPDATE가 나가는 Dirty Checking (Day12)
> 다음 Day: 2주차 버퍼로 `ddl-auto: validate`와 `CHECK` 제약, `cancel_reason` 독립과제 적용 (Day14)

## 1. 시험 범위와 진행 방식

### 1) 재개 시점과 출제 범위

시험은 2026-08-22에 봤다. Week B D1~D3은 8/9에 끝냈고, 그 사이 노트북 데이터 유실로 D4~D7 기록을 잃어 8/22에 D4부터 다시 진행했다. 따라서 Week A 후반부와 Week B 초반부는 13일 만에 다시 꺼내는 내용이었다.

출제 범위는 다음과 같다.

- Week A: 요청 흐름, DTO와 Domain, 생성자 주입과 싱글톤 Bean, 전역 예외 처리
- Week B D1~D3: Flyway 마이그레이션, 순수 JDBC와 JPA의 차이
- Week B D4~D5: 영속성 컨텍스트, `save()` 없는 `UPDATE`

노트를 덮고 먼저 답하고, 틀리거나 막히면 힌트를 받은 뒤 다시 답하는 방식으로 진행했다. 힌트 이후의 답은 통과로 기록하되, 힌트가 필요했다는 사실은 따로 남겼다.

### 2) 시험 결과

| 구분 | 문항 | 기록 |
|---|---|---|
| 힌트 없이 통과 | 1 요청 흐름, 4 싱글톤과 무상태, 7 `save()` 없는 `UPDATE` | 4·7번은 같은 날 오전 인출·실험과 동일한 개념 |
| 힌트 후 통과 | 3 생성자 주입, 5 JDBC 대비 JPA, 8 `@RestControllerAdvice` | 5번은 커넥션 풀(HikariCP)이 JPA에서도 그대로 쓰인다는 점을 힌트로 받음 |
| 오답 후 교정 | 2 DTO/Domain 분리, 6 Flyway 파일 불변 | 2번은 인과 역전, 6번은 용어 혼동 |

13일 공백 동안 가장 많이 흐려진 영역은 Week A 후반부(DI와 싱글톤의 이유)와 Flyway 용어였다. 아래에서는 2·3·6번을 다룬다. 3번은 최종 판정이 통과지만 개념 자체를 기억하지 못한 상태에서 시작했으므로 오답과 같은 무게로 다시 정리했다.

### 3) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| DTO | 계층 경계를 넘는 데이터의 모양만 담은 불변 객체 |
| Domain Model | 상태와 그 상태를 바꾸는 규칙을 함께 가진 객체 |
| Constructor Injection | 필요한 의존성을 생성자 매개변수로 받아 조립 시점에 고정하는 DI 방식 |
| Checksum | 마이그레이션 파일 내용을 해시로 압축해 기록된 값과 대조하는 검증 기준 |
| Dirty Checking | 관리 중인 Entity의 현재 값을 로드 시점 스냅샷과 비교해 변경을 감지하는 동작 |

> **더 볼 것**
> - [Migrations - Redgate Flyway](https://documentation.red-gate.com/flyway/flyway-concepts/migrations): 버전 마이그레이션과 스키마 이력 관리
> - [Hibernate ORM User Guide](https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html): 영속성 컨텍스트와 Entity 상태 전이
> - [Dependency Injection — Spring Framework Reference](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html): 생성자 기반 DI와 생성자 인자 타입 매칭
> - [Records — Java Language Reference (Java 17)](https://docs.oracle.com/en/java/javase/17/language/records.html): `record`의 불변 필드와 자동 생성 멤버

## 2. 시험에서 틀린 문제

### 1) 문항 2 — DTO와 Domain 분리의 인과관계

> **DTO** = 계층 경계를 넘는 데이터의 모양만 담아 옮기는 객체

**질문.** DTO(`record`)와 Domain을 왜 분리하는가?

**최초 답변.** "dto는 데이터 모양 정의, domain은 immutable하지 않아서 상태 변경 로직 수행"

**왜 틀렸나.** 두 타입의 성질은 맞게 짚었지만 원인과 결과를 뒤집었다. "불변이 아니라서 상태 변경 로직이 있다"가 아니라, **상태 변경이 필요한 객체라서 애초에 불변으로 만들 수 없다**가 맞다.

원인과 결과를 바꿔 외우면 "그럼 Domain도 `record`로 만들면 되지 않나"라는 질문에 답할 수 없다. 불변 여부가 먼저 정해진 게 아니라, 역할이 먼저 정해지고 불변 여부가 거기서 따라 나온다.

**교정 기준.** 역할 → 필요한 성질 → 선택한 문법 순서로 설명한다.

#### 두 타입이 필요한 이유

요청 JSON과 예약 객체를 한 타입으로 합치면 두 가지 요구가 한 클래스에서 부딪힌다. 요청 본문은 "지금 클라이언트가 보낸 값"을 그대로 옮기기만 하면 되지만, 예약은 확정·취소처럼 시간이 지나며 상태가 바뀐다.

`Reservation`은 `confirmed`가 `false → true → false`로 바뀌어야 한다. 필드가 모두 `final`인 `record`로는 이 변화를 같은 객체 안에서 표현할 수 없다.

반대로 `ReservationRequest`는 Controller가 값을 꺼내 Service에 넘기면 역할이 끝난다. 바뀔 이유가 없으니 불변으로 두는 편이 안전하고, `record`는 그 불변 데이터 타입을 한 줄로 만드는 문법이다.

#### 요청 하나에서 타입이 바뀌는 순서

```text
JSON 본문
→ @RequestBody + @Valid: Jackson이 ReservationRequest(record) 생성, @NotBlank 검사
→ Controller: request.roomName(), request.requesterName()를 꺼내 Service에 전달
→ Service: new Reservation(roomName, requesterName)
→ reservation.confirm()으로 상태 변경
→ Repository에 저장
```

DTO의 수명은 Controller 메서드 안에서 끝난다. Domain은 그 뒤로 Service와 Repository를 거쳐 DB의 한 행과 연결되고, 이후 요청에서 다시 조회되어 `cancel()`로 상태가 바뀐다. 수명이 다르니 변경 가능성도 달라진다.

| 구분 | DTO `ReservationRequest` | Domain `Reservation` |
|---|---|---|
| 역할 | 경계를 넘는 데이터의 모양 | 상태와 규칙의 주체 |
| 상태 변화 | 없음 → `record`로 불변 | 확정·취소 → 가변, 변경은 메서드로만 |
| 수명 | Controller 메서드 안에서 끝남 | 저장 후 다음 요청에서 다시 조회됨 |

#### 보장 범위와 한계

`record`가 보장하는 불변은 필드 재대입 금지다. 필드가 가리키는 객체 내부까지 얼리지는 않는다. `ReservationRequest`는 `String` 두 개만 가지므로 현재는 이 차이가 드러나지 않는다.

Domain이 가변이라고 해서 아무 필드나 바꿀 수 있는 것도 아니다. 시험 시점의 `confirmed`는 `private`이고, 바꾸는 통로는 `confirm()`·`cancel()` 두 메서드뿐이다. 가변성은 허용하되 변경 경로를 메서드로 좁히는 것이 캡슐화다. 이 통로는 Day14에서 `cancel(String cancelReason)`으로 바뀌고, 새 필드 `cancelReason`도 같은 메서드로만 채워진다.

시험 시점의 `Reservation`은 `@Entity`이기도 하다. Day10에서 Hibernate가 조회 결과로 객체를 만들 진입점인 `protected Reservation()`을 추가했고, 그 과정에서 `roomName`·`requesterName`의 `final`도 뗐다. 따라서 지금 코드에서는 "상태 변경이 필요하다"는 도메인 이유에 "JPA가 빈 객체를 만들고 필드를 채운다"는 매핑 조건이 겹쳐 있다.

> **정리.** Domain은 불변이 아니라서 상태 변경을 하는 것이 아니다. 상태 변경이 필요한 역할이라서 불변으로 만들 수 없고, 그 변경 경로를 메서드로 좁힌다.

### 2) 문항 3 — Constructor Injection의 이유

> **Constructor Injection** = 필요한 의존성을 생성자 매개변수로 받는 DI 방식

**질문.** 생성자 주입을 쓰는 이유를 세 가지 이상 설명하라.

**최초 답변.** 처음에는 "생성자 주입이 뭔지 기억 안 남"이었다. 개념을 다시 설명받은 뒤 "mock으로 교체 가능"이라고 답했고, 힌트 후 "new로 직접 만들면 모든 호출부를 고쳐야 하지만 DI면 주입 대상만 바꾸면 됨(구현체 교체 시 Service 코드 무변경)"까지 도달했다.

**왜 막혔나.** Day05에 생성자 주입을 쓰고 설명까지 했지만, 13일 뒤에는 용어와 코드 모양이 연결되지 않았다. 이유 두 가지는 힌트를 거쳐 복원했고, 세 번째 이유(의존성의 필수성과 불변성)는 시험에서 나오지 않았다.

**교정 기준.** 생성자 주입은 "누가 만들고 어떤 통로로 넣는가"의 문제다. 이유는 그 통로가 생성자라는 데서 나온다.

#### Constructor Injection이 해결하는 문제

`ReservationService`가 저장소를 직접 만든다고 가정하면 다음과 같다.

```java
private final InMemoryReservationRepository repository = new InMemoryReservationRepository();
```

이 경우 Service는 "무엇이 필요한가"뿐 아니라 "어느 구현을 어떻게 만드는가"까지 안다. Day10에 저장소를 JPA로 바꿀 때 이 줄을 고쳐야 했을 것이고, 테스트에서 다른 저장소를 넣을 방법도 없다.

현재 코드는 필요한 것을 생성자 매개변수로 선언만 한다.

```java
private final ReservationRepository reservationRepository;

public ReservationService(ReservationRepository reservationRepository){
    this.reservationRepository = reservationRepository;
}
```

#### 기동 시 조립 순서

```text
ApplicationContext 기동
→ 컴포넌트 스캔이 @Repository·@Service·@RestController 클래스를 수집
→ JpaReservationRepository Bean 생성
→ ReservationService 생성자의 매개변수 타입(ReservationRepository)에 맞는 Bean을 찾아 전달
→ 만들어진 Service를 ReservationController 생성자에 전달
```

생성자를 호출하는 주체가 애플리케이션 코드에서 컨테이너로 넘어간 것이 IoC이고, 그 부품을 생성자 매개변수로 건네는 방식이 생성자 주입이다. Service 코드에는 Spring 문법이 없고, 평범한 Java 생성자일 뿐이다.

#### 세 가지 이유와 현재 코드의 증거

| 이유 | 설명 | 현재 코드의 증거 |
|---|---|---|
| 구현 교체 시 Service 무변경 | Service는 인터페이스 타입만 알고 구현을 모름(DIP) | Day10 JDBC → JPA 교체에서 `ReservationService` 변경 없음 |
| 테스트에서 직접 교체 | 컨테이너 없이 `new`로 원하는 구현을 넣을 수 있음 | `ReservationServiceTest`의 `new ReservationService(new InMemoryReservationRepository())` |
| 필수 의존성의 명시와 고정 | 없으면 객체를 만들 수 없고, `final` 필드는 이후 바뀌지 않음 | `private final ReservationRepository reservationRepository` |

두 번째 행은 시험 답의 "mock으로 교체 가능"을 현재 코드로 옮긴 것이다. 실제 테스트에는 mock 라이브러리가 아니라 직접 만든 메모리 구현이 들어간다. 중요한 것은 mock이라는 도구가 아니라, 생성자가 교체 지점을 열어둔다는 구조다.

#### 보장 범위와 한계

생성자 주입은 "어떤 구현이 들어오든 Service 코드를 바꾸지 않는다"를 보장하지 않는다. Service가 인터페이스 타입에 의존할 때만 성립한다. 매개변수 타입을 `InMemoryReservationRepository`로 선언했다면 생성자 주입이어도 구현 교체 때 Service를 고쳐야 한다.

주입이 가능하려면 매개변수 타입에 맞는 Bean이 컨테이너에 있어야 한다. Day05에서 `@Repository`를 떼자 컴파일은 통과하고 컨텍스트 조립이 `NoSuchBeanDefinitionException`으로 멈췄다. 생성자 주입은 이 누락을 요청 처리 중이 아니라 **기동 시점**에 드러낸다.

> **정리.** 생성자 주입의 이유는 하나의 구조에서 나온다. 의존성을 타입으로 선언하고 밖에서 받기 때문에 구현을 바꿀 수 있고, 테스트에서 직접 넣을 수 있고, 없으면 객체가 만들어지지 않는다.

### 3) 문항 6 — Flyway Checksum과 Hibernate Dirty Checking의 구분

> **Checksum** = 마이그레이션 파일의 해시를 계산해 이미 적용된 기록의 해시와 대조하는 값

**질문.** 이미 적용된 Flyway 마이그레이션 파일을 왜 고치면 안 되는가?

**최초 답변.** "Dirty Checking시 탈락한다" → 용어 정정 후 "체크섬이 달라지고 Flyway는 오류가 난다"

**왜 틀렸나.** 체크섬이라고 답해야 할 자리에 같은 날 D5에서 배운 Hibernate의 dirty checking을 넣었다. 두 장치가 "뭔가 바뀐 걸 감지한다"는 한 문장으로 겹쳐 보였기 때문이다. 새로 배운 개념이 기존 개념의 자리를 덮어쓴 경우다.

**교정 기준.** "무엇을, 어떤 기준값과, 언제 비교하고, 다르면 무엇을 하는가" 네 가지로 두 장치를 나눈다.

#### Checksum 검증의 필요성과 동작 순서

Flyway는 SQL 파일을 순서대로 한 번씩만 실행한다. 이 규칙이 성립하려면 "이미 실행한 파일이 그때 그 내용 그대로인가"를 확인할 수 있어야 한다. 파일이 바뀌었는데 확인하지 않으면, 내 DB는 옛 내용으로 만들어졌고 새 DB는 새 내용으로 만들어져 같은 버전 번호가 서로 다른 스키마를 가리키게 된다.

```text
애플리케이션 기동
→ Flyway가 db/migration의 파일마다 체크섬 계산
→ flyway_schema_history에 기록된 체크섬 조회
→ 적용된 버전의 두 값 비교
→ 같으면 미적용 버전만 실행하고 기동 계속
→ 다르면 FlywayValidateException으로 기동 거부
```

Flyway 공식 문서는 DB의 `flyway_schema_history`와 로컬 마이그레이션 파일을 함께 대조해 검증을 통과시키는 validate 단계를 다음처럼 그린다.

![왼쪽 Database 상자 안에 초록색 flyway_schema_history 테이블과 파란 테이블 세 개가 있고, 더하기 기호 옆에 V1__Initial.sql, V2__Changes.sql, V3__RefData.sql 마이그레이션 파일 세 개가 놓여 있다. 화살표가 오른쪽 초록 체크 표시로 이어져, 스키마 이력의 기록과 파일을 대조한 validate가 통과했음을 나타낸다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day13-web-flyway-validate.png)

*출처: [Flyway schema history table — Redgate Flyway Documentation](https://documentation.red-gate.com/fd/flyway-schema-history-table-273973417.html) — Copyright 1999 - 2026 Red Gate Software Ltd. All rights reserved.*

Day08에 이미 적용된 `V1__init.sql` 끝에 주석 한 줄만 붙이고 다시 띄웠을 때 이 흐름의 마지막 줄이 실행됐다.

```text
FlywayValidateException:
  Validate failed: Migrations have failed validation
  Migration checksum mismatch for migration version 1
```

그날 테스트 10개 중 6개가 함께 깨졌다. SQL과 무관한 `reservationServiceBeanIsSingleton`도 포함됐는데, Flyway가 멈추면 Spring 컨텍스트 자체가 뜨지 못하기 때문이다.

#### Dirty Checking의 필요성과 동작 순서

변경 감지는 반대로 애플리케이션 실행 중 객체의 변화를 DB에 옮기는 장치다. 관리 중인 Entity의 필드를 바꿀 때마다 개발자가 `UPDATE`를 직접 쓰지 않도록, 영속성 컨텍스트가 로드 시점 값을 보관해두고 비교한다.

```text
트랜잭션 안에서 findById(id)
→ 영속성 컨텍스트가 Entity와 로드 시점 스냅샷을 함께 보관
→ managed.cancel()로 필드 변경 (save() 호출 없음)
→ flush() 시점에 스냅샷과 현재 필드 비교
→ 다르면 UPDATE SQL 생성·실행
```

Day12 실험에서 코드에 `repository.save(managed)`가 없는데도 `update reservation set confirmed=?, requester_name=?, room_name=? where id=?`가 로그에 찍혔다.

![시퀀스 다이어그램 두 개가 위아래로 놓여 있다. 위 sd 체크섬 검증에서는 애플리케이션 기동 시 Spring Boot가 Flyway에 마이그레이션을 요청하고, Flyway가 파일마다 체크섬을 계산한 뒤 H2의 flyway_schema_history에서 기록된 체크섬을 받아온다. alt 프레임에서 두 값이 같으면 미적용 버전만 실행하고 기동을 계속하며, 다르면 FlywayValidateException으로 기동을 거부한다. 아래 sd 변경 감지에서는 테스트가 findById(id)를 호출하면 영속성 컨텍스트가 로드 스냅샷을 보관하고 managed를 돌려준다. 테스트가 managed에 cancel을 호출해 필드만 바꾸고 save()는 호출하지 않는다. flush() 시점에 영속성 컨텍스트가 스냅샷과 현재 필드를 비교하고, alt 프레임에서 값이 다르면 H2에 UPDATE를 보내고 같으면 UPDATE를 보내지 않는다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day13-checksum-vs-dirty-checking.png)

#### 두 장치의 비교

| 비교 항목 | Flyway 체크섬 검증 | Hibernate 변경 감지 |
|---|---|---|
| 비교 대상과 기준값 | 마이그레이션 **파일** ↔ `flyway_schema_history`의 기록 | Entity **필드** ↔ 영속성 컨텍스트의 로드 스냅샷 |
| 비교 시점 | 애플리케이션 기동 | 트랜잭션 안의 flush |
| 다를 때 결과 | 기동 거부 | `UPDATE` 발행 |

한 줄로 줄이면, 체크섬 검증은 **바뀌면 안 되는 것이 바뀌었는지** 막는 장치이고, 변경 감지는 **바뀐 것을 DB에 반영하는** 장치다. 감지라는 동작은 같아도 목적이 반대다.

#### 보장 범위와 한계

체크섬은 해시다. 입력이 조금만 달라도 값이 완전히 달라지므로, Flyway는 "주석 한 줄이라 의미 없음" 같은 판단을 하지 않는다. 대신 비교 대상은 파일뿐이다. 누군가 DB에 직접 `ALTER TABLE`을 실행한 변화는 파일 체크섬 비교의 대상이 아니다(미검증).

변경 감지는 영속성 컨텍스트가 관리 중인 Entity에만 동작한다. 또 "지금 값"이 아니라 "로드 시점 값과 다른가"를 본다. Day12에서 스냅샷과 최종값이 같아지는 테스트를 두 번 짰는데, 두 테스트는 변경 감지가 동작하든 안 하든 통과했다. 값이 같을 때 `UPDATE`가 나가지 않는다는 점은 로그로 따로 확인하지 않았다(미검증).

CS 쪽으로 보면 두 장치 모두 "기준값을 저장해두고 현재 값과 대조한다"는 같은 뼈대를 가진다. 체크섬은 파일 전체를 짧은 해시로 압축한 기준값이고, 스냅샷은 필드 값을 그대로 복사한 기준값이다.

> **정리.** 적용된 마이그레이션을 고치면 안 되는 이유는 dirty checking이 아니라 체크섬 불일치다. Flyway는 기동 시 파일을 비교해 멈추고, Hibernate는 flush 시 필드를 비교해 `UPDATE`를 만든다.

## 3. 오답 재발 방지와 D7 연결

### 1) 복습큐 재등록

세 개념을 복습큐에 다시 올렸다. 모두 +2일 뒤인 8/24에 도래한다.

| 개념 | 판정 | 다음 도래일 |
|---|---|---|
| DTO/Domain 분리의 인과관계 | 절반 | 8/24 |
| 생성자 주입의 이유 | 힌트 필요 | 8/24 |
| Flyway 체크섬 ≠ Hibernate dirty checking | 힌트 필요 | 8/24 |

### 2) 교정한 기준이 D7에서 쓰인 자리

시험 직후 D7에서 스키마를 두 번 더 바꿔야 했다. 빈 문자열을 막는 `CHECK` 제약과 독립과제의 `cancel_reason` 컬럼이다. 몇 시간 전 시험에서 틀린 문항이 정확히 "적용된 마이그레이션은 고치지 않는다"였으므로, 두 번 모두 `V1__init.sql`을 열지 않고 `V2`·`V3`를 새로 쌓았다.

판단 기준을 교정한 직후에 그 기준을 쓸 자리가 두 번 나온 셈이다. 구체적인 SQL과 시행착오는 Day14 글에 정리한다.

### 3) 자동 검증 범위

이날은 시험만 진행해 새 코드나 테스트가 없다. 시험 기록은 `quiz.md`로 남겼고 D4~D7 작업과 함께 [9e3dfc3](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/9e3dfc3a3956d03e68588499e7a54772a7a6d599)에 커밋했다. 위에서 인용한 체크섬 오류 메시지는 Day08, `UPDATE` 로그는 Day12의 실행 결과다.

## 4. 학습 정리와 다음 범위

### 1) 전체 흐름 다시 보기

오늘 다룬 시험 범위인 Week B 저장 계층이 전체 구조의 어디에 있는지 한 장으로 다시 보면 다음과 같다. Controller는 이 그림 왼쪽의 Service 앞에서 요청을 받고, 그림의 Database 스키마는 애플리케이션 기동 시 Flyway가 먼저 마이그레이션해 둔다.

![왼쪽부터 다섯 구역이 점선으로 나뉘어 있다. Application Modules 구역의 Service가 Repository를 호출하고, Repository는 O/R Mapper 구역의 Spring Data JPA를 거쳐 JPA 인터페이스를 구현한 Hibernate로 이어진다. 빨간 점선 테두리는 Repository부터 Hibernate까지를 한 묶음으로 표시한다. Hibernate는 JDBC Interfaces 구역의 JDBC Basic APIs와 접속 설정을 가진 DataSource를 사용하고, 둘은 JDBC Implementations 구역의 JDBC Driver로 모인 뒤 Persistence Layer 구역의 Database에 도달한다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day13-overview-data-access-stack.png)

*출처: [6.3. Database Access (JPA) — TERASOLUNA Server Framework for Java (5.x) Development Guideline](https://terasolunaorg.github.io/guideline/5.4.1.RELEASE/en/ArchitectureInDetail/DataAccessDetail/DataAccessJpa.html) — NTT DATA Corporation, TERASOLUNA 개발 가이드라인. 저작권은 원저작자에게 있습니다.*

### 2) 이해의 변화와 남은 것

시험 전에는 "변경을 감지한다"는 설명 하나로 Flyway와 Hibernate를 함께 묶어 기억하고 있었다. 비교 대상·기준값·시점·결과를 나눠 적고 나니 두 장치는 목적부터 반대였다. 하나는 과거 파일을 지키고, 하나는 현재 객체의 변화를 내보낸다.

DTO/Domain과 생성자 주입도 같은 방식으로 복원됐다. 성질(불변, mock 교체)을 외운 상태에서는 인과가 뒤집히거나 기억이 끊겼고, 역할과 구조에서 출발하면 성질이 따라 나왔다.

**아직 남은 것**은 인출 간격이다. 13일 공백 뒤 Week A 후반부가 가장 많이 흐려졌다는 것이 이번 시험의 결과다. 세 오답은 8/24 재시험으로 **바로 고칠 것**에 넣었다.

다음 범위는 **Day14 — Week B 버퍼**다. `ddl-auto`를 `validate`로 올리고, `CHECK` 제약과 `cancel_reason` 독립과제를 새 마이그레이션으로 쌓는다.

면접에서 다시 답해볼 항목을 남긴다.

- Domain 객체를 `record`로 만들 수 없는 이유와, DTO를 `record`로 만드는 이유
- Flyway 체크섬 검증과 JPA 변경 감지가 각각 무엇을 언제 비교하는지

---

오늘 공부한 소스코드: `app/study_docs/days/WeekB/Day13_0822/quiz.md`, `app/src/main/java/com/example/studyroom/service/ReservationService.java`, `app/src/test/java/com/example/studyroom/service/ReservationServiceTest.java`
