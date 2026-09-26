# [Spring Study Day 3] Input Validation과 Global Exception Handling — `@Valid`와 `@RestControllerAdvice`

Day2에서 만든 `POST /reservations`는 `roomName`이 빈 문자열로 들어와도 그대로 예약을 만들었다. Day3는 그 요청을 어디에서 막고, 막힌 사실을 어떤 응답으로 돌려줄지를 다룬다. 검증 규칙은 `@NotBlank` 하나만 쓰고, 오류 응답 포맷 설계와 커스텀 예외 계층은 범위 밖으로 뒀다.

> DTO 필드에 `@NotBlank`를 걸고 컨트롤러 파라미터에 `@Valid`를 붙인 뒤, `@RestControllerAdvice` 클래스에서 `MethodArgumentNotValidException`을 400으로 바꿨다. 빈 값을 넣어 요청하니 응답 바디에 실패한 필드 하나만 담겼는데, 이를 "덮어써져서"라고 잘못 설명했다가 `getFieldErrors()`가 애초에 실패한 필드만 담는다는 설명으로 교정했다. 확인은 전부 `curl` 수동 호출이다.

> **오늘의 흐름** `Client → DispatcherServlet → @RequestBody 변환 → @Valid 검증 → reserve() 또는 MethodArgumentNotValidException → GlobalExceptionHandler → 400`
>
> 이전 Day: JSON 요청 본문을 record DTO로 받고 Domain 객체와 분리했다. 입력 검증은 없었다 (Day2)
> 다음 Day: Controller에 몰린 일을 Service·Repository로 나누고 예약에 식별자를 도입하는 단계 (Day4)

## 1. 개념 설명

### 1) Input Validation의 필요성과 위치

> **Input Validation** = 외부에서 들어온 값이 API의 사전조건을 만족하는지 처리 전에 검사하고, 어긴 요청은 처리하지 않고 돌려보내는 일

우리 코드에서는 Day2의 `reserve()`가 받은 값을 확인하지 않고 곧바로 도메인 객체를 만들었다.

```java
public String reserve(@RequestBody ReservationRequest request) {   // Day2: 검증 지시 없음
    Reservation reservation = new Reservation(request.roomName(), request.requesterName());
    reservation.confirm();   // roomName이 ""여도 여기까지 그대로 실행된다
```

`roomName`이 `""`여도 방 이름이 빈 `Reservation`이 만들어지고 예약 완료 문자열이 돌아갔다. 잘못된 값이 도메인 객체까지 들어가면 이후 모든 코드가 "이 값은 비어 있을 수 있다"를 의심해야 한다.

그래서 입력은 **계층의 초입**, 즉 외부 데이터가 자바 객체로 막 바뀐 지점에서 끊는다. CS 개념으로는 계약(contract)의 **사전조건(precondition) 검사**다. "방 이름과 예약자 이름이 비어 있지 않아야 한다"는 조건을 코드로 선언하고, 어긴 요청은 처리하지 않는다.

사전조건 위반은 요청한 쪽의 책임이므로 4xx 계열인 `400 Bad Request`가 맞다.

> **보장 범위** — 오늘 검사한 사전조건은 "문자열이 비어 있지 않다" 하나다. 길이 제한, 형식, 이미 예약된 방인지 같은 규칙은 다루지 않았다.

### 2) Constraint 선언과 Validation 실행의 분리

> **Bean Validation** = 객체의 필드에 제약 애노테이션을 선언해 두고, 검증기가 그 선언을 읽어 위반을 판정하는 Jakarta 표준

우리 코드에서는 규칙과 실행 지시가 서로 다른 파일에 있다.

```java
// ReservationRequest.java — 규칙 선언
public record ReservationRequest(@NotBlank(message = "방 이름은 비어있을 수 없습니다") String roomName, ...)
// ReservationController.java — 실행 지시
public String reserve(@RequestBody @Valid ReservationRequest request)   // 검증 실행
public String cancel(@RequestBody ReservationRequest request)           // 같은 DTO, 검증 없음
```

| 구분 | `@NotBlank` | `@Valid` |
|---|---|---|
| 붙는 곳 | DTO의 필드(record 컴포넌트) | 컨트롤러 메서드 파라미터 |
| 역할 | 규칙 선언: 무엇이 위반인가 | 실행 지시: 이 객체를 지금 검사하라 |
| 패키지 | `jakarta.validation.constraints.NotBlank` | `jakarta.validation.Valid` |

`@NotBlank`만 있으면 규칙은 적혀 있지만 아무도 검사하지 않는다. 같은 커밋의 `cancel()`이 그 예다. 같은 `ReservationRequest`를 받지만 `@RequestBody`만 붙어 있어 검증이 실행되지 않는다. 검증은 **타입이 아니라 파라미터 선언에** 걸린다.

`@NotBlank`의 판정 기준은 "null이 아니고, 공백이 아닌 문자를 하나 이상 포함"이다. 빈 문자열 `""`과 공백만 있는 `"  "`이 모두 위반이다.

`message` 속성의 문자열은 개발자 메모가 아니다. 나중에 `error.getDefaultMessage()`로 꺼내져 응답 본문의 값이 되므로 **클라이언트가 실제로 보는 문구**다.

> **보장 범위** — `cancel()`에 빈 값을 넣어 검증이 일어나지 않는 것을 직접 호출해 보지는 않았다. `@Valid`가 없으면 검증이 실행되지 않는다는 것은 코드 구조와 공식 문서로 판단한 내용이다(미검증).

### 3) 검증 실패의 예외 전환 순서

> **`MethodArgumentNotValidException`** = `@Valid`가 붙은 요청 본문 인자가 검증에 실패했을 때 Spring MVC가 컨트롤러 메서드를 호출하지 않고 던지는 예외

우리 코드에서는 `@Valid`를 붙이면서 소스에 결과를 먼저 예측해 적었다.

```java
@PostMapping("/reservations")
public String reserve(@RequestBody @Valid ReservationRequest request) {
    Reservation reservation = new Reservation(request.roomName(), request.requesterName());   // 검증 실패 시 미실행
    ...
//아마 "" 로 해버리면 @Valid 유효성 검사 들어가서 400 BadRequest 뜨지 않을까 싶은데..
```

결과는 400이었다. 중요한 것은 **어느 시점에 멈추는가**다.

```text
클라이언트: POST /reservations {"roomName":"", "requesterName":"김민준"}
→ DispatcherServlet이 reserve() 매핑을 찾음
→ @RequestBody: JSON을 ReservationRequest로 변환(여기까지는 성공)
→ @Valid: Bean Validation이 @NotBlank 규칙 검사 → roomName 위반
→ MethodArgumentNotValidException 발생 (reserve() 본문은 실행 전)
→ DispatcherServlet이 ExceptionHandlerExceptionResolver에 위임
→ GlobalExceptionHandler.handleValidation(ex) 실행
→ 400 + {"roomName":"방 이름은 비어있을 수 없습니다"}
```

변환과 검증은 서로 다른 단계다. 빈 문자열은 JSON 문법상 올바른 값이라 `ReservationRequest` 생성까지는 성공한다. 타입 변환에 성공했다고 값이 유효하다는 뜻은 아니다.

예외는 메서드 본문이 실행되기 **전에** 던져진다. 따라서 `new Reservation(...)`도 `confirm()`도 실행되지 않고, 잘못된 값은 도메인 객체로 옮겨가지 못한다.

![시퀀스 다이어그램. 클라이언트가 roomName이 빈 문자열인 JSON을 POST하면 DispatcherServlet이 자기 자신에게 @RequestBody 변환과 @Valid 검사를 수행한다. alt 프레임의 첫 분기(검증 통과)에서는 ReservationController.reserve()가 호출되고 200 OK가 돌아간다. 두 번째 분기(검증 실패, MethodArgumentNotValidException)에서는 GlobalExceptionHandler.handleValidation()이 호출돼 400과 roomName 키만 담긴 Map이 반환되는데, 이 분기에서 ReservationController 생명선은 한 번도 닿지 않는다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day03-validation-flow.png)

> **보장 범위** — 그림은 변환과 검증을 `DispatcherServlet`의 자기 호출로 묶어 단순화했다. 실제로는 `DispatcherServlet`이 호출하는 인수 처리 단계에서 일어나며, 그 내부 클래스는 오늘 열어보지 않았다.

### 4) `BindingResult`와 `FieldError` 목록

> **`BindingResult`** = 검증 결과 보고서. 위반이 발생한 필드마다 `FieldError`(필드 이름·메시지)를 하나씩 담는다

우리 코드에서는 `handleValidation()`이 이 보고서에서 실패한 필드만 꺼내 Map으로 옮긴다.

```java
Map<String, String> errors = new LinkedHashMap<>();   // 넣은 순서 유지, 키 = 필드 이름
ex.getBindingResult().getFieldErrors()                // 위반이 난 필드의 FieldError만
    .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
```

응답 바디에는 `roomName` 하나만 나왔다. `requesterName`도 어떤 형태로든 나올 거라는 예측과 달랐다. `BindingResult`는 DTO의 사본이 아니고, `getFieldErrors()`는 DTO의 모든 필드를 도는 것이 아니다.

```text
roomName = ""           → @NotBlank 위반 → FieldError(field="roomName", message="방 이름은...") 생성
requesterName = "김민준" → 통과           → FieldError 없음
getFieldErrors()        → [roomName의 FieldError] 1개
forEach                 → errors.put("roomName", "방 이름은 비어있을 수 없습니다")
```

그래서 응답 바디의 길이는 DTO 필드 수가 아니라 **위반 개수**를 따라간다. 통과한 필드는 기록되지 않고, 실패한 항목만 기록된다.

> **보장 범위** — 한 필드에 위반이 여러 개 걸리면 같은 키에 `put`이 다시 일어나 앞의 메시지가 덮어써진다. 오늘 규칙은 필드당 `@NotBlank` 하나라 이 상황은 발생하지 않았다. `FieldError`의 나머지 정보(거부된 값, 코드)는 보지 않았다.

### 5) Global Exception Handler의 수집과 선택

> **`@RestControllerAdvice`** = 특정 컨트롤러를 지정하지 않고 여러 컨트롤러의 예외를 한곳에서 응답으로 바꾸는 컴포넌트 선언. `@ControllerAdvice`에 `@ResponseBody`가 더해진 것

우리 코드에서는 `GlobalExceptionHandler` 안에 `ReservationController`를 가리키는 표시가 하나도 없다.

```java
@RestControllerAdvice                                        // 대상 컨트롤러 지정 없음
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class) // 이 타입의 예외를 맡는다
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
```

그런데도 그 컨트롤러의 검증 실패를 잡는다. 이것이 오늘 배운 "전역"의 실체다.

```text
애플리케이션 시작
→ 컨테이너가 @RestControllerAdvice가 붙은 GlobalExceptionHandler를 Bean으로 생성
→ ExceptionHandlerExceptionResolver가 @ControllerAdvice 계열 Bean을 따로 수집해 전역 후보로 보관

요청 처리 중 예외 발생
→ DispatcherServlet이 리졸버에 위임
→ 리졸버가 후보 중 예외 타입이 맞는 @ExceptionHandler 메서드를 찾아 실행
```

여기서 혼동한 짝이 "Bean으로 등록됨"과 "전역 예외 처리 후보로 취급됨"이다.

| 구분 | `@Service`·`@Component` Bean | `@RestControllerAdvice` Bean |
|---|---|---|
| 컨테이너 등록 | 됨 | 됨 |
| 리졸버의 전역 후보 수집 | 대상 아님 | 대상 |
| 안의 `@ExceptionHandler`가 다른 컨트롤러 예외 처리 | 하지 않음 | 함 |

Bean 등록은 필요조건일 뿐이다. 특정 애노테이션과 특정 리졸버의 조합이 전역 처리를 만든다. CS 개념으로는 **관심사 분리**다. 예외를 응답으로 바꾸는 일은 여러 컨트롤러에 반복되는 공통 관심사(cross-cutting concern)라, 한 클래스로 모으면 각 컨트롤러는 정상 흐름에만 집중할 수 있다.

`GlobalExceptionHandler`에는 `MethodArgumentNotValidException`용과 `Exception`용 두 핸들러가 있다. `MethodArgumentNotValidException`도 `Exception`의 하위 타입이지만 오늘 요청은 500이 아니라 400으로 끝났다. 더 구체적인 예외 타입의 핸들러가 선택됐다.

> **보장 범위** — "모든 컨트롤러 대상"은 이 애노테이션의 기본값이고, `basePackages`·`assignableTypes`로 범위를 좁힐 수 있다(오늘은 사용하지 않음). 더 구체적인 핸들러가 선택된다는 것은 400 응답으로 관찰한 결과이고, 리졸버의 선택 알고리즘 코드는 열어보지 않았다.

### 6) 예상하지 못한 예외의 응답 한계

> **`@ExceptionHandler(Exception.class)`** = 더 구체적인 핸들러가 없는 모든 예외를 받는 마지막 처리 지점

우리 코드에서는 `handleUnexpected()`가 예외 메시지를 그대로 500 응답 본문에 싣는다.

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
    Map<String, String> error = new LinkedHashMap<>();
    error.put("error", ex.getMessage());   // 내부 메시지가 그대로 클라이언트에 노출된다
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
```

예상하지 못한 예외의 메시지에는 내부 구현 정보가 들어 있을 수 있다. 이 구조가 보장하는 것은 "어떤 예외든 JSON 형태의 500으로 끝난다"까지다.

> **보장 범위** — "무엇을 외부에 보여줄지"는 통제하지 않는다. 외부 메시지와 내부 로그를 분리해야 하며, 이 부채는 이후 Day07에서 해결했다. 오늘 500 경로는 실제로 발생시켜 보지 않았다(미검증).

### 7) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| Bean Validation | 필드에 선언한 제약 애노테이션을 검증기가 읽어 위반을 판정하는 Jakarta 표준 |
| `@Valid` | 이 파라미터 객체에 Bean Validation 검증을 지금 실행하라는 지시 |
| Constraint Annotation | `@NotBlank`처럼 필드에 붙어 "무엇이 위반인가"를 선언하는 규칙 |
| `@ExceptionHandler` | 특정 예외 타입을 받아 응답으로 바꾸는 메서드 표시 |
| `@RestControllerAdvice` | 여러 컨트롤러의 `@ExceptionHandler`를 한곳에 모으는 전역 컴포넌트 선언 |
| `ExceptionHandlerExceptionResolver` | `DispatcherServlet`이 예외를 위임하는 리졸버. advice Bean을 전역 후보로 수집해 두었다가 맞는 핸들러를 실행한다 |

> **더 볼 것**
> - [Exceptions — Spring Framework Reference](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-exceptionhandler.html): `@ExceptionHandler` 지원이 `DispatcherServlet`의 `HandlerExceptionResolver` 위에 있다는 근거
> - [Controller Advice — Spring Framework Reference](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-advice.html): `basePackages`·`assignableTypes`로 적용 범위를 좁히는 방법
> - [Validation — Spring MVC Config](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/validation.html): Bean Validation 구현체가 클래스패스에 있으면 글로벌 `Validator`가 등록된다는 부분
> - [NotBlank — Jakarta Bean Validation 3.0 API](https://jakarta.ee/specifications/bean-validation/3.0/apidocs/jakarta/validation/constraints/notblank): null 금지와 공백 아닌 문자 1개 이상이라는 판정 기준
> - 아직 안 본 것 — `FieldError`의 나머지 정보(거부된 값, 코드), `@Validated`, `ProblemDetail` 형식의 오류 응답

## 2. 코드 구현

### 1) DTO의 규칙 선언과 컨트롤러의 실행 지시

```java
public record ReservationRequest(
    @NotBlank(message = "방 이름은 비어있을 수 없습니다") String roomName,
    @NotBlank(message = "예약자 이름은 비어있을 수 없습니다.") String requesterName
) {}

@PostMapping("/reservations")
public String reserve(@RequestBody @Valid ReservationRequest request) { ... }
//아마 "" 로 해버리면 @Valid 유효성 검사 들어가서 400 BadRequest 뜨지 않을까 싶은데..
```

**한 줄씩 보기**

- `@NotBlank(message = "...") String roomName` — 규칙 선언이다. 검증이 실행될 때 이 컴포넌트가 null·빈 문자열·공백만이면 위반으로 판정하고, `message`는 응답 본문의 값이 된다.
- `@PostMapping("/reservations")` — 애플리케이션 시작 시 "POST /reservations → `reserve()`" 매핑으로 등록된다.
- `@RequestBody` — 요청이 올 때 JSON 본문을 `ReservationRequest`로 변환한다. 변환만 하고 값의 유효성은 보지 않는다.
- `@Valid` — 변환 직후, 메서드 본문 실행 전에 Bean Validation 검증을 실행하게 한다. 위반이 있으면 `MethodArgumentNotValidException`이 던져진다.

규칙은 DTO에, 실행 지시는 컨트롤러 파라미터에 뒀다. 같은 파일의 `cancel()`은 `@Valid` 없이 남아 있다.

### 2) 어떤 컨트롤러도 가리키지 않는 예외 처리 클래스

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
            .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) { ... }
}
```

**한 줄씩 보기**

- `@RestControllerAdvice` — 시작 시 Bean으로 등록되고, `ExceptionHandlerExceptionResolver`가 전역 예외 처리 후보로 수집한다. 반환값은 응답 본문이 된다.
- `@ExceptionHandler(MethodArgumentNotValidException.class)` — 어느 컨트롤러에서든 이 타입의 예외가 나오면 리졸버가 이 메서드를 실행한다.
- `new LinkedHashMap<>()` — 필드 이름을 키로, 메시지를 값으로 담을 응답 본문이다. 넣은 순서를 유지한다.
- `ex.getBindingResult().getFieldErrors()` — 검증에 실패한 필드의 `FieldError` 목록만 꺼낸다.
- `.forEach(error -> errors.put(...))` — 실패한 필드마다 `필드 이름 → 메시지` 한 쌍을 넣는다.
- `ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors)` — 상태코드 400과 Map 본문을 직접 지정한다.
- `@ExceptionHandler(Exception.class)` — 더 구체적인 핸들러가 없는 예외를 500으로 바꾼다(1절 6)).

이 클래스를 혼자 작성하다가 컴파일 에러가 11개 났는데, 원인은 세 군데였다. 타입 이름 중간의 공백(`ResponseE ntity`), `new`와 클래스 이름이 붙은 `newLinkedHashMap<>()`, 그리고 두 번째 `@ExceptionHandler` 메서드를 클래스 닫는 `}` **뒤**에 쓴 것이다.

앞의 둘은 입력 실수다. 타입 이름 중간의 공백은 토큰을 둘로 갈라 "`)` or `,` expected" 에러를 만든다. 세 번째는 규칙 문제였다. 클래스를 닫은 뒤는 파일 최상위라 class·interface·enum·record만 올 수 있고, 메서드 선언이 놓일 자리가 아니다.

### 3) 자동 검증 결과

앱을 재시작한 뒤 `roomName`은 빈 문자열, `requesterName`은 `"김민준"`으로 `POST /reservations`를 호출했다.

| 확인 항목 | 결과 | 확인 방법 |
|---|---|---|
| 상태코드 | `400 Bad Request` | `curl` 수동 |
| 응답 바디 | `{"roomName":"방 이름은 비어있을 수 없습니다"}` | `curl` 수동 |
| 컨트롤러 본문 도달 여부 | 도달하지 않음 (`new Reservation(...)` 미실행) | 응답 내용으로 판단 |

- **자동 테스트** — `contextLoads()` 하나뿐이다. 이 상태코드와 본문이 바뀌어도 빌드는 통과한다.
- **수동 확인** — 위 표의 `curl` 호출 한 건.
- **미검증** — `cancel()`의 검증 미실행, `handleUnexpected()`의 500 경로.

오늘 코드는 [`306100f` 커밋](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/306100f660de477643481eea8debd0a8b5de4e84)에 있다.

## 3. 스스로 답한 질문

### 1) 응답 바디에서 빠진 `requesterName`

**질문.** `roomName`만 비우고 `requesterName`은 정상값을 보냈을 때, 응답 바디에 `requesterName`이 왜 나타나지 않았을까?

**A1.** 처음 내놓은 설명은 **"먼저 들어갔다가 덮어써져서 사라졌다"**였다. 틀린 방향이었다. 그 설명이 맞으려면 `forEach`가 두 필드를 모두 순회하며 같은 키에 값을 다시 넣어야 하는데, `errors.put(error.getField(), ...)`의 키는 필드 이름이라 두 필드는 서로 다른 키다.

교정된 답은 순회 대상 자체가 달랐다는 것이다. `getFieldErrors()`는 검증에 실패한 필드의 `FieldError` 목록을 돌려준다. `requesterName`은 `@NotBlank`를 통과했으므로 `FieldError`가 없었고, `forEach`가 만날 원소 자체가 없었다.

재발 방지 기준은 **비어 있는 결과를 봤을 때 "지워졌나"보다 "애초에 들어갔나"를 먼저 확인하는 것**이다. 덮어쓰기는 한 필드에 위반이 여러 개일 때 실제로 생길 수 있지만(1절 4)), 오늘 현상의 원인은 아니었다.

### 2) 컨트롤러를 지정하지 않은 Global Exception Handling

**질문.** `GlobalExceptionHandler`는 `ReservationController`를 어디에도 지정하지 않는데, 어떻게 그 예외까지 잡는가?

**A2.** 처음 답은 **"Spring Bean 관리를 통해 연결 표시 없이도 잡아내는 것"**이었다. 부분 정답이었다. Bean 등록은 필요조건이지만 그것만으로는 부족하다는 점이 빠졌다. `@Service`나 `@Component` Bean은 전역 예외 처리에 참여하지 않는다는 반례로 바로 드러난다.

교정된 답은 특별 취급의 주체를 짚는다. `DispatcherServlet`이 예외를 만나면 `ExceptionHandlerExceptionResolver`에 위임하고, 이 리졸버는 **`@ControllerAdvice` 계열 애노테이션이 붙은 Bean만** 전역 후보로 수집해 두었다가 예외 타입이 맞는 `@ExceptionHandler` 메서드를 실행한다.

### 3) 검증 실패 요청의 컨트롤러 본문 도달 여부

**질문.** `roomName`이 빈 문자열로 들어오면 요청이 컨트롤러 메서드 본문까지 도달하는가?

**A3.** 소스 주석에는 "400 BadRequest 뜨지 않을까"라고 결과만 예측했고, 언제 멈추는지는 적지 않았다. 도달하지 않는다. `@Valid`가 위반을 발견하면 메서드 본문 실행 **전에** `MethodArgumentNotValidException`이 던져지므로 `new Reservation(...)`도 `confirm()`도 실행되지 않는다.

## 4. 학습 정리와 다음 범위

### 1) 전체 흐름 다시 보기

오늘 따라간 경로를 한 장으로 모으면 다음과 같다. ②③의 인자 처리에서 검증을 통과하면 ④ `reserve()`로 가고, 위반이 있으면 빨간 경로로 빠져 ⑤⑥을 거쳐 400이 된다.

![전체 흐름도. Client가 POST /reservations를 보내면 ① DispatcherServlet이 요청을 받고, "인자 처리 · @RequestBody @Valid" 영역에서 ② HttpMessageConverter가 JSON을 ReservationRequest로 바꾼 뒤 ③ Bean Validation이 @NotBlank를 검사한다. 검증을 통과하면 ④ ReservationController.reserve()가 실행돼 200 OK와 예약 완료 문자열이 Client로 돌아간다. 위반이 있으면 빨간 점선으로 MethodArgumentNotValidException이 발생하고 reserve() 본문은 실행되지 않는다. DispatcherServlet이 위임한 ⑤ ExceptionHandlerExceptionResolver가 @RestControllerAdvice 영역의 ⑥ GlobalExceptionHandler.handleValidation()을 실행하고, 400 Bad Request와 {"roomName":"방 이름은 비어있을 수 없습니다"}가 Client로 돌아간다. 하단 주석은 규칙은 DTO 필드, 실행 지시는 파라미터에 있고 응답 본문에는 실패한 필드만 담긴다고 적는다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day03-overview-validation-flow.png)

### 2) 이해의 변화와 남은 것

오늘 바뀐 것은 "검증을 어떻게 켜는가"보다 **검증 결과를 무엇으로 보는가**였다. 처음엔 `BindingResult`를 DTO의 사본처럼 생각해 모든 필드가 담겨 있을 거라고 봤다. 실제로는 위반 사실만 적힌 보고서였다.

응답 바디가 짧았던 이유와 `@RestControllerAdvice`가 전역인 이유는 결국 같은 종류의 질문이었다. "누가 무엇을 모아두는가"를 물으면 두 현상이 모두 설명된다.

**아직 남은 것**은 두 가지다. `handleUnexpected()`가 `ex.getMessage()`를 500 본문에 그대로 싣는 문제는 **바로 고칠 것(Week A D7)**으로 분류했고 Day07에서 해결했다. 오류 응답에 오류 코드·요청 식별자가 없고 한 필드의 여러 위반이 Map 키에서 덮어써지는 문제는 **나중에 고칠 것(Week D D5 또는 Week E D1)**이다.

면접에서 받으면 답이 갈릴 질문을 남긴다.

- 검증 실패 응답을 필드-메시지 Map으로 주는 방식과 오류 코드·요청 식별자를 포함한 고정 스키마로 주는 방식의 클라이언트별 장단점

---

오늘 공부한 소스코드: `app/src/main/java/com/example/studyroom/exception/GlobalExceptionHandler.java`, `controller/ReservationController.java`, `dto/ReservationRequest.java` ([`306100f`](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/306100f660de477643481eea8debd0a8b5de4e84))
