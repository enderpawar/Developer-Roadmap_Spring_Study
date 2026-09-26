# [Spring Study Day 15] Transaction Boundary — flush·commit·rollback

Week B에서는 영속성 컨텍스트와 변경 감지를 관찰하기 위해 `@Transactional`을 설명 없이 사용했다. 이번에는 `ReservationService.cancel()`을 기준으로 어디부터 어디까지 하나의 트랜잭션이어야 하는지 정하고, 정상 반환과 예외 발생이 DB의 최종 상태를 어떻게 갈라놓는지 확인했다.

> 예약 조회와 상태 변경을 Service 메서드 하나의 트랜잭션으로 묶었다. 정상 반환 경로에서는 명시적인 `save()` 없이 `UPDATE`가 commit됐고, 실패 경로에서는 `flush()`로 같은 `UPDATE`를 먼저 실행한 뒤에도 rollback되어 원래 값이 유지됐다. 전체 테스트 18개가 통과했으며, `readOnly=true`는 쓰기 권한 제어가 아니라 조회 의도와 최적화를 위한 힌트라는 한계도 함께 정리했다.

> **오늘의 흐름** `ReservationController → ReservationService.cancel() @Transactional 시작 → findById → 상태 변경(Dirty Checking) → 정상 반환: flush → commit / 예외: rollback`
>
> 이전 Day: 스키마 소유권·V3 `cancel_reason` 정리 (Day14)
> 다음 Day: `@Transactional`을 실행하는 Spring AOP Proxy와 self-invocation (Day16)

## 1. 개념 설명

### 1) Service 계층의 Transaction Boundary

> **Transaction Boundary** = 여러 DB 작업을 전부 성공시키거나 전부 취소시키는 하나의 단위로 묶은 범위

우리 코드에서는 `cancel()` 메서드 전체가 이 범위다. 조회, 상태 변경, DB 반영이라는 서로 다른 단계를 하나의 `@Transactional`로 묶었다.

```java
@Transactional //트랜잭션 경계 : 조회 -> 상태변경 -> DB에 반영
// 전부 성공하거나 전부 취소하는 하나의 단위로 묶는 범위
public Reservation cancel(Long id, String cancelReason) {
    Reservation reservation = reservationRepository.findById(id)
            .orElseThrow(() -> new ReservationNotFoundException(id)); // 1. 예약이 있는지 없는지 조회

    reservation.cancel(cancelReason); //2. 취소 상태로 변경

    return reservation;
}
```

예약 취소는 SQL 한 줄의 이름이 아니다. 애플리케이션이 보장해야 하는 하나의 업무 흐름이다.

```text
예약 ID로 조회
→ 존재하지 않으면 취소 중단
→ 존재하면 confirmed와 cancelReason 변경
→ 두 변경을 DB에 함께 반영
```

이 흐름에서 조회만 성공하고 상태 변경이 실패하거나, 여러 변경 중 일부만 확정되면 “예약 취소”라는 업무는 반쪽짜리가 된다. 그래서 트랜잭션 경계는 `findById()` 한 줄이나 `UPDATE` 한 줄이 아니라, 그 둘을 조율하는 `cancel()` 전체에 두는 것이 자연스럽다.

Spring 공식 문서는 `@Transactional` 메서드 호출에서 트랜잭션이 진입 시 시작되고, 반환 시 commit 또는 rollback되는 경로를 다음처럼 그린다.

![Spring 선언적 트랜잭션 호출 흐름도. Caller가 대상 객체가 아니라 AOP Proxy를 호출하고, 호출은 Transaction Advisor와 Custom Advisor(s)를 거쳐 Target Method에 도달한다. Transaction Advisor에서 들어갈 때 트랜잭션이 생성되고 나올 때 commit 또는 rollback되며, 비즈니스 로직 실행 뒤 제어는 인터셉터 체인을 거꾸로 거쳐 Caller에게 결과를 돌려준다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day15-web-declarative-transaction-proxy.png)

*출처: [Understanding the Spring Framework's Declarative Transaction Implementation — Spring Framework Reference](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html) — Copyright © 2005 - Broadcom. All Rights Reserved. (문서 사본은 무료 배포와 저작권 고지 유지 조건으로 허용)*

이 판단은 ACID의 원자성(Atomicity)과 연결된다. 원자성은 작업을 더 이상 쪼갤 수 없다는 문법 이야기가 아니라, 외부에서 관찰할 때 **전체 성공 또는 전체 실패만 허용한다**는 약속이다. 이번 코드에서 그 단위는 “예약 조회”가 아니라 “예약 취소”다.

`@Transactional` 하나가 ACID 전체를 자동으로 해결한다는 뜻은 아니다. 어떤 격리 수준을 쓸지, DB 제약으로 어떤 일관성을 지킬지, 여러 트랜잭션의 충돌을 어떻게 다룰지는 별도 판단이 필요하다.

> **보장 범위** — 오늘 실험으로 확인한 범위는 Service 경계의 원자성과 commit·rollback이다. 격리 수준, DB 제약을 통한 일관성, 동시 트랜잭션 충돌 처리는 이번 범위 밖이다.

### 2) 정상 반환 경로의 commit 과정

> **commit** = 트랜잭션에서 수행한 변경을 최종 확정하는 동작

우리 코드에서는 상태를 바꾼 뒤에도 `save()`를 다시 호출하지 않는다. 조회된 Entity가 이미 영속 상태이기 때문이다.

```java
Reservation reservation = reservationRepository.findById(id)
        .orElseThrow(() -> new ReservationNotFoundException(id));

reservation.cancel(cancelReason);
// reservationRepository.save(reservation); //3. DB에 반영 -> @Transactional 사용시 안써도 됨.
// 조회된 엔티티가 영속 상태라 변경 감지를 처리한다.
```

`cancel()`에 진입하면 트랜잭션과 영속성 컨텍스트가 해당 호출 범위에서 유지된다. `findById()`가 반환한 `Reservation`은 단순한 값 복사본이 아니라 Hibernate가 관리하는 영속 Entity다.

```text
트랜잭션 시작
→ findById()가 Reservation 조회
→ Hibernate가 조회 당시 상태의 스냅샷 보관
→ reservation.cancel()이 메모리 객체 변경
→ cancel() 정상 반환
→ flush 시 현재 상태와 스냅샷 비교
→ 차이가 있으므로 UPDATE 전송
→ commit으로 변경 확정
```

따라서 이미 관리 중인 Entity에는 `repository.save(reservation)`를 다시 호출할 필요가 없다. 상태를 바꾼 메서드는 Java 객체만 수정하지만, 트랜잭션 종료 시점의 변경 감지가 그 차이를 SQL로 바꾼다.

여기서 `@Transactional`이 변경된 객체를 영속성 컨텍스트에 새로 넣는다고 이해하면 순서가 뒤집힌다. Entity가 관리 대상이 되는 직접적인 계기는 트랜잭션 안의 조회이고, 애노테이션은 그 관리 범위가 Service 메서드 전체에 유지되도록 트랜잭션 경계를 만든다.

> **보장 범위** — `cancelCommitsChangedState()` 테스트로 `save()` 없이도 `UPDATE`가 발생하고 재조회 값이 유지됨을 확인했다. Hibernate 내부의 스냅샷 비교 알고리즘 자체는 이번에 검증한 대상이 아니다.

### 3) flush와 commit의 역할 구분

> **flush** = 영속성 컨텍스트의 변경 내용을 SQL로 DB에 전송하는 동작

우리 코드에서는 `RollbackScenarioService.cancelThenFail()`이 예외 전에 flush를 강제로 호출해 이 구분을 드러낸다.

```java
reservation.cancel("강제 실패");
entityManager.flush(); // UPDATE를 DB에 전송하지만 커밋은 x

throw new RuntimeException("취소 처리 중 실패");
```

`flush()`는 영속성 컨텍스트와 DB 사이의 동기화다. 변경 감지 결과인 `UPDATE`를 DB로 보내지만, 트랜잭션을 끝내거나 변경을 최종 확정하지 않는다.

반면 `commit`은 트랜잭션의 성공을 확정한다. 보통 commit 직전에 flush가 일어나므로 둘이 붙어 보이지만, 개념과 시점은 다르다.

```text
flush 성공 + commit 성공  → UPDATE가 최종 상태로 남음
flush 성공 + rollback     → UPDATE가 실행됐어도 최종 상태는 원래대로 돌아감
flush 전 rollback         → 변경 SQL 자체가 전송되지 않을 수 있음
```

> **보장 범위** — SQL 로그에 `UPDATE`가 보였다는 사실만으로 commit됐다고 결론 내릴 수 없다. 로그는 DB에 SQL이 전달됐음을 보여주지만, 그 트랜잭션이 나중에 확정됐는지 취소됐는지까지 한 줄의 `UPDATE`가 말해주지는 않는다.

### 4) RuntimeException 발생 경로의 rollback 과정

> **rollback** = commit되지 않은 트랜잭션 변경을 취소하는 동작

우리 테스트에서는 rollback 이후 원래 상태로 돌아왔는지를 재조회로 직접 검증한다.

```java
assertThrows(
        RuntimeException.class, () -> rollbackScenarioService.cancelThenFail(saved.getId())
);

Reservation found = reservationRepository
        .findById(saved.getId())
        .orElseThrow();

assertTrue(found.isConfirmed());
assertNull(found.getCancelReason());
```

```text
관리 중인 Entity 상태 변경
→ (실험에 따라) flush로 UPDATE 전송 또는 미전송
→ RuntimeException이 트랜잭션 경계 밖으로 전달
→ rollback
→ 재조회 시 confirmed=true, cancelReason=null (원래 상태)
```

테스트에서는 관리 중인 Entity를 변경한 뒤 `RuntimeException`을 트랜잭션 경계 밖으로 전달했다. Spring의 기본 rollback 규칙에서는 이 예외로 정상 반환 경로가 중단되고 commit 대신 rollback이 선택된다.

처음 실험에서는 `cancel()` 직후 바로 예외를 던졌다. 자동 flush 전에 실패했기 때문에 `UPDATE`가 출력되지 않았고, 영속성 컨텍스트의 메모리 변경만 버려졌다.

두 번째 실험에서는 예외 전에 `entityManager.flush()`를 명시했다. 이번에는 `UPDATE`가 분명히 실행됐지만 아직 commit 전이었고, 이어진 예외로 rollback됐다. 새 트랜잭션에서 다시 조회하자 `confirmed=true`, `cancelReason=null`이었다.

![시퀀스 다이어그램. 참여자는 테스트, @Transactional Service, 영속성 컨텍스트, H2이다. alt 프레임이 두 갈래로 갈린다. 정상 반환 갈래에서는 테스트가 cancel(id, reason)을 호출하고, Service가 findById(id)로 영속성 컨텍스트를 거쳐 H2에 SELECT를 보내 행 1건과 스냅샷을 얻는다. Service가 reservation.cancel(reason)으로 메모리 객체를 바꾼 뒤 정상 반환하면 flush의 변경 감지로 UPDATE가 전송되고 COMMIT된다. 새 트랜잭션의 재조회 SELECT는 confirmed=false와 "일정 변경"을 돌려준다. RuntimeException 갈래에서는 cancelThenFail(id)가 같은 조회와 cancel("강제 실패") 뒤 entityManager.flush()로 UPDATE를 보낸다. 이어서 RuntimeException을 던지자 ROLLBACK되고 예외가 테스트로 전달된다. 재조회 SELECT는 confirmed=true와 null을 돌려준다. 하단 주석은 두 경로 모두 UPDATE가 전송되었고 최종 상태를 가르는 것은 COMMIT과 ROLLBACK이라고 설명한다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day15-transaction-boundary.png)

> **보장 범위** — 오늘 사용한 기본 규칙은 모든 Java 예외에 똑같이 적용되는 규칙이 아니다. 별도 설정이 없다면 `RuntimeException`과 `Error`는 rollback 대상이지만 checked exception은 기본적으로 그렇지 않다. 이 글에서는 실제로 실행한 `RuntimeException` 경로까지만 검증했다.

### 5) `readOnly=true`의 역할과 보장 범위

> **Read-Only Transaction** = 조회 전용 의도를 전달해 최적화에 활용될 수 있는 트랜잭션 힌트

이번 커밋의 코드에는 `readOnly=true`가 적용된 메서드가 없다. 조회 전용 Service에 적용할 수 있는 옵션으로만 검토했다.

```text
@Transactional(readOnly = true) 선언
→ Spring/JPA 구현체가 조회 전용 의도로 인식
→ flush 방식·변경 감지 비용 최적화에 활용 가능
→ DB 쓰기 권한 자체는 그대로 유지됨
```

조회 Service에는 `@Transactional(readOnly = true)`로 “이 작업은 조회 전용”이라는 의도를 표현할 수 있다. Spring과 JPA 구현체는 이 정보를 flush 방식과 변경 감지 비용을 조정하는 최적화 힌트로 활용할 수 있다.

하지만 `readOnly=true`는 사용자의 DB 쓰기 권한을 제거하지 않는다. Java 문법상 `save()` 호출을 막지도 않고, 모든 DB와 드라이버 조합에서 `INSERT`·`UPDATE`가 반드시 실패한다고 보장하지도 않는다.

> **보장 범위** — 이 옵션은 보안 경계가 아니다. 쓰기를 확실히 막아야 한다면 DB 사용자 권한, 애플리케이션 구조, 테스트 같은 별도 장치가 필요하다. `readOnly`의 역할은 조회 의도를 드러내고 가능한 최적화를 돕는 데 있으며, 오늘은 실제 쓰기 차단 여부를 실행하지 않았다.

### 6) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| Transaction Boundary | 여러 DB 작업을 전부 성공시키거나 전부 취소시키는 하나의 단위로 묶은 범위 |
| Persistence Context | 조회된 Entity를 관리 상태로 유지하며 스냅샷과 비교하는 영역 |
| Dirty Checking | 관리 중인 Entity의 현재 상태와 스냅샷을 비교해 변경분을 찾아내는 동작 |
| flush | 영속성 컨텍스트의 변경 내용을 SQL로 DB에 전송하는 동작 |
| commit | 트랜잭션에서 수행한 변경을 최종 확정하는 동작 |
| rollback | commit되지 않은 트랜잭션 변경을 취소하는 동작 |
| Read-Only Transaction | 조회 전용 의도를 전달해 최적화에 활용될 수 있는 트랜잭션 힌트 |

> **더 볼 것**
> - [Spring 선언적 트랜잭션 구현](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html): AOP 프록시와 `TransactionInterceptor`의 트랜잭션 구동 구조
> - [Spring 선언적 트랜잭션 롤백](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html): `RuntimeException`·`Error`의 기본 rollback 규칙과 checked exception의 차이
> - Spring AOP 프록시 — 다음 Day에서 애노테이션만으로 메서드 전후에 트랜잭션 처리가 붙는 이유 확인
> - self-invocation — 같은 객체 내부 호출이 트랜잭션 경계를 통과하지 못하는 조건
> - 트랜잭션 전파 — 이미 트랜잭션이 있을 때 내부 Service가 같은 트랜잭션에 참여할지 새로 만들지 결정하는 규칙

## 2. 코드 구현

### 1) `cancel()` — 업무 흐름 전체의 Transaction Boundary

```java
@Transactional
public Reservation cancel(Long id, String cancelReason) {
    Reservation reservation = reservationRepository.findById(id)
            .orElseThrow(() -> new ReservationNotFoundException(id));

    reservation.cancel(cancelReason);

    return reservation;
}
```

**한 줄씩 보기**
- `@Transactional`: 메서드 진입 시 트랜잭션이 시작되고, 정상 반환 시 commit, 예외 시 rollback으로 이어진다.
- `findById(id).orElseThrow(...)`: 예약을 조회하고, 없으면 즉시 취소 처리를 중단한다.
- `reservation.cancel(cancelReason)`: 조회로 얻은 영속 Entity를 메모리에서 변경한다. 이 변경이 Dirty Checking의 대상이 된다.
- `return reservation`: 메서드가 정상 반환되면 flush 시점에 변경 감지가 일어나 `UPDATE`가 전송되고, 이어서 commit된다.

기존 코드의 `reservationRepository.save(reservation)`는 제거했다. 조회와 상태 변경이 같은 트랜잭션 안에 있으므로 `reservation`은 메서드가 끝날 때까지 관리 상태이고, 변경 감지가 UPDATE를 만든다.

애노테이션을 Repository에 더 붙이는 대신 Service에 둔 이유는 경계의 의미 때문이다. Repository는 저장과 조회라는 데이터 접근을 수행하지만, “예약을 취소한다”는 작업의 시작과 끝을 아는 곳은 Service다.

### 2) 강제 flush 이후의 rollback 대조 실험

```java
@Transactional
public void cancelThenFail(Long id) {
    Reservation reservation = repository.findById(id).orElseThrow();

    reservation.cancel("강제 실패");
    entityManager.flush();

    throw new RuntimeException("취소 처리 중 실패");
}
```

이 메서드는 운영 기능이 아니라 테스트에만 등록한 `RollbackScenarioService` 안에 있다. 운영 Service에 실험용 예외를 섞지 않으면서, 실제 Spring Bean의 트랜잭션 경계와 실제 H2 DB를 사용하기 위해 분리했다.

테스트 클래스 자체에는 `@Transactional`을 붙이지 않았다. 서비스 호출이 끝난 뒤 별도의 Repository 조회로 DB의 최종 상태를 확인해야 했기 때문이다. 테스트 전체가 하나의 바깥 트랜잭션이면 서비스 메서드의 commit·rollback 경계를 독립적으로 관찰하기 어려워진다.

실패 메서드를 호출할 때는 `assertThrows`로 예외 발생을 검증했다. 그다음 새 조회 결과에 `assertTrue(found.isConfirmed())`와 `assertNull(found.getCancelReason())`를 적용해 rollback의 결과를 확인했다. “예외가 났다”만으로 rollback을 증명하지 않고, DB 최종 상태까지 검사한 것이다.

### 3) 자동 검증 결과

| 확인 항목 | 자동 검증 | 결과 |
|---|---|---|
| 정상 종료 시 변경 감지와 commit | `cancelCommitsChangedState()` | `UPDATE` 출력, 재조회 `confirmed=false`, 취소 사유 유지 |
| 강제 flush 뒤 예외 시 rollback | `runtimeExceptionRollsBackChangedState()` | `UPDATE` 출력, 재조회 `confirmed=true`, 취소 사유 `null` |
| 전체 회귀 | `./gradlew test --rerun-tasks` | 18개 테스트, failures 0, errors 0 |

`readOnly=true`가 실제 DB 쓰기를 차단하는지는 실행하지 않았다. 오늘 범위에서는 의미와 보장 경계만 다뤘으며, 쓰기 차단 테스트 결과가 있는 것처럼 주장하지 않는다.

## 3. 스스로 답한 질문

### 1) 롤백 테스트의 UPDATE 미출력 원인

**질문.** 롤백 테스트에서 처음에는 왜 `UPDATE`가 출력되지 않았는가?

**A1.** 처음에는 “기존에 새로 생긴 컬럼이 다시 반영되지 않았기 때문”이라고 답했다. 하지만 `cancel_reason` 컬럼은 이미 Flyway V3로 만들어져 있었고, 컬럼 존재 여부는 원인이 아니었다.

첫 실험은 `reservation.cancel()` 다음 줄에서 바로 `RuntimeException`을 던졌다. 객체는 메모리에서 바뀌었지만 자동 flush 시점인 정상 종료까지 도달하지 못했다. 그래서 DB로 보낼 `UPDATE`가 만들어지기 전에 rollback됐고, SQL 로그에도 UPDATE가 없었다.

원인을 다시 판단할 때는 스키마보다 실행 순서를 먼저 본다. 상태 변경, flush, 예외, commit 중 어디까지 도달했는지를 확인하면 같은 혼동을 줄일 수 있다.

### 2) 명시적 flush 이후의 UPDATE와 rollback

**질문.** 직접 flush한 뒤에도 `UPDATE`가 출력되지 않는가?

**A2.** `entityManager.flush()`를 추가한 실험에서도 처음에는 UPDATE가 나오지 않는다고 예측했다. rollback을 “SQL을 실행하지 않는 것”으로 생각했기 때문이다.

실제 결과는 `INSERT → SELECT → UPDATE → rollback → SELECT`였다. flush는 UPDATE를 보내는 데 성공했고, rollback은 그 뒤에 commit되지 않은 변경을 취소했다. 따라서 SQL 실행 여부와 트랜잭션 최종 확정 여부를 분리해야 한다.

이후에는 SQL 로그에서 UPDATE를 발견하면 “반영됐다”라고 바로 말하지 않고, 트랜잭션 종료 방식과 새 조회 결과를 함께 확인한다.

### 3) `readOnly=true`와 DB 쓰기 권한의 구분

**질문.** `readOnly=true`가 적용되면 조회 권한만 남는가?

**A3.** 처음에는 “readOnly가 붙으면 조회 권한만 있기 때문에 INSERT나 UPDATE는 실행될 수 없다”고 답했다. 애노테이션의 `readOnly`를 DB 권한과 같은 강제 규칙으로 이해한 것이다.

교정된 기준은 다르다. `readOnly=true`는 쓰기 권한 제어가 아니라 조회 전용 의도를 전달하는 힌트다. 최적화에 쓰일 수 있지만, 모든 쓰기를 절대 차단한다고 단정할 수 없다.

권한, 힌트, 검증은 서로 다른 장치다. 이름이 강하게 들리더라도 문법상 호출 가능 여부, 프레임워크의 최적화, DB의 실제 권한을 나눠 확인해야 한다.

## 4. 학습 정리와 다음 범위

### 1) 전체 흐름 다시 보기

오늘 관찰한 `cancel()` 호출은 정상 반환과 예외 발생에서 서로 다른 DB 결과로 이어진다. 두 경로를 나란히 정리하면 다음과 같다.

```text
[정상 반환]
ReservationController → ReservationService.cancel() 진입
→ @Transactional 시작
→ findById()로 Reservation 조회 (영속 상태)
→ reservation.cancel()로 상태 변경 (Dirty Checking 대상)
→ cancel() 정상 반환
→ flush: 스냅샷과 비교해 UPDATE 전송
→ commit: 변경 확정

[예외]
ReservationController → ReservationService.cancel() 진입
→ @Transactional 시작
→ findById()로 Reservation 조회
→ 상태 변경 (필요 시 명시적 flush로 UPDATE 선전송)
→ RuntimeException 발생, 트랜잭션 경계 밖으로 전달
→ rollback: commit되지 않은 변경을 취소
```

### 2) 이해의 변화와 남은 것

이번 실험 전에는 `@Transactional`을 “예외가 나면 알아서 되돌리는 애노테이션” 정도로 볼 수 있었다. 이제는 Service 메서드 전체를 업무 단위로 정하고, 그 안에서 영속성 컨텍스트가 Entity를 관리하며, 정상 종료와 예외가 각각 commit과 rollback 경로를 선택한다고 순서대로 설명할 수 있다.

가장 중요한 교정은 `UPDATE 출력 = DB 반영 완료`가 아니라는 점이다. `flush()`는 SQL을 전송하지만 트랜잭션을 확정하지 않는다. 강제 flush 뒤 rollback 테스트가 통과하면서, 로그에 나타난 SQL과 DB의 최종 상태를 따로 봐야 한다는 기준이 생겼다.

**아직 남은 것**은 `@Transactional` 메서드 앞뒤의 처리가 어떻게 자동으로 붙는지다. 오늘은 Spring이 만든 Bean을 통해 외부에서 호출했을 때의 결과만 확인했다. 같은 객체 내부에서 메서드를 호출하는 self-invocation은 경계를 우회할 수 있는데, 이 문제는 **나중에 고칠 것**이 아니라 바로 다음 Week C D2에서 Spring AOP 프록시 실험으로 확인한다.

면접에서 다시 답해볼 질문을 남긴다.

- SQL 로그에 UPDATE가 보였는데도 데이터가 원래 상태일 수 있는 실행 순서를 설명할 수 있는가
- 트랜잭션 경계를 Repository 메서드가 아니라 Service의 비즈니스 메서드에 두는 이유는 무엇인가

---

오늘 공부한 소스코드: `app/src/main/java/com/example/studyroom/service/ReservationService.java`, `app/src/test/java/com/example/studyroom/service/ReservationServiceTransactionTest.java`
