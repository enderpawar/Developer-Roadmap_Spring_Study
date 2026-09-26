# [Spring Study Day 5] IoC와 DI — Constructor Injection과 Singleton Bean

Day4에서 `cancel()`을 Service로 옮기면서 `ReservationService`는 `ReservationRepository` 인터페이스를 생성자로 받는 모양이 됐다. 그런데 Service 코드 어디에도 `new InMemoryReservationRepository()`가 없어서, 생성자 위에 `//원리는 5일 차에서 배우기..`라는 주석을 달아두고 넘어갔었다. 이번에는 그 주석을 지웠다. 객체를 누가 만들어 어디에 넣는지까지만 다루고, scope 종류 전체나 동시성 재현은 범위 밖으로 둔다.

> Service가 구현체를 직접 만들지 않는데도 도는 이유를 IoC와 DI로 나눠서 설명했다. `@Repository`를 떼고 돌려보니 컴파일은 통과하고 컨텍스트 조립이 `NoSuchBeanDefinitionException`으로 멈췄고, 같은 Service Bean을 두 번 꺼내 비교한 `assertSame`은 통과했다. 공유 인스턴스에서 생길 수 있는 경쟁 상태는 설명만 했고 실행으로 재현하지는 않았다.

> **오늘의 흐름** `SpringApplication.run() → Component Scan → Bean 정의 등록 → Repository → Service → Controller 생성자 주입 → Singleton Bean 공유 → 요청 처리`
>
> 이전 Day: Service/Repository 계층 분리와 식별자 도입 — 생성자 주입의 원리는 주석으로 미뤄 둠 (Day4)
> 다음 Day: 1주차 누적시험 — Reference Equality와 Identifier 기준으로 Day1~5를 노트 없이 다시 인출 (Day6)

## 1. 개념 설명

### 1) IoC Container와 객체 조립 순서

> **IoC** = 객체를 생성하고 서로 연결하는 제어권이 애플리케이션 코드가 아니라 Spring 컨테이너(`ApplicationContext`)에 있는 구조

우리 코드에서는 `new`를 부르는 곳이 `main()` 한 줄의 `run()`뿐이고, 나머지 조립은 그 호출이 띄운 컨테이너가 맡는다.

```java
@SpringBootApplication  // 이 클래스의 패키지 com.example.studyroom 아래가 Component Scan 범위
public class StudyRoomApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(StudyRoomApiApplication.class, args); // ApplicationContext 생성·기동
    }
}
```

`ReservationController`는 `ReservationService`가, `ReservationService`는 `ReservationRepository` 구현체가 있어야 동작한다. 누군가는 이 세 객체를 올바른 순서로 만들어 서로 연결해야 한다.

그 일을 각 클래스가 직접 하면 Service는 필드에서 `new InMemoryReservationRepository()`를 호출해야 한다. 그러면 Service는 "무엇이 필요한가"뿐 아니라 "그것을 어떻게 만드는가"까지 알게 되고, 저장 방식이 바뀔 때마다 Service 코드를 열어 `new` 뒤의 클래스 이름을 고쳐야 한다. IoC는 이 조립 책임을 애플리케이션 코드 밖으로 옮긴 것이다.

```text
SpringApplication.run() → ApplicationContext 기동
→ Component Scan: com.example.studyroom 아래에서
  @Repository·@Service·@RestController가 붙은 클래스를 Bean 후보로 수집
→ ReservationService 생성자의 인자 타입 ReservationRepository에 맞는 Bean을 찾음
→ InMemoryReservationRepository Bean 생성
→ 그 참조를 ReservationService(ReservationRepository) 생성자에 전달
→ 만들어진 Service를 ReservationController(ReservationService) 생성자에 전달
→ 완성된 Bean들을 컨텍스트에 보관
```

생성자에 넣을 인자가 먼저 있어야 하므로 생성이 끝나는 순서는 Repository → Service → Controller다. 요청의 호출 방향(Controller → Service → Repository)과 반대다.

"제어가 역전됐다"는 표현은 이 순서의 실행 주체를 가리킨다. 전에는 호출하는 쪽이 부품을 만들었고, 지금은 컨테이너가 부품을 만들어 호출하는 쪽에 넘긴다.

Spring Framework 공식 문서는 애플리케이션 클래스와 설정 메타데이터가 컨테이너에 들어가 완성된 시스템이 되는 이 구조를 다음처럼 그린다.

![Spring IoC 컨테이너 개요도. 위에서 Your Business Objects (POJOs)가, 왼쪽에서 Configuration Metadata가 The Spring Container로 들어가고, 컨테이너가 produces 화살표로 Fully configured system Ready for Use를 만들어 낸다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day05-web-spring-ioc-container.png)

*출처: [Container Overview — Spring Framework Reference, Figure 1. The Spring IoC container](https://docs.spring.io/spring-framework/reference/core/beans/basics.html) — Copyright © 2005 - Broadcom. All Rights Reserved. (문서 사본은 무료 배포와 저작권 고지 유지 조건으로 허용)*

> **보장 범위** — 위 순서는 오늘 코드의 세 Bean에 한정한 흐름이다. Spring Boot는 이 셋 외에도 많은 Bean을 함께 등록하며, 그 전체 순서나 Bean 정의 등록의 내부 단계는 이번에 확인하지 않았다.

### 2) DI와 Constructor Injection

> **DI** = 객체가 필요한 의존성을 직접 만들지 않고 외부에서 전달받는 것. **Constructor Injection**은 그 전달 통로를 생성자 매개변수로 삼는 방식이다.

우리 코드에서는 Day4 주석을 지우고 남은 `ReservationService` 생성자가 그 통로다.

```java
private final ReservationRepository reservationRepository;   // 초기화 후 바뀌지 않는 필드

public ReservationService(ReservationRepository reservationRepository){  // Spring 문법이 없는 평범한 생성자
    this.reservationRepository = reservationRepository;       // 컨테이너가 넘긴 Bean 참조를 보관
}
```

이 생성자에는 **Spring 문법이 하나도 없다.** Spring이 바꾼 것은 생성자의 모양이 아니라 그것을 누가 언제 호출하느냐다. Spring이 없어도 직접 호출할 수 있는 형태이고, Spring은 기동 시점에 그 호출을 대신 수행해 객체 그래프 조립을 자동화한다.

이번에 가장 오래 혼동한 짝이 IoC와 DI였다. 처음에는 두 용어를 같은 것의 다른 이름처럼 묶어 외운 상태였다.

| 구분 | 묻는 것 | 이 프로젝트에서 |
|---|---|---|
| IoC | 조립 책임이 어디에 있는가 | Spring 컨테이너가 세 객체를 만들고 연결 |
| DI | 그 책임을 가진 쪽이 부품을 어떤 통로로 건네는가 | Repository Bean을 Service 생성자 매개변수로 전달 |

생성자 주입을 고른 이유는 세 가지로 정리된다.

- 이 클래스가 없으면 못 도는 의존성이 생성자 시그니처에 타입으로 드러난다
- 받은 값을 `final` 필드에 넣으면 초기화 이후 바뀌지 않는다
- Service는 구현체를 만드는 방법을 모르고, 받은 인터페이스만 사용한다

생성자를 직접 작성할 때는 문법 조건도 세 가지가 맞아야 한다. 이번에 컴파일러가 하나씩 잡아준 조건이다(2절 3)).

```text
생성자 이름 = 클래스 이름
this.x의 x = 그 클래스에 실제로 선언된 필드
매개변수 타입 = 그 필드에 대입 가능한 타입
```

![클래스 다이어그램. «@RestController» ReservationController가 «@Service» ReservationService를, ReservationService가 «interface» ReservationRepository를 각각 생성자 주입으로 참조한다. «@Repository» InMemoryReservationRepository는 그 인터페이스를 «realize»하는데, 화살표가 구현체가 아니라 인터페이스로 향하는 것이 요점이다. Service는 구현체 이름을 모른다. 주석에는 ApplicationContext가 기동 시 Bean을 만들고 생성자 인자 타입에 맞는 Bean을 찾아 넣는다는 것, 기본 scope가 singleton이라 두 번 꺼내도 같은 인스턴스여서 ==가 true이고 그래서 Service가 무상태여야 한다는 것, @Repository를 떼면 넣어줄 Bean이 없어 기동에서 실패한다는 것이 적혀 있다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day05-ioc-di.png)

> **보장 범위** — 오늘 사용한 주입 방식은 생성자 주입 하나다. 필드 주입·setter 주입은 비교해 실행하지 않았고, 생성자가 하나뿐일 때 `@Autowired` 없이 주입되는 규칙도 이번 코드에서 관찰한 결과까지만 다룬다.

### 3) DIP와 구현체 교체 범위

> **DIP** = 상위 정책이 구체 구현이 아니라 추상화에 의존하도록 의존 방향을 설계하는 원칙

우리 코드에서는 Service가 의존하는 것이 `InMemoryReservationRepository`가 아니라 `ReservationRepository` 인터페이스다. 인터페이스 소스 주석에 그 역할 구분이 남아 있다.

```java
//인터페이스 = "What to do(무엇을 할 수 있는지)"만 약속. "How"는 구현체가 정한다.
public interface ReservationRepository {
    Reservation save(Reservation reservation);
    List<Reservation> findAll();
    Reservation findById(Long id);
}
```

그래서 메모리 구현을 DB 구현이나 테스트용 가짜로 바꿔도 Service와 Controller의 코드는 그대로 둘 수 있다. 학습 중에는 이것을 "여러 계층간 코드 수정이 번거롭게 여러번 일어나지 않아도 된다"고 정리했다.

Wikimedia Commons의 DIP 도식은 구체 클래스를 직접 참조하던 의존이 인터페이스를 향하도록 바뀌는 전후를 다음처럼 비교한다.

![의존성 역전 전후 비교도. Figure 1에서는 Package A의 Object A가 Package B의 Object B를 직접 References한다. Figure 2에서는 Object A가 같은 Package A 안의 Interface A를 References하고, Package B의 Object B가 Interface A를 Inherits해서 의존 화살표가 구현체가 아니라 인터페이스로 향한다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day05-web-dependency-inversion.png)

*출처: [File:Dependency inversion.png — Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Dependency_inversion.png) — Kevin Martin (Mrflay), CC BY-SA 4.0*

DIP와 DI는 층위가 다르다.

| 구분 | 다루는 것 | 이 프로젝트에서 |
|---|---|---|
| DIP | **의존 방향**에 대한 설계 원칙 | Service → `ReservationRepository` 인터페이스 |
| DI | 그 방향대로 **실제 객체를 넣어주는 수단** | 컨테이너가 `InMemoryReservationRepository` Bean을 생성자로 전달 |

인터페이스에 의존하도록 설계해도 누군가 구현체를 넣어주지 않으면 실행되지 않는다. 그 "누군가"가 다음 소절의 Bean 그래프 검사로 이어진다.

> **보장 범위** — 이 설계가 보장하는 것은 교체 시 수정 범위가 좁아진다는 것까지다. 오늘은 구현체가 하나뿐이라 실제 교체는 하지 않았고, 구현체가 둘 이상일 때 컨테이너가 어느 것을 고르는지도 범위 밖이다.

### 4) 타입 그래프와 Bean 그래프

> **Component Scan** = 기동 시점에 지정 패키지 아래에서 `@Repository`·`@Service`·`@RestController` 같은 stereotype 애노테이션이 붙은 클래스를 찾아 Bean 후보로 등록하는 과정

우리 코드에서는 `InMemoryReservationRepository`의 첫 줄 애노테이션이 Bean 등록의 근거다. 소스 주석도 이 줄을 "Spring이 관리하는 Bean"이라고 적어 두었다.

```java
@Repository //Spring이 관리하는 Bean, "이 클래스는 저장소 역할의 Bean이다."
public class InMemoryReservationRepository implements ReservationRepository { // 타입 관계는 이 줄이 결정
```

`@Repository` 한 줄을 떼면 어디서 멈추는지가 이번의 중심 실험이었다. 이 실험은 서로 다른 두 검사 단계를 갈라서 보여줬다.

```text
@Repository 제거
→ compileJava 성공 (implements 관계 유지)
→ Component Scan이 InMemoryReservationRepository를 후보로 수집하지 않음
→ ReservationService 생성자 인자 ReservationRepository에 넣을 Bean 없음
→ NoSuchBeanDefinitionException → 컨텍스트 조립 중단 → contextLoads() 실패
```

| 검사 단계 | 확인하는 것 | `@Repository` 제거 시 |
|---|---|---|
| `compileJava` | Java 타입 관계(`implements`, 대입 가능성) | 성공 |
| 컨텍스트 기동(`contextLoads()`) | 생성자 인자마다 넣을 Bean이 있는가 | `NoSuchBeanDefinitionException`으로 실패 |

컴파일러는 타입 그래프를, 컨테이너는 기동 시점에 Bean 그래프를 검사한다. 인터페이스를 구현했다는 사실만으로 Bean이 되지는 않으므로, 애노테이션 누락은 두 번째 검사에서만 드러난다.

![시퀀스 다이어그램. 참여자는 테스트 실행, ApplicationContext, «@Repository» InMemoryReservationRepository, «@Service» ReservationService, «@RestController» ReservationController다. 먼저 compileJava가 성공하고, 테스트가 contextLoads()로 컨텍스트를 기동하면 ApplicationContext가 Component Scan과 생성자 인자 후보 탐색을 수행한다. alt 프레임의 첫 분기 [@Repository 있음]에서는 ① Repository 생성, ② repository를 Service 생성자에 주입, ③ service를 Controller 생성자에 주입한 뒤 기동 성공과 BUILD SUCCESSFUL을 돌려준다. 둘째 분기 [@Repository 제거]에서는 후보 Bean이 없어 NoSuchBeanDefinitionException이 테스트로 전달되고 contextLoads()가 실패한다. 하단 주석은 두 경우 모두 compileJava가 성공했고 사라진 것은 Bean 등록이라고 적는다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day05-bean-assembly.png)

실험 전에는 "애플리케이션 실행 단계에서 실패할 것"이라고 단계는 맞췄다. 다만 이유를 "구현 클래스가 저장소 인터페이스 역할을 잃는다"고 설명했다. Java 역할은 그대로였고, 잃은 것은 Spring Bean 자격이었다.

> **보장 범위** — 실패 원인은 `contextLoads()`의 원인 체인 마지막에서 `NoSuchBeanDefinitionException`을 확인한 데까지다. 앞쪽 래퍼 예외의 전체 메시지는 글에 옮기지 않았고, 실험 뒤 애노테이션을 복구했으므로 이 상태는 커밋에 남아 있지 않다.

### 5) Singleton Bean과 Reference Equality

> **Singleton Bean** = 기본 scope에서 ApplicationContext 하나에 인스턴스 하나만 만들어 보관하고, 조회할 때마다 그 인스턴스를 돌려주는 Bean

우리 코드에서는 테스트가 같은 타입을 두 번 꺼내 참조가 같은지 확인한다.

```java
ReservationService first = applicationContext.getBean(ReservationService.class);  // 보관 중인 인스턴스 A
ReservationService second = applicationContext.getBean(ReservationService.class); // 같은 인스턴스 A
assertSame(first, second);                                                          // 참조 비교 → 통과
```

```text
getBean(ReservationService.class) → 보관 중인 인스턴스 A 반환
getBean(ReservationService.class) → 같은 인스턴스 A 반환
first == second → true
```

실행 전에는 `false`를 예측했다. Day4에서 서로 다른 `Long` 객체를 `==`로 비교했던 경험을 그대로 가져온 예측이었다. 두 결과는 모순되지 않는다.

| 비교 대상 | 두 참조가 가리키는 것 | `==` 결과 |
|---|---|---|
| 서로 다른 `Long` 객체 | 각각 다른 인스턴스 | `false` |
| 두 번 조회한 Service Bean | 컨테이너가 보관한 같은 인스턴스 | `true` |

`==`는 두 경우 모두 참조 비교다. 달라진 것은 연산자의 의미가 아니라 두 변수가 같은 객체를 가리킬 경로가 있느냐였다. CS로 보면 참조 동일성(identity) 판정이고, 값이 같은지(equality)와는 다른 질문이다.

> **보장 범위** — singleton은 JVM 전체에 하나라는 뜻이 아니라 **ApplicationContext 하나에 하나**다. 컨텍스트가 둘이면 인스턴스도 둘일 수 있다. singleton 외의 scope는 이번에 다루지 않았다.

### 6) Stateless Service와 공유 필드

> **Stateless Service** = 요청마다 달라지는 값을 공유 필드에 저장하지 않고, 매개변수·지역변수로만 다루는 Service

우리 코드에서는 `reserve()`가 요청별 값을 매개변수로 받고, 필드에는 주입받은 Repository 참조만 둔다.

```java
public Reservation reserve(String roomName, String requesterName){      // 요청별 값 = 매개변수
    Reservation reservation = new Reservation(roomName,requesterName);  // 호출마다 새 지역변수
    reservation.confirm();
    return reservationRepository.save(reservation);                     // 필드는 주입받은 Repository뿐
}
```

singleton이라는 조립 규칙은 곧바로 설계 제약으로 이어진다. 여러 요청 스레드가 같은 Service 인스턴스를 함께 쓰므로, 그 인스턴스의 필드도 함께 쓴다. 요청마다 달라지는 값을 필드에 두면 다음 순서가 가능하다.

```text
진우 요청 스레드: this.currentRequesterName = "진우"
민수 요청 스레드: this.currentRequesterName = "민수"   ← 덮어씀
진우 요청 스레드: this.currentRequesterName 읽기 → "민수"
```

그래서 무상태는 취향이 아니라 조건이 된다. 요청별 값은 메서드 매개변수·지역변수에 두어 호출마다 분리하고, 필드에는 `reservationRepository`처럼 호출과 무관한 값만 둔다. 스레드 공유 메모리에서 생기는 경쟁 상태(race condition)의 한 예다.

> **보장 범위** — 위 덮어쓰기 순서는 설명으로 짚은 것이고 코드로 재현하지 않았으므로 **미검증**이다. 또 `InMemoryReservationRepository`의 `ArrayList`는 예약을 보관하려고 변경 가능한 상태를 의도적으로 가지므로 "Service 무상태"와 같은 뜻으로 일반화하지 않는다. 이 저장소의 동시성 안전성도 검증하지 않았다.

### 7) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| IoC | 객체 생성·연결의 제어권이 애플리케이션 코드가 아니라 Spring 컨테이너에 있는 구조 |
| ApplicationContext | Bean을 생성·보관·연결하고 조회에 응답하는 Spring의 IoC 컨테이너 |
| Bean | Spring 컨테이너가 생성·보관·연결하는 객체 |
| Component Scan | 기동 시 지정 패키지에서 stereotype 애노테이션이 붙은 클래스를 찾아 Bean 후보로 등록하는 과정 |
| Stereotype Annotation | `@Repository`·`@Service`·`@RestController`처럼 클래스를 Bean 후보로 표시하고 계층 역할을 알리는 애노테이션 |
| DI | 필요한 의존성을 직접 만들지 않고 외부(컨테이너)에서 전달받는 것 |
| Constructor Injection | 의존성을 생성자 매개변수로 전달받는 DI 방식. `final` 필드와 함께 쓴다 |
| Singleton Scope | ApplicationContext 하나에 Bean 인스턴스를 하나만 두는 기본 scope |

> **더 볼 것**
> - [Container Overview — Spring Framework Reference](https://docs.spring.io/spring-framework/reference/core/beans/basics.html): IoC 컨테이너와 설정 메타데이터
> - [Dependency Injection — Spring Framework Reference](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html): 생성자 기반 DI와 생성자 인자 타입 매칭
> - [Bean Scopes — Spring Framework Reference](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html): singleton이 "JVM당 하나"가 아니라 "컨테이너당 하나"라는 근거
> - 아직 안 본 것 — singleton 외의 scope, OCP, 경쟁 상태를 코드로 재현하는 방법

## 2. 코드 구현

### 1) Day4 주석 제거와 Service 생성자

[`4a0219a` 커밋](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/4a0219a)의 `ReservationService` 앞부분이다.

```java
@Service
public class ReservationService{

    private final ReservationRepository reservationRepository;

    public ReservationService(ReservationRepository reservationRepository){
        this.reservationRepository = reservationRepository;
    }
```

**한 줄씩 보기**

- `@Service` — Component Scan이 이 클래스를 Bean 후보로 수집하게 한다. 기동 시점에 한 번 동작한다.
- `private final ReservationRepository reservationRepository;` — 구현체가 아니라 인터페이스 타입의 필드다. `final`이라 생성자에서 한 번 대입된 뒤 바뀌지 않는다.
- `public ReservationService(ReservationRepository reservationRepository)` — 컨테이너가 기동 시점에 호출하는 생성자다. 인자 타입 `ReservationRepository`가 넣을 Bean을 찾는 기준이 된다.
- `this.reservationRepository = reservationRepository;` — 컨테이너가 넘긴 `InMemoryReservationRepository` Bean 참조를 필드에 보관한다.

Day4까지는 이 생성자 위에 `//생성자 주입 : Spring이 자동으로 InMemoryReservationRepository를 찾아 넣어준다.`와 `//원리는 5일 차에서 배우기..` 두 줄이 붙어 있었고, 이번 커밋에서 지웠다. "자동으로 찾아 넣어준다"는 문장은 1절 1)의 조립 순서로 대체됐다.

대신 Service가 아래처럼 썼다면, Service는 구현체를 만드는 방법까지 알게 된다.

```java
private final InMemoryReservationRepository repository = new InMemoryReservationRepository(); // 비교용, 실행하지 않음
```

`ReservationController` 생성자는 들여쓰기만 정리했다.

### 2) `@Repository` 제거 실험

`InMemoryReservationRepository`에서 애노테이션만 제거하고 `compileJava`와 `test`를 차례로 실행했다. `compileJava`는 성공했고, `contextLoads()`가 실패했다. 원인 체인 마지막에 다음 예외가 있었다.

```text
NoSuchBeanDefinitionException
```

애노테이션을 되돌린 뒤 전체 테스트는 다시 `BUILD SUCCESSFUL`이었다. 복구했으므로 이 실험은 커밋에 남지 않았다.

### 3) 생성자 독립 작성의 컴파일 오류 세 개

완성 예제 읽기 → 대입문 한 줄 채우기 → Controller 생성자 전체 작성을 거친 뒤, `ReservationService` 생성자를 혼자 다시 썼다. 컴파일러가 세 번 잡아줬다.

```text
invalid method declaration; return type required   ← 생성자 이름을 ReservationRepository로 적음
cannot find symbol                                 ← 없는 this.reservationService 필드를 사용
incompatible types                                 ← ReservationService 타입 매개변수를 Repository 필드에 대입
```

원인은 하나였다. Controller 생성자의 모양을 옮겨오면서 클래스명·필드명·의존 타입을 Service 쪽으로 바꾸지 않았다. 세 오류를 메시지 순서대로 고친 뒤 전체 테스트가 통과했다.

### 4) 자동 검증 결과

Singleton 동일성을 확인하는 테스트를 `StudyRoomApiApplicationTests`에 하나 추가했다.

```java
@Test
void reservationServiceBeanIsSingleton() {
    ReservationService first = applicationContext.getBean(ReservationService.class);
    ReservationService second = applicationContext.getBean(ReservationService.class);

    assertSame(first, second);
}
```

| 확인한 것 | 방법 | 결과 |
|---|---|---|
| Bean 등록 누락 시 실패 지점 | 수동 확인 — `@Repository` 제거 후 `compileJava`·`test`, 확인 뒤 복구 | 컴파일 성공, `contextLoads()` 실패, `NoSuchBeanDefinitionException` |
| 같은 Service Bean 두 번 조회 | 자동 테스트 — `reservationServiceBeanIsSingleton()` | `assertSame(first, second)` 통과 |
| 최종 객체 그래프 | 자동 테스트 — `./gradlew test` 전체 | `BUILD SUCCESSFUL` |

**미검증** — 공유 Service 필드가 만드는 경쟁 상태, `InMemoryReservationRepository`의 `ArrayList` 동시성. 둘 다 실행으로 재현하지 않았다.

오늘 코드는 [`4a0219a` 커밋](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/4a0219a)에 있다.

## 3. 스스로 답한 질문

### 1) IoC와 DI의 구분

**질문.** 현재 코드에서 IoC와 DI는 각각 무엇을 가리키는가?

**A1.** 처음 답은 **"잘 모르겠다"**였다. 두 용어를 같은 것의 다른 이름처럼 묶어서 외워둔 상태라, 하나를 설명하려 하면 다른 하나의 설명이 나왔다.

빈칸을 채우며 다시 세운 답은 이렇다. IoC는 제어권이 어디로 옮겨갔는가의 문제로, Controller·Service·Repository의 생성과 연결을 Spring 컨테이너가 맡는다. DI는 그 제어권을 가진 쪽이 부품을 건네는 방식이고, 여기서는 Repository 구현 Bean을 Service 생성자 매개변수로 넘기는 것이다.

힌트를 받은 직후에 맞춘 답이라 진짜 인출인지 확신이 없어서 8/2 재시험 항목으로 걸어뒀다.

### 2) Singleton Bean의 `==` 비교 결과

**질문.** 같은 Bean을 두 번 꺼내 `==`로 비교하면 결과는 무엇인가?

**A2.** 실행 전 예측은 `false`였다. 서로 다른 `Long` Wrapper 객체를 `==`로 비교했던 경험을 그대로 가져다 붙였다. 실제로는 `assertSame(first, second)`가 통과했다.

틀린 것은 `==`의 의미가 아니라 비교 대상에 대한 가정이었다. 두 번의 `getBean()`이 같은 인스턴스를 돌려줬다. 앞으로 `==` 결과를 예측할 때는 연산자만 보지 말고 "두 변수가 같은 객체를 가리킬 경로가 있는가"를 먼저 확인한다.

### 3) Singleton Service의 요청별 필드

**질문.** Singleton Service에 요청별 `currentRequesterName`을 필드로 두면 무엇이 문제인가?

**A3.** 처음 답은 **"요청에 혼선이 생길 수 있다"**였다. 방향은 맞았지만 무엇이 공유되는지가 빠져 있었다.

공유되는 것은 Service 인스턴스이고, 따라서 그 필드도 요청 스레드들이 함께 쓴다. 한 요청이 쓴 값을 다른 요청이 덮어쓰면 첫 요청이 잘못된 이름을 읽을 수 있다. 이 순서는 설명으로 짚었을 뿐 코드로 재현하지 않았으므로 "몇 번 중 몇 번" 같은 수치는 쓰지 않는다.

## 4. 학습 정리와 다음 범위

### 1) 전체 흐름 다시 보기

오늘 따라간 경로를 기동부터 요청 처리까지 한 장으로 모으면 다음과 같다. ②~⑦은 기동 시점에 한 번 일어나고, ⑧의 요청은 ⑦에 보관된 같은 인스턴스를 재사용한다.

![전체 흐름도. ① 애플리케이션 시작에서 SpringApplication.run()이 호출되면 ApplicationContext 기동 영역으로 들어가 ② Component Scan이 @Repository·@Service·@RestController 클래스를 수집하고 ③ Bean 정의를 등록한다. 이어 ④ InMemoryReservationRepository, ⑤ ReservationService(ReservationRepository), ⑥ ReservationController(ReservationService) 순서로 생성되며 앞의 Bean이 다음 생성자에 주입된다. 세 Bean은 ⑦ Singleton Bean 보관 영역으로 모이고, getBean(ReservationService.class)을 두 번 호출해도 assertSame이 통과한다. @Repository를 제거하면 ④에서 빨간 점선으로 빠져 NoSuchBeanDefinitionException으로 기동이 실패한다. ⑧ 요청 처리 영역에서는 Client의 POST /reservations가 DispatcherServlet을 거쳐 ReservationController.reserve(), ReservationService.reserve(), InMemoryReservationRepository.save()로 이어지며 보관된 같은 인스턴스를 사용한다. 하단 주석은 생성 순서가 Repository → Service → Controller이고 요청의 호출 순서는 그 반대라고 적는다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day05-overview-ioc-container.png)

### 2) 이해의 변화와 남은 것

시작할 때는 생성자 주입을 "Spring이 제공하는 특별한 생성자"쯤으로 생각했다. 확인한 것은 반대였다. 생성자는 평범한 Java 생성자이고, 달라진 것은 그것을 호출하는 주체다. `@Repository` 제거 실험이 그 경계를 보여줬다. 컴파일러는 `implements` 관계까지만 보고, Bean 그래프 조립은 컨텍스트 기동이라는 다른 단계에서 벌어진다.

`==` 예측이 틀린 것도 같은 종류의 교정이었다. 연산자의 의미는 처음부터 맞게 알고 있었고, 몰랐던 쪽은 컨테이너가 같은 인스턴스를 돌려준다는 조립 규칙이었다. singleton을 "하나만 만든다"로 외우는 것과 "그래서 필드를 공유한다"까지 잇는 것은 다른 일이었다.

**아직 남은 것**은 두 가지다. 첫째, 공유 인스턴스의 경쟁 상태와 `ArrayList` 동시성은 설명만 하고 재현하지 않았다. 동시성은 이 5주 트랙에서 다루지 않기로 해 **고치지 않을 것**으로 두고 미검증 표시만 남긴다. 둘째, 이번에 늘어난 자동 테스트는 Bean 동일성 하나뿐이라 HTTP 응답 계약은 여전히 회귀를 잡지 못한다. **나중에 고칠 것(Week A D7)**으로 분류했고, 이후 Day07에서 MockMvc로 예약 API의 200·400·404를 고정하면서 해소됐다.

다음은 Week A D6 누적시험이다. 그 전에 힌트가 필요했던 IoC·DI 구분, Singleton 동일성, 생성자 구조를 8/2에 다시 인출한다.

면접에서 다시 답해볼 항목을 남긴다.

- 생성자 주입과 필드에서 직접 `new`로 만드는 방식의 책임 차이
- singleton Bean이 무상태여야 하는 조건

---

오늘 공부한 소스코드: `app/src/main/java/com/example/studyroom/service/ReservationService.java`, `app/src/main/java/com/example/studyroom/controller/ReservationController.java`, `app/src/test/java/com/example/studyroom/StudyRoomApiApplicationTests.java` ([`4a0219a`](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/4a0219a))
