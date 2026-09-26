# [Spring Study Day 1] Request-Response의 왕복 — 기본 HTTP Status Code와 `ResponseEntity`

새 5주 로드맵의 첫날이다. `app/`을 빈 스켈레톤으로 다시 시작하고, `HelloController` 하나로 GET 요청이 응답이 되기까지의 경로를 따라갔다. 다루는 범위는 상태코드가 정해지는 지점과 반환값이 본문이 되는 지점까지다. 요청 본문 처리와 입력 검증은 Day2·Day3에서 다룬다.

> `/hello`·`/health`·`/bye`·`/nope` 네 경로를 `curl.exe -i`로 호출해 기본값 200, 명시한 200, 명시한 201, 자동 404를 확인했다. 200이 붙는 이유를 처음에 "DTO로 미리 저장돼서"라고 잘못 설명했다가, 예외 없는 정상 반환에 붙는 기본값이라는 설명으로 교정했다. 검증은 전부 수동 호출이고 자동 테스트는 `contextLoads()` 하나뿐이다.

> **오늘의 흐름** `Client → 내장 Tomcat → DispatcherServlet → HelloController → 응답(상태코드 + 본문)`
>
> 이전 Day: 없음 — 시리즈 첫날, 빈 스켈레톤에서 시작
> 다음 Day: 요청 본문(JSON)이 Java 객체가 되는 단계 (Day2)

## 1. 개념 설명

### 1) HTTP 응답의 구성과 Status Code 계열

> **HTTP 응답** = 상태줄(상태코드) · 헤더 · 본문 세 부분으로 서버가 요청의 결과를 알리는 메시지

우리 코드에서는 `curl.exe -i`로 `/hello`를 호출하면 응답이 아래 구조로 나온다. 첫 줄이 상태줄이고, 빈 줄 뒤가 본문이다.

```text
HTTP/1.1 200           ← 상태줄: 결과의 종류
(헤더 여러 줄)          ← 헤더: 본문 형식·길이 등 부가 정보
                       ← 빈 줄
Hello,StudyRoom!       ← 본문: hello()가 돌려준 문자열
```

클라이언트는 본문을 읽기 전에 상태코드로 결과의 종류를 먼저 판단한다. 본문만 있고 상태코드가 없다면 `"OK"`라는 문자열이 성공을 뜻하는지, 오류 메시지를 담은 것인지 구분할 수 없다. 상태코드는 본문 내용과 독립적으로 "이 요청이 어떻게 끝났는가"를 전달하는 메타데이터다.

| 계열 | 의미 | 오늘 만난 값 |
|---|---|---|
| 2xx | 요청을 성공적으로 처리함 | `200 OK`, `201 Created` |
| 4xx | 요청 쪽에 문제가 있음 | `404 Not Found` |
| 5xx | 서버가 처리 중 실패함 | 오늘은 발생하지 않음 |

같은 2xx 안에서도 뜻이 갈린다. `200 OK`는 "요청을 정상 처리했다"이고, `201 Created`는 "새 리소스를 만들었다"다. 네트워크 수업에서 배운 400·404가 4xx 계열이라는 점도 여기서 연결됐다.

> **보장 범위** — `GET /bye`가 201을 돌려주는 것은 HTTP 의미론과 어긋난다. GET은 조회 메서드이고 새 리소스를 만들지 않는다. `/bye`는 상태코드를 직접 지정하는 방법을 확인하려는 학습 코드였고, 실제 생성 기능은 POST로 따로 설계한다.

### 2) 요청 한 건의 처리 순서

> **DispatcherServlet** = Spring MVC 안에서 모든 요청을 먼저 받아, 알맞은 Controller 메서드를 찾아 호출하고 결과를 응답으로 돌려주는 서블릿

우리 코드에는 `DispatcherServlet`이 한 줄도 없다. 아래처럼 `@RestController`와 `@GetMapping`만 붙였는데 요청이 `hello()`에 닿는 이유가 이 서블릿이다.

```java
@RestController
public class HelloController {
    @GetMapping("/hello")   // 시작 시 "GET /hello → hello()" 매핑으로 등록된다
    public String hello() {
        return "Hello,StudyRoom!";
    }
}
```

```text
클라이언트: GET /hello
→ DispatcherServlet이 요청 수신
→ HandlerMapping에 "GET /hello를 처리할 메서드"를 조회
→ HelloController.hello() 호출
→ 반환값 "Hello,StudyRoom!"를 응답 본문으로 변환
→ 상태를 지정한 곳이 없으므로 200 OK와 함께 응답
```

애플리케이션이 뜰 때 `@GetMapping("/hello")`가 붙은 메서드가 "GET /hello → `hello()`" 매핑으로 등록된다. 요청이 오면 `DispatcherServlet`이 이 매핑 표에서 처리할 메서드를 찾아 호출하고, 반환값을 HTTP 응답으로 바꿔 내보낸다.

`/nope`처럼 매핑 표에 없는 경로는 컨트롤러까지 가지 않는다. 처리할 핸들러가 없다는 사실 자체가 "요청 쪽 경로가 잘못됐다"는 신호가 되어 404가 된다.

![시퀀스 다이어그램. 클라이언트가 GET /hello를 DispatcherServlet에 보내면 DispatcherServlet이 HandlerMapping에 핸들러를 조회한다. alt 프레임의 첫 분기(매칭되는 핸들러 있음)에서는 hello()가 호출되고 반환된 문자열이 200 OK와 함께 클라이언트로 돌아간다. 아무도 상태를 지정하지 않아 기본값이 붙는다. 두 번째 분기(없음)에서는 HandlerMapping이 핸들러 없음을 알리고 404가 반환되는데, HelloController 생명선까지는 가지도 못한다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day01-request-flow.png)

`DispatcherServlet`보다 앞에는 Spring Boot가 띄운 내장 Tomcat이 있다. Tomcat 공식 문서는 Tomcat이 HTTP 요청 줄과 헤더를 해석한 뒤 `CoyoteAdapter.service()`를 거쳐 서블릿 처리로 넘기는 앞단을 다음처럼 그린다.

![시퀀스 다이어그램. Tomcat의 Processor가 InputBuffer에 parseRequestLine()과 parseHeaders()를 호출해 HTTP 요청 줄과 헤더를 해석하고, prepareRequestProtocol()과 prepareRequest()로 요청 객체를 준비한 뒤 CoyoteAdapter.service()를 호출한다. 노트는 이 지점에서 서블릿 요청 처리가 일어난다고 표시한다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day01-web-tomcat-http11-request.png)

*출처: [Apache Tomcat 10.1 — Request Process Flow](https://tomcat.apache.org/tomcat-10.1-doc/architecture/requestProcess.html) — © The Apache Software Foundation, Apache License 2.0*

`CoyoteAdapter` 이후 요청은 Engine·Host·Context·Wrapper의 Valve 파이프라인과 Filter 체인을 차례로 지난 뒤에야 `DispatcherServlet.service()`에 도착한다. 같은 Tomcat 문서의 동기 처리 도식에 이 전체 경로가 나온다.

> **보장 범위** — 위 시퀀스 그림은 오늘 관찰한 결과를 설명하려고 단순화한 흐름이다. Spring Boot 기본 설정에는 정적 리소스 핸들러도 등록되므로, `/nope`가 404가 되기까지의 실제 내부 경로는 그림보다 단계가 더 있을 수 있다. 오늘은 **컨트롤러에 닿지 않고 404가 돌아왔다**는 결과까지만 확인했다(내부 경로는 미검증).

### 3) 기본 Status Code와 명시적 Status Code

> **기본 상태코드** = 컨트롤러가 예외 없이 정상 반환했고 누구도 다른 상태를 지정하지 않았을 때 붙는 200
> **`ResponseEntity`** = 그 기본값 대신 상태코드·헤더·본문을 개발자가 직접 정해 돌려주는 반환 타입

우리 코드의 세 메서드는 상태코드를 정하는 방식만 다르다.

```java
return "Hello,StudyRoom!";                               // hello()  → 200 (기본값)
return ResponseEntity.status(HttpStatus.OK).body("OK");  // health() → 200 (직접 지정)
return ResponseEntity.status(201).body("Created");       // bye()    → 201 (직접 지정)
```

기본값 200은 "이상 신호가 없으면 성공으로 해석한다"는 암묵적 규칙이다. 반대로 4xx·5xx가 나오려면 404의 "핸들러 없음"처럼 명시적인 실패 신호가 있어야 한다.

`/health`는 `HttpStatus.OK`를 명시했지만 결과는 `/hello`와 같은 200이다. 겉보기 동작은 같아도 코드에는 "이 값을 의도적으로 골랐다"는 판단이 남는다. 나중에 조건에 따라 다른 상태를 돌려줘야 할 때 바꿀 자리도 이미 드러나 있다.

`/bye`는 커밋된 코드에서 `.status(201)`처럼 정수를 그대로 썼다. `HttpStatus.CREATED`를 쓰면 값은 같으면서 의미가 이름으로 드러난다. `HttpStatus.`까지 입력하고 IDE 자동완성에서 `OK`를 찾은 것도 "400=`BAD_REQUEST`처럼 이름 있는 상수가 있을 것"이라는 추측에서 출발했다.

> **보장 범위** — 이 두 가지가 상태코드를 정하는 방법의 전부는 아니다. 404처럼 컨트롤러에 닿기 전에 정해지는 경우가 있고, 메서드에 `@ResponseStatus`를 붙이는 방법도 있다. 오늘 관찰한 정상 응답의 범위가 둘이었을 뿐이다(`@ResponseStatus`는 사용하지 않음).

### 4) 반환값과 응답 본문 변환

> **HttpMessageConverter** = Controller의 반환값을 HTTP 응답 본문(문자열, JSON 등)으로 바꿔 쓰는 변환기

우리 코드에서는 `hello()`가 돌려준 `String` 하나가 그대로 응답 본문 `Hello,StudyRoom!`이 됐다. 이는 `@RestController`가 `@Controller`와 `@ResponseBody`를 합친 애노테이션이기 때문이다.

```java
@RestController                       // = @Controller + @ResponseBody
public class HelloController {
    @GetMapping("/hello")
    public String hello() {
        return "Hello,StudyRoom!";    // 뷰 이름이 아니라 응답 본문이 된다
    }
}
```

```text
@Controller만 있을 때:   반환한 String → 뷰 이름으로 해석   (비교용 설명, 실행하지 않음)
@RestController일 때:    반환한 String → HttpMessageConverter → 응답 본문
```

`@ResponseBody`가 붙은 메서드의 반환값은 뷰 이름으로 해석되지 않는다. 메시지 컨버터가 반환 타입에 맞게 본문으로 직렬화한다. `@GetMapping`은 그보다 앞 단계에서 경로를 메서드에 연결하는 역할만 맡는다.

여기서 오늘 가장 크게 혼동한 짝이 나온다.

| 구분 | 본문(데이터) | 상태코드(메타데이터) |
|---|---|---|
| 무엇을 말하나 | 응답에 담긴 내용 | 요청이 어떻게 끝났는가 |
| 오늘 정해진 곳 | 반환값 → 메시지 컨버터 | 기본값 200 또는 `ResponseEntity` |
| 관련 없는 것 | 상태코드 결정 | 본문 타입(DTO 여부) |

DTO는 계층 사이에서 옮길 데이터의 **형태**이고, 상태코드는 응답의 **메타데이터**다. 200이 붙은 이유를 반환 데이터의 형태에서 찾으면 층이 어긋난다. 아래 3절의 1)이 바로 이 혼동이었다.

> **보장 범위** — 오늘 확인한 반환 타입은 `String`뿐이다. 객체를 JSON으로 바꾸는 변환은 Day2에서 요청 방향부터 다룬다.

### 5) URL 경로와 메서드 이름의 분리

> **URL 경로** = 클라이언트에게 공개되는 주소, **메서드 이름** = 코드 안에서 개발자가 기능을 구분하려고 붙인 이름

우리 코드의 `@GetMapping("/hello")`와 `hello()`가 같은 단어라 둘을 통일해야 하는지 의문이 생겼다. 답은 소스 주석에만 남아 있었다. 둘은 목적이 다르다.

```java
@GetMapping("/hello")      // URL 경로: Spring이 요청을 연결할 때 쓰는 값
public String hello() {    // 메서드 이름: 매핑에는 쓰이지 않는다
```

- **URL 경로**: 어떤 자원을 요청할지 지정한다. 관례상 소문자·하이픈·명사 위주(`/user-profiles`)
- **메서드 이름**: 관례상 동사+명사 카멜케이스(`getUserProfiles()`)

Spring은 매핑 애노테이션의 문자열로 요청을 연결하고, 메서드 이름은 매핑에 쓰지 않는다. 그래서 같게 지을 수는 있어도 같아야 하는 것은 아니다. Day2에서 `/reservations/cancel`·`cancel()`·`canceled()`로 이름이 층마다 갈라지는 모습으로 다시 등장한다.

### 6) Compile Time 규칙과 Runtime 규칙

> **Compile Time** = `javac`가 `.java`를 `.class`로 바꾸며 문법·import·시그니처를 검사하는 시점
> **Runtime** = JVM이 클래스를 올리고 Spring이 `@GetMapping` 매핑을 등록해 요청을 처리하는 시점

메서드 시그니처 중복, static import, bootRun 재시작은 이론이 아니라 우리 코드를 작성하다 걸린 것들이다. 공통점은 **Spring의 규칙이 아니라 Java와 JVM의 규칙**이라는 점이다.

```text
.java 작성
→ [컴파일 시점] javac: 문법, import, 메서드 시그니처 중복 검사
→ .class 생성
→ [실행 시점] bootRun: JVM이 클래스 로드, Spring이 @GetMapping 매핑 등록
→ 요청 처리
```

**메서드 시그니처 중복.** `/bye`를 만들며 경로만 바꾸고 메서드 이름을 `health()` 그대로 뒀더니 컴파일 에러가 났다.

```java
@GetMapping("/health")
public ResponseEntity<String> health() { ... }
@GetMapping("/bye")
public ResponseEntity<String> health() { ... }   // 경로는 달라도 같은 이름·파라미터 → 컴파일 에러
```

 경로 분기는 실행 시점에 Spring이 쓰는 정보이고, 같은 클래스에 메서드를 선언해도 되는지는 그보다 앞서 컴파일러가 판단한다. 컴파일러는 애노테이션 값이 아니라 이름과 파라미터 목록만 본다. 파라미터 목록이 다르면 오버로딩으로 허용된다.

**static import.** `import static org.springframework.http.HttpStatus.OK;`를 두면 `HttpStatus.OK` 대신 `OK`만 쓸 수 있다. 컴파일 시점에 이름을 줄여 해석하는 문법일 뿐이고, 가리키는 대상은 같다.

**bootRun 재시작.** 실행 중인 JVM 프로세스는 이미 로드한 클래스를 그대로 들고 있다. `.java`를 고쳐도 다시 컴파일하고 앱을 재시작하기 전에는 옛 코드가 응답한다. 재시작 없이 호출하면 옛날 응답을 새 코드의 결과로 읽을 수 있다.

> **보장 범위** — `spring-boot-devtools`의 자동 재시작은 존재만 확인했고 사용하지 않았다. 오버로딩 규칙도 설명만 했고 코드로 실행해 보지 않았다.


### 7) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| 내장 Tomcat | Spring Boot가 애플리케이션 안에 함께 띄우는 서블릿 컨테이너. HTTP 요청을 받아 서블릿에 넘긴다 |
| `DispatcherServlet` | Spring MVC에서 모든 요청을 먼저 받아 알맞은 Controller 메서드로 넘기는 서블릿 |
| `HandlerMapping` | "어떤 요청 → 어떤 메서드"인지 적힌 매핑 표. `@GetMapping`이 여기에 등록된다 |
| `HandlerAdapter` | 찾아낸 Controller 메서드를 실제로 호출해 주는 실행기 |
| `HttpMessageConverter` | Controller의 반환값을 응답 본문(문자열·JSON 등)으로 바꾸는 변환기 |

> **더 볼 것**
> - [DispatcherServlet — Spring Framework Reference](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet.html): 요청 처리 흐름의 앞부분
> - [@ResponseBody — Spring Framework Reference](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responsebody.html): `@RestController`가 `@Controller` + `@ResponseBody`라는 근거
> - [ResponseEntity — Spring Framework Reference](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responseentity.html): `@ResponseBody`에 상태와 헤더를 더한 반환 타입
> - [HTTP response status codes — MDN](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Status): 5계열 전체 목록
> - 아직 안 본 것 — `@ResponseStatus`, 오버로딩, `DevTools` 자동 재시작

## 2. 코드 구현

### 1) 기본값과 명시값을 나란히 둔 컨트롤러

시작점은 빈 스켈레톤이었다(Spring Boot 3.5.3 · Java 17 · web + validation, 롬복 없음). 컨트롤러를 만들기 전에 `./gradlew test`가 green인지부터 확인했다.

```java
@RestController
public class HelloController{
    @GetMapping("/hello")
    public String hello(){
        return "Hello,StudyRoom!";
    }
    @GetMapping("/health")
    public ResponseEntity<String> health(){
        return ResponseEntity
                .status(HttpStatus.OK)
                .body("OK");
    }
    @GetMapping("/bye")
    public ResponseEntity<String> bye(){
        return ResponseEntity
                .status(201)
                .body("Created");
    }
}
```

**한 줄씩 보기**

- `@RestController` — 이 클래스를 Spring Bean으로 등록하고, 모든 메서드의 반환값을 뷰 이름이 아니라 응답 본문으로 쓰게 한다.
- `@GetMapping("/hello")` — "GET /hello → 이 메서드" 매핑 정보다. 애플리케이션이 뜰 때 HandlerMapping에 등록된다.
- `public String hello()` — 반환 타입이 `String`이라 메시지 컨버터가 문자열 그대로 본문에 쓴다.
- `return "Hello,StudyRoom!";` — 상태를 지정한 곳이 없으므로 기본값 200이 붙는다.
- `ResponseEntity<String>` — 본문 타입이 `String`인 응답 전체(상태·헤더·본문)를 메서드가 직접 만든다는 선언이다.
- `.status(HttpStatus.OK)` — 이름 있는 상수로 200을 지정한다. 결과는 `hello()`와 같지만 의도가 코드에 남는다.
- `.status(201)` — 정수로 201을 지정한다. `HttpStatus.CREATED`와 같은 값이다.
- `.body("OK")` / `.body("Created")` — 응답 본문을 정한다.

대조군으로는 등록하지 않은 `/nope`를 호출해 "기본 성공 대 명시적 실패 신호"를 비교했다.

### 2) `/bye` 작성 중의 컴파일 에러 네 개

힌트 없이 `/bye`를 만들어 201을 반환하는 과제였는데, 작성한 코드가 컴파일되지 않았다. 코드 리뷰로 원인을 하나씩 찾았다.

1. **미완성 구문**: 자동완성을 탐색하다 지우지 않은 `HttpStatus.` 한 줄
2. **import 누락**: `ResponseEntity`는 import했지만 `HttpStatus`는 빠짐
3. **세미콜론 누락**: `.body("OK")` 뒤의 `;`
4. **메서드 시그니처 중복**: `health()`라는 이름을 두 메서드에 사용

앞의 셋은 입력 실수이고, 네 번째는 컴파일 시점 규칙을 오해한 것이었다(3절 2)). 네 개를 고친 뒤 `/bye`가 `201 Created`를 반환했다.

### 3) 자동 검증 결과

| 요청 | 결과 | 확인 방법 |
|---|---|---|
| `GET /hello`, `GET /health` | `200 OK` | `curl.exe -i` 수동 |
| `GET /bye` | 컴파일 에러 수정 후 `201 Created` | `curl.exe -i` 수동 |
| `GET /nope` | `404 Not Found` | `curl.exe -i` 수동 |

자동 테스트는 `contextLoads()` 하나라서 네 경로의 상태코드나 본문이 바뀌어도 빌드는 통과한다. 오늘 코드는 [`76a0fe5` 커밋](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/76a0fe5)에 있다.

## 3. 스스로 답한 질문

### 1) 기본 HTTP Status Code 200의 발생 원인

**질문.** `hello()`에 상태코드를 명시하지 않았는데 왜 200이 나왔을까?

**A1.** 처음에는 **"HTTP 요청 반환 형식이 DTO로 미리 저장되어있어서 그런가?"**라고 답했다. 완전히 틀린 방향이었다. DTO는 옮길 데이터의 형태이고 상태코드는 응답 메타데이터라, 같은 층의 개념이 아니다.

교정된 답은 이렇다. `hello()`가 예외 없이 정상 반환했고 다른 상태를 지정한 곳이 없으니 기본값 200이 붙었다. 반환한 문자열은 어딘가에 저장되는 것이 아니라 메시지 컨버터를 거쳐 그대로 응답 본문이 된다.

정답을 본 직후 다시 설명한 것이라 진짜 인출인지 확신할 수 없었다. 그래서 복습큐에 **+1일 재시험**으로 등록했다.

### 2) 경로가 다른 메서드의 이름 중복

**질문.** `@GetMapping` 경로가 다른데 왜 같은 이름의 메서드를 선언할 수 없을까?

**A2.** 경로가 다르니 될 거라고 예측했지만 컴파일 에러가 났다. 경로는 Spring이 **실행 시점에** 요청을 분기할 때 쓰는 정보다. 같은 클래스에 그 메서드를 선언해도 되는지는 그보다 앞서 Java 컴파일러가 판단하고, 컴파일러는 애노테이션을 보지 않고 이름과 파라미터 목록만 본다.

재발 방지 기준은 "이 규칙을 누가 언제 검사하는가"를 먼저 구분하는 것이다. 프레임워크 규칙과 언어 규칙은 적용 시점이 다르다.

### 3) `OK`만으로 상수를 참조한 원인

**질문.** `HttpStatus.OK` 대신 `OK`만 써도 되는 이유는?

**A3.** 곁가지로 나온 질문이었다. IDE가 `import static org.springframework.http.HttpStatus.OK;`를 자동으로 넣었기 때문이다. static import는 클래스 이름 없이 static 멤버를 쓰게 하는 문법일 뿐이고, 가리키는 대상은 `HttpStatus.OK`와 같다.

## 4. 학습 정리와 다음 범위

### 1) 전체 흐름 다시 보기

오늘 따라간 경로를 처음부터 끝까지 한 장으로 모으면 다음과 같다. ①②는 Tomcat, ③~⑦은 Spring MVC가 처리하고, 매핑이 없으면 ④에서 바로 404로 빠진다.

![전체 흐름도. Client가 curl.exe로 GET /hello를 보내면 내장 Tomcat의 ① Connector(Coyote)가 HTTP 요청 줄과 헤더를 파싱하고 ② Filter Chain을 지난다. 이어 Spring MVC 영역에서 ③ DispatcherServlet이 요청을 받아 ④ HandlerMapping에서 /hello → hello() 매핑을 찾고, ⑤ HandlerAdapter가 ⑥ HelloController.hello()를 호출한다. ⑦ HttpMessageConverter가 반환된 String을 본문으로 바꿔 200 OK와 "Hello,StudyRoom!"을 Client에 돌려준다. 매핑이 없는 /nope는 ④에서 빨간 점선으로 빠져 Controller에 닿지 않고 404 Not Found가 돌아간다. 하단 주석은 상태코드가 기본값 200이거나 ResponseEntity로 직접 지정된다(/bye → 201)고 적는다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day01-overview-request-flow.png)

### 2) 이해의 변화와 남은 것

오늘 바뀐 것은 "응답"을 보는 단위다. 전에는 컨트롤러가 돌려준 문자열이 곧 응답이라고 생각했다. 지금은 응답을 상태코드와 본문으로 나눠 보고, 상태코드는 흐름 끝의 기본값이거나 개발자가 의도적으로 고른 값이라고 설명할 수 있다.

상태코드 자체는 네 경로 모두 예상대로 나왔다. 그런데 "왜"를 설명하려니 막혔다. 결과를 맞히는 것과 메커니즘을 아는 것은 다른 일이었다.

**아직 남은 것**은 두 가지다. 첫째, 네 경로의 상태코드를 고정하는 자동 테스트가 없다. **나중에 고칠 것(Week A D7)**으로 분류했고, 이후 Day07에서 Reservation API의 200·400·404를 MockMvc로 고정하면서 요청 수준 테스트가 처음 들어왔다. 둘째, `GET /bye`의 201은 HTTP 의미에 맞지 않지만 상태 지정 방법을 확인하려는 학습 코드라 **고치지 않을 것**으로 두고, 생성 기능은 POST로 따로 설계한다.

면접에서 다시 답해볼 항목을 남긴다.

- 정상 반환에 200이 붙는 조건과 404가 컨트롤러 없이 정해지는 조건의 차이

---

오늘 공부한 소스코드: `app/src/main/java/com/example/studyroom/HelloController.java` ([`76a0fe5`](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/76a0fe5))
