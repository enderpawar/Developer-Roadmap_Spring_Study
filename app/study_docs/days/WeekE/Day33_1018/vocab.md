# Day33 (10/18, Week E D5) 용어

주제: 누적 독립과제 — 예약 시간대 중복 방지(구간 겹침 검사)

| 용어 | 한줄뜻 | 오늘 코드와 관찰 |
|---|---|---|
| 구간 겹침(Interval Overlap) | 두 시간 구간이 공통된 시간을 공유하는 상태 | `start < otherEnd && otherStart < end` 부등호 공식으로 판정 |
| Allen의 구간 대수(Interval Algebra) | 두 구간의 관계를 overlaps·meets·before 등으로 분류하는 이론 | "겹침"과 "맞닿음"을 구분하는 근거로 확인 |
| 맞닿음(Meets, 경계 접촉) | 한 구간이 끝나는 시각과 다른 구간이 시작하는 시각이 정확히 같아 간격이 0인 상태 | `reserveAllowsTouchingIntervalsInSameRoom` — 10~11시, 11~12시는 겹침 아님 |
| 엄격 부등호 비교 | `<=`가 아니라 `<`로 비교해 경계가 맞닿는 경우를 겹침에서 제외하는 것 | `r.startAt < :endAt and :startAt < r.endAt` |
| 하위 호환 확장 | 기존 호출부·테스트를 고치지 않고 새 동작을 추가하는 설계 | `startAt`/`endAt`을 선택 필드로 둬 없으면 검사를 건너뜀 |
| 오버로드(Overload) | 같은 이름의 메서드를 매개변수 목록만 다르게 여러 개 두는 것 | `reserve(room, name)`이 4-파라미터 버전을 `null, null`로 위임 호출 |
| 가산적 마이그레이션(Additive Migration) | 기존 컬럼·행을 건드리지 않고 NULL 허용 컬럼만 새로 추가하는 변경 | `V7__reservation_time_slot.sql` |
| 제외 제약(Exclusion Constraint) | DB가 겹치는 행의 저장 자체를 거부하도록 강제하는 제약(PostgreSQL `EXCLUDE USING gist` 등) | 이번 구현엔 없음 — 애플리케이션 레벨 검사만 존재 |
| 409 Conflict | 요청 형식은 맞지만 서버의 현재 상태와 충돌해 처리할 수 없다는 상태 코드 | `ReservationOverlapException` → 409 |
| 400 Bad Request | 요청 자체의 형식·값이 잘못됐다는 상태 코드 | `InvalidReservationTimeException`(`endAt`이 `startAt`보다 앞섬) → 400 |

## 핵심 판정 로직

```text
겹침 판정: existing.startAt < new.endAt  AND  new.startAt < existing.endAt
→ 둘 다 참이면 겹침(409)

맞닿는 경우(기존 10~11시, 신규 11~12시):
existing.startAt(10) < new.endAt(12)   → 참
new.startAt(11) < existing.endAt(11)   → 거짓 (11 < 11은 거짓)
→ 하나라도 거짓이면 겹침 아님(허용)
```
