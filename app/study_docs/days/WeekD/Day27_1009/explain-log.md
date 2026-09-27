# Day27 (10/9, Week D D6) 예측과 교정 기록

새 코드 실험은 없다. 이 기록은 시험 중 답이 무너진 두 문항(6·7번)의 근거가 어떻게 재구성됐는지를 남긴다.

## 재구성 1 — HS256 서명 키의 최소 길이(문항 6)

- 1차 답: "128비트 정도면 충분하지 않아요?"
- 교정: RFC 7518 3.2는 HMAC-SHA 알고리즘의 키 크기가 해시 출력 크기 이상이어야 한다고 규정한다. HS256의 해시 출력은 256비트이므로 키도 256비트(32바이트) 이상이어야 한다. Day23에서 160비트 키(`studyroom-secret-key`)로 `WeakKeyException`을 실제로 봤지만, 그때는 "짧아서 안 된다"는 결론만 기억에 남았고 정확한 하한선 숫자는 다시 인출하지 못했다.
- 차이: 오류를 직접 겪은 경험(에피소드 기억)과 규정 수치(사실 기억)는 별개로 인출해야 한다는 게 이번 재구성의 결론이다.

## 재구성 2 — 401·403을 만드는 주체(문항 7)

- 1차 답: "둘 다 `SecurityConfig`가 직접 만들어서 응답하는 거 아닌가요?"
- 교정: `SecurityConfig.securityFilterChain()`은 `exceptionHandling(ex -> ex.authenticationEntryPoint(...).accessDeniedHandler(...))`으로 두 컴포넌트를 **등록**만 한다. 실제 401 JSON은 `CustomAuthenticationEntryPoint.commence()`가, 403 JSON은 `CustomAccessDeniedHandler.handle()`이 각각 만든다. 이 둘을 부르는 주체는 `ExceptionTranslationFilter`이고, 인증 자체가 안 됐는지(401) 인증은 됐지만 권한이 없는지(403)로 갈린다.
- 차이: "설정 클래스 하나가 다 처리한다"는 뭉뚱그려진 그림과, "설정은 등록만 하고 실행은 별도 컴포넌트가 한다"는 실제 구조는 다르다. Day24 velog_post의 "결정" 절에서 이미 이 구분을 코드 레벨로 다뤘는데, 시험에서 다시 물으니 처음부터 다시 뭉개졌다.

## 검증 근거

- `app/src/test/java/com/example/studyroom/security/JwtProviderTest.java` (HS256 키 길이 관련 기존 테스트)
- `app/src/main/java/com/example/studyroom/security/CustomAuthenticationEntryPoint.java`, `CustomAccessDeniedHandler.java`
- `app/study_docs/evidence`(Day23) — `WeakKeyException` 원문, `app/study_docs/days/WeekD/Day24_1004/`(문서화 담당자가 반영할 Day24 기록) — `SecurityConfig`의 `exceptionHandling` 등록 코드

## [직접 작성] 오늘 배운 것을 내 문장으로

<!-- 아래는 학습자가 직접 채운다. 비워두지 말 것. -->

- HS256 키 길이 규정을 "짧으면 안 된다"가 아니라 정확한 숫자로 기억하려면:
- 설정 클래스의 "등록"과 실제 컴포넌트의 "실행"을 구분해야 하는 이유:

## 다음 시작점

Week D D7 — 버퍼(401/403 응답 형식 통일).
