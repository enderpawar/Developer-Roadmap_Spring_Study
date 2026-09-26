# [백엔드 스터디 1주차] Todo API로 익힌 Spring 3계층 구조와 유스케이스 설계

> 1주차는 Todo CRUD API를 직접 만들어보면서 Spring의 Controller-Service-Repository-DTO-Domain 흐름을 손으로 짚어본 주였다. 처음엔 흐름을 두루뭉술하게 이해하고 있었는데, 스스로 질문을 던지고 답해보면서 각 계층이 "왜" 필요한지를 교정할 수 있었다. 이번 글은 그 과정을 정리한 것이다.

## 1. HTTP Client로 CRUD와 에러 응답 직접 확인하기

`.http` 파일로 Todo API의 CRUD 전 구간(생성/전체조회/단건조회/수정/완료처리/삭제)을 호출해보고, 정상 케이스뿐 아니라 에러 케이스도 직접 만들어봤다.

```http
### 변수 정의
@host = http://localhost:8080

### 1. Todo 생성
POST {{host}}/api/todos
Content-Type: application/json

{
  "title": "Http 404 bad request 확인",
  "description": "HTTP Client 사용법 익히기"
}

### 2. Todo 전체 조회
GET {{host}}/api/todos

### 3. Todo 단건 조회
GET {{host}}/api/todos/8

### 4. Todo 수정
PATCH {{host}}/api/todos/2
Content-Type: application/json

{
  "title": "수정된 제목",
  "description": "수정된 설명"
}

### 5. Todo 완료 처리
PATCH {{host}}/api/todos/2/complete

### 6. Todo 삭제
DELETE {{host}}/api/todos/2
```

이 과정에서 두 가지 에러 상황을 직접 재현했다.

**① 404 Not Found — 존재하지 않는 리소스를 조회했을 때**

전체 조회 결과에 없는 id(`8`)로 단건 조회를 요청하니 다음과 같은 응답이 왔다.

```http
HTTP/1.1 404
Content-Type: application/json
Transfer-Encoding: chunked
Date: Fri, 17 Jul 2026 04:26:21 GMT

{
  "timestamp": "2026-07-17T13:26:21.474724",
  "status": 404,
  "error": "Not Found",
  "message": "Todo를 찾을 수 없습니다. id=8",
  "path": "/api/todos/8",
  "errors": {}
}
```

**② 400 Bad Request — 요청 형식이 잘못됐을 때**

`request.http`에서 JSON body의 `title` 필드를 일부러 제거하고 요청을 보내니, JSON 형식 자체가 깨지면서 400 에러가 발생했다. 클라이언트가 보낸 요청을 서버가 아예 해석할 수 없는 상태라는 걸 눈으로 확인한 케이스였다.

## 2. 정상 흐름 정리 (Todo 생성 기준)

처음엔 흐름을 이렇게 이해하고 있었다.

> "Http 요청이 들어오면 DTO/Domain이 미리 정의한 형식대로 받는지 확인하고, Controller가 요청을 해석해서 Service를 부르고, Service가 Repository와 실제로 통신하고, Repository가 데이터를 저장한다."

틀린 방향은 아니었지만 "이 시점에 Domain이 이미 존재한다"는 오해가 섞여 있었다. 실제로 다시 짚어보니 Domain 객체는 검증이 끝난 *뒤*, Service 안에서 처음 생성되는 것이었다. 교정한 흐름은 이렇다.

```text
[Client]
   │  POST /api/todos + JSON body
   ▼
┌─────────────────────────────────────────────┐
│ 1. HTTP 요청 도착                              │
│    POST /api/todos, Content-Type: json       │
└───────────────────┬───────────────────────────┘
                     ▼
┌─────────────────────────────────────────────┐
│ 2. DTO — 입구 검증                             │
│    TodoCreateRequest 바인딩                    │
│    @Valid → @NotBlank, @Size 검사              │
│    ※ 이 시점엔 Domain(Todo) 아직 없음           │
│    검증 실패 시 → 즉시 이탈 (400)                │
└───────────────────┬───────────────────────────┘
                     │ 검증 통과
                     ▼
┌─────────────────────────────────────────────┐
│ 3. Controller — 요청 해석만                     │
│    (POST인지 식별) → 로직 없음                  │
│    todoService.create(request) 호출            │
└───────────────────┬───────────────────────────┘
                     ▼
┌─────────────────────────────────────────────┐
│ 4. Service — 조립 담당                          │
│    ① new Todo(title, description)             │
│       → Domain 객체 최초 생성                   │
│    ② todoRepository.save(todo)                │
│       → 저장을 Repository에 위임                │
│    ③ TodoResponse.from(savedTodo)             │
│       → Domain을 DTO로 변환                    │
└───────────────────┬───────────────────────────┘
                     ▼
┌─────────────────────────────────────────────┐
│ 5. Repository (인터페이스 + 구현체 분리)          │
│    Service → TodoRepository (인터페이스)만 인지  │
│    실제 저장 방식(Map)은                        │
│    InMemoryTodoRepository(구현체)만 앎          │
└───────────────────┬───────────────────────────┘
                     ▼
┌─────────────────────────────────────────────┐
│ 6. 응답                                       │
│    Controller가 Service의 TodoResponse를       │
│    그대로 반환 → 201 CREATED                    │
└───────────────────┬───────────────────────────┘
                     ▼
                 [Client]
```

여기서 교정된 포인트 세 가지를 짚고 넘어가고 싶다.

- **Domain은 요청 검증에 관여하지 않는다.** 검증은 DTO의 몫이고, Domain은 검증을 통과한 뒤 Service 안에서 생성된다.
- **Service는 Repository와의 통신만 하는 게 아니다.** Domain 객체 생성과 `update()`, `complete()` 같은 메서드 호출까지 함께 조립하는 게 Service의 역할이다.
- **Repository는 저장 공간 자체가 아니라, 저장 공간(Map)에 접근하는 창구다.** 인터페이스와 구현체가 분리되어 있어서, 백엔드 입장에서는 "DB에 접근하는 창구"로 이해하는 게 맞다.

## 3. 스스로 묻고 답한 질문들

### Q. Controller는 왜 필요한가?
HTTP 요청을 받아 해석하고(POST/GET/PATCH/DELETE), Service를 호출한 뒤 그 결과를 응답으로 변환하는 창구 역할을 하기 위해서다. 로직은 직접 갖지 않는다.

### Q. Service는 왜 필요한가?
Controller(HTTP 처리)와 Repository(저장 방식) 사이에서 **유스케이스**(use case = "사용자가 어떤 목적을 달성하기 위해 시스템이 수행하는 하나의 작업 단위/시나리오") 흐름을 조립하기 위해서다. Service가 없으면 Controller가 "찾고, 없으면 예외 던지고, 저장하고, 변환하는" 로직을 전부 떠안게 된다. Service가 이 조립 책임을 가져가야 Controller는 HTTP만, Repository는 저장만 신경 쓸 수 있다.

### Q. Repository는 왜 필요한가?
저장 공간(Map, DB 등)에 접근하는 창구(인터페이스)를 따로 두어, Service가 "무엇을 할 수 있는지"만 알고 "어떻게 저장하는지"는 몰라도 되게 하기 위해서다. 인터페이스와 구현체를 분리해두면 저장 방식이 바뀌어도(Map → JPA) Service 코드는 바뀌지 않는다.

### Q. DTO는 왜 필요한가?
API가 주고받을 데이터의 모양을 도메인 모델과 분리해서 정의하기 위해서다. 도메인(Todo)을 그대로 응답에 노출하지 않고 필요한 필드만 골라 내보낼 수 있고, `@NotBlank`, `@Size` 같은 검증 규칙을 붙일 자리도 생긴다. 도메인이 바뀌어도 API 응답 계약은 깨지지 않는다.

### Q. Domain 객체는 어떤 역할인가?
데이터의 상태와, 그 상태가 바뀌는 규칙(`update`, `complete`)을 함께 표현하는 역할이다. 상태 변경 로직을 자기 자신 안에 캡슐화한다.

### Q. Controller에서 Repository를 직접 부르면 왜 안 좋은가?
Service의 `getTodo(id)` 같은 "찾고 없으면 예외" 로직이 여러 메서드(`findById`/`update`/`complete`/`delete`)에서 재사용되는데, Controller가 Repository를 직접 부르면 이런 공통 로직이 Controller마다 중복돼야 한다. 저장 방식이 바뀌면 Service 한 곳만 고치면 될 것이, Controller 여러 곳을 다 고쳐야 하는 문제로 번진다.

### Q. Todo 상태 변경을 Todo 내부 메서드로 처리하면 뭐가 좋은가?
`update()`/`complete()`는 필드 변경과 `updatedAt` 갱신이 항상 같이 일어나도록 강제한다. Service가 필드를 직접 바꾸면(`setTitle` 등) `updatedAt` 갱신을 깜빡할 위험이 있는데, 규칙을 Todo 내부에 캡슐화하면 "상태 변경 시 지켜야 할 규칙"이 한 곳에만 존재해서 나중에 규칙이 추가돼도 Todo 클래스만 고치면 된다.

### Q. TodoNotFoundException은 어디서 발생해야 하는가?
Service 계층의 `getTodo(id)` 내부에서 발생한다. GET 단건 조회뿐 아니라 update, complete, delete까지 총 4곳에서 공통으로 재사용된다.

### Q. GlobalExceptionHandler는 왜 필요한가?
모든 Controller, 모든 HTTP 메서드에서 발생하는 예외를 한곳에서 공통 JSON 에러 포맷으로 변환하기 위해서다. 이게 없으면 Controller 메서드마다 try-catch를 반복해야 한다.

### Q. 다음 주 JPA로 바꾸면 어떤 코드가 가장 많이 바뀌는가?
`InMemoryTodoRepository` 같은 Repository 구현체가 `JpaRepository` 기반으로 통째로 바뀐다. Todo 도메인도 `@Entity` 어노테이션이 붙는 등 일부 바뀔 수 있다. 반면 TodoController, TodoService, DTO들은 거의 안 바뀐다 — Service가 `TodoRepository` 인터페이스에만 의존하기 때문이다.

## 정리하며

1주차의 핵심은 "각 계층이 무엇을 모르고 있어야 하는가"였다. Controller는 저장 방식을 몰라야 하고, Service는 HTTP를 몰라야 하고, Repository는 검증 규칙을 몰라야 한다. 이렇게 서로 모르는 게 나뉘는 경계가 곧 인터페이스이고, 그 경계 덕분에 다음 주 In-Memory에서 JPA로 저장 방식을 바꿔도 Controller와 Service는 거의 그대로 유지될 것으로 예상하고 있다.

---
#Spring #SpringBoot #Backend #TIL #백엔드스터디 #REST #DTO #Repository패턴

오늘 공부한 소스코드: [8week_Spring_Study/week1](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/tree/master/week1)
