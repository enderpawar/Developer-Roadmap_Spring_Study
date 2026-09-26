# [Spring Study Day 6 & Day 7] 1주차 마무리 시험

Week A에서는 HTTP 요청이 Controller에 들어온 뒤 DTO, Service, Repository를 거쳐 응답으로 돌아가는 흐름을 공부했다. 마지막 D6는 새 개념을 추가하는 날이 아니라 지금까지 배운 내용을 노트 없이 꺼내 보는 누적시험이었고, D7은 시험에서 드러난 빈틈과 미뤄 둔 코드 문제를 정리하는 버퍼로 사용했다.

계획상 7월 30일과 31일에 나뉜 일정이었지만 실제 마무리는 8월 2일에 한꺼번에 진행했다. 그래서 이번 글도 Day별 개념 두 편으로 나누지 않고, **시험 → 오답 교정 → 코드 반영 → 검증**의 한 흐름으로 기록한다.

## 1. 시험 범위와 진행 방식

1주차에 다룬 범위는 다음과 같다.

- Day 1 — HTTP 요청·응답 왕복과 상태 코드
- Day 2 — record DTO와 상태를 가진 Domain class의 분리
- Day 3 — Bean Validation과 `@RestControllerAdvice`
- Day 4 — Controller·Service·Repository 책임 분리
- Day 5 — IoC·DI·생성자 주입과 Singleton Bean

Day01~03 문항은 앞선 +2 인출에서 통과했고 다음 복습일이 아직 오지 않았다. 이번 시험에서는 Day04~05에서 새로 도래한 문항과 오답 재시험을 중심으로 확인했다.

시험은 정답을 먼저 읽는 방식이 아니었다. 질문에 먼저 답하고, 틀린 이유를 확인한 뒤 같은 문장을 외우는 대신 코드에서 근거를 다시 찾았다.

| 확인한 항목 | 결과 | 다시 잡은 기준 |
|---|---|---|
| IoC와 DI 구분 | 통과 | 조립 제어권과 의존 객체 전달을 구분 |
| `Long` 값 비교 | 통과 | 참조 동일성과 값 동일성을 구분 |
| Singleton Bean의 `==` | 교정 후 통과 | 값이 아니라 같은 Bean 참조 |
| Repository 인터페이스 의존 | 통과 | 구현 교체 시 변경 전파 감소 |
| PK가 필요한 이유 | 교정 후 통과 | 단건 갱신 대상을 고유하게 식별 |
| `@PathVariable` 불일치 | 통과 | 컴파일이 아닌 요청 처리 시점의 문제 |
| 생성자 주입 문법 | 교정 후 통과 | 생성자명·필드명·타입 확인 |

## 2. 시험에서 틀린 세 문제

### 1) Singleton Bean의 `==`

처음에는 다음과 같이 답했다.

> “객체 상으로는 동일해 보이지만 값이 다르기 때문”

이 답에는 참조 비교와 값 비교가 섞여 있었다. `==`는 객체의 내부 값이 같은지 확인하지 않는다. 두 변수가 같은 객체를 가리키는지 확인한다.

같은 ApplicationContext에서 `ReservationService` Bean을 두 번 조회하면 Spring의 기본 Singleton scope 때문에 동일한 인스턴스를 돌려받는다. 그래서 이 경우에는 `==` 또는 JUnit의 `assertSame()`이 맞다.

반면 두 `Long` ID가 같은 숫자인지 확인할 때는 `.equals()`를 사용한다. 작은 숫자에서는 boxing 캐시 때문에 `==`도 우연히 통과할 수 있어 더 위험하다.

시험 뒤에는 다음 테스트를 근거로 다시 확인했다.

```java
ReservationService first = applicationContext.getBean(ReservationService.class);
ReservationService second = applicationContext.getBean(ReservationService.class);

assertSame(first, second);
```

이번 교정으로 “객체에는 `==`를 쓰면 안 된다”가 아니라, **무엇을 비교하려는지 먼저 정한다**는 기준을 세웠다.

### 2) ID 없이도 수정할 수 있는가

처음에는 `id`가 없어도 수정할 수 있다고 설명했다. 이름이나 방 번호로 검색 조건을 만드는 것 자체는 가능하기 때문이다.

하지만 방 이름과 예약자 이름은 중복될 수 있다. 조건에 맞는 데이터가 여러 개라면 기존 예약 하나를 안전하게 특정할 수 없다.

PK의 핵심은 단순히 조회 기능을 가능하게 만드는 데 있지 않았다. **어느 데이터를 수정할 것인지 고유하게 지목해 단건 갱신 계약을 성립시키는 것**이 핵심이었다.

이 교정은 D7의 저장소 수정으로 바로 이어졌다. ID가 없으면 신규 예약으로 추가하고, 기존 ID가 있으면 같은 위치의 예약을 교체하도록 `save()` 계약을 나눴다.

### 3) Constructor Injection에서 `this.x`는 무엇인가

`this.x`의 `x`를 메서드라고 답했다가 교정했다. `this`는 현재 객체이고, `this.x`는 현재 객체에 선언된 필드다.

생성자 주입 코드를 독립 작성할 때는 세 가지를 함께 확인해야 했다.

1. 생성자 이름이 클래스 이름과 같은가?
2. `this.x`의 `x`가 실제로 선언된 필드인가?
3. 생성자 매개변수 타입을 그 필드에 대입할 수 있는가?

Controller의 생성자 모양을 그대로 복사하는 것이 아니라 현재 클래스의 이름·필드·필요한 의존 타입에 맞게 바꿔야 한다.

## 3. D7 — 시험이 끝난 뒤 코드를 다시 보니

D7에는 새 기능을 억지로 추가하지 않고 Day03·04에서 미뤄 둔 기술부채를 처리했다. 시험에서 교정한 “식별자”와 “계층별 책임”을 바로 현재 코드에 적용해 보는 작업이었다.

### 1) 기존 예약도 무조건 `add()`하고 있었다

기존 `InMemoryReservationRepository.save()`는 ID 유무와 관계없이 `store.add()`를 호출했다. `ArrayList`는 같은 객체 참조를 다시 넣는 것을 막지 않는다.

`findById()`로 꺼낸 예약을 취소한 뒤 다시 저장하면 상태만 바뀌는 것이 아니라 목록에 같은 참조가 한 칸 더 생길 수 있었다.

저장 규칙을 다음과 같이 확정했다.

- ID가 없으면 신규 예약 — ID를 부여하고 `add()`
- 기존 ID면 저장된 위치를 찾아 `set()`
- ID는 있지만 저장소에 대상이 없으면 계약 위반 예외

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
```

### 2) 없는 예약이 `null`로 Service까지 흘렀다

기존 `findById()`는 예약을 못 찾으면 `null`을 반환했다. Service는 확인 없이 `cancel()`을 호출했기 때문에 존재하지 않는 번호가 `NullPointerException`으로 끝날 수 있었다.

이를 계층별로 나눴다.

- Repository — `Optional.empty()`로 값 부재 표현
- Service — `ReservationNotFoundException`으로 업무 의미 부여
- Web — 전역 예외 처리기에서 404 응답으로 변환

```java
Reservation reservation = reservationRepository.findById(id)
        .orElseThrow(() -> new ReservationNotFoundException(id));

reservation.cancel();
reservationRepository.save(reservation);
```

Repository가 HTTP 404를 직접 만들지 않고, Service가 JSON 본문을 만들지 않는다는 경계도 유지했다.

### 3) 입력 오류와 서버 오류를 분리했다

`/reservations/cancel/0`처럼 잘못된 식별자는 Service 전에 거절하도록 `@Positive`를 적용했다. Spring MVC의 메서드 검증 오류는 400으로 변환했다.

예상하지 못한 예외는 500으로 응답하되 `ex.getMessage()`를 그대로 본문에 넣지 않았다. 상세 stack trace는 서버 로그에 남기고, 클라이언트에는 일반 메시지만 반환한다.

## 4. 자동 테스트로 확인한 범위

코드를 읽고 그럴 것이라고 판단하는 데서 끝내지 않고, 단위 테스트와 MockMvc 테스트를 나눠 추가했다.

| 테스트 층 | 확인한 내용 | 결과 |
|---|---|---|
| Spring context | 컨텍스트 기동, Singleton Service 동일성 | 2개 통과 |
| 단위 테스트 | 중복 저장 방지, 없는 ID, 404 변환, 500 정보 비노출 | 4개 통과 |
| MockMvc | 예약 200, DTO 400, 없는 예약 404, 경로 ID 400 | 4개 통과 |

전체 10개 테스트가 깨끗한 빌드에서 통과했다.

Service 단위 테스트는 저장 규칙과 도메인 예외를 빠르게 확인한다. MockMvc 테스트는 URL 매핑, Controller 검증, `@RestControllerAdvice`, 상태 코드, JSON 본문까지 실제 Spring MVC 흐름을 함께 확인한다.

수정 전 코드에 실패 테스트를 먼저 실행한 기록은 없다. 따라서 이번 작업을 TDD의 red-green 순서로 진행했다고 쓰지는 않는다. 확인한 범위와 확인하지 않은 순서를 구분해 남긴다.

구현과 검증 근거는 [commit `6c88dcb`](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/6c88dcb)에 있다.

## 5. 1주차를 마치며

1주차를 시작할 때는 Controller가 요청을 받고 문자열을 반환하는 정도만 보였다. 지금은 요청 본문 검증, DTO와 Domain 분리, Service·Repository 책임, 생성자 주입, 저장 실패와 HTTP 오류 응답까지 한 요청의 흐름으로 연결할 수 있게 됐다.

시험에서 가장 크게 바뀐 이해는 `==`와 PK였다. 둘 다 결국 “같은 대상이라고 판정할 기준이 무엇인가?”라는 질문이었다. 그 기준이 명확해지자 저장소의 추가·갱신 분기와 없는 예약의 처리도 자연스럽게 연결됐다.

아직 공통 오류 코드·타임스탬프·요청 식별자를 가진 오류 DTO는 없다. 이는 Week D D5 또는 Week E D1에서 다룰 기술부채로 남겼다.

다음 시작점은 Week B D1이다. Flyway `V1__init`으로 스키마를 직접 정의하면서, 메모리의 `id`와 저장 계약을 실제 DB의 PK·제약 조건으로 이어갈 예정이다.
