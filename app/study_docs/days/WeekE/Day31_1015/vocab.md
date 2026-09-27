# Day31 (10/15, Week E D3) 용어

주제: Docker / Dockerfile / Compose

| 용어 | 한줄뜻 | 오늘 코드와 관찰 |
|---|---|---|
| 컨테이너(Container) | 호스트 OS 커널을 공유하면서 프로세스·파일시스템을 격리해 애플리케이션을 실행하는 단위 | `app` 서비스 — 이 머신에 직접 설치하지 않고 격리된 환경에서 실행하려는 목적 |
| 이미지(Image) | 컨테이너를 실행하기 위한 파일시스템·실행 명령이 고정된 읽기 전용 템플릿 | `app/Dockerfile`이 정의하는 최종 산출물 |
| 멀티스테이지 빌드(Multi-stage build) | 빌드용 스테이지와 실행용 스테이지를 분리해, 최종 이미지에는 실행에 필요한 것만 남기는 Dockerfile 작성 방식 | `FROM eclipse-temurin:17-jdk AS build` → `FROM eclipse-temurin:17-jre` |
| 레이어(Layer) | Dockerfile의 각 명령이 만드는, 캐시되고 재사용 가능한 이미지의 한 겹 | `COPY`, `RUN` 각각이 레이어를 만듦 |
| `docker compose` | 여러 컨테이너(서비스)의 정의·네트워크·의존 순서를 하나의 YAML로 선언하는 도구 | `compose.yaml`의 `mysql` + `app` 두 서비스 |
| Healthcheck | 컨테이너 프로세스가 떴는지가 아니라 실제로 요청을 처리할 준비가 됐는지를 주기적으로 확인하는 설정 | `mysql` 서비스의 `mysqladmin ping` |
| `depends_on: condition: service_healthy` | 다른 서비스의 healthcheck가 통과할 때까지 이 서비스의 시작을 미루는 compose 설정 | `app`이 `mysql`의 healthcheck 통과를 기다림 |
| 정적 검증(Static validation) | 실제로 실행하지 않고 문법·구조만 확인하는 검증(YAML 파싱 등) | `python -c "import yaml; yaml.safe_load(...)"`로 compose.yaml·프로필 yml 4종 확인 |

## 핵심 구조

```text
docker build (Dockerfile)
1단계(build): eclipse-temurin:17-jdk + 프로젝트 gradlew → bootJar
2단계(실행): eclipse-temurin:17-jre + 1단계 산출물(jar)만 복사
→ 최종 이미지에는 JDK·Gradle 캐시가 남지 않음(JRE + jar만)
```

```text
docker compose up
mysql 컨테이너 시작 → healthcheck(mysqladmin ping) 통과까지 대기
→ app 컨테이너 시작(depends_on: service_healthy 조건 충족 후)
→ app이 jdbc:mysql://mysql:3306/studyroom로 접속(호스트명 = compose 서비스 이름)
```

## 로컬 미검증과 검증 범위

이 머신에는 Docker가 설치돼 있지 않아(`docker: command not found`) `docker build`·`docker compose up`을 직접 실행하지 못했다. 이번 Day에 한 것은 YAML 문법 검사와 신규 런타임 의존성(`mysql-connector-j`, `flyway-mysql`) 추가 후 `./gradlew test` 회귀 확인까지다. 실제 컨테이너 기동·통신·헬스체크는 Day32의 GitHub Actions `docker` 잡이 처음 실행한다.
