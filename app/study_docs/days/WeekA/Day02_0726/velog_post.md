# [Spring Study Day 2] 요청 데이터와 Domain Model의 분리 — record DTO와 Domain class

Day1은 `HelloController` 하나로 GET 요청이 응답이 되는 길을 따라갔다. Day2는 스터디룸 예약을 소재로 `ReservationController`를 만들고, 클라이언트가 **JSON을 보내오는** 방향을 봤다. 다루는 범위는 DTO와 Domain을 다른 타입으로 두는 기준과 `@RequestBody`가 JSON을 객체로 바꾸는 지점까지다. 서비스·저장소 계층과 입력 검증은 범위 밖이다.

> POST 두 개(`/reservations`, `/reservations/cancel`)를 만들어 JSON → record DTO → Domain 객체 → 상태 변경까지의 흐름을 손으로 확인했다. record의 접근자가 `getRoomName()`이 아니라는 것을 `cannot find symbol`로 배웠고, 오타 세 개가 컴파일과 실행을 모두 통과하는 것도 봤다. 검증은 전부 수동 호출이고 자동 테스트는 아직 없다.

> **오늘의 흐름** `Client(JSON) → DispatcherServlet → @RequestBody + HttpMessageConverter → ReservationRequest(record DTO) → Reservation(Domain) → confirm()/canceled() → 응답 문자열`
>
> 이전 Day: GET 요청이 응답이 되는 경로와 기본·명시적 Status Code, `ResponseEntity` (Day1)
> 다음 Day: 빈 값이 든 요청을 `@Valid`로 막고 `@RestControllerAdvice`로 400을 돌려주는 단계 (Day3)

## 1. 개념 설명

### 1) DTO와 Domain Model의 역할 구분

> **DTO** = Data Transfer Object. 계층이나 시스템 경계를 넘어 데이터를 옮기는 역할의 객체
> **Domain Model** = 업무 개념의 상태와, 그 상태를 바꾸는 규칙을 함께 가진 객체

우리 코드에서는 같은 두 값(`roomName`, `requesterName`)을 담는 타입이 둘이다.

```java
public record ReservationRequest(String roomName, String requesterName) {}  // DTO: 들어온 데이터의 모양

public class Reservation {          // Domain: 상태 + 규칙
    private final String roomName;
    private final String requesterName;
    private boolean confirmed;      // 규칙에 따라 바뀌는 상태
```

두 타입은 서로 다른 질문에 답한다.

- `ReservationRequest`: 시스템 경계를 넘어 **들어온 데이터의 모양**은 무엇인가
- `Reservation`: 그 데이터로 만든 예약이 **어떤 상태를 갖고 어떤 규칙으로 바뀌는가**

한 타입으로 두 역할을 맡기면 경계가 흐려진다. 요청 JSON의 모양이 바뀔 때 도메인 규칙을 가진 클래스도 같이 흔들린다. 반대로 `confirmed` 같은 내부 상태가 외부 입력 형식에 섞여 들어갈 여지가 생긴다.

record는 DTO 역할을 만드는 Java 문법이다. 처음엔 DTO와 record를 다른 것으로 생각했는데, 소스 주석에 "record가 DTO 그 자체다"라고 적으며 역할과 문법의 관계로 정리했다.

| 구분 | DTO (`ReservationRequest`) | Domain Model (`Reservation`) |
|---|---|---|
| 답하는 질문 | 들어온 데이터의 모양 | 상태와 그 상태를 바꾸는 규칙 |
| 오늘 쓴 문법 | `record` (불변) | 일반 `class` (일부 가변) |
| CS 연결 | 불변 값 객체 | OOP Encapsulation |

> **보장 범위** — 오늘은 이 분리의 설계 근거만 세웠다. Domain 객체를 `@RequestBody`로 직접 받는 대조군은 실행하지 않았다(미검증).

### 2) JSON Deserialization 순서

> **`@RequestBody`** = 요청 본문(JSON)을 HttpMessageConverter로 읽어 파라미터 타입의 Java 객체로 변환(Deserialization)하라는 지시

우리 코드에서는 `reserve()`의 파라미터 하나에 붙어 있다.

```java
//@RequestBody = 클라이언트가 보낸 JSON body를 자바 객체 (record)로 자동 변환
@PostMapping("/reservations")
public String reserve(@RequestBody ReservationRequest request) {    // 변환이 끝난 객체를 받는다
    Reservation reservation = new Reservation(request.roomName(), request.requesterName());
```

Day1에서는 반환값이 메시지 컨버터를 거쳐 응답 본문이 됐다. `@RequestBody`는 같은 장치를 반대 방향으로 쓴다.

```text
클라이언트: POST /reservations, Content-Type: application/json
→ DispatcherServlet이 reserve() 매핑을 찾음
→ @RequestBody 파라미터 발견
→ JSON 메시지 컨버터(Jackson)가 본문을 읽어 ReservationRequest 생성
→ reserve(request) 본문 실행
→ new Reservation(...) → confirm()
→ 반환 문자열이 응답 본문으로 변환
```

Jackson은 JSON의 키 이름(`roomName`, `requesterName`)을 record 컴포넌트에 맞춰 생성자를 호출한다. record는 컴포넌트 선언이 곧 생성자 파라미터라서 **DTO 쪽에는 내가 쓸 코드가 사실상 없다.** 대신 Domain 쪽에는 `confirm()` 같은 행동을 직접 써야 한다.

이 순서에서 컨트롤러 메서드 본문은 변환이 끝난 뒤에야 실행된다. `reserve()`가 받는 것은 이미 만들어진 `ReservationRequest` 객체다.

> **보장 범위** — 변환 과정의 내부 동작(Jackson이 record 생성자를 찾는 방식)은 오늘 열어보지 않았다. 흐름 블록은 수동 호출 결과와 공식 문서의 `@RequestBody` 설명을 합쳐 정리한 것이다.

### 3) record의 자동 생성 멤버와 접근자 이름

> **record** = 컴포넌트 선언만으로 `private final` 필드·생성자·접근자·`equals`·`hashCode`·`toString`을 컴파일러가 만들어 주는 Java 타입

우리 코드에서는 DTO가 선언 한 줄이다.

```java
// record = 데이터 모양만 정의. 생성자, getter, equals, hashcode 자동 생성.
// 불변(immutable) 속성을 지님. - 필드를 한 번 정하면 못 바꾼다.
public record ReservationRequest(String roomName, String requesterName) {
}
```

이 한 줄이 다음을 만든다.

- `private final` 필드 두 개
- 두 값을 받는 생성자
- 접근자 `roomName()`, `requesterName()`
- `equals()`, `hashCode()`, `toString()`

오늘 틀린 지점이 접근자 이름이었다.

| 구분 | record | JavaBean 관례 클래스 |
|---|---|---|
| 접근자 이름 | `roomName()` | `getRoomName()` |
| 누가 만드나 | 컴파일러가 자동 생성 | 개발자가 직접 작성 |
| 값 변경 | 불가(필드가 `final`) | setter가 있으면 가능 |

접근자에 `get`이 붙지 않는 것은 record가 "getter를 가진 객체"가 아니라 "데이터 그 자체"라는 설계 의도가 이름에 드러난 것이다. 접근자 이름은 컴포넌트 이름에서 그대로 만들어지므로 대소문자까지 일치해야 한다. `roomname`으로 선언하면 생기는 접근자는 `roomname()`이다.

> **보장 범위** — record의 불변은 **필드 재할당을 막는다는 뜻**이다. 필드가 가리키는 객체 내부까지 얼리지는 않는다. 오늘 컴포넌트는 불변 타입인 `String`뿐이라 이 차이가 드러나지 않았다.

### 4) Domain Model의 Encapsulation

> **Encapsulation** = 객체의 상태를 `private`으로 숨기고, 그 상태를 바꾸는 규칙을 객체의 메서드로만 노출하는 설계

우리 코드에서는 `Reservation`이 `confirmed`를 숨기고 두 메서드로만 바꾼다.

```java
private boolean confirmed;          // 외부에서 직접 바꿀 수 없는 상태
// 상태를 바꾸는 규칙을 객체 안에 캡슐화 - 외부에서 필드를 직접 못 건드리게 하기 위해서.
public void confirm() {
    this.confirmed = true;
}
public void canceled() { this.confirmed = false; }
```

소스 주석은 `Reservation`을 일반 class로 둔 이유를 "record 형태가 아님. 상태가 바뀔 수 있어야하니까"라고 적었다. 판단 기준은 필드 개수가 아니라 **바뀌어야 하는 상태가 있는가**다. 다만 "Domain은 가변"이 모든 필드가 바뀐다는 뜻은 아니었다.

```text
new Reservation("301호", "김민준")
→ roomName, requesterName: final로 고정
→ confirmed = false (생성자에서 초기화)
→ confirm() 호출 → confirmed = true
→ canceled() 호출 → confirmed = false
```

- 바뀌면 안 되는 값: `final` 필드로 잠근다
- 규칙에 따라 바뀌는 값: `private`으로 숨기고 메서드로만 바꾼다

외부 코드는 `reservation.confirmed = true`처럼 필드를 직접 바꿀 수 없다. 상태 변경은 반드시 `confirm()`·`canceled()`를 거친다. 나중에 "이미 취소된 예약은 확정할 수 없다" 같은 규칙이 생기면 그 메서드 안에만 추가하면 된다.

![클래스 다이어그램. ReservationController가 ReservationRequest를 «use»하고 Reservation을 «create»한다. «record» ReservationRequest는 roomName·requesterName이 둘 다 public에 {readOnly}이고 접근자가 roomName()·requesterName()이라 getRoomName()은 생성되지 않는다. Reservation은 roomName·requesterName이 private {readOnly}이고 confirmed만 가변인데 그마저 private이라, 외부는 confirm()·canceled()로만 상태를 바꿀 수 있다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day02-dto-domain.png)

그림의 `ReservationController`에는 `reserve()`만 있지만, 같은 커밋의 `cancel()`도 같은 방식으로 `ReservationRequest`를 받고 `Reservation`을 만든다. 한 메서드 안에서 DTO는 `request.roomName()`, Domain은 `reservation.getRoomName()`으로 꺼낸다. 두 명명 규칙이 공존하는 것은 두 타입의 성격 차이가 이름에 드러난 결과다.

> **보장 범위** — 지금 `confirm()`·`canceled()`는 값을 바꾸기만 하고 막는 규칙이 없다. 캡슐화는 규칙을 넣을 자리를 만든 것이지 규칙 자체는 아직 없다. 또 두 엔드포인트 모두 요청마다 `new`로 만든 객체의 상태를 바꾸므로 그 변경은 어디에도 남지 않는다.

### 5) 이름과 Identifier의 검사 범위

> **Identifier** = 클래스·메서드·변수처럼 컴파일러가 선언과 사용을 대조하는 이름. 문자열 리터럴은 Identifier가 아니라 데이터다

우리 코드에서는 취소 기능 하나에 층마다 다른 이름이 붙었다.

```java
@PostMapping("/reservations/cancel")                          // URL: 문자열 데이터
public String cancel(@RequestBody ReservationRequest request) { // 컨트롤러 메서드 Identifier
    Reservation reservation = new Reservation(request.roomName(), request.requesterName());
    reservation.canceled();                                     // 도메인 메서드 Identifier
```

이 코드를 혼자 만들다 `rerservations`·`cancle`·`cancled` 세 군데에 오타를 냈다. 컴파일도 실행도 통과했다.

| 대상 | 컴파일러가 보는 것 | 보지 않는 것 |
|---|---|---|
| 메서드 이름 | 정의한 이름과 호출한 이름의 일치 | 영어 철자가 맞는가 |
| URL 문자열 | 문자열 리터럴 문법 | 내용 자체(식별자가 아니라 데이터) |

메서드 이름은 오타를 정의와 호출 양쪽에 똑같이 쓰면 문제가 없다. URL은 애초에 문자열 데이터라 컴파일러가 검사할 규칙이 없다. 컴파일 성공은 "이름이 서로 맞았다"는 뜻이지 "의도대로 동작한다"는 뜻이 아니다.

URL·컨트롤러 메서드·도메인 메서드의 이름은 결국 `/reservations/cancel`, `cancel()`, `canceled()`로 층마다 달라졌다. Day1의 "URL 경로와 메서드 이름은 목적이 다르다"가 한 겹 더 늘어난 셈이다.

> **보장 범위** — 오늘 오타를 드러낸 수단은 엔드포인트 직접 호출이었다. IDE 철자 검사와 자동 테스트도 이 빈틈을 채울 수 있지만 쓰지 않았다. 노트에는 도메인 메서드를 `cancel()`로 적었지만 커밋의 실제 코드는 `canceled()`라 커밋을 기준으로 적었다.

### 6) package 선언과 Namespace

> **package** = 클래스의 전체 이름(Namespace)을 정하는 선언. `package` + 클래스 이름이 그 클래스의 정규 이름이 된다

우리 코드에서는 도메인 클래스의 전체 이름을 정하고, 컨트롤러가 그 이름으로 가져다 쓴다.

```java
package com.example.studyroom.domain;          // Reservation.java 첫 줄
public class Reservation { ... }

import com.example.studyroom.domain.Reservation; // ReservationController.java
```

코드를 쓰다 "왜 파일 맨 위에 `package`를 굳이 써주지?"라는 의문이 생겼고, 답을 `Reservation.java` 주석에 남겼다.

```text
package com.example.studyroom.domain;  +  class Reservation
→ 전체 이름: com.example.studyroom.domain.Reservation
→ 다른 라이브러리의 Reservation과 충돌하지 않음
→ 컨트롤러는 import com.example.studyroom.domain.Reservation; 으로 참조
```

이름이 같은 클래스가 여러 곳에 있어도 전체 이름이 다르면 구분된다. `ReservationController`가 `controller` 패키지에 있으면서 `Reservation`을 쓸 수 있는 것도 `import`가 이 전체 이름을 가리키기 때문이다.

> **보장 범위** — 주석은 "컴파일러가 이 선언으로 파일이 `src/main/java/com/example/studyroom/domain/`에 있다고 인식한다"고 적었는데, 범위를 좁혀야 한다. 패키지와 폴더를 일치시키는 것은 관례이고 빌드 도구와 IDE가 이를 전제로 동작하지만, 불일치 자체가 곧 컴파일 에러는 아니다. 같은 커밋의 `HelloController.java`는 `studyroom/` 폴더에 있으면서 `package com.example.studyroom.controller;`를 선언한 상태였다.

### 7) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| DTO | 계층·시스템 경계를 넘어 데이터를 옮기는 역할의 객체. 요청 본문의 모양을 정의한다 |
| Domain Model | 업무 개념의 상태와 그 상태를 바꾸는 규칙을 함께 가진 객체 |
| `@RequestBody` | 요청 본문을 메시지 컨버터로 읽어 컨트롤러 파라미터 객체로 만들라는 Spring MVC 지시 |
| `HttpMessageConverter` | HTTP 본문과 Java 객체를 양방향으로 변환하는 변환기. Day1의 응답 방향, Day2의 요청 방향 모두 담당한다 |
| Deserialization | JSON 같은 외부 표현을 Java 객체로 복원하는 과정 |

> **더 볼 것**
> - [Records — Java Language Reference (Java 17)](https://docs.oracle.com/en/java/javase/17/language/records.html): 접근자 이름 규칙과 자동 생성 멤버의 근거
> - [@RequestBody — Spring Framework Reference](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestbody.html): 요청 본문이 객체로 변환되는 단계
> - 아직 안 본 것 — Jackson이 record를 역직렬화하는 내부 동작, JavaBean 관례 자체, 오타를 잡아줄 테스트 코드와 IDE 철자 검사 설정

## 2. 코드 구현

### 1) 같은 두 필드를 담은 두 타입

```java
// record = 데이터 모양만 정의. 생성자, getter, equals, hashcode 자동 생성.
public record ReservationRequest(String roomName, String requesterName) {
}

public class Reservation{
    private final String roomName;
    private final String requesterName;
    private boolean confirmed;

    public void confirm() {
        this.confirmed = true;
    }
    public void canceled() { this.confirmed = false; }
    // 생성자, getRoomName(), getRequesterName(), isConfirmed()
}
```

**한 줄씩 보기**

- `public record ReservationRequest(...)` — 컴파일 시점에 필드·생성자·접근자 `roomName()`·`requesterName()`이 만들어진다.
- `private final String roomName;` — 생성자에서 한 번 정해지고 이후 재할당되지 않는다.
- `private boolean confirmed;` — 이 클래스에서 유일하게 바뀌는 상태다. 생성자가 `false`로 초기화한다.
- `confirm()` / `canceled()` — 컨트롤러가 호출하는 시점에 `confirmed`를 바꾸는 유일한 통로다.

DTO는 한 줄로 끝나고, Domain은 같은 두 필드를 들고도 `final`과 가변 필드, 행동 메서드를 직접 갖는다. 두 파일의 길이 차이가 곧 두 역할의 차이다.

### 2) `@RequestBody` 파라미터와 DTO→Domain 변환

```java
@PostMapping("/reservations")
public String reserve(@RequestBody ReservationRequest request) {
    Reservation reservation = new Reservation(request.roomName(), request.requesterName());
    reservation.confirm();
    return reservation.getRequesterName() + "님이 " + reservation.getRoomName()
            + " 예약 완료 (확정: " + reservation.isConfirmed() + ")";
}
```

**한 줄씩 보기**

- `@PostMapping("/reservations")` — 애플리케이션 시작 시 "POST /reservations → reserve()" 매핑으로 등록된다.
- `@RequestBody ReservationRequest request` — 메서드 본문이 실행되기 전에 JSON 본문이 `ReservationRequest`로 변환돼 들어온다.
- `new Reservation(request.roomName(), ...)` — DTO의 record 접근자로 값을 꺼내 Domain 객체를 만든다. 이 시점의 `confirmed`는 `false`다.
- `reservation.confirm()` — Domain의 행동으로 상태를 `true`로 바꾼다.
- `return ... + reservation.isConfirmed()` — Domain의 JavaBean식 접근자로 값을 읽어 응답 문자열을 만든다. 상태를 지정하지 않았으므로 Day1과 같은 기본값 200이 붙는다.

`cancel()`은 같은 구조에서 `confirm()` 대신 `canceled()`를 호출한다. 컨트롤러가 하는 일은 DTO에서 값을 꺼내 Domain을 만들고 행동을 호출하는 것까지다.

### 3) 한글 JSON 전송 실패와 추정 원인

`curl.exe -d`로 한글이 섞인 JSON(`"301호"`, `"김민준"`)을 보내니 요청이 통과하지 않았다. 관찰과 해석을 나눠 적는다.

**확인한 것**

- `400 Bad Request`가 돌아왔고, 이어서 curl 자체가 `Malformed input to a URL function` 에러를 냈다
- 같은 값을 PowerShell 네이티브 방식으로 보내니 정상 처리됐다

**추정한 것 (분리해서 재현하지 않음)**

- Windows 콘솔의 기본 인코딩이 UTF-8이 아니라 한글 바이트가 깨졌다
- 수동으로 이스케이프한 큰따옴표(`\"`)를 PowerShell이 한 번 더 해석하면서 인자가 깨졌다

두 원인을 각각 끄고 켜보지 않았으므로 어느 쪽이 결정적이었는지는 확인하지 못했다. 해결 방법만 확실하다. 해시테이블을 `ConvertTo-Json`에 넘기고 `Invoke-RestMethod`에 `-ContentType "application/json; charset=utf-8"`로 보내자, 이스케이프와 인코딩을 셸이 처리해서 문제가 사라졌다.

### 4) 자동 검증 결과

| 요청 | 결과 | 확인 방법 |
|---|---|---|
| `POST /reservations` | `Invoke-RestMethod` 전환 후 정상 처리(응답 원문 미기록) | 수동 |
| `POST /reservations/cancel` | `김민준님이301호 예약을 취소하셨습니다 (확정 : false)` | 수동 |

- **자동 테스트**: `contextLoads()` 하나뿐이라 두 엔드포인트의 경로·응답이 바뀌어도 빌드는 통과한다.
- **수동 확인**: 위 표의 두 호출.
- **미검증**: Domain 객체를 요청 본문으로 직접 받는 대조군, 한글 전송 실패의 두 추정 원인 분리.

오늘 코드는 [`975be06` 커밋](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/975be06)에 있다.

## 3. 스스로 답한 질문

### 1) record 접근자 호출의 컴파일 에러

**질문.** `request.getRoomName()`은 왜 컴파일되지 않았을까?

**A1.** Domain에 직접 만든 getter가 `getRoomName()`이었으니 DTO도 당연히 될 거라고 예측했다. 결과는 `cannot find symbol`이었다.

틀린 지점은 "getter는 다 `getXxx()`"라고 뭉뚱그린 것이다. record가 만드는 접근자는 컴포넌트 이름 그대로인 `roomName()`이다.

여기에 컴포넌트명을 `roomname`(소문자 n)으로 쓴 것까지 겹쳐 같은 에러를 한 번 더 만났다. Java는 대소문자를 구분하므로 `roomName`으로 선언해야 `roomName()` 접근자가 생긴다. 재발 방지는 규칙을 외우는 것이 아니라 **선언부를 먼저 보고 접근자 이름을 확인하는 것**으로 잡았다.

### 2) 오타가 컴파일과 실행을 통과한 원인

**질문.** `rerservations`·`cancle`·`cancled` 오타가 왜 컴파일도 실행도 통과했을까?

**A2.** 이 결과는 예측과 같았다. 정의와 호출에 같은 오타를 썼으니 이름이 일치했고, 컴파일러는 영어 철자를 검사하지 않는다. URL 문자열은 식별자가 아니라 데이터라서 검사 규칙 자체가 없다. 오타를 실제로 드러낸 것은 엔드포인트를 직접 호출해본 것이었다.

### 3) `package` 선언의 역할

**질문.** 왜 파일 맨 위에 `package`를 굳이 써주지?

**A3.** 오답이 아니라 코드를 쓰다 생긴 의문이라 소스 주석에 답을 남겼다. 클래스 이름 충돌을 막는 전체 이름이고, 다른 패키지에서 `import`로 가져올 수 있게 하는 전제다. 컨트롤러가 `import com.example.studyroom.domain.Reservation;`으로 도메인을 쓸 수 있는 이유가 여기 있다. 폴더 위치와의 관계는 1절 6)에서 범위를 좁혀 정리했다.

## 4. 학습 정리와 다음 범위

### 1) 전체 흐름 다시 보기

JSON 요청 본문이 객체가 되고 다시 응답이 되기까지를 한 장으로 모으면 다음과 같다. 그림의 Resource가 오늘의 record DTO(`ReservationRequest`)에 해당하고, (3) Validator 검증과 (5)·(6) Service·Repository 단계는 오늘 범위 밖이다.

![Spring MVC의 RESTful Web Service 처리 흐름. (1) 클라이언트가 HTTP 요청을 DispatcherServlet으로 보내면 (2) HttpMessageConverter가 JSON 본문을 Resource(Java Bean) 객체로 변환하고 (3) Validator가 입력값을 검증한 뒤 (4) Application Layer의 REST API Controller를 호출한다. (5) Controller는 Domain Layer의 Service를, (6) Service는 Repository를 호출한다. (7) Controller가 반환한 Resource를 HttpMessageConverter가 다시 JSON으로 바꾸고 (8) 응답으로 클라이언트에 보낸다. 개발자가 구현하는 범위는 Resource·Controller·Service·Repository다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day02-overview-json-request-flow.png)

*출처: [5.1. RESTful Web Service — TERASOLUNA Server Framework for Java (5.x) Development Guideline 5.4.1.RELEASE](https://terasolunaorg.github.io/guideline/5.4.1.RELEASE/en/ArchitectureInDetail/WebServiceDetail/REST.html) — NTT DATA Corporation. 저작권은 원저작자에게 있습니다. © 2013-2018 NTT DATA Corporation, NTT Corporation. Reference document: TERASOLUNA Server Framework for Java (5.x) Development Guideline ([Terms of Use](https://terasolunaorg.github.io/guideline/5.4.1.RELEASE/en/Introduction/TermsOfUse.html)).*

### 2) 이해의 변화와 남은 것

오늘 바뀐 것은 "DTO는 record, Domain은 class"라는 문장이 아니라 **그 선택의 기준**이다. 타입을 고를 때 필드 개수나 편의가 아니라 "이 값이 바뀌어야 하는가, 바뀐다면 그 규칙은 누가 지키는가"를 먼저 묻게 됐다. 접근자 이름이 두 타입에서 다른 것도 그 기준이 이름까지 내려온 결과였다.

컴파일 성공의 의미도 좁아졌다. Day1에는 컴파일 에러 네 개를 고쳐야 `/bye`가 동작했는데, 오늘은 오타 세 개를 안고도 빌드가 통과했다. 통과했다는 것은 이름이 서로 맞았다는 뜻이지 의도대로 동작한다는 뜻이 아니다.

**아직 남은 것**은 두 가지다. 첫째, 두 엔드포인트를 고정하는 자동 테스트가 없어 오타나 경로 변경이 수동 호출 때만 드러난다. **나중에 고칠 것(Week A D7)**으로 분류했다. 둘째, `cancel` 요청이 취소하는 대상은 방금 `new`로 만든 객체라 취소가 어디에도 남지 않는다. 저장소 계층을 배우는 Day4에서 다룰 **나중에 고칠 것**이다.

면접에서 다시 답해볼 항목을 남긴다.

- 빈 방 이름을 거부하는 규칙은 DTO가 맡아야 하는가, Domain이 맡아야 하는가

---

오늘 공부한 소스코드: `app/src/main/java/com/example/studyroom/controller/ReservationController.java`, `domain/Reservation.java`, `dto/ReservationRequest.java` ([`975be06`](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/975be06))
