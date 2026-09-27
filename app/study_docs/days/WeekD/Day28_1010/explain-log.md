# Day28 (10/10, Week D D7 버퍼) 예측→실행→차이 기록

주제: 401/403 응답에 `code`·`timestamp` 추가(기존 `error` 필드 유지)

## 실험 1 — 필드 추가가 기존 단언을 깨는가

- 코드: `GlobalExceptionHandler.handleInvalidCredentials()`·`CustomAuthenticationEntryPoint`·`CustomAccessDeniedHandler` 세 곳에 `code`·`timestamp`만 추가하고 `error`는 그대로 둠
- 예측: 필드명을 바꾸거나 빼지 않았으니 기존 `jsonPath("$.error")` 단언은 전부 그대로 통과할 것이다
- 실행 결과: `./gradlew test` 첫 실행부터 초록 — 컴파일/런타임 오류 없음, 기존 단언 수정 없이 그대로 유효
- 판정: 예측과 일치. 오류를 지어내지 않고, 실제로 안 났다는 사실을 그대로 남긴다.

## 실험 2 — 응답 조합 방식이 서로 다른 두 곳을 같은 모양으로 맞추기

- `GlobalExceptionHandler`는 `Map<String, Object>`를 Jackson이 직렬화한다. `CustomAuthenticationEntryPoint`·`CustomAccessDeniedHandler`는 `HttpServletResponse.getWriter()`로 문자열을 직접 쓴다(Day24부터 유지된 설계, `ExceptionTranslationFilter` 단계라 `@RestControllerAdvice`가 안 닿기 때문).
- 예측: 조합 방식이 달라도 최종 JSON의 필드 구성(`error`·`code`·`timestamp`)만 맞추면 클라이언트 입장에서는 동일하게 보일 것이다.
- 실행 결과: 세 응답 모두 같은 세 필드를 가진 JSON으로 확인됨(`ReservationControllerHttpTest`·`AuthControllerHttpTest`의 `$.code`/`$.timestamp` 단언 추가로 검증).

## 판단 로직 교정 과정 — "일관성"의 범위

- 1차 생각: 일관성을 맞추려면 세 곳의 구현 방식(Map 직렬화 vs 문자열 조합)도 하나로 통일해야 하지 않을까
- 교정: 이번 부채 항목이 요구하는 일관성은 **클라이언트가 보는 응답 모양**이지, 서버 내부 구현 방식이 아니다. `CustomAuthenticationEntryPoint`를 Jackson 방식으로 바꾸는 건 더 안전하지만(이스케이핑 자동 처리) 범위 밖의 리팩터링이라 남겨뒀다.

## 검증 근거

- `app/src/main/java/com/example/studyroom/exception/GlobalExceptionHandler.java`
- `app/src/main/java/com/example/studyroom/security/CustomAuthenticationEntryPoint.java`, `CustomAccessDeniedHandler.java`
- `./gradlew test --console=plain` BUILD SUCCESSFUL, 51/51 (커밋 [`d38c973`](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/d38c97362738824b5e39dee48f3926d79b37ce42))

## [직접 작성] 오늘 배운 것을 내 문장으로

<!-- 아래는 학습자가 직접 채운다. 비워두지 말 것. -->

- 응답 "일관성"이 구현 방식이 아니라 무엇을 기준으로 판단해야 하는지:
- 기존 필드를 지우지 않고 추가만 하는 변경이 왜 더 안전한지:

## 다음 시작점

Week D 전체 완료. 다음은 Week E D1 — 로깅과 설정관리.
