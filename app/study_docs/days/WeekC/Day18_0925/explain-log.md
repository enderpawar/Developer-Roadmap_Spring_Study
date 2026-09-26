# Day18 (9/25, Week C D4) 예측→실행→차이 기록

주제: 연관관계 매핑(`@ManyToOne`) + Hibernate LAZY 프록시

## 실험 — LAZY 프록시 초기화 시점

- 코드: `MemberLazyProxyTest.memberFieldIsProxyBeforeAccess()` — `Member` 저장 → `Reservation`에 `assignMember()` → `flush()` → `entityManager.clear()`(1차 캐시 비움) → `findById()`로 재조회 → `getClass()`/`getName()` 순서로 접근
- 예측(수정 후 최종): ① `reservation` 테이블 SELECT는 즉시 나가되 `member` 테이블 SELECT는 별개로 지연됨 ② `getMember().getClass()`는 `Member`가 아니라 프록시 클래스일 것 ③ `getName()` 호출 시점에 `member` SELECT 발생
- 실행 결과(`show-sql` 로그, 발생 순서 그대로):

```text
insert into member (name, id) values (?, default)
insert into reservation (cancel_reason, confirmed, member_id, requester_name, room_name, id) values (?, ?, ?, ?, ?, default)
select r1_0.id, r1_0.cancel_reason, r1_0.confirmed, r1_0.member_id, r1_0.requester_name, r1_0.room_name from reservation r1_0 where r1_0.id=?
클래스 타입: class com.example.studyroom.domain.Member$HibernateProxy
select m1_0.id, m1_0.name from member m1_0 where m1_0.id=?
이름: 진우
```

- 판정: 세 예측 모두 최종적으로 정답. `클래스 타입:` 출력과 `select ... from member` 사이의 순서가, `getMember()` 호출과 `getName()` 호출이 서로 다른 순간이라는 걸 로그로 직접 증명했다.

## 코드 작성 중 겪은 오류 2건

### 오류 1 — 연관관계 애노테이션 누락

`Reservation.java`에 `private Member member;` 필드만 추가하고 `@ManyToOne`/`@JoinColumn`을 빼먹었다.

- 증상: `./gradlew test` 23개 중 19개 실패, 전부 `org.hibernate.type.descriptor.java.spi.JdbcTypeRecommendationException`
- 원인: 애노테이션이 없으면 Hibernate는 `member`를 연관관계가 아니라 **일반 컬럼 값**으로 취급한다. `Member` 객체를 매핑할 JDBC 타입을 못 찾아 예외가 남.
- 교정: `@ManyToOne(fetch = FetchType.LAZY)` + `@JoinColumn(name = "member_id")` 추가.

### 오류 2 — `@ManyToOne`을 `@ManyToMany`로 오타

- 증상: 여전히 19개 실패, 이번엔 `org.hibernate.AnnotationException`(`CollectionBinder`)
- 원인: `@ManyToMany`는 `Set`/`List` 같은 컬렉션 타입을 기대하는데 필드는 단일 `Member`라 타입 불일치.
- 교정: `@ManyToOne`으로 수정. 이후 `./gradlew test` BUILD SUCCESSFUL, 23/23.

## self-invocation 재확인 실패 (Day17 복습 실패, 오답 3회)

`outer()`(무애노테이션) → `this.inner()`(`@Transactional`) 호출이 `false`인 이유를 3회 연속 틀렸다.

1. "Propagation으로 inner 트랜잭션이 outer와 묶였다" — self-invocation(프록시 우회 자체) 문제를 propagation(합류 판단) 문제로 착각
2. "transactionalOuter에서 호출한 inner가 트랜잭션 범위를 합쳤다" — 질문 대상이 아닌 다른 메서드로 답변 대상이 바뀜
3. "프록시 객체"(this가 가리키는 대상) — target 내부 코드에서 `this`는 항상 target 자기 자신을 가리키며 프록시를 가리킬 수 없다는 걸 놓침

교정: target 객체 내부에서의 `this.inner()`는 프록시를 거치지 않는 평범한 Java 메서드 호출이라 `TransactionInterceptor`의 가로채기 자체가 일어나지 않는다. `transactionalOuter()`가 `true`인 건 self-invocation을 우회해도 상관없이, **바깥 메서드 진입 시점에 이미 트랜잭션이 열려 있었기 때문**이다.

## 검증 근거

- `src/main/java/com/example/studyroom/domain/Member.java`, `Reservation.java:13-16, 25-31`
- `src/main/java/com/example/studyroom/repository/MemberRepository.java`
- `src/main/resources/db/migration/V4__member.sql`
- `src/test/java/com/example/studyroom/repository/MemberLazyProxyTest.java`
- `./gradlew test` 전체 스위트 최종 BUILD SUCCESSFUL(23/23), 오류 2건 각각 재현·교정 확인

## [직접 작성] 오늘 배운 것을 내 문장으로

- `@ManyToOne`에 애노테이션을 안 붙이면 Hibernate가 벌이는 일:
- `this.inner()`가 프록시를 거치지 않는 이유:

## 다음 시작점

Day18 이어서(미완료) — 빈칸 예제(`Member.reservations`에 `@OneToMany(mappedBy=...)` 채우기)부터 재개. 이후 독립 변형 → 인출 → 복습큐 등록.

## 이월분 완료 — 컬렉션 방향 LAZY 실험 (9/27)

주제: `Member.reservations`(컬렉션, `mappedBy`) 방향의 LAZY 동작이 단일 참조(`Reservation.member`)와 같은지 다른지 확인.

- 코드: `MemberLazyProxyTest.memberReservationsCollectionIsLazyPersistentBag()` — `Member` 저장 → `Reservation`에 `assignMember()` → `flush()`/`clear()` → `memberRepository.findById()`로 재조회 → `getReservations()`의 타입·초기화 여부·`size()` 순서로 확인
- 예측: ① 컬렉션도 실제 `ArrayList`가 아니라 Hibernate가 감싼 래퍼일 것이다 ② `.size()` 호출 전까지는 미초기화 상태일 것이다
- 실행 결과: `assertInstanceOf(PersistentBag.class, ...)` 통과, `.size()` 호출 전 `Hibernate.isInitialized()` == `false`, 호출 후 `true`, `size()` == `1`
- 판정: 두 예측 모두 맞았다. 다만 래퍼의 정체가 Day18 본문에서 본 것과 같은 `Entity$HibernateProxy`가 아니라 `org.hibernate.collection.spi.PersistentBag`이라는 별개의 클래스였다 — 단일 참조는 프록시로, 컬렉션은 컬렉션 전용 래퍼로 감싼다는 걸 이번에 구분했다.

## 새로 겪은 것 — import 경로 직접 확인

`PersistentBag`의 실제 패키지 경로(`org.hibernate.collection.spi.PersistentBag`)를 임포트하기 전에, 프로젝트가 실제로 물고 있는 `hibernate-core-6.6.18.Final.jar` 안의 클래스 목록을 직접 열어 확인했다 — 레퍼런스 문서에 따라 6.x부터 `org.hibernate.collection.internal` → `spi`로 패키지가 이동한 이력이 있어, 검색 결과만 믿지 않고 실제 jar를 확인하는 절차를 거쳤다. 그 결과 이번 유닛은 새로운 컴파일·런타임 오류 없이 한 번에 통과했다.

## 검증 근거(이월분)

- `src/main/java/com/example/studyroom/domain/Member.java:19-20, 37-39`
- `src/test/java/com/example/studyroom/repository/MemberLazyProxyTest.java:49-72`
- `./gradlew test --console=plain` BUILD SUCCESSFUL, 27/27 (커밋 [`7a9626d`](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/7a9626dcf62c0df95a8459fa6cb9472acbf0cbce))
