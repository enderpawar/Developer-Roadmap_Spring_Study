# Day21 (9/29, Week C D7 버퍼) 인출 기록

새 개념 인출보다, D6(9/28) 시험에서 다룬 개념을 실제 설계 판단에 쓰는 데 집중한 날이다.

## 1. 설계 판단 — 목록 조회의 join 선택

| # | 질문 | 학습자 판단 | 근거 | 판정 |
|---|---|---|---|---|
| P1 | `findAllWithMember()`(inner join fetch)를 그대로 서비스 목록 조회에 쓸 수 있는가 | 아니오 | 지금 `POST /reservations`로 만드는 예약은 전부 member가 없어서, inner join을 쓰면 방금 만든 예약이 목록에서 통째로 빠진다 | ✅ (`FetchJoinInnerVsLeftTest.innerJoinFetchDropsReservationWithoutMember`로 실측 확인 후 판단) |
| P2 | 그럼 `findAllWithMember()` 자체를 left join으로 고치면 안 되는가 | 아니오, 새 메서드를 추가한다 | `NPlusOneTest`가 "inner join으로도 N+1이 없다"는 것 자체를 검증하는 대조군이라, 그 쿼리의 의미를 바꾸면 그 테스트의 의도가 달라진다 | ✅ |
| P3 | left join fetch로 조회했을 때 member가 없는 행의 `getMember()`는 무엇을 반환하는가 | `null` | 오른쪽(member)에 대응 행이 없을 뿐 왼쪽(reservation)은 그대로 남는다는 SQL 의미 그대로 | ✅ (`assertNull(found.getMember())`로 확인) |

세 판단 모두 D6 시험의 9번 문항("fetch join이 매핑 자체를 바꾸는가")에서 교정한 기준 — **fetch join은 쿼리 단위 지시이지 매핑을 바꾸는 게 아니다** — 을 그대로 썼다. `findAllWithMember()`를 고치지 않고 옆에 `findAllWithMemberOrNull()`을 추가한 것 자체가 그 기준의 적용이다.

## 2. `readOnly` 선택 근거

목록 조회(`findAllSummaries()`)에 `@Transactional(readOnly = true)`를 붙일지 판단했다. 이 메서드는 값을 하나도 바꾸지 않고 DTO로 변환만 하므로, 변경 감지를 위한 로드 스냅샷 비교·flush 대상이 될 이유가 없다. Week C D1에서 "주어진 래퍼"로만 썼던 `@Transactional`을, "조회 전용" 범위를 스스로 판단해 붙인 첫 지점이다.

## 3. 복습 일정

이번 세션은 새 항목을 복습큐에 등록하지 않았다. D6에서 갱신한 항목(self-invocation 9/29, AOP·LAZY 프록시 구분·`REQUIRES_NEW`·fetch join 적용 범위 9/30)의 다음 도래일은 그대로 두고, 다음 세션(Week D D1)에서 이어서 처리한다.
