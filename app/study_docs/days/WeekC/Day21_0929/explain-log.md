# Day21 (9/29, Week C D7 버퍼) 예측→실행→차이 기록

주제: inner join vs left join fetch, 조회 전용(`readOnly`) 목록 API

## 실험 1 — inner join fetch가 member 없는 예약을 빼는가

- 코드: `FetchJoinInnerVsLeftTest.innerJoinFetchDropsReservationWithoutMember()` — member 있는 예약(A-101) 1건 + member 없는 예약(B-202) 1건 저장 → `findAllWithMember()` 호출
- 예측: inner join이므로 B-202가 결과에서 빠질 것이다
- 실행 결과: `result.size()` == 1, B-202는 결과에 없음
- 판정: 예측과 일치

## 실험 2 — left join fetch 전환 후 유지 여부

- 코드: `FetchJoinInnerVsLeftTest.leftJoinFetchKeepsReservationWithoutMember()` — 같은 데이터로 `findAllWithMemberOrNull()` 호출
- 예측: B-202도 결과에 남고, `getMember()`는 `null`일 것이다
- 실행 결과: `result.size()` == 2, B-202의 `getMember()` == `null`
- 판정: 예측과 일치. SQL의 `join`/`left join` 차이가 그대로 결과 건수 차이(1건 vs 2건)로 이어졌다

```sql
-- left join fetch
select r1_0.id, ..., m1_0.id, m1_0.name, ...
from reservation r1_0
left join member m1_0 on m1_0.id=r1_0.member_id

-- join fetch (대조군)
select r1_0.id, ..., m1_0.id, m1_0.name, ...
from reservation r1_0
join member m1_0 on m1_0.id=r1_0.member_id
```

## 겪은 오류

이번 유닛에서도 `./gradlew test`가 처음부터 초록으로 끝났다 — 새로 발생한 컴파일·런타임 오류는 없다. Day19에서 겪었던 실수(`ReservationRepository`에 추상 메서드를 그냥 추가하면 대조군 구현체가 깨진다, `@Query`는 메서드에만 붙는다)를 이번엔 처음부터 `default` 메서드 패턴으로 피해 갔다.

## 검증 근거

- `src/test/java/com/example/studyroom/repository/FetchJoinInnerVsLeftTest.java`
- `src/main/java/com/example/studyroom/service/ReservationService.java:41-50`
- `src/main/java/com/example/studyroom/controller/ReservationController.java:32-35`
- `./gradlew test --console=plain` BUILD SUCCESSFUL, 30/30 (커밋 `094c6b3`)

## [직접 작성] 오늘 배운 것을 내 문장으로

<!-- 아래는 학습자가 직접 채운다. 비워두지 말 것. -->

- inner join fetch와 left join fetch가 결과 건수에서 갈리는 지점:
- `readOnly = true`를 붙이는 근거가 "느려서"가 아니라 무엇인지:

## 다음 시작점

Week C 전체 완료. 다음은 Week D D1 — BCrypt 비밀번호 저장.
