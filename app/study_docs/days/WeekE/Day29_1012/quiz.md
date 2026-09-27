# Day29 (10/12, Week E D1) 인출 기록

## 1. 복습큐 도래분

| 개념 | 결과 |
|---|---|
| REQUIRED의 판단 로직("있으면 합류, 없으면 새로 시작")을 REQUIRES_NEW와 대비 | ✅ |
| N+1 Problem — 목록 조회 1번 뒤 항목마다 SELECT N번 | ✅ |
| fetch join이 매핑(`FetchType.LAZY`)을 바꾸지 않는다는 것 | ✅ (힌트 없이 통과) |

## 2. 세션 예측과 교정 — 로깅/설정

| # | 질문 | 학습자 답 | 판정 | 교정 |
|---|---|---|---|---|
| P1 | `logging.pattern.console`을 `application.yml`에 넣으면 Spring 컨테이너 없이 필터 객체만 직접 호출한 테스트에도 커스텀 패턴이 찍히는가 | "yml에 써놨으니 어디서든 적용될 것" | ❌ | 커스텀 패턴은 `ApplicationContext` 기동 시 `LoggingApplicationListener`가 `Environment`를 읽어야 적용됨. 컨테이너 없이 필터만 직접 부르면 기본 패턴이 찍힘(`RequestIdFilterTest` 실측) |
| P2 | `MDC.remove()`를 `finally`에 안 두면 무슨 문제가 생기는가 | "다음 요청 로그에 이전 requestId가 섞여 나올 수 있다" | ✅ | 정확 — 서블릿 컨테이너의 스레드 재사용이 원인 |
| P3 | `spring.profiles.active: local`을 공통 파일 기본값으로 둬도 테스트 실행에 영향이 없는 이유 | "테스트는 어차피 환경변수로 덮으니까" | ✅ | `build.gradle.kts`의 Test 태스크가 `SPRING_PROFILES_ACTIVE=test`를 강제, OS 환경변수가 yml 기본값보다 우선 |
| P4 | `application-prod.yml`에 `${DB_URL}` 값이 없으면 어떻게 되는가 | "기본값이 없으니까 그냥 null로 뜨지 않을까" | ❌ | null로 조용히 뜨는 게 아니라 placeholder 미해결로 기동 자체가 막힘(fail fast) — "값 없이 조용히 뜨는 것"과 정반대 |
| P5 | Authorization 헤더를 로그에 마스킹 없이 그대로 남기면 안 되는 이유 | "토큰 원문이 그대로 새면 다른 사람이 그 토큰으로 로그인한 것처럼 요청을 보낼 수 있으니까" | ✅ | 정확 |

## 3. 다음 복습 질문

1. `LoggingApplicationListener`가 커스텀 로그 패턴을 적용하는 시점과, 컨테이너 없는 단위 테스트에서 기본 패턴이 찍히는 이유
2. `FilterRegistrationBean`에 `Ordered.HIGHEST_PRECEDENCE`를 준 이유(Spring Security 필터체인과의 순서 관계)
3. placeholder 미해결 예외가 "값 없이 조용히 기동"보다 안전한 이유

## 4. 복습 일정

Day29 완료일 10/12 기준, 오늘 새로 등록한 항목(MDC/requestId, 프로필 분리, placeholder 미해결)은 +2일 10/14에 먼저 인출한다.
