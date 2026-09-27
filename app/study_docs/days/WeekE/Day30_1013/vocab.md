# Day30 (10/13, Week E D2) 용어

주제: 디버깅 실습 — 재현 커밋과 수정 커밋으로 나눈 실제 버그 추적

| 용어 | 한줄뜻 | 오늘 코드와 관찰 |
|---|---|---|
| 가설-검증 디버깅(Hypothesis-driven debugging) | 증상을 보고 원인을 추측한 뒤, 그 추측이 맞는지 실행·로그로 확인하는 절차 | "trim 다음 substring(1)이 원인" 가설을 로그로 확정 |
| 재현(Reproduce) | 버그를 우연이 아니라 항상 같은 조건에서 다시 발생시킬 수 있게 테스트로 고정하는 것 | `ReservationCancelReasonHttpTest.cancelReasonKeepsFirstCharacterAfterTrimming` |
| 부수 피해(Collateral failure) | 새 변경이 원래 의도한 범위 밖의 기존 테스트까지 함께 깨뜨리는 현상 | `ReservationServiceTransactionTest.cancelCommitsChangedState`가 같이 실패 |
| 로그 레벨과 출력의 분리 | 코드에 `log.debug(...)`를 추가해도 실행 시 로그 레벨이 그보다 낮아야 실제로 출력된다는 원칙 | 기본 INFO 레벨에서는 `log.debug` 줄이 안 보이고, `LOGGING_LEVEL_...=DEBUG`로 올려야 보임 |
| requestId와 애플리케이션 로그의 상관 | Day29의 `RequestIdFilter`가 남긴 요청 로그와 서비스 계층의 디버그 로그가 같은 MDC(같은 스레드)라 같은 requestId로 묶이는 것 | 두 로그 줄이 동일한 `[841cc97f-...]`로 출력됨 |
| Off-by-one 오류 | 인덱스나 길이 계산이 하나 어긋나 경계값에서 잘못된 결과를 내는 대표적인 버그 유형 | `trimmed.substring(1)`이 이미 트리밍된 문자열의 첫 글자를 추가로 잘라냄 |
| 재현 커밋 / 수정 커밋 분리 | 버그를 먼저 실패하는 테스트로 커밋해 남기고, 별도 커밋에서 수정하는 기록 방식 | 커밋 `a9d9864`(재현, 빌드 실패) → `8a030ea`(수정, 빌드 성공) |

## 핵심 진행 순서

```text
요구사항: "취소 사유 앞뒤 공백을 트리밍해서 저장"
→ 구현 실수: trim() 결과에 substring(1)을 추가로 적용
→ 재현 테스트 작성·실행 → 의도한 테스트 실패 + 기존 테스트까지 부수 피해로 실패
→ 두 실패의 expected/actual이 동일("일정 변경" vs "정 변경") → 공통 원인 가설
→ DEBUG 로그로 raw/trimmed/sanitized 세 값을 확인 → substring(1)이 원인으로 확정
→ substring(1) 제거 → 전체 재실행, 두 실패 모두 회복
```
