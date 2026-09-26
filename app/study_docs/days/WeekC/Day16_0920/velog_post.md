# [Spring Study Day 16] Spring AOP Proxy — `@Transactional`과 self-invocation

Day15에서는 `ReservationService.cancel()`에 `@Transactional`을 붙이고 commit과 rollback을 관찰했다. 이번에는 애노테이션 한 줄이 메서드 전후에 트랜잭션 처리를 붙이는 구조를 열어보고, 같은 메서드라도 외부 호출과 객체 내부 호출에서 결과가 달라지는 조건을 테스트했다.

> `ReservationService` Bean의 실제 런타임 타입은 원본 클래스가 아니라 `ReservationService$$SpringCGLIB$$0`이었다. 외부에서 `@Transactional` 메서드를 호출하면 트랜잭션이 활성화됐지만, 같은 객체의 일반 메서드가 내부에서 해당 메서드를 호출하면 프록시를 우회해 비활성 상태였다. 바깥 비즈니스 메서드에 트랜잭션 경계를 두는 방식으로 세 호출 경로를 검증했다.

> **오늘의 흐름** `Caller → ReservationService Proxy(CGLIB) → TransactionInterceptor → target ReservationService → 반환 시 commit/rollback` (self-invocation은 `outer() → this.inner()`로 Proxy 우회)
>
> 이전 Day: `cancel()`의 Transaction Boundary와 commit/rollback (Day15)
> 다음 Day: 트랜잭션 전파 REQUIRED/REQUIRES_NEW (Week C D3)

## 1. 개념 설명

### 1) 애노테이션과 실행 주체의 분리

> **AOP (Aspect-Oriented Programming)** = 트랜잭션 같은 공통 관심사를 비즈니스 코드에서 분리해, 프록시가 메서드 호출 전후에 가로채 적용하는 방식

우리 코드에서는 `ReservationService.cancel()`에 `@Transactional`만 붙였고, 트랜잭션을 시작·commit·rollback하는 코드는 어디에도 없다.

```java
@Transactional
public Reservation cancel(Long id, String cancelReason) {
    Reservation reservation = reservationRepository.findById(id)
            .orElseThrow(() -> new ReservationNotFoundException(id)); // 1. 조회
    reservation.cancel(cancelReason); // 2. 상태 변경, DB 반영은 프록시가 감싼 트랜잭션 안에서 처리
    return reservation;
}
```

Spring은 애플리케이션 컨텍스트를 구성하면서 트랜잭션 대상 Bean 앞에 프록시를 둔다. 다른 Bean이 `ReservationService`를 주입받거나 `ApplicationContext.getBean()`으로 조회하면, 보통 실제 target을 직접 받는 것이 아니라 프록시 참조를 받는다.

```text
Caller 또는 테스트
→ ReservationService Proxy(CGLIB)
→ TransactionInterceptor
→ target ReservationService.cancel()
→ 정상 반환 시 commit / 대상 예외 발생 시 rollback
```

Spring 공식 문서는 호출 코드가 먼저 프록시의 메서드를 호출하고, 프록시가 그다음 실제 객체의 메서드를 호출하는 구조를 다음처럼 그린다.

![호출 코드(Calling code)가 pojo.foo()를 호출하면 요청이 먼저 Proxy 안으로 들어가 프록시의 foo()가 실행되고, 그다음 Proxy가 감싼 Plain Object의 foo()가 실행된 뒤 결과가 호출 코드로 돌아가는 구조](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day16-web-aop-proxy-call.png)

*출처: [Proxying Mechanisms — Understanding AOP Proxies, Spring Framework Reference](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html#aop-understanding-aop-proxies) — Copyright © 2005 - Broadcom. All Rights Reserved. (문서 사본은 무료 배포와 저작권 고지 유지 조건으로 허용)*

이 구조는 비즈니스 메서드 안에 트랜잭션 시작과 종료 코드를 직접 넣지 않고도 공통 처리를 적용한다. AOP의 관심사 분리는 여기서 드러난다. 예약 취소 코드는 예약 규칙에 집중하고, 트랜잭션 처리는 프록시와 부가 기능이 맡는다.

> **보장 범위** — `AopUtils.isAopProxy(service)`와 `isCglibProxy(service)` 두 assertion으로 프록시 존재 자체는 확인했다. 프록시 내부에서 트랜잭션 시작·commit·rollback이 정확히 어떤 순서로 실행되는지는 Spring 소스코드까지 따라가 검증하지 않았다(공식 문서의 구조도로만 확인).

### 2) Bean 조회와 Proxy 생성 시점

> **Proxy** = target 앞에 위치해 호출을 가로채고 부가 기능을 적용한 뒤 target에 위임하는 대리 객체

우리 코드에서는 `ApplicationContext.getBean(ReservationService.class)`로 조회한 참조가 원본 클래스가 아니라 프록시임을 다음 코드로 확인했다.

```java
ReservationService service =
        applicationContext.getBean(ReservationService.class);

assertTrue(AopUtils.isAopProxy(service));
assertTrue(AopUtils.isCglibProxy(service));
assertNotEquals(ReservationService.class, service.getClass());
```

처음에는 `getBean()`이 임시 프록시 객체를 만든다고 생각했다. 실제 순서는 반대다. 프록시는 컨테이너의 Bean 생성·후처리 과정에서 준비되고, `getBean()`은 이미 관리 중인 Bean을 조회한다.

```text
ApplicationContext 초기화
→ ReservationService target 생성
→ @Transactional 적용 대상 확인
→ target을 감싸는 프록시 준비
→ 프록시를 ReservationService Bean으로 노출
→ getBean()이 같은 Singleton 프록시 반환
```

target은 프록시 뒤에서 실제 비즈니스 코드를 실행하는 원본 객체다. 현재 `ReservationService`는 별도의 Service 인터페이스를 구현하지 않는다. 실행 결과에서 Spring이 원본 클래스를 상속한 CGLIB 프록시를 사용했고, 런타임 클래스 이름도 다음과 같이 출력됐다.

```text
com.example.studyroom.service.ReservationService$$SpringCGLIB$$0
```

> **보장 범위** — `reservationServiceBeanIsSingleton()`의 `assertSame(first, second)` 통과로 두 번 조회한 참조가 같은 프록시 객체임을 확인했다. 프록시가 컨테이너 초기화 중 정확히 어느 단계에서 만들어지는지는 Spring 내부 코드까지 따라가지 않았다.

### 3) 외부 호출과 self-invocation의 경로 차이

> **Self-Invocation** = 같은 객체 내부에서 `this`를 통해 자신의 다른 메서드를 직접 호출하는 형태

우리 코드에서는 `outer()`가 애노테이션 없는 상태에서 내부적으로 `inner()`를 호출한다.

```java
public boolean outer() {
    return inner(); // this.inner() 호출은 프록시를 다시 거치지 않는다.
}

@Transactional
public boolean inner() {
    return TransactionSynchronizationManager.isActualTransactionActive();
}
```

프록시는 자기 앞을 통과하는 호출만 가로챌 수 있다. 외부 코드가 프록시 참조의 `inner()`를 호출하면 트랜잭션 부가 기능이 동작한다. 반면 target의 `outer()`가 `inner()`를 호출하면, 실행 위치는 이미 실제 객체 내부다. `return inner()`는 사실상 `return this.inner()`이며, 앞에 놓인 프록시로 다시 나갔다 들어오지 않는다.

```text
외부 inner() 호출
→ 프록시 통과
→ @Transactional 적용
→ active=true

외부 outer() 호출
→ 프록시 통과
→ target.outer()
→ this.inner()
→ 프록시 재진입 없음
→ active=false
```

Spring 공식 문서는 프록시 없이 객체 참조를 직접 호출하는 경로를 다음처럼 그린다. target 안의 `this.inner()`도 이렇게 프록시 없는 객체 참조 호출이다.

![호출 코드(Calling code)가 pojo.foo()를 호출하면 중간 객체 없이 Plain Object의 foo()가 바로 실행되고 결과가 호출 코드로 돌아가는 직접 참조 호출 구조](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day16-web-aop-plain-pojo-call.png)

*출처: [Proxying Mechanisms — Understanding AOP Proxies, Spring Framework Reference](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html#aop-understanding-aop-proxies) — Copyright © 2005 - Broadcom. All Rights Reserved. (문서 사본은 무료 배포와 저작권 고지 유지 조건으로 허용)*

`inner()`의 애노테이션이 삭제되거나 Bean 등록이 실패한 것이 아니다. 호출이 애노테이션을 해석하고 부가 기능을 적용하는 프록시 경로를 지나지 않은 것이 원인이다.

![시퀀스 다이어그램. 참여자는 테스트, Spring AOP 프록시(TransactionInterceptor), SelfInvocationService target이다. 세 프레임이 위에서 아래로 놓인다. 첫 프레임에서 테스트가 service.inner()를 호출하면 프록시가 @Transactional을 확인해 트랜잭션을 시작하고 target.inner()에 위임한다. isActualTransactionActive()는 true를 돌려주고 프록시가 트랜잭션을 종료한 뒤 active = true를 반환한다. 둘째 프레임에서 service.outer()는 애노테이션이 없어 프록시가 그대로 위임한다. target 안의 this.inner()는 프록시로 다시 들어가지 않으므로 isActualTransactionActive()가 false이고, 테스트는 active = false를 받는다. 셋째 프레임에서 service.transactionalOuter()는 프록시가 트랜잭션을 먼저 시작한다. 내부 this.inner()는 프록시를 우회하지만 이미 열린 트랜잭션 안이므로 true이고, 테스트는 active = true를 받는다. 하단 주석은 세 번째 true가 inner()의 애노테이션 효과가 아니라 바깥 경계의 효과라고 설명한다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day16-aop-self-invocation.png)

> **보장 범위** — `externalCallPassesThroughTransactionalProxy()`의 `assertTrue(service.inner())`와 `selfInvocationBypassesTransactionalProxy()`의 `assertFalse(service.outer())` 두 테스트로 두 경로의 결과 차이까지만 확인했다. `this.inner()` 호출이 바이트코드 수준에서 프록시를 거치지 않는다는 사실은 관찰된 동작(`false`)으로 추정한 것이며, 바이트코드를 직접 디컴파일해 검증하지는 않았다.

### 4) 바깥 비즈니스 메서드의 Transaction Boundary

> **Advice** = 프록시가 target 메서드 호출 전후에 실행하는 부가 기능 코드. 트랜잭션 시작·commit·rollback도 여기 포함된다

우리 코드에서는 `transactionalOuter()`에 애노테이션을 붙여 트랜잭션 경계를 외부에서 호출되는 비즈니스 메서드에 뒀다.

```java
@Transactional
public boolean transactionalOuter() {
    return inner();
}
```

self-invocation 문제를 피하는 첫 번째 기준은 트랜잭션 경계를 외부에서 호출되는 비즈니스 메서드에 두는 것이다.

```text
service.transactionalOuter()
→ 프록시가 바깥 @Transactional 확인
→ 트랜잭션 시작
→ target.transactionalOuter()
→ this.inner()
→ 내부 호출은 프록시 우회
→ 이미 활성화된 트랜잭션 안에서 active=true
```

여기서 `true`는 내부 `inner()`의 애노테이션이 적용됐다는 증거가 아니다. 프록시가 바깥 메서드의 애노테이션을 보고 먼저 트랜잭션을 시작했다는 증거다.

업무 경계와 내부 기능을 서로 다른 Spring Bean으로 분리하는 방법도 있다. Bean A가 Bean B의 트랜잭션 메서드를 호출하면 호출이 Bean B의 프록시를 통과할 수 있다.

최초 작성에서는 `transactionalOuter()`의 애노테이션을 빠뜨렸다. `assertTrue`는 `expected true but was false`로 실패했고, 애노테이션을 추가한 뒤에만 통과했다. 메서드 이름에 `transactional`을 넣거나 의도만 적는 것으로는 트랜잭션이 만들어지지 않는다는 증거가 됐다.

> **보장 범위** — `transactionalOuterStartsTransactionBeforeSelfInvocation()`의 `assertTrue` 통과로 바깥 경계 배치는 코드로 확인했다. 업무 경계와 내부 기능을 별도 Bean으로 분리하는 방법은 이번 실험에서 실행하지 않았다.

### 5) ApplicationContext와 Persistence Context의 관리 대상

> **ApplicationContext** = Spring Bean과 AOP 프록시를 생성·관리하는 IoC 컨테이너

우리 코드에서는 `cancel()`이 조회한 `Reservation`이 영속성 컨텍스트의 관리 대상이 되는 지점을 확인했다.

```java
Reservation reservation = reservationRepository.findById(id) // 영속성 컨텍스트가 관리하는 Entity로 전환
        .orElseThrow(() -> new ReservationNotFoundException(id));
reservation.cancel(cancelReason); // 상태 변경은 dirty checking으로 flush 시 반영
```

트랜잭션이 시작된 뒤 "현재 객체가 영속성 컨텍스트에 올라간다"는 표현은 구분이 필요하다. `SelfInvocationService`, `ReservationService`, Repository, AOP 프록시는 Spring Bean이며 ApplicationContext가 관리한다. 영속성 컨텍스트는 JPA Entity를 관리한다.

```text
ReservationService.cancel() 호출
→ ApplicationContext가 관리하는 프록시 통과
→ target.cancel() 실행
→ findById()로 조회한 Reservation이 영속성 컨텍스트에 등록
→ 상태 변경은 dirty checking으로 flush 시 반영
```

| 컨테이너 | 관리 대상 |
|---|---|
| ApplicationContext | Service, Repository, AOP 프록시 같은 Spring Bean |
| 영속성 컨텍스트 | `Reservation`처럼 JPA로 조회·저장한 `@Entity` |

같은 "컨텍스트"라는 단어가 들어가지만 목적과 관리 대상이 전혀 다르다. 이번 `SelfInvocationService.inner()`는 DB를 조회하지 않고 현재 트랜잭션 활성 여부만 확인한다. 트랜잭션은 열려 있어도 관리할 Entity를 읽거나 저장하지 않았기 때문에, Service 객체가 영속 Entity로 바뀌는 일은 없다.

> **보장 범위** — 오늘 `SelfInvocationService.inner()`는 DB 조회 없이 트랜잭션 활성 여부만 확인했고, Service 객체 자체가 영속성 컨텍스트에 등록되지 않는다는 점은 이 테스트로 관찰했다. `cancel()`에서 조회한 `Reservation`이 dirty checking으로 반영된다는 사실은 Day15에서 확인한 결과를 그대로 인용한 것이며, 오늘 다시 실행하지는 않았다.

### 6) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| AOP | 공통 관심사를 프록시로 분리해 메서드 호출 전후에 적용하는 방식 |
| Proxy | target 앞에서 호출을 가로채 부가 기능을 적용한 뒤 위임하는 대리 객체 |
| Target | 프록시 뒤에서 실제 비즈니스 코드를 실행하는 원본 객체 |
| Advice | 프록시가 호출 전후에 실행하는 부가 기능 코드 |
| Self-Invocation | 같은 객체 내부에서 `this`로 자기 메서드를 직접 호출하는 형태 |
| CGLIB Proxy | 원본 클래스를 상속한 동적 하위 클래스 기반 프록시 방식 |
| ApplicationContext | Spring Bean과 프록시를 생성·관리하는 IoC 컨테이너 |
| Persistence Context | JPA로 조회·저장한 Entity를 관리하는 공간 |

> **더 볼 것**
> - [Spring 선언적 트랜잭션 구현](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html): AOP 프록시와 트랜잭션 부가 기능의 역할
> - [Spring `@Transactional` 사용법](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html): 프록시 모드의 외부 호출 조건과 self-invocation 제한
> - Hibernate LAZY 프록시 — Week C D4에서 다룰 Entity 지연 로딩 장치로, Spring AOP 프록시와 목적이 다름

## 2. 코드 구현

### 1) ReservationService Bean의 Proxy 판별

```java
ReservationService service =
        applicationContext.getBean(ReservationService.class);

assertTrue(AopUtils.isAopProxy(service));
assertTrue(AopUtils.isCglibProxy(service));
assertNotEquals(ReservationService.class, service.getClass());
```

**한 줄씩 보기**

- `getBean(ReservationService.class)`: ApplicationContext에서 Bean 참조(실제로는 프록시)를 꺼낸다.
- `assertTrue(AopUtils.isAopProxy(service))`: 반환된 Bean이 AOP 프록시인지 확인한다.
- `assertTrue(AopUtils.isCglibProxy(service))`: 프록시 구현이 CGLIB(클래스 상속) 방식인지 확인한다.
- `assertNotEquals(ReservationService.class, service.getClass())`: 런타임 클래스가 원본 클래스와 다름을 확인한다.

타입 이름에 `SpringCGLIB`이 포함됐다는 문자열 관찰만으로 끝내지 않았다. Spring이 제공하는 `AopUtils`로 AOP 프록시와 CGLIB 프록시 여부를 각각 검사하고, 실제 클래스가 원본 클래스와 다른지도 확인했다.

### 2) 세 호출 경로의 트랜잭션 활성 상태

```java
public boolean outer() {
    return inner();
}

@Transactional
public boolean inner() {
    return TransactionSynchronizationManager
            .isActualTransactionActive();
}

@Transactional
public boolean transactionalOuter() {
    return inner();
}
```

**한 줄씩 보기**

- `outer()`의 `return inner()`: 애노테이션 없는 메서드 내부에서 `this.inner()`로 이동한다.
- `@Transactional inner()`: 프록시를 통해 호출될 때만 트랜잭션을 새로 연다.
- `isActualTransactionActive()`: 현재 실행 지점의 트랜잭션 활성 여부를 직접 확인한다.
- `transactionalOuter()`: 바깥 메서드에 애노테이션을 둬 self-invocation 이전에 트랜잭션을 연다.

`TransactionSynchronizationManager.isActualTransactionActive()`를 사용해 DB 결과를 간접 추측하지 않고, 해당 실행 지점의 트랜잭션 활성 상태를 직접 확인했다.

최초 작성에서는 `transactionalOuter()`의 애노테이션을 빠뜨렸다. `assertTrue`는 `expected true but was false`로 실패했고, 애노테이션을 추가한 뒤에만 통과했다. 메서드 이름에 `transactional`을 넣거나 의도만 적는 것으로는 트랜잭션이 만들어지지 않는다는 증거가 됐다.

### 3) 자동 검증 결과

| 검증 항목 | 테스트 | 결과 |
|---|---|---|
| 실제 Bean의 CGLIB 프록시 여부 | `reservationServiceIsTransactionalProxy()` | 프록시 판별 3개 assertion 통과 |
| 외부 호출과 self-invocation | `externalCall...`, `selfInvocation...` | `true`, `false` |
| 바깥 트랜잭션 경계 | `transactionalOuterStarts...` | 애노테이션 누락 시 실패, 추가 후 `true` |

자동 테스트로 확인한 것은 위 세 항목이다. 수동 확인은 실행 콘솔에 찍힌 런타임 클래스 이름(`ReservationService$$SpringCGLIB$$0`)이다. 최종 `./gradlew test --rerun-tasks` 실행 결과는 전체 22개 테스트, failures 0, errors 0이다.

## 3. 스스로 답한 질문

### 1) `getBean()`과 Proxy 생성 시점의 구분

**질문.** `getBean(ReservationService.class)`이 호출될 때마다 임시 프록시가 생성되는가?

**A1.** 처음에는 `getBean()`을 통해 임시 객체가 만들어진다고 설명했다. 하지만 기존 Singleton 테스트와 맞지 않는 설명이었다.

프록시는 Bean 생성·후처리 과정에서 준비된다. `getBean()`은 컨테이너가 관리 중인 같은 프록시를 조회한다. 따라서 두 번 조회한 참조는 `assertSame`을 통과한다.

### 2) self-invocation의 트랜잭션 비활성 원인

**질문.** `outer()` 내부의 `inner()`에서 트랜잭션이 비활성인 이유가 `inner()`의 Bean 미등록인가?

**A2.** 처음에는 `inner()`가 Bean으로 등록되지 않아서라고 설명했다. 일반 메서드 하나하나가 별도 Bean이 되는 구조로 잘못 연결한 답이었다.

실제 Bean은 `SelfInvocationService` 객체 전체다. 외부 호출은 그 Bean 앞의 프록시를 지나지만, target 내부의 `this.inner()` 호출은 프록시로 되돌아가지 않는다. 트랜잭션 비활성의 원인은 Bean 등록이 아니라 호출 경로다.

### 3) 트랜잭션과 Persistence Context의 객체 관리 범위

**질문.** 트랜잭션이 시작되면 현재 Service 객체가 영속성 컨텍스트에 등록되는가?

**A3.** Service와 AOP 프록시는 ApplicationContext가 관리하는 Spring Bean이다. 영속성 컨텍스트는 `Reservation`처럼 JPA로 조회하거나 저장한 `@Entity`를 관리한다.

`@Transactional`은 트랜잭션 경계를 제공하지만 임의의 Java 객체를 Entity로 바꾸지 않는다. 오늘 테스트에서는 DB 조회가 없었으므로 활성 트랜잭션 안에서도 관리 대상 Entity가 새로 생기지 않았다.

## 4. 학습 정리와 다음 범위

### 1) 전체 흐름 다시 보기

오늘 확인한 두 호출 경로를 한 장으로 정리하면 아래와 같다. 왼쪽은 target 내부의 `this` 호출이 TransactionInterceptor를 건너뛰는 self-invocation이고, 오른쪽은 외부 호출이 프록시와 TransactionInterceptor를 거쳐 실제 target의 `@Transactional` 메서드로 위임되는 경로다.

![두 호출 경로 비교 그림. 왼쪽 PizzaService 상자에서는 ① 외부 호출이 eatPizza로 들어온 뒤 ② 같은 객체 안의 @Transactional pizza를 직접 호출하며 TransactionInterceptor를 거치지 않는다. 오른쪽 PizzaService (Proxy) 상자에서는 ① 호출이 프록시로 들어오고 ② TransactionInterceptor로 넘어간 뒤 ③ @Transactional pizza가 실행된다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day16-overview-proxy-self-invocation.png)

*출처: [Spring Transactional Rollback Deep Dive](https://hyperconnect.github.io/2025/02/10/spring-transactional-rollback.html) — JunHee Kim, Hyperconnect Tech Blog. 저작권은 원저작자에게 있습니다.*

### 2) 이해의 변화와 남은 것

Day15에는 `@Transactional`을 붙이면 commit과 rollback이 일어난다는 결과를 관찰했다. Day16에는 그 사이에 Spring AOP 프록시가 있다는 구조를 확인했다. 애노테이션은 조건을 선언하고, 프록시와 트랜잭션 부가 기능이 외부 메서드 호출 전후에 실제 트랜잭션 처리를 붙인다.

self-invocation은 애노테이션의 존재보다 호출 경로가 중요하다는 사실을 보여줬다. 같은 `inner()`라도 프록시를 통과한 외부 호출은 `true`, target 안에서 직접 이동한 내부 호출은 `false`였다. 바깥 비즈니스 메서드에 경계를 두자 내부 호출은 여전히 프록시를 우회했지만, 이미 시작된 트랜잭션 안에서 안전하게 실행됐다.

**아직 남은 것**은 여러 트랜잭션 메서드가 서로 호출될 때 기존 트랜잭션에 참여하는 방식이다. 기본 `REQUIRED`와 별도 트랜잭션을 만드는 `REQUIRES_NEW`의 연결 및 커넥션 비용은 Week C D3에서 확인한다.

면접에서 다시 답해볼 항목을 남긴다.

- `@Transactional` 애노테이션과 AOP 프록시의 역할 분담
- self-invocation에서 트랜잭션이 적용되지 않는 호출 경로

---

오늘 공부한 소스코드: `app/src/test/java/com/example/studyroom/StudyRoomApiApplicationTests.java`, `app/src/test/java/com/example/studyroom/service/TransactionProxySelfInvocationTest.java`
