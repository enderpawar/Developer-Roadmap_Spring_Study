# [Spring Study Day 31] 컨테이너 이미지 빌드 — 멀티스테이지 Dockerfile과 Compose 서비스 의존

지금까지는 `./gradlew bootRun`으로 이 머신에 직접 애플리케이션을 띄웠다. 오늘은 애플리케이션(`app`)과 MySQL(`mysql`)을 각각 컨테이너로 만들고, `compose.yaml`로 두 서비스의 시작 순서·의존 관계를 선언했다. 이 머신에는 Docker가 설치돼 있지 않아 실제 `docker build`·`docker compose up`은 이번 Day에 실행하지 못했다 — 실제 기동 검증은 Day32의 GitHub Actions가 한다. 이미지 빌드 캐싱 최적화나 오케스트레이션(Kubernetes 등)은 이 글의 범위가 아니다.

> `eclipse-temurin:17-jdk`로 빌드하고 `eclipse-temurin:17-jre`로 실행만 하는 2단계 Dockerfile을 작성했고, `compose.yaml`에서 `mysql`의 healthcheck가 통과해야 `app`이 시작되도록 `depends_on: condition: service_healthy`를 걸었다. Docker 자체가 없어 로컬 실행은 못 했지만, YAML 문법 검사와 신규 의존성 추가 후 `./gradlew test` 61/61 통과까지는 이 머신에서 확인했다.

> **오늘의 흐름** `Dockerfile 2단계 작성(build→jre) → compose.yaml로 app+mysql 정의 → healthcheck·depends_on으로 시작 순서 보장 → 로컬 정적 검증(YAML 파싱, 테스트 회귀)까지만 완료 → 실제 기동은 Day32 CI로 이월`
>
> 이전 Day: 취소 사유 트리밍 버그의 재현과 수정 (Day30)
> 다음 Day: GitHub Actions에 docker 잡 추가, 실제 컨테이너 기동 검증 (Day32)

![컨테이너화된 애플리케이션과 가상머신을 나란히 비교하는 구조 다이어그램. 컨테이너는 App A~F가 공유 Docker 엔진과 단일 Host OS 위에서 동작하고, 가상머신은 App A~C가 각각 독립된 Guest OS를 가지고 Hypervisor 위에서 동작한다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day31-web-container-vs-vm.png)

*출처: [What is a Container? — Docker](https://www.docker.com/resources/what-container/) — Docker, Inc.*

## 1. 개념 설명

### 1) 컨테이너와 이미지의 관계

> **컨테이너(Container)** = 호스트 OS 커널을 공유하면서 프로세스·파일시스템을 격리해 애플리케이션을 실행하는 단위

위 그림처럼 컨테이너는 VM과 달리 Guest OS를 따로 두지 않고 Host OS 커널을 공유한다. 그래서 VM보다 가볍고 시작이 빠르다. 우리 코드에서는 이 격리 단위를 만드는 설계도가 `Dockerfile`이다.

```dockerfile
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
ENV SPRING_PROFILES_ACTIVE=docker
ENTRYPOINT ["java", "-jar", "app.jar"]
```

`FROM ... AS build`로 이름 붙인 1단계는 소스 전체와 JDK·Gradle까지 다 갖고 있어 무겁다. `COPY --from=build`가 그 1단계에서 딱 필요한 산출물(jar 하나)만 2단계로 가져오고, 1단계 자체(JDK, 소스, Gradle 캐시)는 최종 이미지에 전혀 남지 않는다. 이게 **멀티스테이지 빌드**다 — 빌드 도구와 실행 환경을 분리해서 최종 이미지를 가볍게 만드는 방식.

공식 `gradle` 이미지 태그를 쓰는 방법도 있었지만, 이 프로젝트의 `gradle-wrapper.properties` 버전(8.14.5)과 정확히 일치하는 태그가 있는지 로컬에서 확인할 수 없었다(Docker가 없어서). 그래서 그 리스크를 검증하는 대신, JDK 이미지 위에서 프로젝트 자체 `gradlew`를 그대로 쓰는 쪽으로 설계해 버전 불일치 가능성 자체를 피했다.

이미지는 레이어가 쌓이는 구조라, `COPY`·`RUN` 한 줄마다 캐시 가능한 레이어 하나가 생긴다.

![Docker 이미지가 아래에서 위로 베이스 이미지 → 런타임 → 의존성 → 소스 순서로 레이어가 쌓이는 구조를 보여주는 플로우차트.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day31-web-image-layers.png)

*출처: [Understanding the image layers — Docker Docs](https://docs.docker.com/get-started/docker-concepts/building-images/understanding-image-layers/) — Docker, Inc.*

지금 `app/Dockerfile`은 `COPY . .` 한 줄로 소스 전체를 한 레이어에 넣고 바로 `bootJar`를 실행한다 — 의존성만 먼저 캐싱해 빌드 시간을 줄이는 레이어 순서 최적화는 하지 않았다. 이미지를 빌드할 때마다 Gradle 의존성을 새로 받는 셈이라, 빌드 시간 단축은 학습 범위 밖으로 남겨뒀다.

### 2) Compose의 서비스 의존과 Healthcheck

> **Healthcheck** = 컨테이너 프로세스가 떴는지가 아니라 실제로 요청을 처리할 준비가 됐는지를 주기적으로 확인하는 설정

우리 코드에서는 `mysql` 서비스에 healthcheck를, `app` 서비스에는 그 healthcheck 결과를 기다리는 조건을 걸었다.

```yaml
mysql:
  image: mysql:8
  healthcheck:
    test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-p${MYSQL_ROOT_PASSWORD:-studyroom_root}"]
    interval: 5s
    timeout: 5s
    retries: 10

app:
  build: { context: ./app, dockerfile: Dockerfile }
  depends_on:
    mysql:
      condition: service_healthy
```

```text
docker compose up
→ mysql 컨테이너 시작(포트는 곧 열림)
→ healthcheck(mysqladmin ping)가 5초 간격으로 최대 10회 재시도
→ 통과(healthy) 전까지 app 컨테이너는 시작을 미룸
→ 통과 후 app 시작 → jdbc:mysql://mysql:3306/studyroom 접속 시도
```

`depends_on`만 쓰고 `condition`을 안 주면 "컨테이너가 시작됐다"까지만 보장한다. mysqld는 포트를 먼저 열고 나서도 초기화를 계속 진행하는 구간이 있어서, 그 틈에 `app`이 먼저 붙으면 접속 실패가 날 수 있다. `service_healthy` 조건은 "포트가 열렸다"가 아니라 "핑에 응답한다"까지 확인해 이 경쟁 상태를 막는다.

`application-docker.yml`의 데이터소스 URL은 호스트명이 `localhost`가 아니라 `mysql`이다 — compose 네트워크 안에서는 서비스 이름 자체가 DNS처럼 동작하기 때문에, 컨테이너 밖에서 쓰던 `localhost`를 그대로 옮기면 안 된다.

### 3) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| 컨테이너 | 커널을 공유하며 프로세스·파일시스템을 격리하는 실행 단위 |
| 이미지 | 컨테이너 실행을 위한 읽기 전용 템플릿 |
| 멀티스테이지 빌드 | 빌드 스테이지와 실행 스테이지를 분리해 최종 이미지를 가볍게 만드는 방식 |
| Healthcheck | 프로세스 생존이 아니라 요청 처리 준비 상태를 확인하는 설정 |
| `depends_on: service_healthy` | 다른 서비스의 healthcheck 통과를 기다리는 시작 순서 제어 |

> **더 볼 것**
> - [What is a Container? — Docker](https://www.docker.com/resources/what-container/): 컨테이너와 VM의 구조 차이
> - [Understanding the image layers — Docker Docs](https://docs.docker.com/get-started/docker-concepts/building-images/understanding-image-layers/): 레이어 캐싱과 재사용
> - [Compose file — depends_on](https://docs.docker.com/reference/compose-file/services/#depends_on): `condition` 옵션과 시작 순서 보장 범위

## 2. 코드 구현

### 1) `.dockerignore`와 프로필 연결

`app/Dockerfile`이 `COPY . .`로 컨텍스트 전체를 복사하기 때문에, 이미지에 넣을 필요 없는 파일을 먼저 걸러야 한다.

```text
build/
.gradle/
data/
*.log
study_docs/
```

**한 줄씩 보기**

- `build/`, `.gradle/`: 로컬 빌드 산출물·캐시 — 이미지 안에서 다시 빌드하므로 불필요.
- `study_docs/`: 학습 기록 문서 — 런타임에 전혀 쓰이지 않음.
- `ENV SPRING_PROFILES_ACTIVE=docker`(Dockerfile): 컨테이너로 뜰 때는 Day29에서 만든 프로필 분리 구조 중 `docker` 프로필을 항상 쓰도록 고정.
- `application-docker.yml`의 `${MYSQL_USER}` 등: compose의 `environment:` 값이 실제로 주입되는 지점.

### 2) 자동 검증 결과

| 검증 항목 | 방법 | 결과 |
|---|---|---|
| compose.yaml·신규 yml 4종 문법 | `python -c "import yaml; yaml.safe_load(...)"` | 예외 없음(문법 오류 없음) |
| 신규 런타임 의존성 추가 후 회귀 | `./gradlew test --console=plain` | BUILD SUCCESSFUL, 61/61 |
| 실제 컨테이너 기동·통신·헬스체크 | (로컬 Docker 없음) | **미검증 — Day32 CI가 처음 실행** |

이번 유닛은 파일을 추가하는 작업이라 컴파일·런타임 오류는 없었다. Docker 실행 자체가 이 시점에는 불가능했다는 것을 그대로 남긴다.

커밋: [bb5a462](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/bb5a462ca0b24c004ac24b9dd3400391c4e46f58)

## 3. 스스로 답한 질문

### 1) 리스크를 검증 대신 회피 설계로 다룬 판단

**질문.** 공식 `gradle` 이미지 태그가 프로젝트의 wrapper 버전과 맞는지 확인했는가?

**A1.** 확인하지 못했다 — 로컬에 Docker가 없어 실제로 이미지를 당겨볼 수 없었다. 대신 "맞는 태그가 있는지 검증하는 것" 자체를 포기하고, JDK 이미지 위에서 프로젝트 자체 `gradlew`를 쓰는 설계로 바꿔 버전 불일치 리스크가 애초에 발생하지 않게 했다. 이건 예측이 맞았는지를 확인한 사례가 아니라, 검증 불가능한 리스크를 회피 설계로 우회한 사례라는 점을 분명히 해둔다.

### 2) `depends_on`의 보장 범위

**질문.** `depends_on: condition: service_healthy`가 없으면 정확히 어떤 상황이 위험한가?

**A2.** 처음에는 "mysql이 늦게 뜨면 app이 에러가 날 것 같다" 정도로만 답했다. 정확히는, mysql 컨테이너가 "시작됨" 상태와 "mysqld가 연결을 받을 준비가 됨" 상태 사이에 시간차가 있고, `depends_on`만 쓰면 전자만 보장한다. `service_healthy`는 `mysqladmin ping`으로 후자까지 확인해야 통과하므로, 그 시간차 구간에 `app`이 먼저 접속을 시도하는 경쟁 상태를 막는다.

## 4. 학습 정리와 다음 범위

### 1) 이해의 변화와 남은 것

이전까지 배포는 "서버에 jar를 올려서 실행하는 것"이었다. 오늘은 실행 환경 자체를 이미지로 고정하고, 여러 서비스의 시작 순서를 코드(compose.yaml)로 선언하는 방식을 봤다. 동시에 "로컬에서 검증할 수 있는 것"과 "실행해봐야 아는 것"의 경계가 이번처럼 뚜렷하게 나뉜 적은 없었다.

**아직 남은 것**은 Docker 실행 자체가 전부 미검증이라는 점이다. `docker build`, `docker compose up`, 컨테이너 간 통신, `/health` 응답까지 전부 Day32의 CI가 처음 실행한다 — **바로 다음 Day에서 확인**. 이미지 빌드 시 의존성 레이어를 분리하지 않아 매번 새로 받는 점은 **나중에 고칠 것**으로 남긴다.

면접에서 다시 답해볼 항목을 남긴다.

- 로컬에서 실행해보지 못한 설정을 그대로 커밋하는 것이 왜 정당화될 수 있는지, 그 조건은 무엇인가
- healthcheck 없이 애플리케이션 컨테이너 자체의 준비 상태를 확인하려면 어떤 대안이 있는가

---

오늘 공부한 소스코드: `app/Dockerfile`, `app/.dockerignore`, `compose.yaml`, `app/src/main/resources/application-docker.yml`, `app/build.gradle.kts`
