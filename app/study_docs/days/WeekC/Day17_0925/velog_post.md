# [Spring Study Day 17] Transaction Propagation — REQUIRED와 REQUIRES_NEW

Day16에서는 애노테이션과 실행 주체(Spring AOP 프록시)가 분리돼 있다는 구조를 확인했다. 이번에는 트랜잭션 메서드가 또 다른 트랜잭션 메서드를 호출할 때, 새 경계가 기존 트랜잭션과 어떤 관계를 맺는지를 확인했다. `REQUIRED`(기본값)와 `REQUIRES_NEW` 둘만 다뤘고, 나머지 전파 옵션은 범위 밖이다.

> `PropagationOuterService.reserveThenFail()`(`@Transactional`)이 `PropagationInnerService.reserve()`를 호출한 뒤 예외를 던지는 상황에서, `reserve()`가 `REQUIRED`(기본값)면 저장한 예약이 outer와 함께 롤백됐고, `REQUIRES_NEW`로 바꾸면 outer 롤백과 무관하게 살아남았다. 전체 테스트 23개가 통과했다.

> **오늘의 흐름** `reserveThenFail() 트랜잭션 A 시작 → reserve() 호출 → REQUIRED는 A에 합류 / REQUIRES_NEW는 A를 보류하고 B 시작 → outer 예외 → A만 롤백`
>
> 이전 Day: Spring AOP 프록시와 self-invocation (Day16)
> 다음 Day: 연관관계 + Hibernate LAZY 프록시 (Week C D4)

REQUIRED와 REQUIRES_NEW의 차이는 결국 "outer가 실패했을 때 inner의 커밋이 몇 개의 트랜잭션에 속해 있었는가"로 갈린다.

![REQUIRED에서는 reserveThenFail의 예외가 트랜잭션 A 전체를 롤백해 inner가 저장한 예약까지 사라지지만, REQUIRES_NEW에서는 inner가 트랜잭션 A를 보류하고 별도 트랜잭션 B를 열어 커밋하므로 A만 롤백되고 예약은 남는 시퀀스 비교](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day17-transaction-propagation.png)

## 1. 개념 설명

### 1) REQUIRED — 진행 중인 트랜잭션에 합류

> **REQUIRED** = propagation을 지정하지 않았을 때의 기본값. 진행 중인 트랜잭션이 있으면 새로 만들지 않고 그 트랜잭션에 합류하고, 없으면 새로 시작한다

우리 코드에서는 `reserve()`에 propagation을 지정하지 않아 처음엔 `REQUIRED`였다.

```java
@Transactional
public void reserveThenFail(String roomName, String requesterName){
    innerService.reserve(roomName, requesterName);
    throw new RuntimeException("의도적 실패");
}
```

`reserveThenFail()`이 먼저 트랜잭션 A를 시작한 상태에서 `reserve()`가 호출된다. `REQUIRED`는 "이미 A가 있는가"를 판단 기준으로 삼는다 — 있다면 새 트랜잭션을 만들지 않고 A 안으로 들어간다.

```text
reserveThenFail() 호출 → 트랜잭션 A 시작
→ reserve() 호출, REQUIRED: 이미 A가 있음 → 합류(새로 안 만듦)
→ INSERT는 A의 일부로 실행(미커밋)
→ reserveThenFail()에서 예외 발생 → A 전체 rollback
→ reserve()의 INSERT도 함께 취소
```

처음엔 "같은 트랜잭션 범위에 포함되서"라고만 답했다가, "있으면 롤백, 없으면 저장"으로 판단 기준을 잘못 짚었다. 실제로는 롤백 여부가 아니라 **새 트랜잭션을 만드는가**가 REQUIRED의 판단 기준이다. 이 REQUIRED 동작은 이후 `reserve()`를 `REQUIRES_NEW`로 바꾸며 코드에서 대체됐다 — `noneMatch`로 롤백을 확인한 실행 로그는 남아 있지만, 현재 커밋의 소스에는 REQUIRED 버전의 테스트가 남아 있지 않다(2절 참고).

![REQUIRED는 reserveThenFail()이 시작한 트랜잭션 A의 단일 경계 안에 reserve()가 그대로 합류하는 구조를 보여준다. 두 메서드가 같은 경계 안에 있어 커넥션도 1개만 유지되고, 예외가 발생하면 A 전체가 rollback되어 reserve()의 INSERT까지 함께 취소된다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day17-required-tx-boundary.png)

### 2) REQUIRES_NEW — 독립된 트랜잭션 분리

> **REQUIRES_NEW** = 진행 중인 트랜잭션이 있어도 무시하고, 그것을 잠시 보류(suspend)한 뒤 완전히 독립된 새 트랜잭션을 시작한다. 새 트랜잭션이 끝나면 보류했던 트랜잭션을 재개한다

우리 코드에서는 `reserve()`에 propagation을 명시해 이 동작으로 바꿨다.

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public Reservation reserve(String roomname, String requestername) {
    Reservation reservation = new Reservation(roomname,requestername);
    reservation.confirm();
    return repository.save(reservation);
}
```

REQUIRED와 달리 "이미 트랜잭션이 있는가"를 무시한다. 있어도 항상 새로 만든다.

```text
reserveThenFail() 호출 → 트랜잭션 A 시작
→ reserve() 호출, REQUIRES_NEW: A를 보류하고 트랜잭션 B 시작
→ INSERT + commit은 B 안에서 완결
→ B 종료 → A 재개
→ reserveThenFail()에서 예외 발생 → A만 rollback
→ 이미 commit된 B는 영향받지 않음
```

`REQUIRES_NEW`로 바꾸기 전 "예약이 남아있을 것"이라고 예측했고, `reservationRepository.findAll().stream().anyMatch(r -> r.getRoomName().equals("D-101"))`가 `true`로 확인돼 예측과 일치했다. 두 경로의 시퀀스 비교는 서두 그림 참고. 다만 `assertThrows`와 `anyMatch` 두 assertion으로 "예외가 발생했다"와 "예약이 남았다"까지만 확인했고, 트랜잭션 B가 정확히 언제 커밋되는지(메서드 반환 시인지, 다른 시점인지)는 Spring 소스코드까지 따라가 검증하지 않았다.

![REQUIRES_NEW는 reserveThenFail()의 트랜잭션 A를 보류(점선 경계)하고 reserve()가 완전히 독립된 트랜잭션 B(실선 경계)를 새로 여는 구조를 보여준다. 보류된 A와 신규 B가 겹치는 구간에는 커넥션이 동시에 2개 필요하고, B가 커밋을 마치면 A가 재개돼 outer 예외로 A만 rollback된다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day17-requiresnew-tx-boundary.png)

### 3) 커넥션 풀 고갈 위험

> **커넥션 풀 고갈** = 동시에 필요한 DB 커넥션 수가 풀 크기를 넘어서, 나머지 요청이 커넥션을 기다리며 병목·정체되는 상태

`REQUIRES_NEW`가 실행되는 동안은 보류된 A용 커넥션과 새로 시작한 B용 커넥션이 **동시에** 필요하다(1절 2) 그림의 "동시" 구간). `REQUIRED`는 항상 커넥션 1개로 끝나던 것과 다르다.

| 구분 | 동시 필요 커넥션 | 위험 |
|---|---|---|
| REQUIRED | 1개(합류) | 없음 |
| REQUIRES_NEW | 2개(보류 + 신규) | 풀 고갈 시 병목, 최악의 경우 대기 요청끼리 교착 |

이 위험은 REQUIRES_NEW의 동작 원리(트랜잭션 보류 + 신규 시작)로부터 도출한 설명이며, 실제로 커넥션 풀을 고갈시키는 동시 요청 테스트는 이번 Day에서 실행하지 않았다(미검증, 4절 참고).

### 4) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| Propagation | 새 트랜잭션 경계가 진행 중인 트랜잭션과 맺는 관계를 정하는 규칙 |
| REQUIRED | 있으면 합류, 없으면 새로 시작(기본값) |
| REQUIRES_NEW | 있어도 보류하고 항상 새로 시작 |
| Suspend(보류) | REQUIRES_NEW 시작 시 기존 트랜잭션을 잠시 멈추는 것 |
| Connection Pool | 미리 만들어 둔 DB 커넥션을 빌려주고 반납받는 저장소 |

> **더 볼 것**
> - [Spring Transaction Propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html): REQUIRED 외 나머지 전파 옵션(MANDATORY, NESTED 등)
> - Hibernate LAZY 프록시 — Week C D4에서 다룰 Entity 지연 로딩 장치

## 2. 코드 구현

### 1) REQUIRES_NEW 전환과 테스트

```java
@Test
void requiresNewSurviesOuterFailure() {
    assertThrows(RuntimeException.class,
            () -> outerservice.reserveThenFail("D-101", "진우"));

    assertTrue(reservationRepository.findAll().stream()
            .anyMatch(r -> r.getRoomName().equals("D-101")));
}
```

**한 줄씩 보기**

- `assertThrows(RuntimeException.class, ...)`: `reserveThenFail()`이 실제로 예외를 던지는지 먼저 확인한다.
- `reservationRepository.findAll().stream()`: 저장된 예약 전체를 스트림으로 연다.
- `.anyMatch(r -> r.getRoomName().equals("D-101"))`: 이번 테스트가 만든 그 예약이 하나라도 있는지 확인한다.

처음엔 `assertTrue(reservationRepository.findAll().isEmpty())`처럼 "DB 전체가 비어있다"로 검증했다. 단독 실행은 통과했지만 `./gradlew test` 전체 스위트에서는 실패했다 — 다른 테스트(`ReservationServiceTransactionTest` 등)가 테스트 메서드 자체에 `@Transactional`이 없어 실제로 커밋한 예약이 같은 DB에 남아 있었기 때문이다. "전체가 비어있다"가 아니라 "이번에 만든 이 레코드가 있는가/없는가"로 assertion 범위를 좁혀야 했다.

### 2) 자동 검증 결과

| 검증 항목 | 테스트 | 결과 |
|---|---|---|
| REQUIRES_NEW의 예외 발생과 예약 생존 | `requiresNewSurviesOuterFailure()` | `assertThrows` + `anyMatch` 통과 |
| 전체 스위트 회귀 | `./gradlew test` | 23개 테스트 전체 통과 |

수동 확인은 REQUIRED 상태에서 `noneMatch`로 롤백을 먼저 확인한 실행 로그다(코드는 REQUIRES_NEW로 대체됨). 커밋: [`2933d93`](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/2933d93/app/src/test/java/com/example/studyroom/service/TransactionPropagationTest.java)

## 3. 스스로 답한 질문

### 1) REQUIRED의 판단 기준

**질문.** REQUIRED가 롤백/저장 여부를 정하는가, 새 트랜잭션 생성 여부를 정하는가?

**A1.** 처음엔 "트랜잭션이 있으면 롤백, 없으면 저장"이라고 답해 REQUIRES_NEW와 반대로 이해했다. REQUIRED의 판단 기준은 롤백 여부가 아니라 **새 트랜잭션을 만드는가**다 — 있으면 합류(안 만듦), 없으면 새로 시작. "새로 만든다"는 오히려 REQUIRES_NEW 쪽이다.

### 2) `findAll().isEmpty()`가 전체 스위트에서만 실패한 이유

**질문.** 단독 실행은 통과하는데 전체 스위트에서만 실패한 이유는?

**A2.** `findAll().isEmpty()`는 "DB 전체가 비어있다"는 전제였는데, 이 프로젝트의 다른 테스트들은 메서드 자체에 `@Transactional`이 없어 실제로 커밋한다. 전체 스위트로 돌리면 그 커밋된 데이터가 먼저 쌓여 있어 전제가 깨졌다. `anyMatch`/`noneMatch`로 "이번 테스트가 만든 레코드"만 특정하도록 좁혀 해결했다.

## 4. 학습 정리와 다음 범위

### 1) 이해의 변화와 남은 것

Day16까지는 "트랜잭션 경계를 어디에 두는가"만 다뤘다. Day17에서는 그 경계 두 개가 겹칠 때 어떤 관계를 맺는지가 추가됐다. REQUIRED는 기본값이라 별생각 없이 써도 되는 것처럼 보이지만, "함께 롤백된다"는 성질 자체가 REQUIRES_NEW를 써야 할 이유이기도 하다는 걸 이번에 연결했다.

**아직 남은 것**

- 나중에 고칠 것: 커넥션 풀 고갈이 실제 동시 요청에서 병목·교착으로 이어지는지는 이론 설명만 했고 동시성 테스트로 실측하지 않았다(해결 Day 미정, 범위 밖으로 남겨둠).
- 고치지 않을 것: 테스트 메서드명 `requiresNewSurviesOuterFailure`의 오타(Survies→Survives)는 동작에 영향이 없어 그대로 둔다.

면접에서 다시 답해볼 항목을 남긴다.

- REQUIRED와 REQUIRES_NEW 중 무엇을 기본으로 쓰고, 언제 REQUIRES_NEW로 바꾸는가
- REQUIRES_NEW가 outer 실패와 무관하게 살아남는 이유를 커넥션·트랜잭션 경계로 설명하면

---

오늘 공부한 소스코드: `app/src/test/java/com/example/studyroom/service/TransactionPropagationTest.java`
