# Day33 (10/18, Week E D5) 예측→실행→차이 기록

주제: 예약 시간대 중복 방지 — 구간 겹침 검사

## 실험 1 — `ddl-auto: validate`와 신규 컬럼 매핑 일치

- 변경: `Reservation`에 `startAt`/`endAt` 필드 추가, `V7__reservation_time_slot.sql`로 같은 이름의 TIMESTAMP 컬럼 추가
- 예측: 컬럼명과 필드명을 맞추면 `ddl-auto: validate`가 첫 실행부터 통과할 것
- 실행: `./gradlew test` 첫 실행부터 71개 전부 통과. `--info`로 재실행해 실제 SQL을 확인하니 `start_at`/`end_at`이 정확히 매핑됨
- 교정 필요했던 부분: 없음 — 여러 계층(마이그레이션·엔티티·리포지토리 3종·서비스·DTO·컨트롤러·예외 핸들러)을 동시에 손댔는데도 오류가 안 난 것 자체가 드문 일이라, 실패를 지어내지 않고 "안 났다"는 사실을 그대로 남겼다

## 실험 2 — 맞닿는 구간(touching)의 겹침 판정

- 코드: `findOverlapping()`이 `r.startAt < :endAt and :startAt < r.endAt`(부등호, 등호 없음)로 겹침을 판정
- 예측: 10~11시 기존 예약과 11~12시 신규 예약처럼 경계가 정확히 맞닿는 경우는 "겹치지 않음"으로 분류될 것
- 실행: `reserveAllowsTouchingIntervalsInSameRoom`(Unit), `touchingIntervalsInSameRoomAreBothAccepted`(HTTP) 둘 다 예외 없이/200으로 통과
- 교정 필요했던 부분: 없음. 다만 이 결과는 Allen의 구간 대수에서 "overlaps"가 아니라 "meets"에 해당한다는 걸 나중에 자료를 찾아보고서야 용어로 연결했다 — 코드를 먼저 검증하고 이론 용어는 뒤에 붙인 순서였다

## 실험 3 — 기존 2-파라미터 `reserve()` 호출부의 하위 호환

- 변경: 기존 `reserve(roomName, requesterName)`가 새 4-파라미터 버전을 `reserve(roomName, requesterName, null, null)`로 위임하도록 오버로드
- 예측: 시간대가 없으면 새 검증·중복검사 로직이 아예 개입하지 않으므로 기존 컨트롤러 테스트들을 하나도 안 고쳐도 그대로 통과할 것
- 실행: 기존 테스트 61개 전부 무수정 통과(`reservationWithoutTimeSlotStillWorksLikeBefore`로 별도 확인까지 추가)
- 교정 필요했던 부분: 없음

## 판단 로직 교정 과정

- **취소된 예약 제외**: 1차 근거 "브리프 문구에 없는 조건이라 안 넣어도 될 것 같다" — 결과적으로 빠뜨릴 뻔한 판단. `cancel()`이 `confirmed`를 `false`로 되돌리는 기존 도메인 규칙과 대조한 뒤에야 "취소된 예약이 계속 새 예약을 막으면 취소 자체가 의미 없어진다"는 근거로 `confirmed = true` 조건을 추가했다.
- **시간대 필드를 선택으로 설계**: 1차 근거 "필수(`@NotNull`)로 걸면 검증이 간단하다" — 실제로 걸어보고서야 시간대 없이 예약하던 기존 테스트 8개가 400으로 깨지는 걸 확인했다. "둘 다 있을 때만 검증·중복검사를 수행한다"는 가드 조건으로 하위 호환을 지키는 쪽으로 교정했다.

## 검증 근거

- `src/test/java/com/example/studyroom/service/ReservationOverlapTest.java`(Unit 6개), `src/test/java/com/example/studyroom/controller/ReservationOverlapHttpTest.java`(Integration 4개)
- `./gradlew test --console=plain` — BUILD SUCCESSFUL, 71개 중 71개 통과(`build/test-results/test/*.xml` 전수 확인)
- 커밋 [`11763ea`](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/11763ea1bd113e9b9ec7a5e7b14dbe440ec9466e)

## [직접 작성] 오늘 배운 것을 내 문장으로

<!-- 아래는 학습자가 직접 채운다. 비워두지 말 것. -->

- 겹침 판정에서 등호 대신 부등호를 쓰는 이유:
- 취소된 예약을 검사 대상에서 빼야 하는 이유를 도메인 규칙으로 설명하면:
- "지금 안 깨진다"와 "설계가 맞다"가 다른 이유(시간대 필수/선택 판단에서):

## 다음 시작점

Week E D6(Day34) — 최종 인출 시험(전 범위) + 루브릭 자가평가.
