# Spring Study를 마치며 — 5주 백엔드 기본기 트랙 회고와 다음 목표

Spring Study 5주 트랙이 드디어 끝났다.

7월 말에 시작하면서 세운 목표는 하나였다. Spring을 "쓸 줄 아는" 걸 넘어서, Spring이 뒤에서 뭘 대신 해 주는지 **설명할 수 있는** 사람이 되자는 것.

이번 글은 시리즈 마무리 겸 회고다. 최근에 글이 갑자기 우르르 올라온 이유, 5주 동안 남은 것과 아직 부족한 것, 앞으로 뭘 할지까지 가볍게 정리해 봤다.

## 1. 글이 한꺼번에 올라온 이유

### 1) GDGoC 백엔드 코어멤버

이번 학기에 GDGoC 백엔드 파트 코어멤버를 맡게 됐다. 막상 맡고 보니 아는 걸 말로 설명하는 것만으로는 부족했다. 세션이 끝나고도 멤버들이 다시 찾아볼 수 있고, 직접 따라 치다가 나랑 똑같은 오류를 만나 볼 수 있는 자료가 필요했다.

문제는 혼자 공부하면서 남긴 기록이 거의 나만 알아보는 메모였다는 것. 그래서 세션 자료로 쓸 수 있게 Day별 글을 같은 형식으로 싹 다시 정리했다.

- 개념 → 코드 → 스스로 답한 질문 → 다음 범위 순서로 맞췄고
- 실제로 냈던 오류 메시지는 손대지 않고 원문 그대로 남겼고
- 흐름이 있는 개념에는 공식 문서 그림이나 직접 그린 다이어그램을 붙였다

정리된 글부터 순서대로 올리다 보니 짧은 기간에 몰려서 올라가게 됐다. 올린 시점만 몰렸을 뿐 공부한 순서는 Day 번호 그대로고, 코드는 커밋 이력으로 저장소에 다 남아 있다.

### 2) 공모전과 추석 연휴

여름방학에는 관광공사 공모전 프로젝트와 이 스터디를 같이 했다. 공모전 마감이 다가오면서 스터디는 Week C 초반에서 멈췄고, 그 뒤의 Week D~E는 계획한 날짜를 못 지켰다.

그래서 남은 부분을 추석 연휴 나흘 동안 몰아서 했다. 인증, 테스트 분류, 로깅, Docker, CI를 며칠 만에 한꺼번에 본 셈이다. 코드를 직접 돌리고 테스트를 통과시키는 데까지는 했지만, 솔직히 전부 막힘없이 설명할 수 있냐고 하면 아직은 아니다.

그래서 이 글은 "다 끝냈다"는 자랑보다는 **여기까진 됐고 여긴 아직 비어 있다**를 적어 두는 기록에 가깝다.

## 2. 5주 동안 한 것

### 1) 주차별 내용

스터디룸 예약 API 하나를 5주 내내 계속 키워 가면서, 요청 하나가 DB까지 가는 길을 한 층씩 열어 봤다.

- **Week A (웹 계층)**: 요청이 Controller → Service → Repository를 어떻게 지나가는지
- **Week B (데이터 접근)**: JDBC를 직접 짜 보고 JPA가 뭘 대신 해 주는지 확인. `save()`를 안 불렀는데 UPDATE가 나가는 이유
- **Week C (트랜잭션·프록시)**: `@Transactional`이 실제로 어떻게 도는지, N+1을 숫자로 줄여 보기
- **Week D (인증·테스트)**: BCrypt, JWT, 401과 403이 갈리는 지점, 테스트 종류 나누기
- **Week E (운영·통합)**: 로그로 버그 추적, Docker, CI, 그리고 처음부터 혼자 기능 하나 만들기

### 2) 공부 방식

커리큘럼은 김영한 강사님의 Spring 강의 로드맵과 [Developer Roadmap](https://roadmap.sh/backend)의 백엔드 경로를 기준으로 잡고, "기반 → 추상화" 순서를 5주 분량으로 줄여서 직접 짰다. 매일 비슷한 흐름으로 진행했다.

- 완성 예제 보기 → 빈칸 채우기 → 조건을 살짝 바꿔서 혼자 다시 짜기
- 돌리기 전에 결과를 먼저 예측하고, 돌려 본 뒤 뭐가 달랐는지 설명하기
- 노트 덮고 다시 답하기, 2일·7일·14일 뒤에 같은 질문 또 풀기
- 틀린 답은 지우지 않고 고친 과정까지 남기기

마지막 방식이 이 시리즈의 핵심이다. 글마다 "처음엔 이렇게 생각했는데 실제로는 이렇게 돌더라"가 들어 있다. 멤버들 반응도 정답보다 이 오답 기록이 제일 좋았다.

## 3. 남은 것과 부족한 것

### 1) 숫자로 보면

- 예약 API 하나를 35일 동안 계속 확장했다
- 자동 테스트 71개가 전부 통과한다
- N+1은 목록 조회 쿼리를 4번에서 fetch join 1번으로 줄였고, 쿼리 수를 테스트로 고정해 뒀다
- GitHub Actions에서 테스트가 통과하면 Docker 이미지를 빌드하고, MySQL과 같이 띄워서 헬스 체크까지 자동으로 확인한다
- 매주 짠 코드는 패턴 38개로 정리해 두고, 도메인을 바꾼 빈칸 문제로 다시 풀어 봤다

### 2) 제일 기억에 남는 오류

마지막 주 CI에서 터진 오류다. Week B부터 수십 번 멀쩡히 돌던 첫 번째 마이그레이션 파일이, 실제 MySQL 컨테이너에서 처음 실행되자마자 `Error Code : 1064`로 실패했다.

원인은 SQL 주석 `--` 뒤에 공백 한 칸이 없었던 것. 로컬 테스트에서 쓰던 H2의 MySQL 호환 모드는 이걸 그냥 통과시켜 주고 있었다.

테스트가 계속 초록불이었다고 해서 실제 환경에서도 돈다는 보장은 없다는 걸 공백 한 칸으로 제대로 배웠다. 이 일 때문에 다음 목표에 Testcontainers를 넣었다.

### 3) 아직 부족한 부분

졸업 기준으로 정한 질문 12개 중 10개는 노트 없이 답할 수 있었다. 나머지 2개는 한 번 틀리고 재시험에서 맞혔는데, 며칠 두고 다시 확인하지 못해서 "보완"으로 남겨 뒀다.

- **JWT의 stateless와 로그아웃 문제**: 서버가 상태를 안 들고 있다 → 그래서 만료시간 말고는 토큰을 막을 방법이 없다. 이 흐름을 술술 설명하려면 연습이 더 필요하다.
- **Unit · Slice · Integration 테스트 구분**: `@WebMvcTest`가 뭘 안 띄우는지, 그래서 뭘 mock해야 하는지 아직 바로 답이 안 나온다.

코드에도 알면서 남겨 둔 구멍이 있다. 겹치는 예약이 동시에 들어오면 DB 차원에서 못 막고, 만료 토큰 테스트는 `sleep`에 기대고 있고, 로그아웃해도 토큰이 바로 죽지 않는다. 전부 기술부채 목록에 적어 뒀고, 다음 목표가 여기서 나온다.

## 4. 앞으로의 계획

### 1) 2회독

이번 5주는 전체를 한 번 훑어서 머릿속에 지도를 그리는 1회독이었다. 이제 2회독을 한다. 트랙을 짤 때 기준으로 삼았던 김영한 강사님의 Spring 강의(스프링 핵심 원리, JPA 활용)와 Developer Roadmap을 직접 따라가면서, "보완"으로 남긴 인증과 테스트 전략부터 다시 채울 생각이다. 주 2회 복습도 계속 이어 간다.

### 2) 남은 구멍을 다음 과제로

이번에 남긴 한계를 그대로 다음 공부 거리로 삼았다.

- H2 호환 모드로만 테스트했으니 → Testcontainers로 진짜 MySQL을 붙여 테스트하기
- 동시 예약을 못 막으니 → 락과 DB 제약으로 동시성 제어, 동시 요청 테스트 짜기
- 로그아웃해도 토큰이 살아 있으니 → Refresh Token과 Redis로 토큰 관리해 보기
- CI까지만 자동화했으니 → 실제 배포까지 이어지는 CD 파이프라인 만들기

### 3) GDGoC 세션과 공모전 프로젝트

GDGoC에서는 이 시리즈로 세션을 진행한다. 설명하다 말이 막히는 곳이 곧 내가 덜 이해한 곳이라, 가르치는 것 자체를 2회독의 일부로 쓰려고 한다. 공모전 프로젝트에서는 이번에 배운 계층 분리, 트랜잭션 경계, 인증, 테스트를 실제 서비스 코드에 넣어서, 혼자 연습한 코드를 여러 사람이 같이 쓰는 코드로 옮겨 보는 게 목표다.

## 5. 마무리

5주 전의 나는 `@Transactional`만 붙이면 알아서 다 되는 줄 알았다. 지금은 그 뒤에서 프록시가 뭘 가로채는지, 같은 클래스 안에서 부르면 왜 안 먹히는지를 테스트로 보여 줄 수 있다. 반대로 JWT나 테스트 전략처럼 아직 "한 번 맞혀 본" 수준인 부분도 분명히 있다.

잘한 것도 부족한 것도 숨기지 않고 적어 두는 게 이 시리즈를 쓴 이유였다. 모르는 건 모른다고 적고, 확인한 건 테스트와 로그로 남기는 습관은 계속 가져가려고 한다. 다음 목표는 이 코드를 진짜 사용자가 쓰는 서비스까지 가져가는 것이다.

끝까지 읽어 줘서 고맙다.

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

전체 소스코드와 학습 기록은 여기 있다: [Developer-Roadmap_Spring_Study](https://github.com/enderpawar/Developer-Roadmap_Spring_Study)
