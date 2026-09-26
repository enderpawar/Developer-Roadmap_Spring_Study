# Day21 (9/29, Week C D7 버퍼) 용어

주제: 기술부채 상환(inner join fetch의 member-null 누락) + 조회 전용 목록 API

| 용어 | 한줄뜻 | 오늘 코드와 관찰 |
|---|---|---|
| Left Join Fetch | JPQL의 `left join fetch` — 오른쪽(연관 엔티티)에 대응 행이 없어도 왼쪽 엔티티를 결과에서 빼지 않는 fetch 방식 | `findAllWithMemberOrNull()` |
| Inner Join Fetch | Day19의 `join fetch` — 연관 엔티티에 대응 행이 없으면 그 왼쪽 행 자체가 결과에서 빠지는 방식 | `findAllWithMember()`(대조군으로 유지) |
| `@Transactional(readOnly = true)` | 조회 전용임을 선언해 변경 감지용 스냅샷 비교·flush를 생략하는 트랜잭션 옵션 | `ReservationService.findAllSummaries()` |
| 응답 전용 DTO(record) | Entity를 그대로 직렬화하지 않고 필요한 값만 꺼내 담은 불변 응답 타입 | `ReservationSummary(roomName, requesterName, memberName)` |
| default 메서드 위임 패턴 | Port 인터페이스에 새 시그니처를 `default`로 추가해 대조군 구현체를 안 건드리는 패턴(Day19에서 확립) | `ReservationRepository.findAllWithMemberOrNull()` |

## 핵심 대조 — inner join fetch vs left join fetch (같은 데이터 기준)

| 구분 | inner join fetch(`findAllWithMember`) | left join fetch(`findAllWithMemberOrNull`) |
|---|---|---|
| member 없는 예약(`B-202`) | 결과에서 빠짐 | 남고 `getMember()`가 `null` |
| 오늘 실측(A-101 + B-202 2건 기준) | 1건 반환 | 2건 반환 |
| 서비스 목록 조회에 쓰는가 | 아니오(`NPlusOneTest` 대조군 전용으로 유지) | 예(`findAllSummaries()`) |

## CS 연결

관계대수에서 inner join은 두 집합의 교집합이고, left join은 왼쪽 집합 전체를 남기고 오른쪽에 짝이 없으면 `NULL`로 채운다. `member_id`가 없는 예약은 오늘 HTTP로 만드는 예약 전부이므로, inner join을 그대로 서비스에 노출하면 방금 만든 데이터가 목록에서 사라지는 눈에 보이는 버그가 된다.
