# Day29 (10/12, Week E D1) 예측→실행→차이 기록

주제: Request ID·MDC·민감정보 마스킹 + profile 분리

## 실험 1 — 커스텀 로그 패턴의 적용 시점

- 코드: `application.yml`의 `logging.pattern.console: "... [%X{requestId}] ..."`
- 예측: `application.yml`에 써놨으니 `RequestIdFilterTest`(Spring 컨테이너 없이 필터 객체만 직접 생성해 호출)에서도 그대로 찍힐 것이다
- 실행(`--info`로 표준출력 확인): `RequestIdHttpTest`(`@SpringBootTest`)에서는 `[fd9ad7d6-...]`처럼 찍혔지만, `RequestIdFilterTest`에서는 기본 패턴(`INFO com.example...`)이 찍히고 `[requestId]` 자리가 아예 없었다
- 차이 설명: yml 값 자체가 로그를 직접 조립하는 게 아니라, `ApplicationContext` 기동 중 `LoggingApplicationListener`가 `Environment`를 읽어 logback을 재구성해야 반영된다. 컨테이너를 안 띄우면 이 재구성이 일어나지 않아 logback 기본 패턴 그대로 남는다.

## 실험 2 — MDC 값이 Authorization 마스킹과 함께 실제로 새는지

- 코드: `maskAuthorization()`에 `"Bearer eyJhbGciOiJIUzI1NiJ9.super-secret-token-body.signature"`를 통째로 넣고 로그 출력 확인
- 예측: 토큰의 일부(예: 앞 몇 글자)는 식별용으로 남을 수도 있을 것 같다
- 실행: 로그 줄은 무조건 `Authorization=Bearer ***`만 찍혔다 — 원문 토큰 문자열은 로그 어디에도 없음(`RequestIdFilterTest.maskAuthorizationNeverIncludesRawHeaderValue`로 확인)
- 차이 설명: 코드가 스킴 유무만 보고 "Bearer ***" 고정 문자열을 반환하도록 구현되어 있어(부분 노출 로직 자체가 없음), 애초에 "일부만 남기기"는 이 구현의 선택지가 아니었다. 예측이 코드를 안 보고 한 추측이었다는 걸 확인.

## 판단 로직 교정 과정

- 1차 이해: "yml 설정은 파일에 있으니 항상 전역 적용된다"
- 교정: yml은 "무엇을"만 정의하고, "언제 반영되는가"는 `ApplicationContext` 기동이라는 별도의 시점에 달려있다. 컨테이너 생명주기 밖에서 만든 객체(순수 단위 테스트의 필터 인스턴스)는 이 재구성 시점을 거치지 않는다.

## 검증 근거

- `app/src/test/java/com/example/studyroom/logging/RequestIdFilterTest.java`
- `app/src/test/java/com/example/studyroom/logging/RequestIdHttpTest.java`
- `./gradlew test --console=plain` → BUILD SUCCESSFUL, 60/60
- `./gradlew test --info` (표준출력 확인용, 커밋 `21fd3d5` 시점)

## [직접 작성] 오늘 배운 것을 내 문장으로

- 커스텀 로그 패턴이 실제로 적용되는 시점과 조건:
- MDC를 finally에서 지워야 하는 이유:
- prod 프로필이 기본값을 두지 않은 이유:

## 다음 시작점

Week E D2 — 디버깅 실습(취소 사유 트리밍 버그 재현·수정).
