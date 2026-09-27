# Day34 (10/20, Week E D6) 예측과 교정 기록

새 코드 실험은 없다. 이 기록은 시험 중 답이 무너진 세 문항(10·11·13번)의 근거가 어떻게 재구성됐는지를 남긴다.

## 재구성 1 — JWT stateless의 의미(문항 10)

- 1차 답: "stateless는 토큰에 만료시간이 있어서 서버가 세션을 안 지워도 된다는 뜻 아니에요?" — 만료시간이라는 부속 개념과 stateless라는 핵심 성질을 뒤바꿔 이해
- 교정: stateless는 "서버가 요청 사이에 클라이언트 상태(세션)를 저장하지 않는다"는 뜻이다. 매 요청마다 토큰 자체에 필요한 정보(사용자 식별자, 만료시간 등)가 실려 있어 서버가 별도 저장소를 조회할 필요가 없다는 게 핵심이다. 로그아웃 무효화 문제도 여기서 나온다 — 서버에 지울 세션 상태가 없으므로, 발급된 토큰은 유효기간이 끝날 때까지 계속 유효하다.
- 차이: 만료시간은 stateless의 "결과로 필요해진 안전장치"이지 stateless를 정의하는 성질이 아니다. 정의와 그 정의가 만드는 문제(로그아웃 무효화)를 뒤섞어서 이해하고 있었다.

## 재구성 2 — 테스트 슬라이스가 올리는 범위(문항 11)

- 1차 답: "`@WebMvcTest`는 Controller 테스트니까 Service까지 실제 Bean으로 다 띄워주는 거 아니에요?"
- 교정: `@WebMvcTest`는 `@Controller`·`@ControllerAdvice`·필터 등 **웹 계층 Bean만** 로드한다. `Service`·`Repository`는 컨텍스트에 없어서, 컨트롤러가 의존하는 Service는 `@MockBean`으로 직접 등록해야 컨텍스트 로딩 자체가 성공한다. "슬라이스"라는 이름이 정확히 이 의미였다 — 계층 하나만 잘라 올리고 나머지는 mock으로 대체한다.
- 차이: "통합 테스트보다는 가볍다"는 결과만 기억하고, **정확히 어디까지 올리고 어디부터 mock인지**의 경계를 잊고 있었다.

## 재구성 3 — 멀티스테이지 빌드가 이미지 크기를 줄이는 원리(문항 13)

- 1차 답: "레이어 캐싱 때문에 빌드가 빨라져서 이미지도 작아지는 거 아니에요?"
- 교정: 레이어 캐싱(Day31에서 그림으로 확인)은 **빌드 속도**를 개선하는 장치고, 멀티스테이지 빌드는 **최종 이미지 크기**를 줄이는 별개의 장치다. 우리 `Dockerfile`은 1단계(`eclipse-temurin:17-jdk`)에서 `gradlew`로 빌드하고, 2단계(`eclipse-temurin:17-jre`)로 jar 파일만 복사한다 — 1단계에만 있던 JDK·Gradle 캐시·소스 코드는 최종 이미지에 전혀 남지 않는다.
- 차이: "가벼워진다"는 결과는 맞게 기억했지만, 그 원인을 "속도 최적화"와 "용량 최적화" 중 엉뚱한 쪽에 붙이고 있었다.

## 검증 근거

- `src/test/java/com/example/studyroom/config/JwtAuthenticationFilterTest.java`, `src/test/java/com/example/studyroom/controller/AuthControllerHttpTest.java` (JWT stateless·401/403 관련 기존 테스트)
- `src/test/java/com/example/studyroom/controller/*WebMvcTest.java`, `*DataJpaTest.java` (Day26 슬라이스 테스트)
- `app/Dockerfile`, `app/study_docs/days/WeekE/Day31_1015/velog_post.md`(레이어 구조 그림)

## [직접 작성] 오늘 배운 것을 내 문장으로

<!-- 아래는 학습자가 직접 채운다. 비워두지 말 것. -->

- stateless를 만료시간이 아니라 "세션 저장 여부"로 다시 설명하면:
- `@WebMvcTest`가 로드하는 것과 로드하지 않는 것을 구분하면:
- 레이어 캐싱과 멀티스테이지 빌드가 각각 줄이는 대상(속도 vs 크기):

## 다음 시작점

Week E D7(Day35) — 버퍼 / 졸업판정. 위 세 오답을 재시험하고, 5주 전체 기술부채를 최종 정리한다.
