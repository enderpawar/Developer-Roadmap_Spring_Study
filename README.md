# StudyRoom API — Spring 백엔드 기본기 학습 기록

빈 Spring Boot 프로젝트에서 출발해 웹 요청 처리부터 데이터 접근, 트랜잭션, 인증, 테스트와 운영까지 단계적으로 구현하는 학습 저장소입니다.

완성된 결과만 모으기보다 **예측 → 구현·실행 → 결과 비교 → 오답 교정** 과정을 코드와 문서로 함께 남기고 있습니다. 이미 아는 CS 개념을 Spring 동작 원리와 연결하고, 학습한 내용은 +2일·+7일·+14일 간격으로 다시 인출합니다.

## 현재 진행 상황

현재 코드와 학습 기록으로 검증된 범위는 **Week B Day 10 — Entity 매핑과 Spring Data JPA 기본 CRUD**까지입니다. 다음 학습은 **Day 11 — 영속성 컨텍스트·1차 캐시·동일성**입니다.

| 단계 | 학습 주제 | 구현·학습 증거 | 상태 |
|---|---|---|---|
| Week A · Day 1~7 | HTTP, DTO·Domain, 검증·오류, 계층 분리, IoC·DI | [Week A 기록](app/study_docs/days/WeekA/) · [주차 마무리 글](app/study_docs/velog/week-a-identity-storage-error-boundary.md) | 완료 |
| Day 8 | Flyway 스키마 버전 관리 | [기술 글](app/study_docs/days/WeekB/Day08_0801/velog_post.md) · [실험 기록](app/study_docs/days/WeekB/Day08_0801/explain-log.md) | 완료 |
| Day 9 | JDBC 직접 구현과 저장 계약 | [기술 글](app/study_docs/days/WeekB/Day09_0802/velog_post.md) · [실험 기록](app/study_docs/days/WeekB/Day09_0802/explain-log.md) | 완료 |
| Day 10 | Entity 매핑과 Spring Data JPA CRUD | [기술 글](app/study_docs/days/WeekB/Day10_0807/velog_post.md) · [검증 기록](app/study_docs/days/WeekB/Day10_0807/progress.md) | 완료 |
| Day 11 | 영속성 컨텍스트·1차 캐시·동일성 | [5주 로드맵](app/study_docs/FUNDAMENTALS_ROADMAP.md) | 다음 학습 |

Day 10까지 `ReservationRepository` 경계는 유지하면서 저장 구현을 InMemory → JDBC → Spring Data JPA 어댑터로 교체했습니다. Flyway가 스키마를 관리하고 Hibernate는 Entity 매핑을 통해 CRUD SQL을 실행합니다. JPA 통합 테스트는 신규 저장·조회와 기존 ID 갱신·중복 방지를 검증합니다.

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

코드 작성 형태를 복습할 때는 [패턴 드릴](app/study_docs/PATTERN_DRILLS.md)을 먼저 풀고, 막히거나 틀린 뒤에 [코드 패턴 참조서](app/study_docs/CODE_PATTERNS.md)와 실제 소스를 대조합니다. 현재 두 파일에는 Day 10까지 P1~P17과 D1~D17이 기록되어 있으며, 여러 계층의 관계와 실행 순서는 UML 스타일 Mermaid 도식으로 확인할 수 있습니다.

## 현재 코드에서 확인할 수 있는 것

- `ReservationController`: HTTP 요청과 응답 처리
- `ReservationService`: 예약 생성·취소 규칙 조정
- `ReservationRepository`: 저장소 계약을 인터페이스로 분리
- `JpaReservationRepository`: 기존 저장소 계약을 Spring Data JPA에 연결하는 어댑터
- `SpringDataReservationRepository`: 런타임 Repository 구현 생성
- `ReservationRequest`: `record` DTO와 Bean Validation
- `GlobalExceptionHandler`: 검증 오류의 공통 응답 처리

현재는 Spring Web·Validation·JDBC·Flyway·Spring Data JPA·H2를 사용합니다. Spring Security와 JWT는 Week D에서 추가합니다. 최종 기술 목록을 미리 넣어 완성된 것처럼 보이지 않도록 **현재 구현과 계획을 구분**했습니다.

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

학습일이 끝나면 코드, 퀴즈, 설명 로그와 복습큐를 함께 커밋해 구현 결과와 이해 과정을 같은 시점의 기록으로 남깁니다.
