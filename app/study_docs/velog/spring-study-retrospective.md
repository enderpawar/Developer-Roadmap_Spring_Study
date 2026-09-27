# Spring Study를 마치며 — 5주 백엔드 기본기 트랙 회고와 다음 목표

7월 말에 "Spring을 쓸 줄 아는 사람"이 아니라 "Spring이 무엇을 대신해 주는지 설명할 수 있는 사람"이 되겠다는 목표로 시작한 Spring Study 5주 트랙을 마쳤다. 이 글은 시리즈 전체를 정리하는 마지막 글이다. 짧은 기간에 글이 한꺼번에 올라온 이유, 5주 동안 실제로 남은 것과 아직 부족한 것, 3학년 2학기와 그 이후의 계획을 함께 적어 둔다.

> 요청 하나가 Controller에 들어와 DB에 반영되기까지의 경로를 Week A~E에 걸쳐 한 층씩 열어 봤다. 순수 JDBC에서 시작해 JPA, 트랜잭션과 프록시, 인증과 테스트, 로깅·Docker·CI까지 같은 스터디룸 예약 API 하나를 계속 키워 가며 확인했다. 자동 테스트는 71개가 통과하고, GitHub Actions에서 실제 MySQL 컨테이너에 올려 헬스 체크까지 확인했다. 다만 인증·테스트 전략 일부는 아직 한 번 맞힌 수준이라 계속 복습하고 있다.

## 1. 글이 한꺼번에 올라온 배경

### 1) GDGoC 백엔드 코어멤버와 강의자료

이번 학기에 GDGoC 백엔드 파트 코어멤버를 맡게 됐다. 코어멤버의 역할은 내가 아는 것을 말로 설명하는 데서 끝나지 않는다. 매 세션마다 멤버들이 다시 찾아볼 수 있고, 직접 따라 치며 같은 오류를 만나 볼 수 있는 자료가 필요하다.

혼자 공부하며 써 둔 기록은 나만 알아볼 수 있는 메모에 가까웠다. 그래서 세션 자료로 쓸 수 있게 Day별 글을 같은 형식으로 다시 정리했다.

- 개념 설명 → 코드 → 스스로 답한 질문 → 다음 범위
- 실제로 냈던 오류 메시지는 원문 그대로 남겼다.
- 흐름이 있는 개념에는 공식 문서 그림이나 직접 그린 다이어그램을 붙였다.

정리가 끝난 글을 순서대로 공개하다 보니 짧은 기간에 여러 편이 올라가게 됐다. 게시 시점은 몰려 있지만 학습 순서는 Day 번호 그대로다. 코드는 커밋 이력으로 저장소에 남아 있다.

### 2) 방학 중 공모전과 추석 연휴

여름방학에는 관광공사 공모전 프로젝트와 이 스터디를 병행했다. 공모전 마감이 가까워지면서 스터디가 Week C 초반에서 멈췄고, 트랜잭션·프록시 이후의 Week D~E는 계획한 날짜를 지키지 못했다.

남은 부분은 추석 연휴 나흘 동안 몰아서 진행했다. 인증, 테스트 분류, 로깅, Docker, CI를 짧은 기간에 한꺼번에 다룬 셈이다. 코드를 직접 돌리고 테스트를 통과시키는 데까지는 했지만, 모든 개념을 내 말로 막힘없이 설명할 수 있는 수준까지 다졌다고 말하기는 어렵다. 그래서 이 글은 "다 끝냈다"는 선언이 아니라 **어디까지 됐고 어디가 비어 있는지**를 나누어 적는 기록이다.

## 2. 5주 트랙의 구성과 학습 방식

### 1) 주차별 범위

| 주차 | 범위 | 핵심 질문 |
|---|---|---|
| Week A | 웹 계층 | 요청 하나가 Controller·Service·Repository를 어떻게 지나가는가 |
| Week B | 데이터 접근 | JDBC 대비 JPA가 무엇을 추상화하고, `save()` 없이 UPDATE가 나가는 원리는 무엇인가 |
| Week C | 트랜잭션·프록시·성능 | `@Transactional`은 어떻게 동작하고, N+1은 숫자로 어떻게 줄였는가 |
| Week D | 인증·테스트 | JWT는 왜 stateless이고, 401과 403은 어디서 갈리는가 |
| Week E | 운영·디버깅·통합 | 로그로 버그를 어떻게 추적하고, 로컬에서 통과한 코드가 실제 DB에서도 동작하는가 |

### 2) 학습 방식

유료 강의를 처음부터 끝까지 따라가는 대신, 김영한 강사의 Spring 강의 로드맵과 [Developer Roadmap](https://roadmap.sh/backend)의 백엔드 경로를 커리큘럼의 기준선으로 삼았다. "기반 → 추상화" 순서를 5주 분량으로 압축해 직접 짠 트랙이다. 매일 같은 순서로 진행했다.

- 개념과 완성 예제를 먼저 보고, 빈칸을 채우고, 조건을 바꿔 혼자 다시 짠다.
- 실행하기 전에 결과를 먼저 예측하고, 실행한 뒤 예측과 다른 부분을 설명한다.
- 노트를 덮고 다시 답하고, +2일·+7일·+14일에 같은 질문을 다시 푼다.
- 틀린 답은 지우지 않고 교정 과정과 함께 남긴다.

마지막 항목이 이 시리즈의 성격을 가장 잘 보여 준다. 글마다 "처음에는 이렇게 답했고, 실제로는 이렇게 동작했다"는 기록이 들어 있다. 멤버들에게 가장 도움이 됐던 부분도 정답보다 이 오답 기록이었다.

## 3. 5주 동안 남은 것

### 1) 숫자로 확인한 결과

- 스터디룸 예약 API 하나를 35일 동안 계속 확장했다.
- 자동 테스트 71개가 통과한다. 단위·슬라이스·통합 테스트를 구분해 두었다.
- N+1은 목록 조회 쿼리를 4번에서 fetch join 1번으로 줄였고, Hibernate Statistics로 쿼리 수를 테스트에 고정했다.
- GitHub Actions에서 테스트 통과 후 Docker 이미지를 빌드하고, MySQL과 함께 띄워 헬스 체크까지 자동으로 확인한다.
- 매주 끝낸 코드는 `CODE_PATTERNS.md`에 패턴 38개로 정리했고, 다른 도메인으로 바꾼 빈칸 드릴로 다시 풀었다.

### 2) 가장 크게 배운 오류

가장 기억에 남는 오류는 마지막 주 CI에서 나왔다. Week B부터 수십 번 통과하던 첫 번째 마이그레이션 파일이 실제 MySQL 컨테이너에서 처음 실행되자 `Error Code : 1064`로 실패했다. 원인은 SQL 주석 `--` 뒤에 공백이 없었던 것이었다. 로컬 테스트에 쓰던 H2의 MySQL 호환 모드는 이 형식을 통과시켜 주고 있었다.

"테스트가 계속 초록불이었다"는 사실이 "실제 환경에서도 동작한다"는 뜻은 아니라는 걸 이 한 글자로 배웠다. 호환 모드는 실제 DB가 아니고, 한 번은 실제 환경에서 돌려 봐야 한다. 이 경험 때문에 다음 목표에 Testcontainers를 넣었다.

### 3) 아직 부족한 부분

졸업 기준으로 정한 12개 질문 중 10개는 노트 없이 답할 수 있었다. 나머지 2개는 한 번 틀린 뒤 재시험에서 맞혔지만, 아직 간격을 두고 다시 확인하지 못해 "보완"으로 남겨 두었다.

- **JWT의 stateless 특성과 로그아웃 무효화의 한계**: 서버가 상태를 저장하지 않는다는 정의와, 그 결과로 만료시간 외에는 토큰을 무효화할 수단이 없다는 점을 순서대로 설명하는 연습이 더 필요하다.
- **Unit·Slice·Integration 테스트의 구분**: `@WebMvcTest`가 무엇을 로드하지 않는지, 그래서 무엇을 mock해야 하는지를 아직 즉답하지 못한다.

코드에도 알고 남겨 둔 한계가 있다.

- 겹치는 예약이 동시에 들어오는 경쟁 상황을 DB 차원에서 막지 않는다.
- 만료 토큰 테스트가 `sleep`에 기대고 있다.
- 로그아웃 시 토큰을 즉시 무효화하지 못한다.

전부 기술부채 목록에 분류해 두었고, 다음 목표가 이 목록에서 나온다.

## 4. 3학년 2학기 목표와 계획

### 1) 부족한 개념 보강

5주 트랙은 전 범위를 한 번 훑어 멘탈 모델을 만드는 1회독이었다. 이제 2회독을 한다. 이번에는 트랙을 짤 때 기준으로 삼았던 김영한 강사의 Spring 강의(스프링 핵심 원리, JPA 활용)와 Developer Roadmap의 백엔드 경로를 직접 따라가며, 이번에 "보완"으로 남긴 인증·테스트 전략부터 다시 채운다. 주 2회 복습 인출도 이어 간다.

### 2) 기술부채를 다음 과제로

이번 트랙에서 남긴 한계를 그대로 다음 학습 과제로 삼는다.

| 남은 한계 | 다음 과제 |
|---|---|
| H2 호환 모드에서만 테스트 | Testcontainers로 실제 MySQL 통합 테스트 |
| 동시 예약 경쟁 미대응 | 락과 DB 제약으로 동시성 제어, 동시 요청 테스트 |
| JWT 로그아웃 무효화 불가 | Refresh Token과 Redis 기반 토큰 관리 |
| CI까지만 자동화 | 실제 배포까지 이어지는 CD 파이프라인 |

### 3) GDGoC와 공모전 프로젝트

GDGoC에서는 이 시리즈를 바탕으로 세션을 진행한다. 설명하다 막히는 지점이 곧 내가 덜 이해한 지점이라, 가르치는 과정 자체를 2회독의 일부로 삼으려 한다. 공모전 프로젝트에서는 이번에 배운 계층 분리, 트랜잭션 경계, 인증, 테스트를 실제 서비스 코드에 적용해 "혼자 연습한 코드"를 "여러 사람이 쓰는 코드"로 옮겨 보는 것이 목표다.

## 5. 마무리

5주 전에는 `@Transactional`이 붙어 있으면 그냥 되는 줄 알았다. 지금은 그 뒤에서 프록시가 무엇을 가로채고, 같은 클래스 안에서 호출하면 왜 적용되지 않는지 테스트로 보여 줄 수 있다. 반대로 JWT와 테스트 전략처럼 아직 한 번 맞힌 수준인 부분도 분명히 있다. 둘 다 숨기지 않고 기록해 두는 것이 이 시리즈를 쓴 이유다.

모르는 것을 모른다고 적고, 확인한 것은 테스트와 로그로 남기는 습관을 계속 이어 가겠다. 다음 목표는 이 코드를 실제 사용자가 쓰는 서비스까지 가져가는 것이다.

---

### Spring Study 시리즈 전체 목록

**Week A — 웹 계층**
- [Day 1. Request-Response의 왕복](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekA/Day01_0725/velog_post.md)
- [Day 2. 요청 데이터와 Domain Model의 분리](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekA/Day02_0726/velog_post.md)
- [Day 3. Input Validation과 Global Exception Handling](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekA/Day03_0728/velog_post.md)
- [Day 4. Service/Repository Separation of Concerns](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekA/Day04_0728/velog_post.md)
- [Day 5. IoC와 DI](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekA/Day05_0729/velog_post.md)
- [Day 6 & 7. 1주차 마무리 시험](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/velog/week-a-identity-storage-error-boundary.md)

**Week B — 데이터 접근**
- [Day 8. Schema Migration](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekB/Day08_0801/velog_post.md)
- [Day 9. Relational Data Access 계층 — JDBC](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekB/Day09_0802/velog_post.md)
- [Day 10. ORM — Entity 매핑과 Spring Data JPA](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekB/Day10_0807/velog_post.md)
- [Day 11. Persistence Context](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekB/Day11_0822/velog_post.md)
- [Day 12. Dirty Checking](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekB/Day12_0822/velog_post.md)
- [Day 13. 2주차 누적시험](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekB/Day13_0822/velog_post.md)
- [Day 14. ddl-auto validate와 CHECK Constraint](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekB/Day14_0822/velog_post.md)

**Week C — 트랜잭션·프록시·성능**
- [Day 15. Transaction Boundary](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekC/Day15_0920/velog_post.md)
- [Day 16. Spring AOP Proxy와 self-invocation](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekC/Day16_0920/velog_post.md)
- [Day 17. Transaction Propagation](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekC/Day17_0925/velog_post.md)
- [Day 18. Association Mapping과 Lazy Loading](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekC/Day18_0925/velog_post.md)
- [Day 19. N+1 Problem과 Fetch Join](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekC/Day19_0926/velog_post.md)
- [Day 20 & 21. 3주차 마무리 시험](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/velog/week-c-transaction-proxy-and-fetch-strategy.md)

**Week D — 인증·테스트**
- [Day 22. BCrypt 비밀번호 저장](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekD/Day22_1001/velog_post.md)
- [Day 23. JWT 발급·검증](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekD/Day23_1002/velog_post.md)
- [Day 24. SecurityFilterChain과 401/403](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekD/Day24_1004/velog_post.md)
- [Day 25. JWT 인증 실패 케이스와 CSRF·CORS](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekD/Day25_1005/velog_post.md)
- [Day 26. 테스트 범위와 Mock 전략](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekD/Day26_1007/velog_post.md)
- [Day 27 & 28. 4주차 마무리 시험](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/velog/week-d-authentication-and-test-strategy.md)

**Week E — 운영·디버깅·통합**
- [Day 29. Request ID 로깅과 설정 분리](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekE/Day29_1012/velog_post.md)
- [Day 30. 버그 재현과 로그 기반 가설 검증](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekE/Day30_1013/velog_post.md)
- [Day 31. 멀티스테이지 Dockerfile과 Compose](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekE/Day31_1015/velog_post.md)
- [Day 32. GitHub Actions CI와 MySQL 1064 오류](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekE/Day32_1016/velog_post.md)
- [Day 33. 예약 시간대 중복 방지](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/days/WeekE/Day33_1018/velog_post.md)
- [Day 34 & 35. 5주차 마무리 시험](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/blob/master/app/study_docs/velog/week-e-operations-and-graduation.md)

전체 소스코드와 학습 기록: [Developer-Roadmap_Spring_Study](https://github.com/enderpawar/Developer-Roadmap_Spring_Study)
