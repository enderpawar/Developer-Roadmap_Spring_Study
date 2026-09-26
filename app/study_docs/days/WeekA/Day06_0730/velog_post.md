# [Spring Study Day 6] 1주차 누적시험 — Reference Equality와 Identifier 기준

Week A D6는 새 개념을 배우는 날이 아니라 Day01~05를 노트 없이 다시 꺼내 보는 누적시험이었다. 계획은 7월 30일이었지만 실제로는 8월 2일에 D7과 함께 진행했다. 이 글은 시험에서 다시 설명한 개념, 즉 `==`와 `.equals()`, Singleton Bean, PK, 생성자 주입을 교과서처럼 한 번 더 정리하고 실제 오답 세 개를 교정 순서대로 남긴다.

> `==`가 틀린 연산자가 아니라, 비교 목적이 **같은 객체인가**인지 **같은 값인가**인지에 따라 맞는 연산자가 달라진다는 점을 다시 잡았다. 같은 ApplicationContext에서 두 번 조회한 `ReservationService`는 `assertSame`이 통과하는 같은 참조였고, 예약 번호 `Long`은 `.equals()`로 값을 비교한다. 도래한 Day04~05 문항 7개는 모두 통과했으며, 그중 Singleton Bean·PK·생성자 주입 세 문항은 오답을 교정한 뒤 통과했다.

> **오늘의 흐름** `비교 목적 결정 → 참조(==) 또는 값(.equals()) → Singleton Bean 조회 → PK로 단건 식별 → Constructor Injection의 타입 연결`
>
> 이전 Day: Service가 구현체를 직접 만들지 않아도 도는 이유를 IoC와 DI로 나누고, `assertSame`으로 같은 Service Bean을 확인한 단계 (Day5)
> 다음 Day: 저장소의 신규·기존 저장 계약을 id로 나누고 값 부재를 `Optional`과 404로 드러내는 1주차 버퍼 (Day7)

## 1. 개념 설명

### 1) Reference Equality와 Value Equality의 구분

> **Reference Equality** = 두 변수가 같은 객체를 가리키는지 비교하는 것, **Value Equality** = 서로 다른 객체라도 클래스가 정의한 논리적으로 같은 값을 나타내는지 비교하는 것

우리 코드에서는 `InMemoryReservationRepository` 한 파일 안에 두 비교가 나란히 있다.

```java
if (reservation.getId() == null){        // save(): 참조가 비어 있는가 → ==
...
if(r.getId().equals(id)){                // findById(): 같은 번호인가 → .equals()
    return Optional.of(r);
}
```

Java의 참조 타입 변수에는 객체 자체가 아니라 객체를 가리키는 참조값이 들어 있다. 그래서 비교 연산을 고를 때는 "참조를 비교할 것인가, 참조가 가리키는 객체의 값을 비교할 것인가"를 먼저 정해야 한다.

저장소 안의 `Reservation.id`와 URL에서 들어온 `id`는 각각 다른 경로로 만들어진 `Long` 참조다. 둘이 같은 숫자를 나타내도 같은 객체라는 보장은 없다.

```text
a == b
→ 두 변수에 든 참조값을 비교
→ 같은 인스턴스를 가리킬 때만 true

a.equals(b)
→ a의 클래스가 재정의한 equals() 실행
→ Long.equals(): 인수가 null이 아니고 Long이며 같은 long 값이면 true
```

`==`는 객체 안을 들여다보지 않는다. 반면 `.equals()`는 클래스가 정한 "같다"의 정의를 따르고, `Long`은 담긴 `long` 값이 같으면 같다고 정의한다. 그래서 `save()`의 null 확인에는 `==`가, `findById()`의 번호 비교에는 `.equals()`가 맞다.

| 비교 수단 | 비교 대상 | 이 프로젝트에서 맞는 자리 |
|---|---|---|
| `==` | 참조값 | `getId() == null`, Singleton Bean 두 조회 |
| `.equals()` | 클래스가 정의한 논리적 값 | 두 `Long` 예약 번호 |
| `assertSame` / `assertEquals` | 각각 `==` / `.equals()` 기준 | Bean 동일성 / 값 검증 |

> **보장 범위** — `.equals()`가 값 비교가 되는 것은 클래스가 이를 재정의했을 때뿐이다. `Object`의 기본 `equals()`는 참조 비교와 같다. 현재 `Reservation`은 `equals()`를 재정의하지 않았으므로, 두 `Reservation`을 `.equals()`로 비교해도 같은 인스턴스인지만 확인한다.

### 2) Long Boxing 캐시와 우연한 통과

> **Boxing 캐시** = `Long.valueOf(long)`이 -128~127 범위의 값에 대해 미리 만들어 둔 같은 `Long` 인스턴스를 돌려주는 규칙

우리 코드에서는 원시 타입 `long` 카운터를 `Long`을 받는 메서드에 넘기므로 boxing이 일어난다.

```java
private long nextId = 1;                 // InMemoryReservationRepository: 원시 타입 long
reservation.assignId(nextId++);          // save(): Long으로 boxing되어 전달
public void assignId(Long id) {          // Reservation: 참조 타입 Long으로 받음
```

```text
assignId(nextId++)
→ javac가 autoboxing을 Long.valueOf(long) 호출로 컴파일
→ Long.valueOf는 -128~127 범위의 값을 항상 캐시
→ 범위 안: 같은 값이면 캐시된 같은 인스턴스를 재사용할 수 있음
→ 범위 밖: 같은 값이어도 같은 인스턴스라는 보장 없음
```

`Long` 값 비교에서 `==`가 특히 위험한 이유는 작은 숫자에서 우연히 맞아 보이기 때문이다. id가 1, 2, 3인 개발 초기 데이터로만 확인하면 `==`도 통과하는 것처럼 보일 수 있다. 연산자의 의미가 바뀐 것이 아니라, 두 참조가 캐시 때문에 같은 인스턴스를 가리켰을 뿐이다.

`Long.valueOf` 문서는 범위 밖의 값도 **캐시할 수 있다**고 적는다. 즉 1000을 `==`로 비교하면 반드시 `false`라는 뜻도 아니다. 결과가 구현과 생성 경로에 달려 있으므로 값 비교 수단으로 믿을 수 없다는 것이 정확한 결론이다.

![객체 다이어그램 세 구획. 첫째 구획에서 test 객체의 first와 second 두 링크가 같은 reservationService 인스턴스 하나를 가리키고, applicationContext도 그 하나를 관리하므로 first == second가 true이고 assertSame이 통과한다. 둘째 구획에서 r의 id 링크는 value 1000인 storedId, findById의 인수 링크는 value 1000인 requestedId를 가리켜 인스턴스가 둘이므로 == 결과는 보장되지 않고 equals는 true다. 셋째 구획에서 value 1인 Long은 캐시된 인스턴스 하나를 두 링크가 공유해 ==가 우연히 true일 수 있다. Long 구획은 캐시 규칙에 따른 도식이며 실행 측정은 하지 않았다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day06-reference-identity.png)

이 함정은 Day04에서 처음 만났다. 당시 `findById()`에서 `.equals()`를 쓴 이유를 "null값 탐지"라고 답했다가 교정했고, D6 누적시험의 `Long` 값 비교 문항은 통과했다.

CS 관점에서는 객체의 identity와 equality 구분이다. identity는 "같은 객체인가", equality는 "같다고 정의한 관계에 있는가"다. vocab에 적은 **동등 관계**라는 말처럼, `equals()`는 클래스가 정하는 관계이고 `==`는 언어가 정한 참조 비교다.

> **보장 범위** — 작은 값과 큰 값의 `==` 결과를 직접 실행해 비교한 기록은 없다. 위 그림의 `Long` 구획과 흐름은 JLS·`Long` API 문서의 규칙에 따른 설명이며 **미검증**이다.

### 3) Singleton Scope와 Bean Reference Equality

> **Singleton Scope** = Bean 정의 하나당 ApplicationContext가 인스턴스를 하나만 만들어 모든 조회와 주입에 같은 참조를 재사용하는 Spring의 기본 scope

우리 코드에서는 `@Service`만 붙인 `ReservationService`를 테스트에서 두 번 조회해 참조를 비교한다.

```java
ReservationService first = applicationContext.getBean(ReservationService.class);
ReservationService second = applicationContext.getBean(ReservationService.class);

assertSame(first, second);   // equals()가 아니라 == 기준: 같은 인스턴스인가
```

```text
ApplicationContext 초기화
→ @Service가 붙은 ReservationService의 Bean 정의 등록
→ 생성자에 ReservationRepository Bean을 넣어 인스턴스 1개 생성
→ 컨테이너가 singleton Bean 캐시에 보관
→ getBean(ReservationService.class) 첫 호출: 캐시의 참조 반환
→ 두 번째 호출: 같은 참조 반환
→ assertSame(first, second): 두 참조값이 같으므로 통과
```

Service는 요청마다 새로 만들 필요가 없다. 상태 없이 규칙만 실행하는 객체라면 하나를 만들어 여러 요청이 함께 쓰는 편이 생성 비용과 관리 측면에서 단순하다.

`getBean()`은 호출할 때마다 객체를 만드는 메서드가 아니라, 컨테이너가 이미 관리 중인 Bean을 조회하는 메서드다. 그래서 두 조회 결과를 비교하는 질문은 "값이 같은가"가 아니라 "같은 Bean인가"이며, 여기서는 `==`와 `assertSame`이 정확한 도구다.

Spring 공식 문서는 Bean 정의 하나에서 인스턴스가 한 번만 만들어지고, 그 같은 인스턴스가 협력 객체마다 주입되는 구조를 다음처럼 그린다.

![Spring Singleton scope 도식. 오른쪽의 accountDao Bean 정의 하나에서 인스턴스가 한 번만 생성되고(원 안의 1), 그 같은 공유 인스턴스가 화살표를 따라 왼쪽의 세 협력 Bean 정의에 ref="accountDao"로 각각 주입된다. 위 문구는 Only one instance is ever created, 아래 문구는 and this same shared instance is injected into each collaborating object다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day06-web-singleton-scope.png)

*출처: [Spring Framework Reference — Bean Scopes, The Singleton Scope](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html) — Copyright © 2005 - Broadcom. All Rights Reserved. (문서 사본은 무료 배포와 저작권 고지 유지 조건으로 허용)*

Spring의 Singleton은 GoF Singleton 패턴과 범위가 다르다.

| 구분 | 인스턴스가 하나인 범위 | 이 글의 관찰 |
|---|---|---|
| Spring Singleton scope | ApplicationContext 하나 안의 Bean 정의 하나 | 같은 context에서 두 번 조회 |
| GoF Singleton 패턴 | ClassLoader 안의 클래스 하나 | 사용하지 않음 |

여러 요청이 같은 `ReservationService`를 함께 쓰므로 `currentRequesterName` 같은 요청별 값을 필드에 두면 다른 요청이 덮어쓸 수 있다. 현재 `reserve(String roomName, String requesterName)`는 요청 값을 매개변수와 지역변수로만 다루고, 필드는 생성 시 한 번 주입되는 `final` 저장소 참조 하나뿐이다.

> **보장 범위** — 보장하는 것은 **같은 컨테이너, 같은 Bean 정의**에서 같은 인스턴스를 받는다는 점이다. 스레드 안전성은 보장하지 않으며, 공유 필드 경쟁 상태를 여러 스레드로 실제 재현하지는 않았다. 이후 Day16에서 `@Transactional`을 붙인 뒤에는 이 참조가 원본 클래스가 아니라 CGLIB 프록시라는 사실을 확인했고, 그때도 두 조회 결과는 같은 Singleton 프록시였다.

### 4) PK와 단건 갱신 대상의 식별

> **PK** = 데이터 하나를 고유하게 식별해 갱신·삭제 대상을 하나로 지목하게 하는 값

우리 코드에서는 `Reservation`이 저장 전에는 비어 있는 `id` 필드를 갖고, 취소는 그 번호로 찾은 인스턴스에만 적용된다.

```java
private Long id;   // Reservation: 저장 전 null, save()가 assignId(nextId++)로 채움

// ReservationService.cancel(): 같은 번호의 인스턴스를 찾아 그 객체의 상태만 바꿈
Reservation reservation = reservationRepository.findById(id)
        .orElseThrow(() -> new ReservationNotFoundException(id));
reservation.cancel();
```

```text
reserve 요청
→ Service가 new Reservation(...) 생성 후 confirm()
→ Repository.save()가 id가 null임을 확인하고 assignId(nextId++)
→ 응답 문자열에 "예약 번호" 포함
→ 클라이언트가 POST /reservations/cancel/{id}
→ findById(id)가 r.getId().equals(id)로 같은 번호의 인스턴스를 찾음
→ 찾은 그 인스턴스에 cancel()
```

기존 예약 하나를 취소하려면 "어느 예약인가"를 가리킬 수단이 필요하다. Day04에서 `cancel()`을 `new Reservation(roomName, requesterName)`으로 만들었을 때, 예약이 취소되는 대신 같은 값의 새 인스턴스가 하나 더 생겼다. 값이 같아도 다른 객체였기 때문이다.

방 이름과 예약자 이름은 중복될 수 있다. 같은 사람이 같은 방을 두 번 예약하면 두 데이터는 값으로 구분되지 않는다. 조건 검색 자체는 가능하지만, 결과가 여러 개일 때 어느 하나를 갱신할지 정할 규칙이 없다.

| 기준 | 할 수 있는 일 | 단건 갱신 계약 |
|---|---|---|
| 조건 검색(이름·방) | 조건에 맞는 데이터 조회 | 결과가 여럿이면 성립하지 않음 |
| PK(`id`) | 데이터 하나를 고유하게 지목 | 성립 |

PK의 역할은 조회 기능을 가능하게 만드는 데 있지 않다. **갱신 대상을 고유하게 지목해 단건 갱신 계약을 성립시키는 것**이 역할이다. 이 기준은 D7에서 `save()`가 신규와 기존을 id로 나누는 분기로 이어졌다. CS 관점에서는 관계형 DB의 엔티티 무결성, 즉 기본키가 행 하나를 유일하게 식별해야 한다는 규칙과 같은 질문이다.

> **보장 범위** — 위 발췌의 `Optional`·`orElseThrow`·`ReservationNotFoundException`은 같은 커밋의 D7 작업에서 바뀐 부분이다. 현재 id는 메모리의 `nextId` 카운터라서 프로세스를 재시작하면 1부터 다시 시작한다. DB 기본키로 옮기는 작업은 Week B에서 진행한다.

### 5) Constructor Injection의 타입 연결

> **Constructor Injection** = 필수 의존성을 생성자 매개변수로 선언하고, 컨테이너가 그 타입에 맞는 Bean을 찾아 생성 시점에 넣어 주는 DI 방식

우리 코드에서는 `ReservationService`가 저장소 인터페이스 하나를 생성자로 받는다.

```java
private final ReservationRepository reservationRepository;   // 필드: 인터페이스 타입

public ReservationService(ReservationRepository reservationRepository){   // 매개변수
    this.reservationRepository = reservationRepository;   // 현재 객체의 필드 ← 매개변수
}
```

```text
컨테이너가 ReservationService 생성자를 확인
→ 매개변수 타입 ReservationRepository 확인
→ 그 타입에 대입 가능한 Bean(InMemoryReservationRepository) 선택
→ new ReservationService(그 Bean) 호출
→ this.reservationRepository = reservationRepository
```

`ReservationService`는 저장소 없이 동작할 수 없다. 이 필수 의존성을 생성자 매개변수로 드러내면, 컨테이너는 생성자를 보고 무엇을 넣어야 하는지 알 수 있고 객체는 생성 직후부터 완전한 상태가 된다.

`this.reservationRepository`는 현재 객체의 필드이고, 오른쪽의 `reservationRepository`는 생성자 매개변수다. 매개변수 타입이 필드 타입과 같으므로 대입이 성립한다. 연결 기준은 "저장소 역할처럼 보이는 객체"가 아니라 Java 타입이다. 독립 작성 때 확인할 항목은 세 가지로 정리했다.

1. 생성자 이름이 클래스 이름과 같은가
2. `this.x`의 `x`가 현재 클래스에 선언된 필드인가
3. 매개변수 타입을 그 필드에 대입할 수 있는가

필드 타입이 구현체가 아니라 `ReservationRepository` 인터페이스라는 점은 DIP, 즉 상위 정책이 구체 구현보다 추상 계약에 의존하는 원칙과 연결된다. Service가 계약에만 의존하므로 메모리 저장소를 JPA 저장소로 바꿀 때 상위 계층까지 수정이 연쇄 전파되는 것을 줄일 수 있다.

| 구분 | 다루는 것 | 이 코드에서 |
|---|---|---|
| IoC | 객체 그래프의 생성·연결을 누가 제어하는가 | Spring 컨테이너가 Repository → Service를 조립 |
| DI | 그 과정에서 의존 객체를 전달하는 구체적 통로 | `ReservationService` 생성자 매개변수 |

> **보장 범위** — 구현체 교체로 인한 변경 전파 감소는 설명한 효과이고, 실제 JPA 저장소로의 교체는 Week B의 범위다. 오늘 확인한 것은 인터페이스 타입 하나에 구현 Bean이 하나일 때의 연결뿐이다.

### 6) Request Mapping과 컴파일 검사의 경계

> **Request Mapping** = URI·본문 같은 HTTP 입력을 Controller 메서드와 그 인수에 연결하는 Spring MVC의 처리

우리 코드에서는 URL 템플릿의 `{id}`가 `@PathVariable` 매개변수 `id`에 연결된다.

```java
@PostMapping("/reservations/cancel/{id}")   // 문자열: 컴파일러가 내용을 검사하지 않음
public String cancel(@PathVariable @Positive(message = "예약 번호는 1 이상이어야 합니다") Long id) {
```

`@PathVariable` 문항은 통과했지만, Day04에서 겪은 경계를 다시 설명한 문항이라 짧게 남긴다. URL 템플릿 `"/reservations/cancel/{id}"`는 문자열이므로 Java 컴파일러가 그 안의 `{id}`와 매개변수 이름을 대조하지 않는다.

```text
./gradlew compileJava → 성공 (문자열 내용은 검사 대상 아님)
./gradlew test        → 성공 (당시 HTTP 요청 테스트 없음)
실제 요청             → Spring MVC가 요청 매핑 시점에 템플릿 변수를 찾음
                     → 실패
```

Day04 당시 실제 응답은 `{"error":"Required URI template variable 'id' for method parameter type Long is not present"}`였다. 이 경계는 D7에서 MockMvc 테스트를 추가하는 이유와도 이어진다.

> **보장 범위** — 위 발췌는 이름이 맞게 고쳐진 `6c88dcb` 시점 코드이고, `@Positive`는 같은 커밋의 D7 작업에서 추가됐다. 이름 불일치 실패는 Day04 기록을 인용한 것으로, 오늘 다시 실행하지는 않았다.

### 7) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| IoC | 객체를 생성하고 연결하는 제어권이 애플리케이션 코드가 아니라 Spring 컨테이너에 있는 것 |
| DI | 필요한 의존 객체를 직접 만들지 않고 생성자 같은 통로로 외부에서 전달받는 것 |
| Constructor Injection | 필수 의존성을 생성자 매개변수로 받아 생성 시점에 주입하는 DI 방식 |
| ApplicationContext | Bean 정의를 등록하고 Bean을 생성·연결·보관하며 조회 요청에 응답하는 Spring 컨테이너 |
| Bean Definition | 컨테이너가 Bean 하나를 만들 때 따르는 설계 정보. `@Service` 같은 스테레오타입으로 등록된다 |
| Singleton Scope | Bean 정의 하나당 컨테이너가 인스턴스 하나를 재사용하는 기본 scope |
| Request Mapping | HTTP 요청의 URI·메서드를 Controller 메서드와 인수에 연결하는 Spring MVC 처리 |
| URI Template Variable | `{id}`처럼 URL 템플릿에 선언해 `@PathVariable` 인수로 받는 경로 변수 |

> **더 볼 것**
> - [JLS 5.1.7 Boxing Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.7): 작은 정수 boxing 결과의 참조 재사용 규칙
> - [Java `Long` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Long.html): `valueOf(long)`의 -128~127 캐시와 `equals()` 정의
> - [Spring Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html): Singleton scope의 범위와 GoF Singleton과의 차이
> - 아직 안 본 것 — 여러 스레드가 Singleton Service를 공유할 때의 경쟁 상태 재현, DB 기본키 생성 전략

## 2. 코드 구현

### 1) 누적시험 범위와 결과

Day01~03 문항은 앞선 +2 인출에서 통과했고 다음 복습일이 아직 오지 않아 간격을 유지했다. 이번 시험은 Day04~05에서 도래한 문항과 오답 재시험을 중심으로 진행했다.

| 확인한 항목 | 결과 | 다시 잡은 기준 |
|---|---|---|
| IoC와 DI 구분 | 통과 | 조립 제어권과 의존 객체 전달을 구분 |
| `Long` 값 비교 | 통과 | 참조 동일성과 값 동일성을 구분 |
| Singleton Bean의 `==` | 교정 후 통과 | 값이 아니라 같은 Bean 참조 |
| Repository 인터페이스 의존 | 통과 | 구현 교체 시 변경 전파 감소 |
| PK가 필요한 이유 | 교정 후 통과 | 단건 갱신 대상을 고유하게 식별 |
| `@PathVariable` 불일치 | 통과 | 컴파일이 아닌 요청 처리 시점의 문제 |
| 생성자 주입 문법 | 교정 후 통과 | 생성자명·필드명·타입 확인 |

### 2) 같은 Bean과 같은 값의 비교 코드

```java
// StudyRoomApiApplicationTests — 같은 Bean인지: 참조 비교
ReservationService first = applicationContext.getBean(ReservationService.class);
ReservationService second = applicationContext.getBean(ReservationService.class);
assertSame(first, second);

// InMemoryReservationRepository.findById() — 같은 번호인지: 값 비교
for (Reservation r : store) {
    if (r.getId().equals(id)) {
        return Optional.of(r);
    }
}
```

**한 줄씩 보기**

- `applicationContext.getBean(ReservationService.class)` — 테스트 실행 시 이미 초기화된 컨테이너에서 타입으로 Bean을 조회한다. 새 객체를 만들지 않고 보관 중인 참조를 돌려준다.
- 두 번째 `getBean(...)` — 같은 Bean 정의를 다시 조회하므로 Singleton scope에서는 첫 번째와 같은 참조가 나온다.
- `assertSame(first, second)` — `.equals()`를 호출하지 않고 두 참조를 `==` 기준으로 비교한다. 통과하면 같은 인스턴스라는 뜻이다.
- `for (Reservation r : store)` — `findById()`가 호출될 때마다 메모리 목록을 처음부터 순회한다.
- `r.getId().equals(id)` — 저장된 번호와 URL로 받은 번호가 같은 `long` 값인지 비교한다. 두 `Long`이 같은 객체인지는 따지지 않는다.
- `return Optional.of(r)` — 찾은 그 인스턴스를 담아 돌려준다. 반환형 `Optional`은 같은 커밋의 D7 작업에서 바뀐 부분이다.

테스트 통과는 두 Service의 필드 값이 우연히 같다는 뜻이 아니라 컨테이너가 같은 인스턴스를 반환했다는 뜻이다. `findById()`에서는 저장소 계약상 같은 숫자인지가 중요하므로 `.equals()`가 맞다.

### 3) 자동 검증 결과

| 구분 | 근거 | 결과 |
|---|---|---|
| 자동 테스트 | `reservationServiceBeanIsSingleton()`, `ReservationServiceTest`(기존 예약 취소 시 중복 없음·없는 번호의 도메인 예외) | 통과, 전체 Gradle 테스트 10개 통과 |
| 수동 확인 | `quiz.md` 누적 인출 7문항 | 전부 통과(3문항 교정 후) |
| 미검증 | `Long` 캐시 범위 안팎의 `==` 결과, Singleton 공유 필드 경쟁 상태, 성능 | 실행하지 않음 |

테스트 통과는 코드 상태의 증거이며 인출 답안 자체를 대신하지 않는다.

검증 코드와 Day06·07 산출물: [commit `6c88dcb`](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/6c88dcb)

## 3. 스스로 답한 질문

### 1) Singleton Bean 비교 결과의 판단 근거

**질문.** 같은 ApplicationContext에서 `ReservationService` Bean을 두 번 조회해 `==`로 비교하면 왜 `true`인가?

**A1.** 처음 기록은 "객체 상으로는 동일해 보이지만 값이 다르기 때문"이었다. 여기에는 값 비교와 참조 비교가 섞여 있었다. `==`는 값이 다른지를 판단하지 않고, 두 변수가 같은 객체를 가리키는지를 본다.

교정된 답은 "기본 Singleton scope 때문에 같은 컨테이너가 동일한 Bean 인스턴스를 반환하므로 `true`"다. 근거는 `reservationServiceBeanIsSingleton()`의 `assertSame(first, second)` 통과다.

재발 방지로, 연산자를 고르기 전에 비교 목적을 "참조인가, 값인가"로 먼저 말한다. 그다음 "두 변수가 같은 객체를 가리킬 경로가 있는가"를 확인한다.

### 2) PK 없이 수정하는 방식의 한계

**질문.** 기존 예약을 안전하게 수정하는 데 PK가 필요한 이유는 무엇인가?

**A2.** 처음에는 `id`가 없어도 수정할 수 있다고 설명했다. 이름이나 방 번호로 조건 검색을 하는 것 자체는 가능하기 때문이다.

하지만 값이 중복되면 어느 하나를 수정할지 결정할 수 없으므로 안전한 단건 갱신 계약이 되지 못한다. 교정 뒤에는 PK를 "조회 기능을 가능하게 하는 값"이 아니라 "대상 하나를 고유하게 식별하는 값"으로 설명했다.

이 기준은 Day07에서 저장소의 추가와 교체 분기에 그대로 사용했다.

### 3) Constructor Injection에서 `this.x`의 대상

**질문.** 생성자 주입 코드의 `this.x`에서 `x`는 무엇을 가리키는가?

**A3.** 처음에는 `this.x`의 대상을 메서드라고 답했다. `this`는 현재 객체이고 `this.x`는 그 객체의 필드를 가리킨다. 생성자 매개변수의 값을 현재 객체 필드에 대입하는 코드다.

Controller의 생성자 모양을 그대로 복사하는 것이 아니라, 현재 클래스의 이름·필드·필요한 의존 타입에 맞게 바꿔야 한다. 재발 방지로 생성자명과 클래스명, `this` 뒤의 실제 필드명, 필드와 매개변수의 대입 가능한 타입을 순서대로 확인한다.

## 4. 학습 정리와 다음 범위

### 1) 전체 흐름 다시 보기

`==`와 `.equals()`의 차이는 결국 Stack의 변수가 Heap의 어느 객체를 가리키는지의 문제다. Singleton Bean과 `Long`에도 그대로 적용되는 이 구조를 가장 흔한 String 예시로 한 장에 모으면 다음과 같다(`a == c`는 true, `b == d`는 false, `.equals()`는 모두 true).

![Stack 영역의 변수 a, b, c, d가 Heap 영역의 객체를 가리키는 그림. a와 c는 Heap 안 String Pool의 "apple" 객체 하나를 함께 가리키고, b와 d는 new String("apple")로 만든 서로 다른 두 객체를 각각 가리킨다. 따라서 a == c는 같은 참조라 true, b == d는 다른 참조라 false이며, 값은 모두 "apple"이다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day06-overview-reference-equality.png)

*출처: [[자바] 문자열 비교하기 ==와 equals의 차이](https://velog.io/@beneficial/%EC%9E%90%EB%B0%94-%EB%AC%B8%EC%9E%90%EC%97%B4-%EB%B9%84%EA%B5%90%ED%95%98%EA%B8%B0-%EC%99%80-equals%EC%9D%98-%EC%B0%A8%EC%9D%B4) — Romy(velog @beneficial). 저작권은 원저작자에게 있습니다.*

### 2) 이해의 변화와 남은 것

누적시험 전에는 `==`를 "객체에서 쓰면 안 되는 연산자"처럼 외우기 쉬웠다. 지금은 같은 연산자가 Singleton Bean 확인에는 정확하고 `Long` 값 비교에는 부정확한 이유를 비교 목적과 인스턴스 생성 경로로 나눠 설명할 수 있다.

이 구분은 PK와 생성자 주입에도 이어졌다. 서로 다른 Day에서 나온 개념이 시험에서는 **두 대상을 같은 것으로 판정하는 기준**이라는 한 질문으로 모였다. 객체는 참조, 값은 `equals()`, 데이터는 PK, 의존성은 타입이 그 기준이다.

**아직 남은 것**은 Singleton Service의 공유 필드 경쟁 상태를 여러 스레드로 재현하지 않았다는 점이다. 이번 트랙에서는 **고치지 않을 것**으로 분류했고, 요청별 값은 필드가 아닌 지역변수에 둔다는 규칙만 현재 코드에 적용한다. 다음 범위는 Week A D7 버퍼로, Day03·04의 즉시 수정 기술부채를 처리한다.

면접에서 다시 답해볼 항목을 남긴다.

- Spring Singleton scope와 GoF Singleton 패턴의 범위 차이
- `Long` 식별자를 `==`로 비교한 코드가 테스트를 통과하는 조건

<!-- 선택 복습 메모: 게시 화면에는 노출하지 않는다.
### 1) 선택 추가 설명

[직접 작성] Singleton Bean 비교에서는 `==`가 의미 있고, `Long` 식별자 값 비교에서는 `.equals()`가 필요한 이유를 한 문단으로 설명한다.
-->
