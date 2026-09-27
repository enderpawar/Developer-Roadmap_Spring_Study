# Day30 (10/13, Week E D2) 예측→실행→차이 기록

주제: 취소 사유 트리밍 버그의 재현과 수정

## 실험 1 — 재현 테스트의 영향 범위

- 코드: `sanitizeCancelReason()`에 `trim()` 다음 `substring(1)`을 실수로 추가한 상태에서 `ReservationCancelReasonHttpTest.cancelReasonKeepsFirstCharacterAfterTrimming()`을 작성
- 예측: 공백이 있는 입력을 쓰는 이 새 테스트만 실패할 것이다
- 실행: `./gradlew test` → 신규 테스트뿐 아니라 공백 없는 입력("일정 변경")을 쓰는 기존 `ReservationServiceTransactionTest.cancelCommitsChangedState`까지 함께 실패(61개 중 59개 통과, 2개 실패)
- 차이 설명: `substring(1)`은 입력에 공백이 있는지와 무관하게 `cancel()`을 부르는 모든 경로에서 첫 글자를 잘라낸다. "트리밍 기능 추가"와 "그 구현의 버그"는 서로 다른 것이었는데, 예측 단계에서 이 둘을 하나로 묶어서 생각했다.

## 실험 2 — 로그 레벨과 디버그 출력

- 코드: `sanitizeCancelReason()`에 `log.debug("cancelReason raw='{}' sanitized='{}'", ...)` 추가
- 예측: 새로 추가한 로그이니 테스트 실행 시 표준출력에 바로 보일 것이다
- 실행: 기본 로그 레벨(INFO)로 돌리면 이 줄이 전혀 안 보였다. `LOGGING_LEVEL_COM_EXAMPLE_STUDYROOM_SERVICE=DEBUG` 환경변수를 주고서야 `<system-out>`에서 다음 원문을 확인했다.

```text
23:51:32.701 [Test worker] INFO  [841cc97f-0a65-46e9-9407-a8466073f2e1] c.e.s.logging.RequestIdFilter - POST /reservations/cancel/1 Authorization=Bearer ***
23:51:32.809 [Test worker] DEBUG [841cc97f-0a65-46e9-9407-a8466073f2e1] c.e.s.service.ReservationService - cancelReason raw='  일정 변경  ' trimmed='일정 변경' sanitized='정 변경'
```

- 차이 설명: 로그 코드가 실행됐다는 것과, 그 결과가 눈에 보인다는 것은 별개다. 레벨(DEBUG)이 실행 시 설정된 임계값(INFO)보다 낮으면 로그 프레임워크가 아예 그 줄을 만들지 않는다. Day29에서 "yml 설정이 언제 적용되는가"로 확인한 것과 같은 종류의 교훈이, 이번엔 "로그가 왜 안 보이는가"라는 실전 디버깅 국면에서 다시 나타났다.

## 판단 로직 교정 과정

- 1차 이해: 새 기능(트리밍)을 추가하면 그 기능이 관여하는 입력에만 영향이 국한된다
- 교정: 실제 영향 범위는 "그 코드 경로를 지나는 모든 호출"이다. 트리밍 자체는 무해했지만, 같은 메서드 안에 있던 `substring(1)`이라는 별개의 실수가 경로 전체에 영향을 줬다. 실패한 두 테스트의 `expected`/`actual`이 완전히 같았다는 사실이 "공통 원인 하나"라는 가설의 근거가 됐다.

## 검증 근거

- `app/src/test/java/com/example/studyroom/controller/ReservationCancelReasonHttpTest.java`
- `app/src/main/java/com/example/studyroom/service/ReservationService.java`
- 재현: `./gradlew test --console=plain` → BUILD FAILED, 61개 중 59개 통과, 2개 실패(커밋 `a9d9864`)
- 수정: `./gradlew test --console=plain` → BUILD SUCCESSFUL, 61/61(커밋 `8a030ea`)

## [직접 작성] 오늘 배운 것을 내 문장으로

- substring(1)이 실제로 지워버린 문자와 그 이유:
- 부수 피해로 깨진 테스트를 보고 공통 원인을 의심하게 된 근거:
- DEBUG 로그가 안 보였을 때 먼저 확인해야 하는 것:

## 다음 시작점

Week E D3 — Dockerfile/compose.yaml로 컨테이너 이미지 만들기.
