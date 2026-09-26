# Day18 (9/25, Week C D4) 인출 기록

## 1. 복습큐 도래분 (9/22 도래 3건, 「밀렸을 때 규칙」대로 오답재시험 우선 3건만 뽑음)

| 개념 | 결과 |
|---|---|
| self-invocation의 뜻 | ❌ 용어 자체를 기억 못해 위키 검색 → 오답재시험(9/26) |
| 바깥 메서드에 `@Transactional`을 두면 내부 self-invocation도 이미 활성 트랜잭션 안에서 실행되는 이유 | ❌ "몰라"로 답변 → 오답재시험(9/26) |
| `RuntimeException`이 트랜잭션 경계 밖으로 전달되면 rollback되는 이유 | ✅ 힌트 후 "프록시(`TransactionInterceptor`)가 RuntimeException 기준으로 판단" 도달 |

> 8/9 오답재시험 항목(Entity 기본 생성자)은 학습자 지적대로 이번 세션에서 제외했다 — 직전 세션에서 갱신 후 커밋되지 않아 상태가 꼬인 것으로 판단.

## 2. 세션 예측과 교정 — LAZY 프록시 실험

`MemberLazyProxyTest.memberFieldIsProxyBeforeAccess()`, `entityManager.clear()` 후 재조회.

| # | 질문 | 학습자 1차 답 | 판정 | 교정 |
|---|---|---|---|---|
| P1 | `clear()` 직후 `findById()` 시점에 member 컬럼(FK)을 읽는 SELECT가 나가는가 | "안 나가지, entityManager를 clear 했으니까" | ❌ | `clear()`는 SELECT를 막는 게 아니라 1차 캐시를 비워 **다음 조회가 DB로 다시 가게** 만드는 장치. 재답변 후 정정: "다시 나간다" → ✅ |
| P2 | `found.getMember().getClass()`의 출력 | "정확히 Member로 나올 것 — findById/orElseThrow 했으니까" | ❌ | `getMember()` 호출 자체는 초기화를 일으키지 않음. 재답변("다른 순간이니까 Member로 안 나올 수도") → ✅. 실제로는 `Member$HibernateProxy` |
| P3 | `getMember().getName()` 호출 시 member 테이블 SELECT가 나가는가 | "나간다" | ✅ | 즉답 정답 |

## 3. self-invocation 관련 오답 상세 (Day17 개념 재확인 실패)

`outer()`(무애노테이션) → `this.inner()`(`@Transactional`)가 `false`인 이유를 묻는 질문에서 세 번 틀렸다:

1. "Propagation으로 inner의 트랜잭션이 outer와 묶여버렸다" — self-invocation(프록시 우회) 문제를 propagation(합류/신규 트랜잭션 판단) 문제로 착각
2. "transactionalOuter에서 호출한 inner가 트랜잭션 범위를 합쳐버린거지" — 질문 대상(`outer()`)이 아닌 다른 메서드(`transactionalOuter()`) 얘기로 넘어감
3. "프록시 객체" — `this`가 가리키는 대상을 프록시로 착각(실제로는 항상 target 자기 자신)

세 번째 오답 이후 직접 설명으로 교정했고, 재출제는 하지 않아 아직 학습자 스스로 재인출은 못 했다 — 9/26 재시험 대상.

## 4. 다음 복습 질문

1. self-invocation이 정확히 무엇이고, `this`가 왜 프록시를 가리킬 수 없는지
2. `outer()`와 `transactionalOuter()`의 결과가 다른 이유 (트랜잭션이 "언제" 열리는가 기준)
3. `@ManyToOne` 기본 fetch 전략(EAGER)과 `@OneToMany` 기본값의 차이
4. `entityManager.clear()`가 SELECT를 늘리는 방향으로 작용하는 이유

## 5. 복습 일정

오늘 새로 등록한 연관관계·LAZY 프록시 3항목은 +2일 9/27에 인출한다. self-invocation 계열 2항목은 오답재시험으로 9/26 우선 재시험. 상세는 `복습큐.md` 참고.

## 6. 이월분 완료 인출 (9/27)

미뤄뒀던 빈칸 예제(`Member.reservations`의 `mappedBy` 컬렉션) 4개를 채웠다.

| 빈칸 | 학습자 답 | 판정 |
|---|---|---|
| 애노테이션 이름 | `@OneToMany` | ✅ |
| `mappedBy` 값 | `"member"` | ✅ |
| `fetch` 값 | `FetchType.LAZY` | ✅ |
| 컬렉션 구현체 | `ArrayList` | ✅ |

4개 모두 오타 없이 한 번에 맞았다 — 위 5절 상단에 이미 이 빈칸이 제시되고 정답이 확정돼 있었던 내용이라, 인출이라기보다 지연 제출에 가깝다.

이어서 독립 변형으로 컬렉션 방향(`Member.reservations`)의 LAZY 동작을 예측했다.

| # | 질문 | 학습자 답 | 판정 |
|---|---|---|---|
| Q1 | `findById()` 직후 `found.getReservations()`의 런타임 타입은 필드 선언 그대로 `ArrayList`인가 | "아니다, Hibernate가 감싼 다른 래퍼일 것" | ✅ (`PersistentBag`으로 확인) |
| Q2 | `.size()` 호출 전/후 `Hibernate.isInitialized()` 값 | "호출 전 false, 후 true" | ✅ |

두 예측 모두 맞았지만, Q1의 답을 "프록시일 것"이 아니라 "다른 종류의 래퍼일 것"으로 신중하게 답했다는 점이 눈에 띈다. Day18 본문에서 단일 참조는 `Entity$HibernateProxy`였는데, 실제로 컬렉션은 프록시가 아니라 `PersistentBag`이라는 별도 이름의 래퍼였다 — 이름까지 미리 맞히지는 못했지만 "단일 참조와 같은 장치는 아닐 것"이라는 방향은 맞혔다.
