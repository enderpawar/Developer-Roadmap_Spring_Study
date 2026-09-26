# [Spring Study Day 20 & Day 21] 3주차 마무리 시험

Week C는 `@Transactional`을 "설명 없이 주어진 래퍼"로만 쓰던 Week B의 블랙박스를 여는 주였다. D1~D3에서 트랜잭션 경계·Spring AOP 프록시·전파(`REQUIRED`/`REQUIRES_NEW`)를 열어봤고, D4~D5에서 Hibernate LAZY 프록시와 N+1을 확인했다. D5(Day19)까지 Full 루프가 완성예제까지만 끝난 채 D7로 넘어갔던 이월분(`Member.reservations` 빈칸 예제, `NPlusOneTest`의 println 검증)은 D6 시험 전에 먼저 처리했다. 이 글은 남은 D6 누적시험과 D7 버퍼를 묶어, 3주차 전체를 인출하고 코드에 반영한 기록이다.

> D6에서는 Week A~C 전체를 10문항으로 인출했다. 6개는 힌트 없이 통과했지만, self-invocation은 세 번째 도전에서도 근거가 무너져 다시 오답으로 기록했고, Spring AOP 프록시와 Hibernate LAZY 프록시를 "둘 다 프록시니까 같다"고 답했으며, fetch join이 매핑 자체를 바꾼다고 오해했다. D7에서는 Day19에서 이월된 기술부채(inner join fetch가 member 없는 예약을 빼는 문제)를 `left join fetch`로 해소하고, 조회 전용 목록 API(`GET /reservations`)를 `@Transactional(readOnly = true)`로 추가했다. 최종 테스트 30개 통과.

## 1. 시험 범위와 진행 방식

### 1) 이월분 선처리와 출제 범위

D6 시험 전에 두 가지 이월분을 먼저 닫았다(9/27, 커밋 [7a9626d](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/7a9626dcf62c0df95a8459fa6cb9472acbf0cbce)). `Member.reservations`에 `@OneToMany(mappedBy = "member", fetch = FetchType.LAZY)` 빈칸을 채우고, 그 컬렉션의 런타임 타입이 프록시가 아니라 `PersistentBag`이라는 걸 새 테스트로 확인했다. 그리고 `NPlusOneTest`의 `println` 관찰을 Hibernate `Statistics.getPrepareStatementCount()` 단언으로 바꿔, 사람이 로그를 세지 않아도 회귀를 잡을 수 있게 했다.

D6(9/28) 출제 범위는 다음과 같다.

- Week A: DTO/Domain 분리, 생성자 주입
- Week B: JDBC 대비 JPA의 추상화 대상, 영속성 컨텍스트·1차 캐시, 변경 감지(dirty checking) vs Flyway 체크섬
- Week C D1~D5: 트랜잭션 경계, self-invocation, 전파(`REQUIRED`/`REQUIRES_NEW`), LAZY 프록시, N+1과 fetch join

노트를 덮고 먼저 답하고, 틀리거나 막히면 힌트를 받은 뒤 다시 답하는 방식은 Day13(Week B D6)과 같다. 다만 이번엔 "결론은 맞지만 근거가 무너지는" 경우를 통과가 아니라 **오답**으로 기록했다 — 결과만 외운 답이 다음 재시험에서도 다시 무너지는 걸 이미 세 번 봤기 때문이다.

### 2) 시험 결과

| 구분 | 문항 | 기록 |
|---|---|---|
| 힌트 없이 통과 | 1 DTO/Domain, 2 생성자 주입, 3 JDBC 대비 JPA, 4 1차 캐시, 5 dirty checking vs 체크섬, 7 REQUIRED/REQUIRES_NEW 판단 로직 | Week A·B는 여러 차례 재시험을 거쳐 안정됨 |
| 힌트 후 통과 | 8 `REQUIRES_NEW`가 위험한 이유 | "커넥션 2개 동시 사용" 힌트 후 병목까지 도달 |
| 오답 후 교정 | 6 self-invocation, 9 AOP 프록시 vs Hibernate 프록시, 10 fetch join의 적용 범위 | 전부 Week C |

10문항 중 흔들린 건 전부 Week C였다 — 로드맵이 이 주를 "벽"으로 지정한 것과 정확히 일치한다. 아래에서는 6·9·10번을 다룬다.

## 2. 시험에서 틀린 문제

### 1) 문항 6 — self-invocation의 근거

> **Self-invocation** = target 객체 내부의 `this.메서드()` 호출이 프록시를 다시 통과하지 않아, 그 메서드의 `@Transactional`이 적용되지 않는 현상

**질문.** `@Transactional`이 애노테이션만으로 어떻게 동작하며, self-invocation에서는 왜 적용되지 않는가?

**최초 답변.** "프록시가 메서드 호출을 가로채서 트랜잭션을 시작·종료하고, `this.inner()`로 부르면 프록시를 다시 안 거쳐서 적용이 안 됨." 근거를 묻자 "그냥 프록시가 아니니까 적용이 안 되는 거 아니에요?"로 되돌아갔다.

**왜 틀렸나.** 결론(적용 안 됨)은 Day17부터 세 번째로 정확히 말했다. 문제는 매번 "왜 다시 안 거치는가"를 물으면 결론을 다른 말로 반복하는 순환논리에 빠진다는 것이다. 결과를 외운 것과 근거를 아는 것은 다른 능력이다.

아래 그림은 Spring 공식 문서가 그리는 AOP 프록시 호출 구조다. 호출 코드(Calling code)는 원본 객체가 아니라 프록시를 거쳐야만 프록시 안의 부가 기능(트랜잭션 등)을 통과한다.

![호출 코드(Calling code)가 pojo.foo()를 호출하면 요청이 먼저 Proxy 안으로 들어가 프록시의 foo()가 실행되고, 그다음 Proxy가 감싼 대상 객체의 foo()가 실행된 뒤 결과가 호출 코드로 돌아가는 구조](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day16-web-aop-proxy-call.png)

*출처: [Proxying Mechanisms — Understanding AOP Proxies, Spring Framework Reference](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html#aop-understanding-aop-proxies) — Copyright © 2005 - Broadcom. All Rights Reserved.*

**교정 기준.** `this`는 자바 언어 수준에서 항상 **호출을 시작한 객체 자기 자신**을 가리킨다. `target.inner()`를 외부에서 부를 때만 그 호출이 위 그림의 "Calling code" 화살표처럼 프록시 인스턴스에서 시작된다. target 내부의 `this.inner()`는 애초에 프록시에서 시작된 호출이 아니므로, "프록시를 우회한다"보다 "그 호출 경로 자체에 프록시가 없다"가 더 정확하다. 결과와 근거를 분리해서 인출하는 연습이 필요하다는 게 이번 시험의 결론이라, 복습큐에도 통과가 아니라 오답으로 다시 올렸다.

### 2) 문항 9 — Spring AOP 프록시와 Hibernate LAZY 프록시의 구분

> **Spring AOP 프록시** = Bean의 메서드 호출을 가로채 부가 기능을 적용하는 대리 객체. **Hibernate 프록시** = Entity의 필드 접근을 가로채 SELECT를 지연시키는 별개의 장치

**질문.** 두 프록시는 같은 원리로 동작하는가?

**최초 답변.** "둘 다 프록시니까 결국 같은 원리 아니에요? 어차피 뭔가를 가로채는 거잖아요."

**왜 틀렸나.** "가로챈다"는 공통점만 보고, 가로채는 **대상**(메서드 호출 vs 필드 접근)과 **목적**(부가 기능 삽입 vs SELECT 지연)이 다르다는 걸 놓쳤다. `ReservationService$$SpringCGLIB$$0`(Day16)과 `Member$HibernateProxy`(Day18)를 나란히 놓고 "어느 쪽이 SQL을 지연시키느냐"는 질문을 받은 뒤에야 구분해서 재답변했다.

| 구분 | Spring AOP 프록시 | Hibernate LAZY 프록시 |
|---|---|---|
| 가로채는 대상 | Bean의 메서드 호출 | Entity의 필드 접근 |
| 목적 | 트랜잭션 등 부가 기능 삽입 | SELECT 지연(Lazy Loading) |
| 확인한 클래스명 | `ReservationService$$SpringCGLIB$$0`(Day16) | `Member$HibernateProxy`(Day18) |

**교정 기준.** "프록시"라는 같은 이름이 오히려 학습을 방해했다. Day18 velog_post에서 이미 표로 정리해뒀던 내용인데, 시험이라는 다른 맥락에서 다시 물으니 처음부터 다시 헷갈렸다 — 정리해둔 지식과 인출 가능한 지식은 다르다.

### 3) 문항 10 — Fetch Join의 적용 범위

**질문.** fetch join으로 한 번 확인했으면, 이제 `findAll()`도 자동으로 JOIN으로 나가는가?

**최초 답변.** "fetch join으로 확인했으니까 이제 findAll()도 자동으로 JOIN으로 나가는 거 아니에요?"

**왜 틀렸나.** fetch join은 **그 쿼리 메서드 하나에만** 적용되는 쿼리 수준 지시이지, `Reservation.member`의 매핑(`FetchType.LAZY`) 자체를 바꾸지 않는다. `NPlusOneTest`가 `findAll()`과 `findAllWithMember()`를 별도 메서드로 검증하고 있다는 사실이 그 자체로 반례다 — 매핑이 바뀌었다면 `findAll()`도 N+1을 내지 않아야 하는데, 실제로는 여전히 4번을 낸다.

**교정 기준.** 매핑은 "기본값"이고, fetch join은 "이 쿼리 한 개에만 적용하는 예외"다. 이 기준은 곧바로 D7의 설계 판단에 쓰였다(3절 참고).

## 3. D7 코드 적용

Day19에서 이월된 기술부채는 정확히 문항 10에서 교정한 기준이 필요한 자리였다. `findAllWithMember()`(inner join fetch)는 `member_id`가 `null`인 예약을 결과에서 빼는데, 지금 `POST /reservations`로 만드는 예약은 전부 member가 없다. 이 메서드를 그대로 서비스 목록 조회에 쓰면 방금 만든 예약이 눈에 보이게 사라진다.

먼저 실제로 유실되는지 대조 테스트로 확인했다.

```java
List<Reservation> result = reservationRepository.findAllWithMember();
assertEquals(1, result.size()); // B-202(member 없음)가 결과에서 사라졌다
```

교정 기준(문항 10) 그대로, 기존 `findAllWithMember()`를 고치지 않고 `left join fetch` 쿼리를 **새 메서드**로 추가했다.

```java
// join fetch(inner join)는 member_id가 null인 예약을 결과에서 빼버린다.
// left join fetch로 바꾸면 member가 없어도(오른쪽이 없어도) 왼쪽(reservation)은 그대로 남는다.
@Query("select r from Reservation r left join fetch r.member")
List<Reservation> findAllWithMemberOrNull();
```

`findAllWithMember()`(inner join)는 그대로 뒀다 — `NPlusOneTest`가 "inner join으로도 N+1이 없다"는 것 자체를 검증하는 대조군이라, 쿼리의 의미를 바꾸면 그 테스트의 의도가 달라진다. 아래 그림은 같은 데이터(A-101에는 member 있음, B-202에는 없음)로 두 쿼리를 실행했을 때 결과가 갈리는 지점을 직접 그린 것이다.

![시퀀스 다이어그램. A-101(member 있음)과 B-202(member 없음)를 저장한 뒤 findAllWithMember()(inner join fetch)를 호출하면 DB가 1행(A-101)만 돌려주고 B-202는 결과에서 제외된다. findAllWithMemberOrNull()(left join fetch)을 호출하면 DB가 2행을 돌려주고, B-202는 결과에 남되 getMember()가 null이다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day21-inner-vs-left-fetch.png)

SQL 의미론으로 보면 inner join은 두 집합의 교집합만 남기고, left join은 왼쪽 집합 전체를 남기고 오른쪽에 짝이 없으면 `NULL`로 채운다.

![원 A 전체와 교집합이 칠해진 벤 다이어그램. A Left Join B는 B에 짝이 없는 A의 행도 결과에 남긴다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day19-web-left-join.png)

*출처: [File:SQL Join - 01 A Left Join B.svg](https://commons.wikimedia.org/wiki/File:SQL_Join_-_01_A_Left_Join_B.svg) — GermanX, Wikimedia Commons, CC BY-SA 4.0*

이 쿼리를 조회 전용 서비스 메서드에 연결했다.

```java
@Transactional(readOnly = true) // 조회 전용 — 변경 감지·flush를 위한 스냅샷 비교를 생략해 가볍다
public List<ReservationSummary> findAllSummaries() {
    return reservationRepository.findAllWithMemberOrNull().stream()
            .map(r -> new ReservationSummary(
                    r.getRoomName(),
                    r.getRequesterName(),
                    r.getMember() == null ? null : r.getMember().getName()))
            .toList();
}
```

`readOnly = true`는 Week C D1에서 "주어진 래퍼"로만 썼던 `@Transactional`에, "이 메서드는 값을 하나도 바꾸지 않으니 변경 감지용 스냅샷 비교가 필요 없다"는 판단을 처음으로 직접 붙인 지점이다. `Entity`를 그대로 반환하지 않고 `ReservationSummary` record로 감싼 이유도 같은 결이다 — Jackson이 직렬화하는 시점(컨트롤러 밖)에 LAZY 필드에 접근하면 트랜잭션이 이미 끝나 있을 수 있어서, 서비스 안에서 필요한 값만 미리 꺼내 담았다.

`GET /reservations`를 추가하고 MockMvc로 확인했다.

```java
mockMvc.perform(get("/reservations"))
        .andExpect(jsonPath("$[0].memberName").value(nullValue()));
```

## 4. 자동 검증 범위

| 확인한 것 | 방법 | 결과 |
|---|---|---|
| inner join fetch가 member 없는 예약을 뺀다 | 자동 — `FetchJoinInnerVsLeftTest` | 1건 반환, B-202 제외 |
| left join fetch로 바꾸면 유지된다 | 자동 — `FetchJoinInnerVsLeftTest` | 2건 반환, `getMember()` == `null` |
| `GET /reservations`가 member 없는 예약도 `memberName: null`로 응답 | 자동 — MockMvc | 통과 |

전체 `./gradlew test --console=plain` 30개 통과. 코드는 커밋 [7a9626d](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/7a9626dcf62c0df95a8459fa6cb9472acbf0cbce)(이월분)와 [094c6b3](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/094c6b3c001d26604ff1dcacca1b107b0d22a300)(D7)에 있다.

**미검증 범위**를 구분해둔다. `GET /reservations`는 페이징이 없어 예약이 많아지면 전체를 한 번에 반환한다 — 이 5주 트랙 범위 밖으로 남긴다. `ReservationSummary`는 취소 여부(`confirmed`/`cancelReason`)를 담지 않는다 — 이번 독립 과제 범위를 "member 이름 포함 목록"으로 좁혔기 때문이다.

## 5. 3주차 정리와 다음 시작점

Week C를 시작할 때 `@Transactional`은 "붙이면 되는 애노테이션"이었다. D1~D5를 거치며 그 뒤에 프록시가 있고, 프록시가 메서드 호출을 가로채며, `this`를 통한 호출은 그 경로에 없다는 걸 로그와 클래스 이름으로 직접 봤다. 이번 시험에서 가장 크게 확인한 건 "본 적 있다"와 "근거를 댈 수 있다"의 차이다 — self-invocation은 세 번째로 결론을 맞혔지만, 근거를 대라는 질문에는 세 번 다 다른 방식으로 막혔다.

**아직 남은 것**은 두 가지다. self-invocation의 근거 수준 설명은 이번에도 오답으로 남아 복습큐에서 계속 추적한다 — **바로 고칠 것(다음 재시험, 9/29)**. `GET /reservations`의 페이징 부재는 이 트랙 범위 밖으로 **고치지 않을 것**으로 분류한다.

다음 시작점은 **Week D D1 — BCrypt 비밀번호 저장**이다. 지금까지는 "이미 있는 데이터를 어떻게 조회·갱신하는가"를 다뤘다면, Week D부터는 "누가 그 요청을 보냈는지"를 다룬다.

면접에서 다시 답해볼 항목을 남긴다.

- self-invocation이 일어나지 않게 하는 설계 대안(자기 자신을 프록시로 주입받기, 메서드 분리 등)과 각각의 트레이드오프
- `@Transactional(readOnly = true)`가 실제로 생략하는 것과, 그럼에도 여전히 트랜잭션이 필요한 이유

---

오늘 공부한 소스코드: `app/src/test/java/com/example/studyroom/repository/FetchJoinInnerVsLeftTest.java`, `app/src/main/java/com/example/studyroom/service/ReservationService.java`, `app/src/main/java/com/example/studyroom/controller/ReservationController.java`, `app/src/main/java/com/example/studyroom/dto/ReservationSummary.java`
