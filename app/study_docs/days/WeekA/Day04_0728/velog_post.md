# [Spring Study Day 4] Service/Repository Separation of Concerns — Identifier 도입과 Wrapper 값 비교

Day3까지 `ReservationController`가 요청을 받는 일부터 `Reservation` 객체를 만들고 확정하는 일까지 전부 맡고 있었다. 이번에는 그 일을 `ReservationService`와 `ReservationRepository`로 나누고, 먼저 옮긴 `reserve()`에 이어 `cancel()`을 같은 패턴으로 옮기는 독립 변형을 마무리했다. 계층을 나눈 뒤 드러난 "취소할 예약의 지목 수단"이 이 글의 중심 질문이다. 저장소는 아직 메모리이고 JPA·DB는 범위 밖이다.

> `cancel()`을 `reserve()`와 똑같은 패턴으로 옮겼더니 예약이 취소되는 대신 하나 더 생겼다. 원인은 `new Reservation(...)`이 매번 새 인스턴스를 만드는데 저장소에는 둘을 구별할 식별자가 없었다는 것이었고, `Reservation`에 `id`를 넣고 `findById()`로 찾아 고치는 흐름으로 바꿔 curl로 reserve→cancel까지 확인했다. 그 과정에서 `Long`의 `==` 비교 함정과, 컴파일·테스트가 초록불이어도 요청을 실제로 던져야만 드러나는 `@PathVariable` 불일치를 함께 겪었다.

> **오늘의 흐름** `POST /reservations/cancel/{id} → ReservationController → ReservationService → ReservationRepository.findById() → Reservation.cancel() → 응답`
>
> 이전 Day: `@Valid`와 `@RestControllerAdvice`로 빈 입력을 400으로 막는 단계 (Day3)
> 다음 Day: Service가 구현체를 직접 만들지 않아도 도는 이유 — IoC·DI와 Singleton Bean (Day5)

## 1. 개념 설명

### 1) Separation of Concerns와 변경 이유

> **Separation of Concerns** = 서로 다른 이유로 바뀌는 코드를 서로 다른 클래스·계층에 두는 설계 원칙

우리 코드에서는 취소 요청이 세 계층을 지나고, Service에는 "찾아서 바꾼다"는 규칙만 남는다.

```java
public Reservation cancel(Long id){                                  // ReservationService
    Reservation reservation = reservationRepository.findById(id);    // 저장소 접근은 Repository에 맡긴다
    reservation.cancel();                                            // 상태 변경은 도메인 객체가 한다
    reservationRepository.save(reservation);
    return reservation;                                              // 응답 문자열은 Controller가 만든다
}
```

Day3의 `ReservationController.reserve()`는 요청 본문을 받고, `new Reservation(...)`으로 객체를 만들고, `confirm()`으로 상태를 바꾼 뒤 응답 문자열까지 만들었다. 요청 형식이 바뀌어도, 예약 규칙이 바뀌어도, 저장 방식이 생겨도 이 한 메서드를 고쳐야 했다.

나누는 기준은 **"무엇이 바뀌면 이 파일을 고치게 되는가"**다. 클래스마다 변경 이유를 하나만 두라는 것이 단일 책임 원칙(SRP)이고, 이를 어기면 CS 용어로 응집도가 낮은 상태가 된다.

| 계층 | 책임 | 바뀌는 계기 |
|---|---|---|
| `ReservationController` | HTTP 입출력(경로·본문 → 인자, 결과 → 응답) | 요청·응답 형식 변경 |
| `ReservationService` | 상태 변경 규칙(확정, 취소) | 예약 규칙 변경 |
| `ReservationRepository` | 저장소 접근(저장, 조회) | 저장 방식 변경(메모리 → DB) |

```text
POST /reservations/cancel/1
→ ReservationController.cancel(@PathVariable Long id)   경로에서 id=1을 꺼낸다
→ ReservationService.cancel(1)                          규칙: 찾아서 상태를 바꾼다
→ ReservationRepository.findById(1)                     What만 선언된 계약
   └ InMemoryReservationRepository.findById(1)          How: ArrayList를 순회
→ reservation.cancel()                                  confirmed: true → false
→ ReservationRepository.save(reservation)
→ Controller가 결과 객체로 응답 문자열 생성
```

분리의 실무적 이득은 테스트에서 먼저 드러난다. 규칙이 Controller 안에 있으면 테스트마다 가짜 HTTP 요청을 만들어야 하지만, Service로 분리하면 HTTP 없이 규칙만 호출할 수 있다.

> **보장 범위** — 계층 분리가 보장하는 것은 변경의 파급 범위이고 동작의 정확성은 아니다. `cancel()`을 올바른 계층으로 옮겼는데도 예약이 하나 더 생긴 것이 그 증거다(2절 2)). HTTP 없이 Service만 호출하는 테스트는 설명만 했고 오늘 작성하지 않았다.

### 2) Repository 인터페이스와 DIP

> **DIP** = 상위 모듈(Service)이 하위 모듈의 구체 구현이 아니라 추상화(인터페이스)에 의존하게 하는 설계 원칙

우리 코드에서는 `ReservationService`의 필드와 생성자 파라미터 타입이 모두 인터페이스다. `InMemoryReservationRepository`라는 이름은 Service 어디에도 나오지 않는다.

```java
//인터페이스 = "What to do(무엇을 할 수 있는지)"만 약속. "How"는 구현체가 정한다.
public interface ReservationRepository {
    Reservation save(Reservation reservation);
    Reservation findById(Long id);                       // 오늘 추가한 계약
}
@Repository //Spring이 관리하는 Bean, "이 클래스는 저장소 역할의 Bean이다."
public class InMemoryReservationRepository implements ReservationRepository { ... }
```

구현체의 `@Override` 자리에는 "메서드를 여기서 override로 재정의해주는거지. 즉 How를 정의해준다!"라고 적었다.

```text
ReservationService ──의존──▶ «interface» ReservationRepository
                                      ▲
                           InMemoryReservationRepository (implements)
```

상위 정책(Service)과 하위 구현(메모리 저장소)이 모두 인터페이스를 향한다. 의존 화살표가 구현체에서 멈추지 않고 추상화로 모이므로, 구현체를 바꿔도 Service는 바뀌지 않는 느슨한 결합이 된다.

여기서 혼동한 짝이 있다. "Repository의 책임"을 처음에 **"인터페이스 역할 수행"**이라고 답했다(3절 1)).

| 구분 | 내용 | 이 프로젝트에서 |
|---|---|---|
| 책임 | 이 계층이 맡은 일 | 저장소 접근(저장·조회) |
| 구현 방식 | 그 책임을 표현한 형태 | 인터페이스 + 구현체 |

인터페이스라는 것은 책임을 담는 형태일 뿐이다. 메모리가 DB로 바뀌어도 "저장소 접근"이라는 책임은 그대로 남는다.

> **보장 범위** — 이 설계가 약속하는 것은 "구현체를 바꿔도 Service는 안 바뀐다"인데, 오늘은 구현체가 하나뿐이라 실제 교체는 해보지 않았다. JPA 기반 구현으로 바꾸는 Week B가 이 주장의 시험대다. 실제 구현체를 Service 생성자에 넣어주는 주체도 아직 열어보지 않았다. 소스에 `//원리는 5일 차에서 배우기..` 주석을 남기고 Day5로 미뤘다.

### 3) Domain Model의 상태와 Encapsulation

> **Domain Model** = 상태와 그 상태를 바꾸는 규칙(business rule)을 함께 가진 객체

우리 코드에서는 `Reservation`이 record가 아니라 class다. 소스 주석에 이유를 적어두었다.

```java
// record 형태가 아님. 상태가 바뀔 수 있어야하니까.
public class Reservation{
    private final String roomName;          // 바뀌지 않는 값
    private boolean confirmed;              // 바뀌어야 하는 상태
    public void confirm() { this.confirmed = true; }   // 변경 통로를 메서드로 캡슐화
    public void cancel() { this.confirmed = false; }   // 오늘 canceled()에서 이름을 바꿈
```

`roomName`, `requesterName`은 `final`이지만 `confirmed`는 바뀌어야 한다. 그 변경 통로를 필드 직접 접근이 아니라 `confirm()`·`cancel()` 메서드로 막아 두었다. 생성 직후 `confirmed`는 `false`이고, Service가 `confirm()`을 호출해야 `true`가 된다.

```text
new Reservation(...)  → confirmed=false
→ Service가 confirm() → confirmed=true
→ Service가 cancel()  → confirmed=false
```

> **보장 범위** — 캡슐화는 "상태를 어떤 메서드로 바꾸는가"만 정한다. "어느 예약의 상태를 바꾸는가"는 정하지 않는다. 바꿀 대상을 지목하는 수단이 1절 4)의 Identifier다.

### 4) Identifier와 객체 동일성

> **Identifier** = 값이 같아 보여도 서로 다른 레코드를 구별하는 고유 값. DB의 기본키(PK)와 같은 자리다

우리 코드에서는 `Reservation`에 `id`를 넣고, 저장소가 처음 저장될 때만 번호를 부여한다.

```java
private Long id; // ... final 아님. 저장되기 전에는 아직 값이 없음     (Reservation)

public Reservation save(Reservation reservation) {              // InMemoryReservationRepository
    if (reservation.getId() == null){
        reservation.assignId(nextId++);                          // 처음 저장될 때만 번호 부여
    }
    store.add(reservation);                                      // id 유무와 관계없이 추가
```

`cancel()`을 처음 옮길 때는 `roomName`과 `requesterName`으로 예약을 가리키려 했다. 코드는 `new Reservation(roomName, requesterName)`을 만들어 `cancel()`한 뒤 저장했다.

```text
이름 기반 cancel("301호", ...)
→ new Reservation("301호", ...)       새 인스턴스 r2 (confirmed=false)
→ r2.cancel()                          r2만 false
→ save(r2) → store.add(r2)             store = [r1(true), r2(false)]
→ 기존 예약 r1은 confirmed=true 그대로
```

값이 같아도 자바에서 `new`는 매번 다른 객체를 만든다. 저장소에도 "이미 있으면 갱신"이라는 분기가 없었다. **`Reservation`에는 "이게 바로 그 예약이다"라고 지칭할 수단이 없었다.** 기본키가 없는 테이블과 같은 상태였다.

```text
reserve() → new Reservation(...) → confirm()
→ save(): getId() == null 이므로 assignId(nextId++) → id=1
→ store.add(r1), 응답에 "예약 번호1-..." 노출

cancel(1) → findById(1): store를 돌며 getId().equals(1)인 r1 반환
→ r1.cancel(): 저장돼 있던 그 객체의 confirmed가 false
→ save(r1)
```

`id`가 `final`이 아닌 이유는 저장 전에는 값이 없기 때문이다. 그래서 `save()`는 `getId() == null`일 때만 번호를 부여한다. JPA에서는 `@GeneratedValue`가 이 역할을 맡는다(Week B 예정).

| 구분 | 판단 기준 | 오늘 코드에서 |
|---|---|---|
| 값이 같은 객체 | 필드 값 비교 | 이름 기반 `cancel()`의 r1, r2 |
| 같은 대상 | 식별자 비교 | `findById(1)`이 돌려준 r1 |

![객체 다이어그램 두 장. 위는 이름으로 취소했을 때로, store의 [0]과 [1]이 각각 r1과 r2라는 서로 다른 Reservation 인스턴스를 가리킨다. 둘 다 roomName이 "301호"로 값은 같지만 r1은 confirmed=true, r2는 false다. 취소한 건 r2뿐이라 기존 예약 r1은 그대로다. 아래는 id로 취소한 뒤로, 인스턴스는 id=1인 r1 하나인데 store의 [0]과 [1] 두 링크가 모두 그 하나를 가리킨다. save()가 ID 유무와 무관하게 store.add()를 실행하기 때문인데, 원소 수를 세는 테스트도 findAll() 엔드포인트도 없어 실제로 그런지는 확인하지 않았다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day04-identity-store.png)

값이 같다는 것과 같은 대상이라는 것은 다르다. 상태를 바꾸려면 먼저 식별자로 대상을 지목해야 하고, 지목 수단이 없으면 "갱신"은 "추가"가 된다.

> **보장 범위** — 식별자 도입이 보장하는 것은 "지목한 그 객체의 상태를 바꾼다"까지다. 오늘 코드에는 빈틈이 둘 남았다. 첫째, `save()`가 id 유무와 관계없이 `store.add()`를 호출해 찾아온 r1을 다시 저장하면 같은 참조가 두 번 들어갈 수 있다. 원소 수를 셀 수단이 없어 **미검증**이다. 둘째, `findById()`는 없는 id에 `null`을 반환하고 `ReservationService.cancel()`은 확인 없이 `reservation.cancel()`을 호출한다. 두 빈틈은 이후 Day07에서 "기존 id는 교체"와 `Optional`·`ReservationNotFoundException`으로 바뀌었다.

### 5) Reference Equality와 Value Equality

> **Reference Equality** = 두 참조가 같은 인스턴스를 가리키는가(`==`), **Value Equality** = 두 객체가 담은 값이 같은가(`.equals()`)

우리 코드에서는 `InMemoryReservationRepository` 한 파일 안에 두 비교가 나란히 있다.

```java
if (reservation.getId() == null){          // save(): 참조가 비어 있는가 → ==
...
for(Reservation r : store){
    if(r.getId().equals(id)){              // findById(): 담은 값이 같은가 → .equals()
        return r;
```

`findById()`를 처음 쓸 때는 `if(r.getId() == id)`로 비교했다. `Long`은 원시타입 `long`이 아니라 객체(Wrapper)라서, 객체끼리의 `==`는 값이 아니라 같은 인스턴스인지를 본다.

```text
r.getId() == id       → 두 Long 참조가 같은 인스턴스인가
r.getId().equals(id)  → 두 Long이 담은 long 값이 같은가
```

| 코드 | 묻는 것 | 맞는 비교 |
|---|---|---|
| `reservation.getId() == null` | 참조가 비어 있는가 | 참조 비교 `==` |
| `r.getId().equals(id)` | 담은 값이 같은가 | 값 비교 `.equals()` |

`==`가 위험한 이유는 틀린 결과가 항상 드러나지는 않는다는 데 있다. `Long.valueOf(long)`은 `-128`~`127` 범위의 값을 항상 캐시한다. `nextId++`로 만든 `long`이 `Long`으로 박싱될 때 이 캐시를 타면, id가 1, 2, 3인 동안에는 같은 값의 `Long`이 같은 인스턴스가 되어 `==`가 우연히 통과한다.

`String`을 `==`로 비교하면 안 되는 것과 같은 함정이지만 이유가 완전히 같지는 않다. `String`은 컴파일타임 상수 인터닝 때문에, `Long`은 `valueOf`의 캐시 때문에 `==`가 우연히 맞을 수 있다.

> **보장 범위** — 작은 데이터로 테스트하면 숨어 있다가 값이 커지면 터지는 종류의 버그다. id 128 이상에서 `==`가 실제로 `false`가 되는 반례는 이번에 실행하지 않았으므로 **미검증**이다.

### 6) URI Template Variable과 검사 단계의 경계

> **URI Template Variable** = 매핑 경로 안의 `{id}`처럼, 요청 URL의 해당 자리 값을 꺼내 메서드 파라미터로 넘기는 변수

우리 코드에서는 취소할 대상을 클라이언트가 지목하도록 id를 URL 경로에 싣는다.

```java
@PostMapping("/reservations/cancel/{id}")    // {id}가 템플릿 변수
public String cancel(@PathVariable Long id) {  // 요청 시 "1" → Long 1로 변환되어 들어온다
    Reservation reservation = reservationService.cancel(id);
```

경로가 자원을 식별한다는 REST의 URI 설계와 연결되는 지점이다. `reserve()` 응답에는 `예약 번호1-...`처럼 부여된 id를 노출해 다음 요청에 쓸 수 있게 했다.

```text
요청 POST /reservations/cancel/1 도착
→ Spring MVC가 매핑 경로 "/reservations/cancel/{id}"와 대조
→ URI 템플릿 변수 id = "1" 추출
→ @PathVariable Long id 파라미터에 "1"을 Long으로 변환해 전달
→ ReservationController.cancel(1) 실행
```

매핑 경로에 `{id}`가 없으면 셋째 단계에서 꺼낼 변수가 없다. 이 불일치는 자바 문법으로는 아무 문제가 없다. 애노테이션의 문자열 값과 파라미터 이름을 대조하는 일은 컴파일러가 아니라, 요청을 처리하는 Spring MVC의 인자 해석 단계(`HandlerMethodArgumentResolver`)가 한다.

| 검사 단계 | 잡은 것 | 못 잡은 것 |
|---|---|---|
| `compileJava` | 괄호 짝, 모든 경로의 return | `{id}` 누락, `null` 반환의 의미 |
| `test` (`contextLoads()`뿐) | 컨텍스트 기동 | 경로 바인딩, 응답 본문 |
| 실제 요청(curl) | `{id}` 누락 → 500 | 저장소 원소 수 |

컴파일러는 "모든 경로가 리턴하는가"는 물었지만 "못 찾았을 때 무엇을 리턴해야 하는가"는 묻지 않았다. 테스트 역시 등록된 테스트가 묻는 것에만 답했다.

> **보장 범위** — 빌드 성공은 문법과 등록된 테스트가 묻는 범위의 답이다. API가 계약대로 동작하는지는 그 경로로 요청이 실제로 들어와야 드러난다. 오늘 경로 바인딩은 curl 수동 호출로만 확인했고, 이를 고정하는 요청 수준 테스트는 없다.

### 7) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| Layered Architecture | Controller·Service·Repository처럼 책임별 계층을 두고 위에서 아래로만 호출하게 하는 구조 |
| Service Layer | 상태 변경 규칙을 수행하며 Controller와 Repository 사이를 조율하는 계층. `@Service`로 Bean 등록한다 |
| Repository | 저장소 접근(저장·조회)을 맡는 계층. `@Repository`로 Bean 등록하며 인터페이스 뒤에 구현을 숨긴다 |
| Stereotype Annotation | `@Controller`·`@Service`·`@Repository`처럼 클래스의 역할을 표시하면서 Bean 등록 대상으로 만드는 애노테이션 |
| URI Template Variable | 매핑 경로의 `{id}` 자리 값을 요청 시점에 꺼내 `@PathVariable` 파라미터로 넘기는 변수 |
| HandlerMethodArgumentResolver | 요청의 경로·본문 등에서 Controller 메서드 파라미터 값을 만들어 넣는 Spring MVC 구성 요소 |

> **더 볼 것**
> - [Mapping Requests — Spring Framework Reference](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html): URI 템플릿 변수와 `@PathVariable`의 관계
> - [JLS 5.1.7 Boxing Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.7): 박싱 결과의 `==` 동일성 규칙
> - [`Long.valueOf(long)` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Long.html#valueOf(long)): `-128`~`127` 범위를 항상 캐시한다는 명세
> - 아직 안 본 것 — 생성자 주입이 동작하는 원리(Day5), REST에서의 자원 식별 규칙, 구현체를 JPA로 교체하기(Week B)

## 2. 코드 구현

### 1) `reserve()` 경로의 계층 분리

먼저 끝낸 `reserve()` 경로다. Controller가 직접 하던 `new Reservation(...)`과 `confirm()`이 Service로 옮겨졌고, 이어서 `cancel()`을 옮긴 뒤 응답에 id를 넣었다.

```java
@PostMapping("/reservations")
public String reserve(@RequestBody @Valid ReservationRequest request) {
    Reservation reservation = reservationService.reserve(request.roomName(), request.requesterName());
    return "예약 번호" + reservation.getId() + "-" + reservation.getRequesterName() + "님이 "
            + reservation.getRoomName() + " 예약 완료 (확정: " + reservation.isConfirmed() + ")";
}
```

**한 줄씩 보기**

- `@PostMapping("/reservations")` — 애플리케이션 시작 시 "POST /reservations → 이 메서드" 매핑으로 등록된다.
- `@RequestBody @Valid ReservationRequest request` — 요청 시 JSON 본문을 record로 바꾸고, Day3의 `@NotBlank` 검사를 거친 뒤에야 메서드가 호출된다.
- `reservationService.reserve(...)` — Controller는 DTO에서 값만 꺼내 넘긴다. 객체 생성과 `confirm()`은 Service 안에서 일어난다.
- `reservation.getId()` — Service가 돌려준 객체에는 `save()`가 부여한 id가 이미 들어 있어, 다음 취소 요청에 쓸 번호를 응답에 노출한다.
- 문자열 연결 — 응답 형식은 Controller의 책임이라 여기 남는다.

`cancel()`도 같은 모양으로 옮기면 될 줄 알았다.

### 2) 이름 기반 `cancel()`의 중복 생성

방을 하나 확정한 다음 같은 `roomName`/`requesterName`으로 취소를 호출하면, 기존 예약을 찾아 `confirmed`만 `false`로 바뀔 것이라고 예측했다. 실제로는 새 예약이 하나 더 생겼고, 원래 예약은 `confirmed: true` 그대로 남았다.

```java
// 커밋하지 않은 중간 코드 — explain-log 기록을 바탕으로 옮김
public Reservation cancel(String roomName, String requesterName){
    Reservation reservation = new Reservation(roomName, requesterName); // 새 객체
    reservation.cancel();
    return reservationRepository.save(reservation);
}
```

`findById` 없이 새 인스턴스를 만들어 저장했기 때문이다. `save()`에는 `store.add(...)`만 있었다. 1절 4)의 Identifier가 이 실패에서 출발했다.

### 3) Identifier 도입 과정의 컴파일 오류

`Reservation`에 `id`와 `assignId()`/`getId()`를 추가하고 `findById()`를 작성하자 컴파일 오류가 두 단계로 났다.

1. `if(r.getId() == id)){`의 괄호 개수 불일치 → `illegal start of expression`. 괄호 짝이 맞지 않으면 컴파일러는 그 지점부터 문법을 파싱하지 못한다.
2. 루프가 끝까지 돌고도 못 찾은 경우의 리턴 누락 → `missing return statement`. 메서드의 모든 실행 경로가 값을 리턴해야 한다는 규칙이다.

```java
public Reservation findById(Long id) {
    for (Reservation r : store) {
        if (r.getId().equals(id)) {
            return r;
        }
    }
    return null;
}
```

비교는 `==`에서 `.equals()`로 바꿨다. `return null`로 문법 조건은 채웠지만, **예약이 없을 때 무엇을 할지는 여전히 정하지 않은 것**이다. 이 빈틈은 4절 2)에 적었다.

### 4) `@PathVariable` 경로 불일치의 런타임 500

`@PathVariable Long id`를 컨트롤러 파라미터에 추가했는데, `@PostMapping` 경로에는 `{id}`를 넣지 않은 채 커밋 직전까지 갔다. 컴파일이 통과했으니 정상 동작할 것이라고 예측했다. `./gradlew compileJava`와 `./gradlew test`는 둘 다 성공이었다. 앱을 띄워 요청을 보내자 결과가 달랐다.

```text
POST /reservations/cancel → 500
{"error":"Required URI template variable 'id' for method parameter type Long is not present"}
```

요청이 들어와 Spring이 URI에서 값을 채우려 할 때 나는 런타임 오류다. `@PostMapping("/reservations/cancel/{id}")`로 고친 뒤 정상 동작했다.

### 5) 자동 검증 결과

| 구분 | 방법 | 결과 |
|---|---|---|
| 자동 테스트 | `./gradlew compileJava`, `./gradlew test` | 성공. 단, 테스트는 `contextLoads()` 하나라 `@PathVariable` 불일치를 잡지 못함 |
| 수동 확인 | `bootRun` 후 curl로 reserve → cancel | 아래 응답 확인 |
| 미검증 | 취소 뒤 저장소 원소 수, 빈 문자열 요청의 `@Valid` 400 | 실행하지 않음 |

```text
POST /reservations {"roomName":"A101","requesterName":"tester"}
→ 예약 번호1-tester님이 A101 예약 완료 (확정: true)

POST /reservations/cancel/1
→ tester님이A101 예약을 취소하셨습니다 (확정 : false)
```

`findAll()`을 노출하는 엔드포인트가 없어서 `save()`가 같은 참조를 한 번 더 넣었는지는 확인할 방법이 없었다. 컨트롤러에는 빈 문자열을 보내면 `@Valid`가 400을 낼 것 같다는 추측 주석이 남아 있지만, 그 요청은 보내지 않았으므로 응답 본문은 추측하지 않는다.

오늘 코드는 [`e22bb34` 커밋](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/e22bb34)에 있다. 앞선 `reserve()` 경로 분리는 [`d6320e2`](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/d6320e2)다.

## 3. 스스로 답한 질문

### 1) Repository의 책임과 구현 방식의 구분

**질문.** Controller가 하던 일을 Service와 Repository로 나눴다. Repository의 책임은 무엇인가?

**A1.** 처음엔 **"인터페이스 역할 수행"**이라고 답했다. 인터페이스인 것은 구현 방식이지 책임 자체가 아니다.

진짜 책임은 **저장소 접근을 담당하는 것**이다. 지금은 메모리에 두지만 나중에 DB로 바뀌어도 이 책임은 그대로다. 이후로는 "이 계층이 무엇을 하는가"와 "그것을 어떤 형태로 표현했는가"를 나눠서 답한다.

### 2) `findById`의 `.equals()` 비교 근거

**질문.** `findById`에서 `r.getId() == id` 대신 `r.getId().equals(id)`로 고친 이유는 무엇인가?

**A2.** 처음엔 **"null값 탐지"**를 위해서라고 답했다. 틀렸다. `.equals()`를 쓰는 이유는 null 탐지가 아니라 참조 동일성과 값 동일성의 차이다.

`Long`은 객체라서 `==`는 "같은 주소를 가리킨다"를 검사한다. 게다가 `-128`~`127`은 캐시되어 지금처럼 id가 작을 때는 `==`가 우연히 통과한다. 같은 파일의 `getId() == null`처럼 참조가 비었는지 묻는 자리에서만 `==`가 맞다. 이 항목은 오답이라 복습큐에 재시험으로 등록했다.

## 4. 학습 정리와 다음 범위

### 1) 전체 흐름 다시 보기

오늘 나눈 Controller·Service·Repository가 요청 흐름 전체에서 어디에 놓이는지 한 장으로 보면 다음과 같다. 그림의 Database 자리는 오늘 코드에서 메모리 저장소(`InMemoryReservationRepository`)가 맡고, JPA로 DB에 연결하는 단계는 이후에 다룬다.

![Spring Boot 계층형 구조의 요청 흐름. 왼쪽 Client가 Controller Layer에 Request를 보내고 Response를 돌려받는다. Controller는 Service Layer와 양방향으로 주고받고, Service는 위쪽 Model과 데이터를 주고받으며 오른쪽 Repository Layer를 호출한다. Repository는 CRUD/Native Query로 Database와 통신하고, Model은 JPA로 Database 테이블에 매핑된다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day04-overview-layered-architecture.gif)

*출처: [Understanding Spring Boot Architecture Flow](https://medium.com/@dulanjayasandaruwan1998/understanding-spring-boot-architecture-flow-615d209b95f9) — Dulanjaya Sandaruwan (Medium). 저작권은 원저작자에게 있습니다.*

### 2) 이해의 변화와 남은 것

"Service/Repository로 나눈다"를 규칙으로 외우던 상태에서, **나눈 뒤에 무엇이 필요해지는지**를 보게 됐다. Service가 "찾아서 바꾼다"를 하려면 대상을 지목할 식별자가 있어야 한다. 식별자가 생기면 "같은 id인가"를 판정해야 하고, 거기서 참조 동일성과 값 동일성이 갈린다. 계층 분리 → 식별자 → 동일성은 따로 배우는 세 개가 아니라 한 줄로 이어져 있었다.

두 번째로 바뀐 것은 초록불의 범위다. 컴파일러는 리턴 누락은 잡았지만 "없음"의 의미는 묻지 않았고, 테스트는 `@PathVariable`과 URL이 어긋난 것을 통과시켰다.

**아직 남은 것**은 둘 다 오늘 코드에 있는 결함이다. 첫째, `save()`가 id 유무와 관계없이 `store.add()`를 호출해 같은 참조가 중복으로 들어갈 수 있다(미검증). 둘째, 없는 id로 취소를 부르면 `findById()`의 `null`을 확인 없이 역참조해 그 자리에서 실패한다. 둘 다 저장 계약과 "없음"의 표현을 정하는 문제라 **바로 고칠 것(Week A D7)**으로 분류했다.

면접에서 다시 답해볼 질문을 남긴다.

- 메모리 저장소에서는 찾아온 객체를 고치기만 해도 `store` 안의 값이 바뀐다. JPA로 바꾸면 `save()`를 다시 부르는 이 코드는 무엇이 달라지는가.

---

오늘 공부한 소스코드: `app/src/main/java/com/example/studyroom/controller/ReservationController.java`, `service/ReservationService.java`, `repository/ReservationRepository.java`, `repository/InMemoryReservationRepository.java`, `domain/Reservation.java`
