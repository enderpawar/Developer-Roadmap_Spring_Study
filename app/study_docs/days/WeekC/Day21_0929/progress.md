# Day21 (2026-09-29, Week C D7 버퍼) 진행 기록

> 주제: 기술부채 상환(inner join fetch의 member-null 누락) + 조회 전용 목록 API 독립과제
> 상태: **완료.** 기술부채 원장의 "나중에 고칠 것" 중 Week C D7 슬롯으로 지정된 두 건 중 하나(Day19 join fetch inner/null 누락)를 이 세션에서 해소했다. 나머지 하나(Day18 `Member.reservations` mappedBy 빈칸)는 Day18·19 이월분 세션(9/27, 커밋 `7a9626d`)에서 이미 해소돼 이 문서에서는 반복하지 않는다.

## 1. 완료한 것

| 항목 | 내용 |
|---|---|
| `SpringDataReservationRepository.findAllWithMemberOrNull()` (신규) | `@Query("select r from Reservation r left join fetch r.member")` |
| `ReservationRepository.findAllWithMemberOrNull()` (신규, `default`) | Day19에서 확립한 패턴 그대로 재사용 — 대조군 구현체 무변경 |
| `JpaReservationRepository.findAllWithMemberOrNull()` (신규) | `delegate.findAllWithMemberOrNull()`로 위임 |
| `FetchJoinInnerVsLeftTest.java` (신규) | inner join 유실 / left join 유지 대비 테스트 2건 |
| `ReservationSummary` record (신규) | `roomName`, `requesterName`, `memberName` |
| `ReservationService.findAllSummaries()` (신규) | `@Transactional(readOnly = true)` |
| `ReservationController` `GET /reservations` (신규) | `findAllSummaries()`로 위임 |
| `ReservationControllerHttpTest.listReturnsReservationsWithMemberNameOrNull()` (신규) | MockMvc로 `memberName`이 `null`로 내려오는 것 확인 |

## 2. 겪은 오류

이번 유닛에서도 새로 발생한 컴파일·런타임 오류는 없었다 — Day19에서 겪은 두 가지 실수(`@Query` 위치, 인터페이스 확장의 전파)를 `default` 메서드 패턴으로 처음부터 피했다.

## 3. 결정 — inner join은 그대로, 서비스는 left join

- `findAllWithMember()`(inner join fetch)는 **그대로 둔다** — `NPlusOneTest`에서 "N+1이 없다"만 확인하는 대조군 용도로 계속 쓴다.
- 서비스 목록 조회는 `findAllWithMemberOrNull()`(left join fetch)을 쓴다 — 현재 `POST /reservations`로 생성되는 예약은 전부 member가 없어서, inner join이면 목록 API가 방금 만든 예약을 빼먹는 눈에 보이는 버그가 된다. `FetchJoinInnerVsLeftTest`로 두 경우를 직접 대조해서 내린 결정이다.

## 4. 상태 확인

| 시점 | 결과 |
|---|---|
| 세션 시작(Day18·19 이월분 완료 시점) | `./gradlew test` 27/27 green |
| 최종 | `./gradlew test --console=plain` BUILD SUCCESSFUL, 30/30 |

## 5. 남은 한계·부채

- `GET /reservations`는 페이징이 없다 — 예약이 많아지면 전체를 한 번에 반환한다. 5주 트랙 범위 밖으로 남긴다(고치지 않을 것 후보).
- `ReservationSummary`는 취소 여부(`confirmed`/`cancelReason`)를 담지 않는다 — 이번 독립 과제 범위를 "member 이름 포함 목록"으로 좁혔기 때문. 필요해지면 필드를 늘린다.
- 기술부채 원장(`app/study_docs/기술부채.md`) 갱신은 이 커밋에 포함하지 않았다 — 코드 단계 작업 규칙("study_docs는 커밋하지 않는다")에 따라 문서화 단계 담당자가 위 "3. 결정" 절과 커밋 해시를 근거로 반영한다.

## 6. [직접 작성] 오늘 배운 것을 내 문장으로

<!-- 아래는 학습자가 직접 채운다. 비워두지 말 것. -->

- inner join fetch를 고치지 않고 새 메서드를 추가한 이유:
- `readOnly = true`가 실제로 생략하는 것:

## 7. 다음 시작점

Week D D1 — BCrypt 비밀번호 저장.

---

커밋 해시: `094c6b3` — `study(day21): left join fetch로 member 없는 예약 유지 확인, 예약 목록 조회 API 추가`
