# [Spring Study Day 34 & Day 35] 5주차 마무리 시험

Week E는 "이미 있는 데이터를 조회·갱신하는가"에서 "그 서비스를 운영할 수 있는가"로 질문이 바뀐 주였다. D1~D4에서 로깅·설정 분리·Docker·CI를 다뤘고, D5(Day33)에서 예약 시간대 중복 방지를 0층부터 만드는 누적 독립과제를 했다. 이 글은 D6(Day34) 전 범위 최종 시험과 D7(Day35) 버퍼·졸업판정을 묶어, 5주 트랙 전체를 인출하고 졸업 루브릭에 반영한 기록이다.

> D6에서는 Week A~E 전체를 14문항으로 인출했다. Week A~C의 9개 항목은 이미 여러 차례 재시험을 거쳐 힌트 없이 통과했고, Week D~E의 신규 내용 중 세 문항(JWT stateless, 테스트 슬라이스 범위, Docker 멀티스테이지 빌드)에서 오답이 나왔다. D7에서는 오답 두 개(JWT·테스트 슬라이스)를 재시험해 힌트 없이 통과했고, 졸업 루브릭 12개 항목을 자가평가해 10개 통과·2개 보완으로 판정했다.

## 1. 시험 범위와 진행 방식

D6(10/20) 출제 범위는 Week A(웹 계층)부터 Week E(운영·디버깅·독립과제)까지 전체다.

- Week A~C: DTO/Domain 분리, DI, JDBC 대비 JPA, 영속성 컨텍스트·변경 감지, 트랜잭션 전파, self-invocation, Hibernate LAZY 프록시, N+1
- Week D: BCrypt, JWT 발급·검증, Security Filter Chain, 테스트 분류(Unit/Slice/Integration)
- Week E: 로깅·설정 분리, Docker, GitHub Actions CI, 구간 겹침 검사(Day33)

노트를 덮고 먼저 답하고, 틀리면 힌트를 받은 뒤 다시 답하는 방식은 Day13·Day20과 같다. "결론은 맞지만 근거가 무너지는" 경우는 이번에도 통과가 아니라 오답으로 기록했다.

## 2. 시험에서 틀린 문제

### 1) JWT stateless의 의미와 만료시간의 관계

> **JWT stateless** = 서버가 요청 사이에 클라이언트의 세션 상태를 저장하지 않고, 토큰 자체에 실린 정보로 매 요청을 독립적으로 검증하는 방식

**최초 답변.** "stateless는 토큰에 만료시간이 있어서 서버가 세션을 안 지워도 된다는 뜻 아니에요?"

**왜 틀렸나.** stateless를 정의하는 성질(서버가 세션 상태를 아예 저장하지 않음)과, 그 성질 때문에 필요해진 부속 안전장치(만료시간)를 뒤바꿔 이해했다. 순서를 바로잡으면 "서버에 지울 세션이 없다 → 그래서 토큰은 유효기간 안에서는 계속 유효하다 → 그래서 만료시간이 사실상 유일한 무효화 수단이다"가 된다.

**교정 기준.** 로그아웃 무효화 문제는 이 정의에서 곧바로 따라 나온다. 서버가 저장하는 상태가 없으므로 "로그아웃"이라는 동작이 서버 쪽에서 지울 대상 자체를 갖지 못한다. 이 구조적 한계는 이번 트랙 기술부채에도 그대로 남겨뒀다(5절 참고).

### 2) 테스트 슬라이스가 실제로 로드하는 범위

**질문.** `@WebMvcTest`는 Controller 테스트에 쓰는데, Service까지 실제 Bean으로 띄워주는가?

**최초 답변.** "Controller 테스트니까 Service까지 다 띄워주는 거 아니에요?"

**왜 틀렸나.** "슬라이스"라는 이름이 정확히 "계층 하나만 잘라 올린다"는 뜻인데, 그 경계를 "가볍다"는 인상으로만 기억하고 있었다. `@WebMvcTest`는 웹 계층 Bean(`@Controller`, `@ControllerAdvice`, 필터 등)만 로드하고, 컨트롤러가 의존하는 `Service`는 `@MockBean`으로 직접 등록하지 않으면 컨텍스트 자체가 뜨지 않는다.

**교정 기준.** "무엇을 mock해야 하는가"는 "그 슬라이스가 무엇을 로드하지 않는가"의 반대편 질문이라는 걸 다시 확인했다.

### 3) 멀티스테이지 빌드가 이미지 크기를 줄이는 원리

**질문.** Docker 멀티스테이지 빌드는 왜 최종 이미지를 작게 만드는가?

**최초 답변.** "레이어 캐싱 때문에 빌드가 빨라져서 이미지도 작아지는 거 아니에요?"

**왜 틀렸나.** 레이어 캐싱(재빌드 속도)과 멀티스테이지 분리(최종 산출물 구성)는 `Dockerfile`의 같은 `FROM ... AS ...` 문법을 공유할 뿐 서로 다른 문제를 푼다. 이 프로젝트의 `Dockerfile`은 1단계(`eclipse-temurin:17-jdk`)에서 `gradlew`로 빌드하고, `COPY --from=build`로 jar 파일 하나만 2단계(`eclipse-temurin:17-jre`)에 옮긴다. 1단계에만 있던 JDK·Gradle 캐시·소스는 최종 이미지에 전혀 남지 않는다.

**교정 기준.** D7 재시험에서도 힌트("최종 이미지에 실제로 남는 건 몇 번째 스테이지인가")가 한 번 더 필요했다 — 세 오답 중 유일하게 완전히 안정되지 않은 항목이다.

아래는 Week E D4(Day32)에서 실제로 겪은 CI 실패 사례다. `docker` 잡을 처음 실행했을 때 `V1__init.sql`의 SQL 주석(`--Java는...`)이 MySQL에서는 주석으로 인식되지 않아(`--` 뒤 공백 필요) 마이그레이션이 1064 오류로 실패했다. GitHub Actions의 잡·스텝 구조를 몰랐다면 "이미지가 뭔가 이상하다"는 막연한 추정에서 멈췄을 텐데, 아래 개념도의 `event → runner → job → step` 계층을 알고 있었기 때문에 실패한 스텝(`docker compose logs`)까지 정확히 좁혀갈 수 있었다.

![하나의 Event가 Runner를 트리거하여 여러 Step으로 구성된 Job을 실행하는 GitHub Actions의 event-runner-job-step 계층 구조를 보여주는 공식 개념도](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day32-web-github-actions-overview.png)

*출처: [Understanding GitHub Actions — GitHub Docs](https://docs.github.com/en/actions/get-started/understand-github-actions) — GitHub, Inc., GitHub Docs 콘텐츠 CC BY 4.0*

## 3. 재시험과 루브릭 반영

보통 이 절은 시험 오답을 코드에 반영하는 "D7 코드 적용"을 다룬다. 이번 주는 순서가 반대였다 — 코드 적용(구간 겹침 검사)이 D5(Day33)에서 시험보다 먼저 끝났고, D7(Day35)은 새 코드 없이 시험 오답을 재시험하고 졸업 루브릭에 반영하는 데 썼다.

| 문항 | D6 결과 | D7 재시험 | 루브릭 반영 |
|---|---|---|---|
| JWT stateless / 로그아웃 무효화 | 오답 | 힌트 없이 통과 | 보완 — 교정 직후 1회 확인이라 +7 스페이싱 재확인 전까지는 "통과"로 올리지 않음 |
| 테스트 슬라이스가 로드하는 범위 | 오답 | 힌트 없이 통과 | 보완 — 같은 이유 |
| Docker 멀티스테이지 빌드 원리 | 오답 | 힌트 후 통과 | 졸업 루브릭 12개 항목에는 포함되지 않는 범위(Week E 운영 실습) — 복습큐에서만 계속 추적 |

`FUNDAMENTALS_ROADMAP.md` §5의 12개 항목을 코드 없이 말로 설명하는 걸 기준으로 자가평가한 결과, **10개 통과·2개 보완**이다. "보완"은 오답이 남았다는 뜻이 아니라, 교정은 됐지만 스페이싱 반복(재발 방지 확인)을 아직 한 번밖에 안 거쳤다는 뜻으로 구분했다 — 결과를 외운 것과 근거가 안정된 것을 같은 걸로 세지 않겠다는 이번 트랙 내내의 원칙을 졸업판정에도 그대로 적용했다.

## 4. 자동 검증 범위

Week E에서 늘어난 자동 검증만 정리한다.

| 확인한 것 | 방법 | 결과 |
|---|---|---|
| 구간 겹침 검사(Day33) | `ReservationOverlapTest`(Unit 6) + `ReservationOverlapHttpTest`(Integration 4) | 신규 10개 포함 전체 71개 통과 |
| Docker 이미지 빌드 + 컨테이너 기동 | GitHub Actions `docker` 잡(`docker compose up` + `/health` curl 재시도) | 1차 실행 실패(SQL 주석 오류) → 수정 후 통과 |
| 요청 추적(requestId) 로깅 | `RequestIdFilterTest`(Unit, Spring 컨테이너 없음) | 통과 |

**미검증 범위**는 숨기지 않는다. Testcontainers 기반 통합 테스트와 실제 서버 배포는 로드맵이 처음부터 스트레치로 분류한 항목이라 하지 않았다. JWT 만료 테스트는 실제 대기(`Thread.sleep`) 기반이라 느리고, 결정적이지 않다. 같은 방·같은 시간대 동시 요청에 대한 DB 레벨 제약(exclusion constraint)도 없어 이론적으로 레이스 컨디션을 막지 못한다. 전체 목록은 `기술부채.md`에 최종 상태로 남겼다.

## 5. 5주차 정리와 트랙 전체 회고

Week E를 시작할 때 남은 질문은 "코드가 로컬에서 도는가"였다. D1~D4를 거치며 요청마다 추적 가능한 로그를 남기고, 실행 환경(local/test/prod)을 분리하고, 컨테이너로 묶어 GitHub Actions에서 직접 기동해보면서, 그 질문이 "이 서비스가 나 없이도 재현 가능하게 도는가"로 바뀌었다. Day32의 SQL 주석 오류처럼, 로컬에서는 한 번도 안 보이던 문제가 다른 DB(MySQL)와 다른 실행 환경(CI)에서만 드러나는 경험은 이 주 전에는 해보지 못한 종류의 디버깅이었다.

5주 전체를 돌아보면, 가장 자주 반복된 패턴은 **결과는 맞히지만 근거가 무너지는 것**이었다. self-invocation은 Day17부터 Day34까지 네 번 다뤘고, 매번 "적용 안 됨"이라는 결론은 맞혔지만 근거를 대라는 질문에 세 번 순환논리로 되돌아간 뒤에야 Day34에서 처음 근거 수준까지 안정됐다. 이번 주의 JWT stateless·테스트 슬라이스 오답도 같은 패턴이었다 — "가볍다", "안전하다" 같은 결과만 기억하고 그 경계선은 흐릿했다. 5주 동안 이 트랙이 반복해서 확인시켜준 건, 결과를 외우는 것과 근거를 인출할 수 있는 것은 다른 능력이라는 것이다.

졸업 루브릭은 12개 중 10개 통과·2개 보완으로 마감한다. Week A~C(요청 흐름, DTO/Domain, DI, JPA·트랜잭션·N+1)는 여러 차례 재시험을 거쳐 안정됐고, Week D~E(JWT·테스트 계층)의 두 항목은 이번 주에 막 교정돼 스페이싱 재확인이 남아 있다. 이 두 항목과 Docker 멀티스테이지 빌드는 복습큐에 그대로 남겨 공모전 프로젝트 중에도 추적한다.

다음 단계는 새 Week가 아니라 **공모전 프로젝트**다. 이 5주는 유창한 독립구현이 아니라 1회독 멘탈모델을 만드는 게 목표였고(로드맵 §1), 그 모델을 실제로 손에 익히는 건 다음 단계의 몫으로 남긴다.

면접에서 다시 답해볼 항목을 남긴다.

- 결과는 맞히지만 근거가 무너지는 패턴을 스스로 감지하고 교정하는 절차
- JWT stateless의 장점이 곧 로그아웃 무효화 문제의 원인이 되는 인과관계

---

오늘 공부한 소스코드: `app/src/main/java/com/example/studyroom/service/ReservationService.java`, `app/Dockerfile`, `.github/workflows/ci.yml`, `app/src/test/java/com/example/studyroom/service/ReservationOverlapTest.java`
