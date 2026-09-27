# [Spring Study Day 30] 버그 재현과 수정 — substring Off-by-one과 로그 기반 가설 검증

Day29에서 만든 `RequestIdFilter`를 실전에 써볼 기회가 예상 밖의 곳에서 왔다. 취소 사유(`cancelReason`) 앞뒤 공백을 지우는 작은 기능을 추가하다가, `trim()` 다음에 실수로 넣은 `substring(1)`이 매번 첫 글자를 하나씩 지우는 버그를 그대로 만들었다. 오늘은 이 버그를 실패하는 테스트로 먼저 커밋하고(재현), 원인을 로그로 확정한 뒤 별도 커밋으로 고쳤다(수정). Null 입력 방어나 이 서비스의 다른 검증 로직 리팩터링은 이 글의 범위가 아니다.

> `trim().substring(1)`이 트리밍된 문자열의 첫 글자를 추가로 잘라내는 버그를 만들었고, 재현 테스트를 커밋해 실패 상태를 남긴 뒤(`a9d9864`) 원인을 DEBUG 로그로 확정하고 수정했다(`8a030ea`). `substring(1)` 제거 후 `./gradlew test`는 61/61 전부 통과했다.

> **오늘의 흐름** `트리밍 기능 추가(실수로 substring(1) 포함) → 재현 테스트 작성·커밋(실패) → 두 테스트의 동일한 실패 메시지 관찰 → DEBUG 로그로 raw/trimmed/sanitized 대조 → 원인 확정 → substring(1) 제거·커밋(성공)`
>
> 이전 Day: Request ID·MDC와 Profile별 Secret 관리 (Day29)
> 다음 Day: Dockerfile 멀티스테이지 빌드와 compose.yaml (Day31)

## 1. 개념 설명

### 1) 가설-검증 디버깅과 재현 커밋

> **가설-검증 디버깅** = 증상을 보고 원인을 추측한 뒤, 그 추측이 맞는지 실행·로그로 직접 확인하는 절차

이번 버그는 브레이크포인트로 한 줄씩 멈춰서 보는 대신, HTTP 통합 테스트를 실패 상태로 먼저 커밋하고 로그로 값의 변화를 추적하는 방식을 택했다. 두 방식 모두 "실행 중 상태를 확인한다"는 목적은 같다. IntelliJ의 브레이크포인트 방식이었다면 아래처럼 거터를 클릭해 실행을 멈추고, 그 시점의 변수 값을 직접 들여다봤을 것이다.

![IntelliJ IDEA 에디터에서 코드 거터를 클릭해 3번째 줄에 빨간 브레이크포인트 점을 설정한 화면.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day30-web-intellij-set-breakpoint.png)

*출처: [Tutorial: Debug your first Java application](https://www.jetbrains.com/help/idea/debugging-your-first-java-application.html) — JetBrains s.r.o.*

우리 코드에서는 `ReservationCancelReasonHttpTest.cancelReasonKeepsFirstCharacterAfterTrimming()`을 실제 `POST /reservations/cancel/{id}` 경로로 작성해 버그를 재현 가능한 상태로 고정했다. 이 테스트가 실패하는 채로 먼저 커밋한 것(`a9d9864`)이 "재현"이다.

```text
가설(요구사항): 취소 사유 앞뒤 공백을 trim해서 저장하면 된다
→ 구현(실수): trim() 결과에 substring(1)을 추가로 적용
→ 재현 테스트 작성·실행 → 의도한 테스트 실패
→ 예상 밖 부수 피해: 공백이 전혀 없는 입력을 쓰던 기존 테스트도 함께 실패
```

두 실패의 assertion 메시지가 완전히 같았다는 점이 다음 단계의 단서였다.

```text
org.opentest4j.AssertionFailedError: expected: <일정 변경> but was: <정 변경>
    at app//com.example.studyroom.controller.ReservationCancelReasonHttpTest.cancelReasonKeepsFirstCharacterAfterTrimming(ReservationCancelReasonHttpTest.java:63)

org.opentest4j.AssertionFailedError: expected: <일정 변경> but was: <정 변경>
    at app//com.example.studyroom.service.ReservationServiceTransactionTest.cancelCommitsChangedState(ReservationServiceTransactionTest.java:38)
```

공백 유무가 다른 두 입력이 똑같이 "첫 글자 소실"로 귀결됐다는 것은, 원인이 트리밍 자체가 아니라 그 다음에 실행되는 공통 코드에 있다는 뜻이다.

### 2) 로그 기반 원인 추적과 requestId 상관

> **로그 레벨과 출력의 분리** = 코드에 `log.debug(...)`를 추가해도, 실행 시 로그 레벨이 그보다 낮게 설정돼 있어야 실제로 출력된다는 원칙

`ReservationCancelReasonHttpTest`는 실제 HTTP 경로를 타므로, Day29의 `RequestIdFilter`가 남긴 요청 로그와 `ReservationService.sanitizeCancelReason()`의 디버그 로그가 같은 스레드(같은 MDC)에서 실행된다. 기본 로그 레벨(INFO)로는 `log.debug` 줄이 아예 안 보였고, `LOGGING_LEVEL_COM_EXAMPLE_STUDYROOM_SERVICE=DEBUG` 환경변수를 줘야 아래 원문이 나타났다.

```text
23:51:32.701 [Test worker] INFO  [841cc97f-0a65-46e9-9407-a8466073f2e1] c.e.s.logging.RequestIdFilter - POST /reservations/cancel/1 Authorization=Bearer ***
23:51:32.809 [Test worker] DEBUG [841cc97f-0a65-46e9-9407-a8466073f2e1] c.e.s.service.ReservationService - cancelReason raw='  일정 변경  ' trimmed='일정 변경' sanitized='정 변경'
```

두 줄이 같은 `[841cc97f-...]`로 묶여 있어서, "이 요청 안에서" 값이 `raw`(원본, 공백 포함) → `trimmed`(정상, "일정 변경") → `sanitized`("정 변경", 첫 글자 소실)로 어떻게 망가지는지 한 줄로 확인됐다. `trim()` 자체는 맞게 동작했고, 그 다음 단계가 문제라는 것이 이 순간 확정됐다.

```text
가설 확정 순서
raw='  일정 변경  ' → trim() → trimmed='일정 변경' (정상)
→ substring(1) → sanitized='정 변경' (첫 글자 '일' 소실)
```

동일한 requestId로 필터 로그와 서비스 로그가 묶이는 것을 실제 디버깅 상황에서 확인한 것이 Day29 작업의 첫 실전 활용이었다. 아래 그림은 (브레이크포인트 방식이었다면) 이 시점에 봤을 변수 상태창의 모습이다 — 이번엔 로그 줄이 그 역할을 대신했다.

![IntelliJ IDEA Debug 툴 윈도우 전체 화면. 상단 에디터에 실행 지점이 하이라이트되고, 하단 패널에 스레드 상태와 변수 값이 표시된다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day30-web-intellij-debug-tool-window.png)

*출처: [Tutorial: Debug your first Java application](https://www.jetbrains.com/help/idea/debugging-your-first-java-application.html) — JetBrains s.r.o.*

두 접근의 차이는 이렇다. 브레이크포인트는 실행을 멈추고 그 순간의 상태를 살피는 방식이라 변수 하나하나를 대화식으로 확인할 수 있고, 로그 기반 추적은 실행을 멈추지 않고 미리 정해둔 지점의 값을 기록으로 남긴다. 여러 계층(필터→서비스)을 오가며 "값이 어느 단계에서 망가지는가"를 한 번에 비교하기에는, 이번처럼 로그 한 줄에 세 값을 나란히 찍는 쪽이 더 빨랐다.

### 3) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| 가설-검증 디버깅 | 증상에서 원인을 추측하고 실행·로그로 확인하는 절차 |
| 재현(Reproduce) | 버그를 테스트로 고정해 항상 같은 조건에서 재발생시키는 것 |
| Off-by-one 오류 | 인덱스·길이 계산이 하나 어긋나 경계값에서 잘못된 결과를 내는 오류 |
| 부수 피해(Collateral failure) | 새 변경이 의도한 범위 밖의 기존 테스트까지 함께 깨뜨리는 것 |

> **더 볼 것**
> - [Tutorial: Debug your first Java application — IntelliJ IDEA](https://www.jetbrains.com/help/idea/debugging-your-first-java-application.html): 브레이크포인트 설정과 Debug 툴 윈도우 사용법
> - [Logback Manual — Effective level](https://logback.qos.ch/manual/architecture.html#effectiveLevel): 로그 레벨이 출력 여부를 결정하는 규칙

## 2. 코드 구현

### 1) 버그가 있던 코드와 수정된 코드

버그가 있던 버전(커밋 `a9d9864`)은 `trim()`에 이미 만족스러운 결과가 있는데도, 그걸 못 믿고 한 글자를 더 잘라냈다.

```java
// a9d9864 — 버그 버전
String trimmed = cancelReason.trim();
String sanitized = trimmed.substring(1);   // 실수: trim()이 이미 끝낸 일을 또 건드림
```

수정 버전(커밋 `8a030ea`)은 `substring(1)` 호출을 제거하고 `trimmed`를 그대로 반환한다.

```java
private String sanitizeCancelReason(String cancelReason) {
    String trimmed = cancelReason.trim();
    log.debug("cancelReason raw='{}' sanitized='{}'", cancelReason, trimmed);
    return trimmed;
}
```

**한 줄씩 보기**

- `cancelReason.trim()`: 앞뒤 공백만 제거, 문자열 중간은 건드리지 않음.
- (제거됨) `trimmed.substring(1)`: 인덱스 1부터 끝까지만 남겨 첫 글자를 버림 — 이번 버그의 원인.
- `log.debug(...)`: raw/최종 값을 남겨 다음에 같은 종류의 실수가 나면 로그만으로 바로 의심 지점을 좁힐 수 있게 함.

### 2) 자동 검증 결과

| 시점 | 방법 | 결과 |
|---|---|---|
| 버그 재현(`a9d9864`) | `./gradlew test --console=plain` | BUILD FAILED, 61개 중 59개 통과, 2개 실패 |
| 버그 수정(`8a030ea`) | `./gradlew test --console=plain` | BUILD SUCCESSFUL, 61개 중 61개 통과, 0 failures |

`build/test-results/test/*.xml`을 직접 열어 `tests="61" failures="0" errors="0"`을 확인했다. 이번 Day는 예외적으로 "빌드 실패 상태"를 재현 커밋에 그대로 남기는 흐름을 브리프가 허용한 경우였고, 이후 Day부터는 다시 커밋 전 항상 초록을 유지하는 원칙으로 돌아간다.

커밋: [a9d9864](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/a9d9864f38c9af3eba3873583a9b0e6dd3d19269)(재현), [8a030ea](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/8a030eadd8ec95f01ee48c446a5de1ebd11e90bb)(수정)

## 3. 스스로 답한 질문

### 1) 트리밍 기능 추가가 만든 영향 범위

**질문.** 공백을 지우는 기능을 추가하면, 공백이 있는 입력을 쓰는 테스트에만 영향이 있는가?

**A1.** 처음에는 "그럴 것 같다. 공백 없는 입력은 trim해도 그대로일 테니까"라고 답했다. 실제로는 공백이 전혀 없는 입력("일정 변경")을 쓰던 기존 `ReservationServiceTransactionTest.cancelCommitsChangedState`까지 함께 깨졌다. 원인은 `trim()`이 아니라 그 다음에 붙인 `substring(1)`이었고, 이건 입력에 공백이 있든 없든 항상 실행되는 코드였다. "기능 추가"와 "그 구현의 버그"를 하나로 묶어서 생각한 것이 예측이 틀린 지점이다.

### 2) DEBUG 로그가 보이지 않은 이유

**질문.** `log.debug(...)`를 코드에 추가하면 테스트 실행 로그에 바로 보이는가?

**A2.** 처음에는 "새로 추가했으니 바로 보일 것"이라고 답했다. 실제로는 기본 로그 레벨(INFO)에서 DEBUG 줄이 전혀 출력되지 않았고, `LOGGING_LEVEL_COM_EXAMPLE_STUDYROOM_SERVICE=DEBUG` 환경변수로 레벨을 올려야 확인할 수 있었다. Day29에서 확인한 "로그 설정이 언제 반영되는가"와 같은 축의 교훈이, 이번엔 "로그 레벨이 출력 여부를 결정한다"는 형태로 다시 나타났다.

## 4. 학습 정리와 다음 범위

### 1) 이해의 변화와 남은 것

이전까지 디버깅은 "오류 메시지를 읽고 코드를 고치는 것"에 가까웠다. 오늘은 실패 메시지 두 개가 우연히 같은 게 아니라 공통 원인의 증거라는 것, 그리고 그 원인을 코드 추측이 아니라 로그로 확정하는 절차를 실제로 밟았다. Day29의 requestId가 필터 로그와 서비스 로그를 실제로 묶어준다는 것도 이번에 처음 실전에서 확인했다.

**아직 남은 것**은 두 가지다. ① `sanitizeCancelReason()`은 `null` 입력을 방어하지 않는다(`cancelReason.trim()`에서 NPE 가능) — 컨트롤러의 `@NotBlank`가 이미 막고 있어 실무적으로는 도달하지 않지만 서비스 계층 단독 방어는 아니다. **나중에 고칠 것**. ② `LOGGING_LEVEL_...=DEBUG`로 확인한 로그는 일회성 진단용으로만 썼고 `application-*.yml`에 영구 설정으로 남기지 않았다 — **나중에 고칠 것**(필요해지면 프로필별 로그 레벨 정리).

면접에서 다시 답해볼 항목을 남긴다.

- 서로 다른 두 테스트가 같은 assertion 메시지로 실패했을 때, 이걸 "공통 원인"의 증거로 쓸 수 있는 조건은 무엇인가
- 브레이크포인트 기반 디버깅과 로그 기반 디버깅 중 하나를 고르는 기준

---

오늘 공부한 소스코드: `app/src/main/java/com/example/studyroom/service/ReservationService.java`, `app/src/test/java/com/example/studyroom/controller/ReservationCancelReasonHttpTest.java`, `app/src/test/java/com/example/studyroom/service/ReservationServiceTransactionTest.java`
