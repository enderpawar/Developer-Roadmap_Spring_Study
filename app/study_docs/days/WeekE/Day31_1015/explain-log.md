# Day31 (10/15, Week E D3) 예측→실행→차이 기록

주제: Dockerfile 멀티스테이지 빌드 + compose.yaml

## 실험 1 — MySQL 드라이버 추가가 기존 테스트에 미치는 영향

- 코드: `build.gradle.kts`에 `runtimeOnly("com.mysql:mysql-connector-j")`, `runtimeOnly("org.flywaydb:flyway-mysql")` 추가
- 예측: 테스트는 여전히 H2 URL(`jdbc:h2:mem:...`)로 뜨니 드라이버가 늘어도 영향이 없을 것이다
- 실행: `./gradlew test` → 61개 전부 그대로 통과
- 차이 설명: 예측과 일치했다. Spring Boot는 `DataSource` 설정의 JDBC URL 스킴(`jdbc:h2:` vs `jdbc:mysql:`)을 보고 드라이버를 고른다 — 클래스패스에 MySQL 드라이버가 추가돼도 URL이 H2를 가리키는 한 그쪽을 쓰지 않는다.

## 실험 2 — Docker 부재 상태에서 검증 가능한 범위

- 코드: `compose.yaml`, `application-docker.yml` 등 신규 yml 4종
- 예측: Docker가 없어도 YAML 문법과 Gradle 빌드까지는 이 머신에서 검증할 수 있을 것이다
- 실행: `python -c "import yaml; yaml.safe_load(open('compose.yaml'))"` → 예외 없이 통과. `docker --version` → `bash: docker: command not found`
- 차이 설명: 예측대로 "정적 검증"(문법·의존성 해석)과 "실제 실행 검증"(컨테이너 기동·네트워크·헬스체크)은 서로 다른 층위였다. 이 머신은 전자까지만 가능했고, 후자는 Day32의 CI가 대신 수행한다는 역할 분담이 이번에 명확해졌다.

## 판단 로직 교정 과정

- 공식 `gradle` Docker 이미지 태그가 이 프로젝트의 wrapper 버전(8.14.5)과 정확히 맞는지는 확인하지 않았다(로컬에 Docker가 없어 직접 당겨보지 못함). 이 리스크 자체를 검증하는 대신, JDK 이미지 위에서 프로젝트 자체 `gradlew`를 쓰는 쪽으로 설계를 바꿔 리스크를 피했다 — "예측이 맞았는가"가 아니라 "검증 못 하는 리스크는 회피 설계로 우회한다"는 판단이었다.

## 검증 근거

- `app/Dockerfile`, `compose.yaml`, `app/build.gradle.kts`
- `./gradlew test --console=plain` → BUILD SUCCESSFUL, 61/61(커밋 `bb5a462` 시점)
- `python -c "import yaml; yaml.safe_load(...)"` — compose.yaml 및 신규 yml 4종 파싱 확인

## [직접 작성] 오늘 배운 것을 내 문장으로

- 멀티스테이지 빌드가 최종 이미지를 가볍게 만드는 원리:
- healthcheck 없이 depends_on만 쓰면 생기는 경쟁 상태:
- 정적 검증과 실제 실행 검증의 역할 분담:

## 다음 시작점

Week E D4 — GitHub Actions CI에 docker 잡 추가, 실제 컨테이너 기동 검증.
