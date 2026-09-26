# [Spring Study Day 7] 1주차 버퍼 — 저장 계약과 Absence of Value의 오류 경계

Week A D7은 새 기능을 추가하는 날이 아니라 Day03·04에서 **바로 고칠 것**으로 미뤄 둔 기술부채를 갚는 버퍼였다. 계획은 7월 31일이었지만 D6 누적시험과 함께 8월 2일에 진행했다. 기존 예약을 다시 `save()`하면 같은 객체 참조가 목록에 한 칸 더 들어갈 수 있었고, 없는 번호를 조회하면 `null`이 Service까지 흘러갔으며, 예상 밖 예외의 메시지가 500 본문에 그대로 실렸다. 세 부채는 따로 보이지만 모두 **계약이 정해지지 않은 경계**였다. 저장소는 "저장"의 의미를, 조회는 "없음"의 표현을, 예외 처리기는 "외부에 무엇을 보일지"를 정하지 않은 상태였다.

> 저장소의 신규·기존 계약을 id로 나누고, 값 부재를 `Optional`로 드러낸 뒤 Service가 도메인 예외로, 전역 예외 처리기가 HTTP 404로 단계별 변환하게 했다. D6에서 교정한 "PK는 갱신 대상을 고유하게 지목하는 값"이라는 기준이 이 분기의 근거가 됐다. Service·예외 처리기 단위 테스트와 MockMvc 테스트로 200·400·404·안전한 500을 고정했고 전체 테스트 10개가 통과했다.

> **오늘의 흐름** `save() 저장 계약 → findById()의 Optional → ReservationNotFoundException → GlobalExceptionHandler의 404 → MockMvc로 HTTP 계약 고정`
>
> 이전 Day: 1주차 누적시험에서 `==`와 `.equals()`, Singleton Bean, PK 기준을 다시 교정했다 (Day6)
> 다음 Day: Flyway `V1__init.sql`로 `reservation` 테이블을 정의하고 Checksum 검증을 관찰한다 (Day8)

## 1. 개념 설명

### 1) 저장 계약과 신규·기존 분기

> **저장 계약** = 신규 객체와 기존 객체를 저장할 때 저장소가 각각 어떻게 동작하는지에 대한 약속

우리 코드에서는 `InMemoryReservationRepository.save()`가 id 유무와 존재 여부로 세 갈래를 나눈다.

```java
if (reservation.getId() == null) {          // 신규: 번호 부여 후 추가
    reservation.assignId(nextId++);
    store.add(reservation);
    return reservation;
}
// 기존 id를 찾으면 store.set(index, reservation), 못 찾으면 IllegalArgumentException
```

`save()`라는 이름만으로 동작은 정해지지 않는다. 목록에 무조건 추가할 수도 있고, 같은 id가 있으면 교체할 수도 있다. Day04 시점의 `save()`는 id가 없으면 번호만 부여하고, id 유무와 관계없이 `store.add(reservation)`을 호출했다. 이 코드로 취소 흐름을 따라가면 문제가 보인다.

```text
reserve: save(r) → id 없음 → assignId(1) → store.add(r)      → store = [r]
cancel : findById(1) → 목록 안의 r 참조 반환
       → r.cancel() → 목록 안의 그 객체의 confirmed가 이미 false
       → save(r) → id 있음, 그러나 store.add(r) 실행   → store = [r, r]
```

`Reservation`은 가변 객체이고, `ArrayList`에는 복사본이 아니라 참조가 들어간다. 그래서 `r.cancel()`을 호출하는 순간 상태 변경은 이미 끝났다. 그 뒤의 `add()`는 **같은 참조를 한 칸 더 넣는 동작**이 되고, `ArrayList`는 이를 막지 않는다.

D7에서 계약을 다음처럼 확정했다.

```text
save(reservation)
→ id == null        : 신규. assignId(nextId++) 후 store.add()
→ id가 저장소에 있음 : 기존. 같은 id의 index를 찾아 store.set(index, reservation)
→ id가 저장소에 없음 : 호출 흐름의 모순. IllegalArgumentException
```

세 번째 분기가 중요하다. id가 붙어 있는데 저장소에 없는 객체를 조용히 신규로 넣으면, 외부에서 임의 id를 붙인 객체가 신규 데이터처럼 섞인다. 계약 위반은 예외로 드러낸다.

| 연산 | 목록 크기 | 이 프로젝트의 의미 |
|---|---|---|
| `store.add(r)` | 1 증가 | 신규 예약 추가 (DB의 INSERT에 대응) |
| `store.set(index, r)` | 그대로 | 기존 예약 교체 (DB의 UPDATE에 대응) |

이 분기가 지키는 불변식은 "저장 후에도 목록 크기가 예약 수와 같고, 같은 id가 두 번 들어가지 않는다"이다. CS로는 자료구조의 삽입과 갱신 구분이며, DB에서 PK로 INSERT 대상과 UPDATE 대상을 구분하는 문제와 연결된다.

> **보장 범위** — 지금 구조에서 `set()`은 이미 변경된 같은 참조를 같은 자리에 다시 넣으므로 메모리상으로는 사실상 변화가 없다. 계약을 분리해 둔 가치는 저장소가 DB로 바뀌어도 "기존 id는 갱신"이라는 약속이 유지된다는 데 있다. `ArrayList`와 `nextId`는 여러 스레드의 동시 접근에 안전하지 않고, 데이터는 프로세스 재시작 후 사라진다.

### 2) Absence of Value의 표현 — `null`과 `Optional`

> **Absence of Value** = 조회 대상이 없을 수 있다는 사실. 이를 반환형에 드러낼지, 숨길지가 호출자의 오류 처리를 결정한다

우리 코드에서는 Repository 선언이 부재를 반환형으로 드러내고, Service가 그 부재를 업무 의미의 예외로 바꾼다.

```java
Optional<Reservation> findById(Long id);                     // 없을 수 있음을 선언에 표시

Reservation reservation = reservationRepository.findById(id)
        .orElseThrow(() -> new ReservationNotFoundException(id)); // 없으면 도메인 예외
```

Day04의 `findById()`는 못 찾으면 `null`을 반환했고, `ReservationService.cancel()`은 확인 없이 `reservation.cancel()`을 호출했다. `null` 반환은 `Reservation findById(Long id)`라는 선언만 봐서는 알 수 없는 숨은 경우다. 호출자가 확인을 빠뜨리면 실패는 원인에서 먼 곳에서 `NullPointerException`으로 드러난다.

```text
Repository.findById(999)
→ 목록 순회에서 일치하는 id 없음
→ Optional.empty() 반환
→ Service: .orElseThrow(() -> new ReservationNotFoundException(id))
→ 값이 없으므로 supplier가 예외 객체 생성
→ ReservationNotFoundException("예약을 찾을 수 없습니다. (id: 999)") throw
```

`Optional`의 API 문서도 이 용도를 명시한다. 주로 "결과 없음"을 표현할 분명한 필요가 있고 `null`을 쓰면 오류가 생기기 쉬운 **메서드 반환형**을 위한 타입이다. `orElseThrow(Supplier)`는 값이 있으면 그 값을, 없으면 supplier가 만든 예외를 던진다.

| 표현 | 부재가 드러나는 곳 | 호출자가 받는 것 |
|---|---|---|
| `null` 반환 | 선언에 없음 | 확인을 빠뜨리면 NPE |
| `Optional.empty()` | 반환형 | 부재를 처리하라는 타입 |
| `ReservationNotFoundException` | 예외 타입과 메시지 | 업무 의미가 있는 실패 |

`ReservationNotFoundException`은 `RuntimeException`을 상속하므로 `cancel()` 시그니처에 `throws`를 추가하지 않는다. 이제 `null.cancel()`에서 우연히 생긴 NPE와 "예약 번호가 없음"을 구분할 수 있다. CS 관점에서는 값이 있거나 없는 두 상태를 타입으로 명시하는 합 타입의 발상과 가깝다.

> **보장 범위** — `Optional`은 값이 없다는 사실만 드러낼 뿐, 그것이 업무상 어떤 실패인지는 Service가 정한다. Day04의 `null` 경로를 실제 요청으로 실행한 기록은 없으므로, 그때 NPE가 어떤 응답으로 끝났는지는 미검증이다.

### 3) 계층별 실패 변환과 HTTP Status Code

> **HandlerExceptionResolver** = Controller 밖으로 나온 예외를 받아 HTTP 응답으로 바꾸는 Spring MVC의 예외 처리 전략. `@RestControllerAdvice` 안의 `@ExceptionHandler`가 이 경로로 선택된다

우리 코드에서는 `GlobalExceptionHandler`가 도메인 예외를 404로, 예상 밖 예외를 일반 500으로 바꾼다.

```java
@ExceptionHandler(ReservationNotFoundException.class)   // 구체 타입 → 404와 이유
public ResponseEntity<Map<String, String>> handleNotFound(ReservationNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
}
@ExceptionHandler(Exception.class)                      // 나머지 전부 → 로그 + 일반 500
```

같은 실패도 계층을 지나며 표현이 바뀐다. 각 계층은 자기 언어로만 실패를 말한다.

```text
Repository : Optional.empty()                  — 값이 있는가
Service    : ReservationNotFoundException       — 업무상 무슨 실패인가
Web 경계   : 404 Not Found + {"error": "..."}   — 어떤 HTTP 응답인가
```

Repository는 HTTP를 모르고, Service는 JSON을 모른다. 웹 경계로의 변환은 Spring MVC가 수행한다.

```text
Controller.cancel(id)에서 ReservationNotFoundException 전파
→ DispatcherServlet이 예외를 받음
→ 예외 리졸버가 GlobalExceptionHandler의 @ExceptionHandler 후보 확인
→ ReservationNotFoundException 처리기와 Exception 처리기 중
  더 구체적인 타입인 handleNotFound(ex) 선택
→ ResponseEntity 404 + {"error": "예약을 찾을 수 없습니다. (id: 999999)"}
→ JSON으로 직렬화해 응답
```

`Exception` 처리기가 함께 있어도 404가 나온다는 점은 MockMvc 테스트 `cancelReturns404WhenReservationDoesNotExist()`로 확인했다. 처리기 선택이 가장 넓은 타입 쪽으로 빠지지 않았다는 증거다.

![시퀀스 다이어그램. 참여자는 MockMvc(DispatcherServlet), ReservationController, ReservationService, InMemoryReservationRepository, GlobalExceptionHandler다. POST cancel 요청이 Controller의 cancel(id), Service의 findById(id)로 이어진다. alt 프레임의 첫 구획은 id가 저장소에 있는 경우로, Repository가 Optional.of(r)을 돌려주고 Service가 r.cancel() 후 save(r)를 호출하며 Repository는 store.set(index, r)로 교체한 뒤 r을 반환하고 최종 응답은 200 OK다. 둘째 구획은 id가 없는 경우로, Repository가 Optional.empty()를 돌려주고 Service의 orElseThrow()가 ReservationNotFoundException을 던진다. 예외는 Controller를 지나 DispatcherServlet으로 전파되고, handleNotFound(ex)가 404와 error JSON을 반환한다. 오류 경로는 빨간색이다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day07-storage-contract.png)

예상하지 못한 예외는 다르게 다룬다. Day03의 `handleUnexpected()`는 `ex.getMessage()`를 그대로 500 본문에 실었다. 예외 메시지에는 내부 클래스, DB 접속 정보, 파일 경로 같은 구현 세부가 들어갈 수 있다.

| 대상 | 담는 정보 | 읽는 사람 |
|---|---|---|
| 서버 로그 `log.error("처리하지 못한 예외", ex)` | 메시지와 stack trace 전체 | 운영자·개발자 |
| 500 응답 본문 | `"요청 처리 중 오류가 발생했습니다."` | 클라이언트 |

HTTP 의미론으로 정리하면, 400은 클라이언트 입력이 잘못됐다는 뜻이고 404는 요청한 대상이 없다는 뜻이며 500은 서버가 처리하지 못했다는 뜻이다. 클라이언트가 조치할 수 있는 404에는 구체적인 이유를 주고, 조치할 수 없는 500에서는 내부 정보를 감춘다.

> **보장 범위** — 안전한 500 본문은 `handleUnexpected()`를 직접 호출하는 단위 테스트로만 확인했다. 실제 HTTP 요청으로 500을 일으키는 MockMvc 테스트는 없다. 오류 응답에는 오류 코드·타임스탬프·요청 식별자가 없다.

### 4) Path Variable의 Method Validation

> **Method Validation** = Controller 메서드 매개변수에 직접 선언한 제약을 Spring MVC가 메서드 호출 전에 검사하는 처리

우리 코드에서는 취소 경로의 `{id}`에 `@Positive`를 직접 붙였다.

```java
@PostMapping("/reservations/cancel/{id}")
public String cancel(@PathVariable @Positive(message = "예약 번호는 1 이상이어야 합니다") Long id) {
    // id가 0 이하이면 이 본문은 실행되지 않는다
```

`/reservations/cancel/0`처럼 범위 밖의 번호는 Service에 도달하기 전에 막는 편이 맞다. 저장소를 뒤지기 전에 "입력이 잘못됐다"는 400으로 끝낼 수 있기 때문이다. 취소 경로는 body DTO가 아니라 `{id}` 하나를 받으므로 Day03의 `@Valid` DTO 검증으로는 막을 수 없었다.

```text
POST /reservations/cancel/0
→ 요청 매핑이 cancel(@PathVariable @Positive Long id)를 선택
→ "0"을 Long 0으로 변환
→ 매개변수에 @Positive가 직접 선언돼 있으므로 Spring MVC 내장 메서드 검증 실행
→ 위반 → HandlerMethodValidationException
→ Controller 메서드 본문은 실행되지 않음
→ handleMethodValidation() → 400 + {"errors": ["예약 번호는 1 이상이어야 합니다"]}
```

| 선언 위치 | 발생 예외 | 이 프로젝트의 응답 |
|---|---|---|
| `@RequestBody @Valid ReservationRequest` | `MethodArgumentNotValidException` | 필드명별 메시지 Map |
| `@PathVariable @Positive Long id` | `HandlerMethodValidationException` | `{"errors": [...]}` |

Spring 문서에 따르면 제약 애노테이션을 메서드 매개변수에 직접 선언했을 때 메서드 검증이 적용되고 `HandlerMethodValidationException`이 발생한다.

> **보장 범위** — 이 동작에는 성립 조건이 있다. Controller 클래스에 `@Validated`를 붙이면 내장 검증 대신 AOP 프록시를 통한 검증이 적용된다고 문서는 설명하며, 현재 `ReservationController`에는 클래스 수준 `@Validated`가 없다. 테스트한 값은 `0` 하나이고, 음수나 숫자가 아닌 경로 값의 응답은 미검증이다.

### 5) Unit Test와 MockMvc의 검증 범위

> **MockMvc** = 서버 포트를 열지 않고 `DispatcherServlet`에 요청을 넣어 Spring MVC 처리 흐름 전체를 검증하는 테스트 도구

우리 코드에서는 `ReservationControllerHttpTest`가 없는 예약 번호로 실제 요청 흐름을 태운다.

```java
mockMvc.perform(post("/reservations/cancel/{id}", 999_999L))   // 포트 없이 MVC 흐름 진입
        .andExpect(status().isNotFound())                      // 처리기 선택 결과
        .andExpect(jsonPath("$.error")
                .value("예약을 찾을 수 없습니다. (id: 999999)"));   // JSON 직렬화 결과
```

`GlobalExceptionHandlerTest`는 처리기 메서드를 직접 호출해 반환 객체만 확인한다. 이 테스트만으로는 URL 매핑, `@Positive` 검증, 처리기 선택, JSON 직렬화가 실제로 함께 동작하는지 알 수 없다.

```text
MockMvc.perform(post("/reservations/cancel/{id}", 0L))
→ 실제 포트 없이 DispatcherServlet에 요청 전달
→ 요청 매핑 → 인수 변환 → 메서드 검증 → Controller → Service
→ 예외 발생 시 예외 리졸버 → @RestControllerAdvice
→ 응답 상태 코드와 JSON 본문을 andExpect로 검사
```

| 테스트 | 지나는 경로 | 확인하는 것 |
|---|---|---|
| 예외 처리기 단위 테스트 | Java 메서드 호출 하나 | 반환 상태 코드와 본문 객체 |
| MockMvc 테스트 | Spring MVC 요청 처리 흐름 전체 | 매핑·검증·예외 변환·JSON까지의 HTTP 계약 |

MockMvc를 쓰면 Day04에서 컴파일과 테스트가 모두 성공한 뒤 실제 요청에서만 드러난 `@PathVariable` 불일치 같은 문제를 테스트 단계에서 잡을 수 있다.

> **보장 범위** — MockMvc는 네트워크와 서블릿 컨테이너를 거치지 않으므로 실제 포트를 여는 테스트와 같지는 않다. 내장 Tomcat의 Connector가 HTTP 요청 줄을 파싱하는 앞단은 이 테스트가 지나지 않는다.

### 6) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| `HandlerExceptionResolver` | Controller 밖으로 나온 예외를 받아 응답으로 바꾸는 Spring MVC 예외 처리 전략 |
| `@RestControllerAdvice` | 여러 Controller에 공통으로 적용할 `@ExceptionHandler`를 모아 두는 Bean. 반환값은 응답 본문이 된다 |
| `@ExceptionHandler` | 지정한 예외 타입을 처리하는 메서드 표시. 후보가 여럿이면 더 구체적인 타입이 선택된다 |
| Method Validation | Controller 메서드 매개변수에 직접 선언한 제약을 호출 전에 검사하는 Spring MVC 처리 |
| MockMvc | 서버 포트 없이 `DispatcherServlet`에 요청을 넣어 MVC 흐름을 검증하는 테스트 도구 |

> **더 볼 것**
> - [Spring MVC Validation](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-validation.html): `@RequestBody` 검증과 메서드 인수 검증의 차이, 클래스 수준 `@Validated`의 영향
> - [Spring Controller Advice](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-advice.html): 전역 `@ExceptionHandler`가 Controller에 적용되는 방식
> - [Java Optional API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Optional.html): 반환형으로서의 용도와 `orElseThrow()`의 동작
> - 아직 안 본 것 — RFC 9457 `ProblemDetail` 기반 공통 오류 응답은 Week D·E의 오류 계약에서 검토한다.

## 2. 코드 구현

### 1) 저장 계약의 세 분기

```java
if (reservation.getId() == null) {
    reservation.assignId(nextId++);
    store.add(reservation);
    return reservation;
}

for (int index = 0; index < store.size(); index++) {
    if (store.get(index).getId().equals(reservation.getId())) {
        store.set(index, reservation);
        return reservation;
    }
}

throw new IllegalArgumentException("저장소에 없는 예약 번호입니다: " + reservation.getId());
```

**한 줄씩 보기**

- `if (reservation.getId() == null)` — 아직 번호가 없는 신규 예약인지 판단한다. `reserve()`에서 처음 저장할 때 참이 된다.
- `reservation.assignId(nextId++)` — 저장소가 번호를 부여하고 다음 번호를 올린다.
- `store.add(reservation)` — 신규 분기에서만 목록 크기를 1 늘린다. 바로 `return`해 아래 분기로 내려가지 않는다.
- `for (int index = 0; ...)` — 기존 예약의 위치를 찾기 위해 목록을 index로 순회한다. `set()`에 index가 필요해서다.
- `.getId().equals(reservation.getId())` — `Long` id를 D6에서 정리한 대로 참조가 아니라 값으로 비교한다.
- `store.set(index, reservation)` — 같은 자리를 교체하므로 목록 크기가 그대로다. `cancel()`의 재저장이 여기로 온다.
- `throw new IllegalArgumentException(...)` — id가 있는데 저장소에 없으면 신규로 넣지 않고 호출 흐름의 모순으로 끝낸다.

### 2) Absence of Value의 도메인 예외 변환

```java
// ReservationRepository
Optional<Reservation> findById(Long id);

// ReservationService.cancel()
Reservation reservation = reservationRepository.findById(id)
        .orElseThrow(() -> new ReservationNotFoundException(id));
reservation.cancel();
reservationRepository.save(reservation);
```

인터페이스 선언이 바뀌었으므로 구현체는 못 찾았을 때 `Optional.empty()`를 반환한다. `ReservationNotFoundException`은 생성자에서 `"예약을 찾을 수 없습니다. (id: " + id + ")"` 메시지를 만든다.

### 3) Global Exception Handler와 경로 제약

```java
@ExceptionHandler(ReservationNotFoundException.class)
public ResponseEntity<Map<String, String>> handleNotFound(ReservationNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
}

@ExceptionHandler(Exception.class)
public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
    log.error("처리하지 못한 예외", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "요청 처리 중 오류가 발생했습니다."));
}
```

같은 클래스에 `HandlerMethodValidationException`을 400으로 바꾸는 `handleMethodValidation()`도 추가했다. Controller 쪽 변경은 `cancel(@PathVariable @Positive(message = "예약 번호는 1 이상이어야 합니다") Long id)` 한 줄이다.

### 4) 자동 검증 결과

| 테스트 층 | 확인한 동작 | 결과 |
|---|---|---|
| 단위(Service·예외 처리기) | 취소 후 `findAll().size() == 1`, 없는 ID 도메인 예외, 404 변환, 500 본문에 `jdbc:password=secret` 미포함 | 4개 통과 |
| MockMvc | 예약 200, 빈 DTO 400, 없는 예약 404(`id: 999999`), `id=0` 400 | 4개 통과 |
| Spring context | 컨텍스트 기동, Singleton Service 동일성 | 2개 통과 |

- **자동 테스트** — 전체 `./gradlew.bat test`에서 위 10개 테스트가 모두 통과했다.
- **수동 확인** — 이날은 `curl.exe` 같은 수동 호출 기록을 남기지 않았다. HTTP 경로는 MockMvc로만 확인했다.
- **미검증** — 수정 전 코드를 대상으로 실패 테스트를 먼저 실행한 기록은 없으므로 TDD의 red-green 순서를 수행했다고 주장하지 않는다. HTTP 요청으로 500을 일으키는 경로도 테스트하지 않았다.

구현·테스트와 Day06·07 산출물: [commit `6c88dcb`](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/6c88dcb)

## 3. 스스로 답한 질문

D7에는 새 시험 문항의 오답이 없다. 아래는 Day03·04에서 답을 정하지 못한 채 기술부채로 넘긴 질문이다.

### 1) 취소 후 재저장의 중복 원인

**질문.** 이미 취소한 객체를 다시 `save()`하면 왜 목록에 중복이 생기는가?

**A1.** Day04에서 이 문제를 코드 읽기로 발견했다. 당시에는 원소 수를 세는 테스트도 `findAll()` 엔드포인트도 없어서 실제로 두 칸이 되는지는 확인하지 못했다.

기존 구현은 id 유무와 관계없이 `store.add(reservation)`을 호출했다. 객체 상태는 `cancel()`에서 이미 바뀌었지만, 목록의 칸 수는 `add()` 때문에 늘어난다. D7에서 신규만 `add()`하고 기존 id는 `set()`하도록 바꾼 뒤 `cancelUpdatesExistingReservationWithoutAddingDuplicate()`로 크기 1을 고정했다.

재발 방지로, 저장 계약을 읽을 때는 메서드 이름이 아니라 신규·기존 분기와 자료구조 연산을 확인한다.

### 2) 없는 예약의 표현 방식

**질문.** 예약이 없을 때 `findById()`는 무엇을 돌려주고, 그 뒤 처리는 누가 맡아야 하는가?

**A2.** Day04에서는 "모든 실행 경로가 값을 리턴해야 한다"는 컴파일 규칙을 채우려고 `return null`을 넣었다. 문법 조건은 채웠지만 예약이 없을 때 무엇을 할지는 정하지 않은 상태였다.

D7에서는 역할을 계층별 질문으로 나눴다. Repository는 "값이 있는가?"를 `Optional`로, Service는 "업무상 무슨 실패인가?"를 `ReservationNotFoundException`으로, 웹 계층은 "어떤 HTTP 응답인가?"를 404로 결정한다. `Optional`을 반환하는 것만으로 오류 처리가 끝나지 않는다는 점이 핵심이었다.

### 3) 500 응답의 예외 메시지 노출

**질문.** 모든 예외 메시지를 그대로 응답하면 디버깅이 쉬워지지 않는가?

**A3.** Day03에서 `@ExceptionHandler(Exception.class)`가 `ex.getMessage()`를 500 본문에 싣는 구조를 한계로 남겼다. 클라이언트 쪽에서는 원인이 보여 편해 보이지만, 내부 구현 정보까지 외부에 공개될 수 있다.

D7에서는 상세 원인과 stack trace를 서버 로그에 남기고, 응답은 일반 메시지로 제한했다. `jdbc:password=secret`이라는 내부 메시지를 가진 예외를 처리기에 넘긴 뒤 500 본문에 그 문자열이 포함되지 않는지 `unexpectedExceptionDoesNotExposeInternalMessage()`로 확인했다.

## 4. 학습 정리와 다음 범위

### 1) 전체 흐름 다시 보기

오늘 계약을 고정한 Service·예외 처리기 단위 테스트는 피라미드 맨 아래 Unit Tests에, 포트 없이 HTTP 요청·응답 경계를 거치는 MockMvc 테스트는 그 위 Service Tests 쪽에 놓인다. 오늘 추가한 테스트 전체가 어느 층을 채웠는지 한 장으로 보면 다음과 같다.

![Mike Cohn의 테스트 피라미드. 아래에서 위로 Unit Tests, Service Tests, UI Tests 세 층이 쌓여 있고, 아래층일수록 넓다. 왼쪽 화살표는 아래가 more isolation, 위가 more integration임을, 오른쪽 화살표는 아래가 faster, 위가 slower임을 나타낸다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day07-overview-test-pyramid.png)

*출처: [The Practical Test Pyramid](https://martinfowler.com/articles/practical-test-pyramid.html) — Ham Vocke, martinfowler.com. 저작권은 원저작자에게 있습니다.*

### 2) 이해의 변화와 남은 것

수정 전에는 `save()`, `findById()`, 전역 예외 처리기가 각각 동작했지만 서로의 실패 계약이 연결되지 않았다. 지금은 id가 신규와 기존을 구분하고, `Optional`이 부재를 드러내며, 도메인 예외가 그 의미를 HTTP 404까지 운반한다. D6에서 교정한 PK와 `.equals()`의 기준이 이 분기와 비교 코드에 그대로 쓰였다.

코드가 그럴듯해 보이는 것과 HTTP 계약이 검증된 것은 다르다는 점도 확인했다. 단위 테스트에 MockMvc 테스트를 더하면서 URI 매핑·검증·예외 변환·JSON 본문을 한 경로로 확인했다.

**아직 남은 것**은 두 가지다. 오류 코드·타임스탬프·요청 식별자를 포함한 공통 오류 DTO가 없고, 한 필드에 여러 위반이 생기면 Map 키가 덮어써진다. 이는 **나중에 고칠 것(Week D D5 또는 Week E D1)**이다. `ArrayList`와 `nextId`의 동시성 안전성은 이번 트랙에서 **고치지 않을 것**이며, 재시작 후 데이터가 사라지는 문제는 Week B D3의 영속성 교체에서 해결한다.

다음 범위는 Week B D1이다. Flyway `V1__init`으로 스키마를 정의하면서 메모리의 `id`와 저장 계약을 실제 DB의 PK·제약 조건으로 옮긴다.

면접에서 다시 답해볼 항목을 남긴다.

- Repository·Service·웹 계층이 같은 실패를 각각 어떻게 표현하는지
- 예외 처리기 단위 테스트와 MockMvc 테스트가 보장하는 범위의 차이

---

오늘 공부한 소스코드: `InMemoryReservationRepository`, `ReservationService`, `GlobalExceptionHandler`, `ReservationNotFoundException`, `ReservationController`와 그 테스트 ([`6c88dcb`](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/6c88dcb))

<!-- 선택 복습 메모: 게시 화면에는 노출하지 않는다.
### 1) 선택 추가 설명

[직접 작성] 신규 예약의 저장과 기존 예약의 갱신을 `id`로 어떻게 구분하는지, 그리고 `Optional`이 `null`보다 실패 경계를 어떻게 선명하게 만드는지 설명한다.
-->
