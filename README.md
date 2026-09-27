# StudyRoom API — Spring 백엔드 기본기 학습 기록

빈 Spring Boot 프로젝트에서 출발해 웹 요청 처리부터 데이터 접근, 트랜잭션, 인증, 테스트와 운영까지 단계적으로 구현하는 학습 저장소입니다.

완성된 결과만 모으기보다 **예측 → 구현·실행 → 결과 비교 → 오답 교정** 과정을 코드와 문서로 함께 남기고 있습니다. 이미 아는 CS 개념을 Spring 동작 원리와 연결하고, 학습한 내용은 +2일·+7일·+14일 간격으로 다시 인출합니다.

## 현재 진행 상황

**5주 트랙(Week A~E, Day01~Day35)이 전 구간 완료됐습니다(2026-10-21).** 다음 단계는 새 Day 진행이 아니라 **종료 후 복습(주 2회 20분)**이며, 졸업 루브릭 12개 중 10개는 통과, 2개(JWT stateless·로그아웃 한계 / Unit·Slice·Integration 구분)는 재시험 통과 후 +7 재확인 전이라 보완 상태로 남아 있습니다.

| 단계 | 학습 주제 | 구현·학습 증거 | 상태 |
|---|---|---|---|
| Week A · Day 1~7 | HTTP, DTO·Domain, 검증·오류, 계층 분리, IoC·DI | [Week A 기록](app/study_docs/days/WeekA/) · [주차 마무리 글](app/study_docs/velog/week-a-identity-storage-error-boundary.md) | 완료 |
| Week B · Day 8~14 | Flyway, JDBC, JPA, 영속성 컨텍스트, 변경 감지 | [Week B 기록](app/study_docs/days/WeekB/) · [주차 마무리 글](app/study_docs/velog/week-b-persistence-context-and-dirty-checking.md) | 완료 |
| Week C · Day 15~21 | 트랜잭션 경계·전파, Spring AOP 프록시, Hibernate LAZY, N+1/fetch join | [Week C 기록](app/study_docs/days/WeekC/) · [주차 마무리 글](app/study_docs/velog/week-c-transaction-proxy-and-fetch-strategy.md) | 완료 |
| Week D · Day 22~28 | BCrypt, JWT, SecurityFilterChain, 테스트 슬라이스, 오류 응답 일관성 | [Week D 기록](app/study_docs/days/WeekD/) · [주차 마무리 글](app/study_docs/velog/week-d-authentication-and-test-strategy.md) | 완료 |
| Week E · Day 29~35 | 로깅·설정관리, 디버깅, Docker/Compose, CI, 누적 독립과제, 최종 시험·졸업판정 | [Week E 기록](app/study_docs/days/WeekE/) · [주차 마무리 글](app/study_docs/velog/week-e-operations-and-graduation.md) | 완료 |

`ReservationRepository` 경계는 유지하면서 저장 구현을 InMemory → JDBC → Spring Data JPA 어댑터로 교체했고, 이후 트랜잭션·인증·테스트·운영 계층을 차례로 쌓았습니다. Flyway가 스키마를, Hibernate가 Entity 매핑을 통한 CRUD SQL을, Spring Security+JWT가 인증을 담당합니다. 상세 완료 근거는 [5주 로드맵](app/study_docs/FUNDAMENTALS_ROADMAP.md)에 있습니다.

## 학습 방식

매일 어려운 개념 하나를 다음 순서로 학습합니다.

1. 동작하는 완성 예제를 읽고 실행합니다.
2. 핵심 부분만 비운 예제를 완성합니다.
3. 요구사항을 조금 바꾼 기능을 독립적으로 구현합니다.
4. 노트와 코드를 덮고 원리를 설명합니다.
5. 실행 결과를 먼저 예측한 뒤 실제 결과와 차이를 기록합니다.
6. 오답을 교정하고 [복습큐](app/study_docs/복습큐.md)에 +2일·+7일·+14일 재시험을 등록합니다.

각 학습일에는 다음 네 종류의 증거를 남깁니다.

- `vocab.md`: 처음 만난 용어를 CS 지식과 연결한 설명
- `quiz.md`: 노트 없이 답하는 인출 문제와 실제 답변
- `explain-log.md`: 예측, 실행 결과, 예상과 달랐던 이유
- `velog_post.md`: 먼저 핵심 이론을 독립적으로 복습할 수 있게 설명하고, 이어서 설계 판단·검증·실수·한계를 정리한 기술 회고. 모든 글은 [기술 블로그 템플릿](app/study_docs/VELOg_POST_TEMPLATE.md)의 사실 검증 및 품질 기준을 따른다

코드 작성 형태를 복습할 때는 [패턴 드릴](app/study_docs/PATTERN_DRILLS.md)을 먼저 풀고, 막히거나 틀린 뒤에 [코드 패턴 참조서](app/study_docs/CODE_PATTERNS.md)와 실제 소스를 대조합니다. 현재 두 파일에는 Week A~E 전체 범위인 P1~P38과 D1~D38이 기록되어 있으며, 여러 계층의 관계와 실행 순서는 UML 스타일 Mermaid 도식으로 확인할 수 있습니다.

## 현재 코드에서 확인할 수 있는 것

- `ReservationController`: HTTP 요청과 응답 처리
- `ReservationService`: 예약 생성·취소 규칙 조정
- `ReservationRepository`: 저장소 계약을 인터페이스로 분리
- `JpaReservationRepository`: 기존 저장소 계약을 Spring Data JPA에 연결하는 어댑터
- `SpringDataReservationRepository`: 런타임 Repository 구현 생성
- `ReservationRequest`: `record` DTO와 Bean Validation
- `GlobalExceptionHandler`: 검증 오류·인증 오류 공통 응답 처리(`code`·`timestamp` 필드 포함)
- `SecurityConfig`·`JwtAuthenticationFilter`·`JwtProvider`: SecurityFilterChain과 JWT 발급·검증
- `RequestIdFilter`: MDC 기반 요청 상관관계 추적(`X-Request-Id`)

Spring Web·Validation·JDBC·Flyway·Spring Data JPA·H2에 더해 Spring Security(JWT)·로깅(MDC)까지 5주 트랙 전 범위가 구현돼 있습니다. Docker(멀티스테이지 `Dockerfile` + `compose.yaml`)와 GitHub Actions CI(테스트+이미지 빌드+헬스체크)로 배포 가능한 상태까지 확인했습니다.

## 학습 로드맵

| 주차 | 핵심 범위 |
|---|---|
| Week A | HTTP, DTO·Domain 분리, 검증·오류 처리, 계층 분리, IoC·DI |
| Week B | SQL, Flyway, JDBC, JPA, 영속성 컨텍스트와 변경 감지 |
| Week C | 트랜잭션, Spring AOP 프록시, 지연 로딩, N+1 |
| Week D | BCrypt, JWT, Security Filter Chain, 테스트 분류 |
| Week E | 로깅, 디버깅, Docker, CI, 누적 독립 과제 |

상세 일정과 완료 기준은 [백엔드 기본기 로드맵](app/study_docs/FUNDAMENTALS_ROADMAP.md)에 정리했습니다.

## 저장소 구조

```text
Developer-Roadmap_Spring_Study/
├─ app/                         # 현재 직접 구현하는 Spring Boot 프로젝트
│  ├─ src/                      # 애플리케이션 코드와 테스트
│  └─ study_docs/
│     ├─ days/                  # 날짜별 단어장·퀴즈·설명 로그·회고
│     ├─ FUNDAMENTALS_ROADMAP.md
│     ├─ CODE_PATTERNS.md       # 검증된 코드 골격·판단·실제 오류 참조서
│     ├─ PATTERN_DRILLS.md      # 정답 없는 빈칸·판정·독립 드릴
│     ├─ spring-core-notes.md
│     ├─ interview-notes.md
│     └─ 복습큐.md
├─ archive/                     # 이전 코드 동결본, 읽기 전용 참고
├─ week_review/                 # 이전 주차 복습 자료
└─ past_docs/                   # 폐기된 과거 계획 보관
```

`archive/`의 완성 코드는 정답을 복사하는 용도로 사용하지 않습니다. 현재 실습은 모두 `app/`에서 진행하며, 막힌 원인을 충분히 좁힌 뒤 필요한 부분만 참고합니다.

## 실행 및 검증

요구 사항은 Java 17입니다.

```bash
cd app
./gradlew test
./gradlew bootRun
```

컨테이너로 띄우려면 저장소 루트에서 `docker compose up -d --wait` 실행 후 `curl http://localhost:8080/health`로 확인합니다(`Dockerfile`은 멀티스테이지, `compose.yaml`은 app+MySQL 구성). GitHub Actions(`.github/workflows/ci.yml`)가 push·PR마다 테스트와 Docker 이미지 빌드·헬스체크를 자동 실행합니다.

학습일이 끝나면 코드, 퀴즈, 설명 로그와 복습큐를 함께 커밋해 구현 결과와 이해 과정을 같은 시점의 기록으로 남깁니다.
