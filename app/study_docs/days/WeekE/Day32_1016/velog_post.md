# [Spring Study Day 32] CI 파이프라인의 실제 실행 검증 — GitHub Actions Job 의존과 MySQL 1064 오류

Day31에서 만든 Dockerfile과 compose.yaml은 로컬에 Docker가 없어 정적 검증까지만 하고 넘어갔다. 오늘은 `.github/workflows/ci.yml`에 `docker` 잡을 추가해, 테스트를 통과한 뒤에만 실제로 이미지를 빌드하고 컨테이너를 띄워 `/health`까지 확인하도록 만들었다. 그리고 이 잡을 실제로 push해 실행한 첫 시도에서, Week B부터 한 번도 드러난 적 없던 SQL 오류를 만났다. Kubernetes 같은 오케스트레이션이나 배포 자동화(CD)는 이 글의 범위가 아니다.

> `docker` 잡을 `needs: test`로 걸어 테스트 통과 후에만 실행되게 했다. 첫 push(run 36250928356)는 `docker` 잡이 `/health` 대기 30회 재시도 끝에 실패했고, 로그를 보니 `V1__init.sql`의 SQL 주석 `--Java는`이 실제 MySQL 8.4에서 Error 1064를 냈다. `--` 뒤에 공백 하나(`-- Java는`)를 추가하자 두 번째 실행(run 36251196912)에서 `test`·`docker` 잡 모두 통과했다.

> **오늘의 흐름** `docker 잡 추가(needs: test) → push → 1차 실행: docker 잡 실패(Error 1064) → 원인 확인(H2 MySQL 호환 모드가 실제 MySQL과 다름) → V1 마이그레이션 주석 수정 → 2차 실행: 전체 통과`
>
> 이전 Day: Dockerfile 멀티스테이지 빌드와 compose.yaml (Day31)
> 다음 Day: Allen 구간 대수 기반 예약 겹침 검증 (Day33)

![하나의 Event가 Runner를 트리거해 Job이 여러 Step(Run action, Run script)을 순서대로 실행하고, 이어서 다른 Runner에서 다음 Job이 실행되는 GitHub Actions의 event → runner → job → step 계층 구조를 보여주는 공식 개념도.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day32-web-github-actions-overview.png)

*출처: [Understanding GitHub Actions — GitHub Docs](https://docs.github.com/en/actions/get-started/understand-github-actions) — GitHub, Inc. (CC BY 4.0)*

## 1. 개념 설명

### 1) Workflow의 Job 계층과 needs 의존

> **`needs`** = 한 Job이 다른 Job의 성공적인 완료를 기다리도록 지정하는 의존 관계

위 그림처럼 하나의 Workflow는 여러 Job으로 나뉘고, 각 Job은 자신만의 Runner(가상 머신)에서 Step들을 순서대로 실행한다. 우리 코드에서는 기존 `build` 잡의 이름을 `test`로 바꾸고, 그 뒤에 실행될 `docker` 잡을 추가했다.

```yaml
test:
  runs-on: ubuntu-latest
  steps:
    - run: ./gradlew test --no-daemon --console=plain

docker:
  needs: test
  runs-on: ubuntu-latest
  steps:
    - run: docker build -t study-room-api:ci ./app
    - run: docker compose up -d --wait
```

```text
push/PR 이벤트 발생
→ test 잡 시작(별도 Runner) → ./gradlew test 실행
→ test 잡 성공 → docker 잡 시작(또 다른 별도 Runner, needs: test 조건 충족)
→ docker build → docker compose up -d --wait → /health curl 재시도 → docker compose down -v
```

`needs`가 없으면 두 잡이 동시에 시작된다. 테스트가 실패해도 이미지 빌드·컨테이너 기동에 이미 시간과 러너 자원을 쓰고 있었을 것이다. `needs: test`는 이 순서를 강제해, 테스트가 실패하면 `docker` 잡 자체가 시작되지 않게 한다.

### 2) 호환 모드와 실제 데이터베이스의 파서 차이

> **H2 `MODE=MySQL` 호환 모드** = H2가 MySQL 문법을 흉내 내어, 로컬·테스트 환경에서 실제 MySQL 없이 마이그레이션·쿼리를 검증하게 해주는 설정

`V1__init.sql:7`에는 컬럼 정의 뒤에 SQL 주석이 붙어 있었다.

```sql
requester_name VARCHAR(50) NOT NULL, --Java는 CamelCase 이름을 사용하지만 SQL은 snake case로, ...
```

이 파일은 Week B부터 지금까지 로컬 테스트(H2, `MODE=MySQL`)에서 수십 번 실행됐지만 한 번도 오류를 낸 적이 없다. 실제 MySQL 8.4 컨테이너에 처음 적용됐을 때 비로소 Error 1064가 났다.

```text
Error Code : 1064
Message    : You have an error in your SQL syntax; check the manual that corresponds to your MySQL
             server version for the right syntax to use near '--Java는 CamelCase 이름을 사용하지만
             SQL은 snake case로, 어차피 Sp' at line 7
```

```text
파서 차이
MySQL:  '--' 뒤에 공백(또는 제어문자)이 있어야만 한 줄 주석으로 인식
        → '--Java는'은 주석이 아니라 SQL 토큰으로 파싱 시도 → 문법 오류(1064)
H2(MODE=MySQL): 공백 없는 '--'도 관대하게 주석으로 인식 → 오류 없이 통과
```

이건 SQL 표준·구현체 사이의 자구 단위 차이가 실제로 파서 결과를 가르는 사례다. CS적으로 보면, 렉서(lexer)가 토큰 경계를 정하는 규칙이 두 구현체 사이에 미묘하게 달랐던 것이다. **H2의 호환 모드는 이름 그대로 "호환"이지 "동일"이 아니다** — 오늘 관찰한 범위에서는 주석 문법 하나가 달랐고, 이 사례로 "호환 모드 테스트를 통과했다"가 "실제 DB에서도 통과한다"를 보장하지 않는다는 것이 실증됐다.

### 3) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| Workflow | 이벤트에 반응해 실행되는 Job들을 정의하는 YAML |
| Job | 하나의 Runner에서 순차 실행되는 Step 묶음 |
| `needs` | 다른 Job의 성공 완료를 기다리는 의존 관계 |
| Runner | Job이 실제로 실행되는 가상 머신 |
| MySQL 호환 모드 | H2가 MySQL 문법을 흉내 내는 설정, 실제 MySQL과 완전히 같지는 않음 |

> **더 볼 것**
> - [Understanding GitHub Actions — GitHub Docs](https://docs.github.com/en/actions/get-started/understand-github-actions): event·runner·job·step 계층
> - [GitHub Actions — Jobs, `needs`](https://docs.github.com/en/actions/using-jobs/using-jobs-in-a-workflow): Job 간 의존과 실행 순서 제어
> - [H2 Database — Compatibility Mode](https://www.h2database.com/html/features.html#compatibility): MODE별로 흉내 내는 범위와 한계

## 2. 코드 구현

### 1) `/health` 대기 루프와 실패 시 로그 출력

`app` 컨테이너 자체에는 JRE 이미지에 curl이 없어 healthcheck를 못 달았다. 대신 호스트에서 재시도 루프로 스프링 컨텍스트가 뜨는 시간을 기다린다.

```yaml
- name: Wait for /health
  run: |
    for i in $(seq 1 30); do
      if curl --fail --silent http://localhost:8080/health; then
        echo "health check passed"
        exit 0
      fi
      echo "waiting for app to become healthy... ($i/30)"
      sleep 2
    done
    echo "app never responded on /health"
    docker compose logs
    exit 1
```

**한 줄씩 보기**

- `for i in $(seq 1 30)`: 2초 간격 최대 30회(약 60초) 재시도.
- `curl --fail --silent`: 실패 시 non-zero 종료 코드, 출력은 숨김.
- `docker compose logs`: 실패가 확정된 순간에만 컨테이너 로그를 통째로 남겨 원인 조사에 씀 — 1차 실행의 Error 1064가 바로 이 단계에서 드러났다.
- `docker compose down -v`(항상 실행): 매 실행이 새 Runner라 볼륨을 지워도 무방.

### 2) 자동 검증 결과

| 시점 | 실행 | 결과 |
|---|---|---|
| 1차 push(커밋 `9f088d7`) | [run 36250928356](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/actions/runs/36250928356) | `test` ✓ 51s(71 tests), `docker` ✗ 2m28s(`/health` 30회 재시도 모두 실패, Error 1064) |
| 원인 수정(커밋 `4b58652`) | 로컬 `./gradlew test` | BUILD SUCCESSFUL, 71/71 |
| 2차 push | [run 36251196912](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/actions/runs/36251196912) | `test` ✓ 34s, `docker` ✓ 1m34s, 로그 `OKhealth check passed` |

`OKhealth check passed`는 `/health` 응답 본문 `OK`와 스크립트의 `echo "health check passed"`가 그대로 이어 붙어 찍힌 것이다. 이 한 줄이 Dockerfile 멀티스테이지 빌드, compose의 healthcheck·depends_on, Flyway V1~V7 on MySQL 8.4, `/health`의 `permitAll()`까지 이번 두 Day에 만든 것 전부가 CI 러너에서 실제로 맞물려 동작했다는 근거다.

커밋: [9f088d7](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/9f088d721f6fd7a7015b4b478b39f794f1082a19)(docker 잡 추가), [4b58652](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/4b586526a2f26c59a7a44dc9b998d182dbf0f16e)(SQL 주석 수정)

## 3. 스스로 답한 질문

### 1) 로컬 테스트 통과가 실제 실행을 보장하지 않는 지점

**질문.** `./gradlew test`가 Week B부터 계속 통과했는데, 왜 실제 MySQL에서는 실패했는가?

**A1.** 처음에는 "로컬 테스트가 계속 통과했으니 실제 MySQL에서도 별문제 없을 것"이라고 답했다. 실제로는 1차 CI 실행에서 Error 1064로 실패했다. 로컬·테스트가 쓰는 H2의 `MODE=MySQL`은 MySQL 문법을 흉내 낼 뿐 완전히 같은 파서가 아니라서, `--` 뒤 공백 유무 같은 세부 규칙에서 갈렸다. "테스트가 통과했다"는 "호환 모드 조건에서 통과했다"는 뜻이었지, "실제 MySQL에서도 통과한다"는 보장이 아니었다.

### 2) 체크섬 변경이 이번엔 안전했던 이유

**질문.** 이미 적용된 마이그레이션 파일(`V1__init.sql`)을 고치면 Flyway 체크섬이 바뀌는데, 왜 이번엔 문제가 안 됐는가?

**A2.** 체크섬 불일치는 "이미 적용된 이력이 있는 환경에서 파일 내용이 바뀔 때"만 문제가 된다. 이번이 운영 MySQL에 V1이 처음 적용되는 순간이라 적용 이력 자체가 없었고, 테스트는 매번 새로 만들어지는 인메모리 H2라 이전 실행의 흔적이 남지 않는다. 다만 로컬에 H2 파일 DB(`app/data/`)가 V1을 이미 적용한 채 남아 있다면 `validate` 단계에서 체크섬 불일치가 날 수 있다는 것은 기록만 하고 실행으로 확인하지는 않았다.

## 4. 학습 정리와 다음 범위

### 1) 이해의 변화와 남은 것

Day31까지는 "로컬에서 확인할 수 있는 만큼만 검증하고 나머지는 CI가 한다"는 게 계획이었다. 오늘 그 계획이 실제로 작동하는 걸 봤다 — CI가 로컬에서는 절대 드러나지 않았을 오류(호환 모드와 실제 DB의 파서 차이)를 잡아냈다. "테스트가 초록이다"와 "실제 환경에서 동작한다"는 서로 다른 주장이라는 걸 이번처럼 실패 로그로 직접 확인한 적은 없었다.

**아직 남은 것**은 CI annotation에 남은 두 개의 deprecated 경고(Node.js 20, `actions/*@v4`·`setup-java@v4`)다 — 지금 당장 실행을 막지는 않지만 **나중에 고칠 것**으로 기술부채에 등록한다. `mysql:8` healthcheck가 다른 러너 환경에서도 항상 같은 시간 안에 통과하는지는 이번 2회 실행만으로는 일반화하기 이르다 — 반복 실행으로 더 확인이 필요하다.

면접에서 다시 답해볼 항목을 남긴다.

- 호환 모드(H2 MySQL 모드) 테스트만으로 배포 신뢰도를 어디까지 보장할 수 있는가
- CI에서만 재현되는 오류를 로컬에서 더 일찍 잡으려면 어떤 수단(Testcontainers 등)을 쓸 수 있는가

---

오늘 공부한 소스코드: `.github/workflows/ci.yml`, `app/src/main/resources/db/migration/V1__init.sql`
