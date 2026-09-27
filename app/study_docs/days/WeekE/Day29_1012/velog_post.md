# [Spring Study Day 29] 요청 로깅과 설정 분리 — Request ID·MDC와 Profile별 Secret 관리

Day28에서 401/403 응답 형식을 통일했다. 오늘은 그 요청·응답 하나가 실제로 어떤 로그 줄들을 남기는지, 그리고 그 로그에 비밀번호나 토큰 원문이 섞이지 않는지를 확인했다. 동시에 `application.yml`에 뒤섞여 있던 local/test/prod 설정을 프로필별 파일로 분리했다. 로그 파일 rotation·중앙 로그 수집이나, profile을 코드에서 프로그래밍적으로 전환하는 방법은 이 글의 범위가 아니다.

> 요청마다 `X-Request-Id`를 MDC에 넣어 그 요청이 지나가는 모든 로그 줄에 자동으로 붙게 만들었고, `Authorization` 헤더는 `Bearer ***`로만 남겨 원문 토큰이 로그에 노출되지 않는 것을 테스트로 확인했다. 동시에 `application.yml`에서 datasource·jwt.secret을 분리해 local/test/prod 세 프로필 파일로 옮겼다. `./gradlew test`는 60/60 통과했다.

> **오늘의 흐름** `요청 도착 → RequestIdFilter가 MDC에 requestId 기록 → 로그 한 줄(Authorization 마스킹) → 요청 처리 전체에 requestId 자동 첨부 → 응답 → MDC 제거`
>
> 이전 Day: 401/403 오류 응답 형식 통일 (Day28)
> 다음 Day: 취소 사유 트리밍 버그의 재현과 수정, 이때 오늘 만든 requestId로 로그 두 줄을 실제로 엮어본다 (Day30)

![Logback 내부 아키텍처 시퀀스 다이어그램. Logger.info() 호출이 LoggerContext(TurboFilter 판정, effective level 체크)를 거쳐 LoggingEvent를 만들고, AppenderAttachableImpl이 등록된 Appender들을 순회(Loop ch appenders)하며 각 Appender.doAppend()가 Layout.doLayout()으로 최종 로그 문자열을 만드는 흐름을 보여준다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day29-web-logback-architecture.gif)

*출처: [Chapter 2: Architecture — Logback](https://logback.qos.ch/manual/architecture.html) — QOS.ch (Logback 프로젝트, Ceki Gülcü)*

## 1. 개념 설명

### 1) 요청 추적을 위한 MDC와 Correlation ID

> **MDC(Mapped Diagnostic Context)** = 스레드 로컬에 key-value를 저장해, 그 스레드가 찍는 모든 로그 줄에 자동으로 붙게 하는 SLF4J의 저장소

우리 코드에서는 `RequestIdFilter`가 요청마다 이 저장소에 `requestId` 하나를 넣는다.

```java
try {
    MDC.put(MDC_KEY, requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);
    log.info("{} {} Authorization={}", request.getMethod(), request.getRequestURI(),
            maskAuthorization(request.getHeader("Authorization")));
    filterChain.doFilter(request, response);
} finally {
    MDC.remove(MDC_KEY);
}
```

동작 순서는 다음과 같다.

```text
요청 도착 → X-Request-Id 헤더 확인
→ 있으면 재사용, 없으면 UUID.randomUUID()로 새로 발급
→ MDC.put("requestId", id) — 이 스레드의 이후 모든 로그에 자동 첨부 시작
→ 응답 헤더에도 같은 id를 echo(클라이언트가 다음 문의 때 같은 id로 추적 가능)
→ filterChain.doFilter() — 컨트롤러·서비스까지 같은 스레드에서 실행되므로 같은 id가 계속 붙음
→ finally: MDC.remove("requestId") — 스레드가 다음 요청에 재사용되기 전에 반드시 지움
```

`finally`가 없으면 어떻게 되는지는 코드가 아니라 서블릿 컨테이너의 특성 때문이다. 컨테이너는 스레드 풀로 요청을 처리하므로 스레드는 재사용된다. `MDC.remove()`를 안 하면 다음 요청이 같은 스레드를 받았을 때 이전 요청의 requestId가 로그에 그대로 남는다 — 실제로 지워지는지는 `clearsMdcAfterRequestEvenWhenChainThrows`에서, 체인 도중 강제로 예외를 던지는 상황까지 만들어 확인했다.

패턴 문자열 `%X{requestId}`가 이 값을 로그 줄에 꺼내 쓰는데, 이게 언제 적용되는지는 3절에서 실제로 예측이 틀렸던 지점이다.

### 2) 필터 실행 순서 보장과 민감정보 마스킹

> **`FilterRegistrationBean`** = 서블릿 컨테이너의 필터 체인에 필터를 등록하며, 순서(`order`)와 적용 URL 패턴을 지정하는 Spring Boot 컴포넌트

```java
@Bean
public FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
    FilterRegistrationBean<RequestIdFilter> registration =
            new FilterRegistrationBean<>(new RequestIdFilter());
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    registration.addUrlPatterns("/*");
    return registration;
}
```

`SecurityConfig.addFilterBefore()`로 넣는 방법도 있었지만, 그건 Spring Security 필터체인 **안에서**의 순서만 정한다. `springSecurityFilterChain` 자체도 서블릿 필터 하나일 뿐이라, 인증 실패(401/403) 응답에도 requestId를 남기려면 그 필터체인보다 먼저 실행돼야 한다. `HIGHEST_PRECEDENCE`로 등록하면 서블릿 컨테이너 최상단에서 실행되어 이 조건을 만족한다.

토큰 원문은 로그에 남으면 안 되는 비밀값이다. `maskAuthorization()`은 헤더 유무와 스킴(Bearer)만 남기고 나머지는 전부 버린다.

```java
static String maskAuthorization(String header) {
    if (header == null || header.isBlank()) {
        return "-";
    }
    return "Bearer ***";
}
```

실제 토큰(`Bearer eyJhbGciOiJIUzI1NiJ9.super-secret-token-body.signature`)을 넣어 호출해도 로그에는 `Authorization=Bearer ***`만 찍혔다(`maskAuthorizationNeverIncludesRawHeaderValue`). 부분 노출(예: 앞 6자리만) 같은 정밀한 마스킹은 하지 않는다 — 스킴이 다르면(`Basic` 등) 구분 없이 같은 문자열이 찍히는 정밀도 손실이 있고, 이건 학습 범위에서 남겨둔 한계다.

### 3) 프로필 분리와 Externalized Configuration

> **Externalized Configuration** = 비밀값·환경별 값을 소스코드가 아니라 외부(환경변수 등)에서 주입받는 설정 방식

우리 코드에서는 기존 `application.yml`에 있던 datasource·jwt.secret을 전부 빼고, 환경별 파일로 나눴다.

```yaml
# application-prod.yml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
jwt:
  secret: ${JWT_SECRET}
```

```text
application.yml(공통, active: local 기본값)
→ local 프로필: application-local.yml의 값 사용(로컬 개발용 실제 값)
→ test 프로필: build.gradle.kts가 SPRING_PROFILES_ACTIVE=test를 환경변수로 강제(공통 파일 기본값보다 우선)
→ prod 프로필: application-prod.yml의 ${DB_URL} 등 — 값이 없으면 기동 자체가 막힘
```

prod 프로필 파일에는 실제 비밀값이 없다. `${DB_URL}`처럼 참조만 있고, 이 참조를 채울 환경변수가 배포 환경에 없으면 Spring Boot는 placeholder를 못 풀어 기동을 멈춘다. 비밀값이 없는 채로 "일단 뜨고 보는" 것보다, 시작 단계에서 바로 실패하는 쪽이 안전하다는 판단이다. 이 컴퓨터는 시스템 환경변수에 `spring.profiles.active=prod`가 이미 설정돼 있어서(로드맵에 기록된 환경 부채), 프로필 없이 `bootRun`을 그냥 실행하면 이 placeholder 오류를 만날 가능성이 있다 — 직접 실행해서 확인하지는 않았다.

### 4) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| MDC | 스레드 로컬에 저장해 로그 줄마다 자동 첨부되는 진단 컨텍스트 |
| Correlation ID | 하나의 요청을 여러 로그 줄에 걸쳐 추적하는 고유 식별자 |
| `OncePerRequestFilter` | 요청당 한 번만 실행을 보장하는 서블릿 필터 베이스 클래스 |
| `FilterRegistrationBean` | 필터 체인에 순서·URL 패턴을 지정해 등록하는 컴포넌트 |
| Spring Profile | 환경별 설정 파일을 분리하고 `active` 값으로 선택하는 메커니즘 |

> **더 볼 것**
> - [Logback Manual — Mapped Diagnostic Context](https://logback.qos.ch/manual/mdc.html): MDC의 스레드 로컬 구현과 주의사항
> - [Spring Boot Reference — Profiles](https://docs.spring.io/spring-boot/reference/features/profiles.html): `application-{profile}.yml` 활성화 우선순위
> - [Spring Boot Reference — Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html): 환경변수·커맨드라인 등 설정 소스 우선순위

## 2. 코드 구현

### 1) 필터 등록과 로그 패턴 연결

`RequestIdFilter`는 필터 하나로 끝나지 않고, 이걸 등록하는 `LoggingConfig`와 이 값을 꺼내 쓰는 `application.yml`의 로그 패턴까지 세 지점이 함께 움직여야 동작한다.

```yaml
logging:
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level [%X{requestId}] %logger{36} - %msg%n"
```

**한 줄씩 보기**

- `MDC.put(MDC_KEY, requestId)`: 필터 진입 시 이 스레드의 로그에 requestId를 붙이기 시작.
- `registration.setOrder(Ordered.HIGHEST_PRECEDENCE)`: Spring Security 필터체인보다 먼저 실행되도록 서블릿 컨테이너 최상단에 배치.
- `%X{requestId}`: logback 패턴 문법으로 MDC의 `requestId` 값을 로그 줄에 꺼내 씀.
- `finally { MDC.remove(MDC_KEY); }`: 스레드 재사용 전 반드시 정리.

### 2) 자동 검증 결과

| 검증 항목 | 방법 | 결과 |
|---|---|---|
| requestId 생성·재사용 | `RequestIdHttpTest.responseIncludesGeneratedRequestIdHeader`, `echoesClientProvidedRequestId` | 응답 헤더에 동일 id 반영 확인 |
| Authorization 마스킹 | `RequestIdFilterTest.maskAuthorization*` 3종 | 원문 토큰 미노출, `Bearer ***`만 출력 |
| 비밀번호 비노출 | `RequestIdHttpTest.rawPasswordNeverAppearsInLogsDuringSignupAndLogin` | signup/login 흐름 전체 로그에 원문 비밀번호 없음 |

`./gradlew test`는 60/60 통과했다. 이번 유닛은 첫 실행부터 초록으로 끝나서 새로 겪은 컴파일·런타임 오류는 없다.

커밋: [21fd3d5](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/21fd3d519d61e45518e981c867f9443a445d11ea)

## 3. 스스로 답한 질문

### 1) 커스텀 로그 패턴이 적용되는 시점

**질문.** `application.yml`의 `logging.pattern.console`을 Spring 컨테이너 없이 필터 객체만 직접 호출하는 테스트(`RequestIdFilterTest`)에서도 그대로 볼 수 있는가?

**A1.** 처음에는 "yml에 써놨으니 어디서든 적용될 것"이라고 답했다. 실제로는 `RequestIdHttpTest`(`@SpringBootTest`)에서만 `[fd9ad7d6-...]`처럼 커스텀 패턴이 찍혔고, `RequestIdFilterTest`는 기본 패턴(`INFO com.example...`) 그대로였다. 커스텀 패턴은 `ApplicationContext` 기동 중 `LoggingApplicationListener`가 `Environment`를 읽어 logback을 재구성해야 반영된다 — yml에 값이 있다는 사실 자체가 아니라, 컨테이너 기동이라는 시점이 조건이었다.

### 2) placeholder 미해결 시 동작

**질문.** `application-prod.yml`의 `${DB_URL}`에 대응하는 환경변수가 없으면 어떻게 되는가?

**A2.** 처음에는 "기본값이 없으니 null로 조용히 뜨지 않을까"라고 답했다. 실제로는 그 반대다 — Spring Boot는 값을 못 채운 placeholder를 예외로 취급해 기동 자체를 막는다. "값 없이 조용히 뜨는 것"이 아니라 "값 없이는 아예 못 뜨는 것"이 이 설계의 의도였다.

## 4. 학습 정리와 다음 범위

### 1) 이해의 변화와 남은 것

이전까지는 로그를 "필요할 때 println 찍는 것" 정도로 다뤘다. 오늘은 요청 하나가 여러 계층을 거치는 동안 같은 식별자로 로그를 묶는 장치(MDC)와, 그 장치가 언제 실제로 동작하는지(컨테이너 기동 시점)를 구분해서 보게 됐다. 설정도 마찬가지로 "값이 있다/없다"가 아니라 "언제 어떤 프로필이 그 값을 채우는가"로 생각이 바뀌었다.

**아직 남은 것**은 두 가지다. ① Authorization 마스킹이 스킴 구분 없이 전부 `Bearer ***`로 뭉갠다 — **나중에 고칠 것**(정밀 마스킹이 필요해지면). ② 시스템 환경변수가 이미 `prod`로 설정돼 있어 프로필 없는 `bootRun`이 placeholder 오류로 실패할 가능성이 있는데, 직접 실행해 확인하지는 않았다 — **바로 고칠 것은 아니지만 Docker 프로필을 추가하는 Day31에서 함께 점검**.

면접에서 다시 답해볼 항목을 남긴다.

- MDC가 스레드 로컬이라는 점이 비동기 처리(별도 스레드로 위임하는 로직)와 만나면 왜 문제가 되는가
- `@SpringBootTest` 유무로 로그 패턴 적용이 갈리는 것처럼, 테스트 환경과 운영 환경의 차이가 로깅 외에 또 어디서 드러날 수 있는가

---

오늘 공부한 소스코드: `app/src/main/java/com/example/studyroom/logging/RequestIdFilter.java`, `app/src/main/java/com/example/studyroom/config/LoggingConfig.java`, `app/src/main/resources/application.yml`, `app/src/main/resources/application-prod.yml`, `app/src/test/java/com/example/studyroom/logging/RequestIdFilterTest.java`, `app/src/test/java/com/example/studyroom/logging/RequestIdHttpTest.java`
