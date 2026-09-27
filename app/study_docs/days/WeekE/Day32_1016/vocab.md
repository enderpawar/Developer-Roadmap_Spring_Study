# Day32 (10/16, Week E D4) 용어

주제: GitHub Actions CI — docker 잡 추가와 실제 실행 검증

| 용어 | 한줄뜻 | 오늘 코드와 관찰 |
|---|---|---|
| Workflow | 이벤트(push, PR 등)에 반응해 실행되는 하나 이상의 Job을 정의하는 YAML 파일 | `.github/workflows/ci.yml` |
| Job | 하나의 Runner(가상 머신)에서 순차 실행되는 Step들의 묶음 | `test` 잡, `docker` 잡 |
| `needs` | 한 Job이 다른 Job의 성공적인 완료를 기다리도록 지정하는 의존 관계 | `docker: needs: test` — 테스트 통과 후에만 이미지 빌드·기동 검증 |
| Runner | Workflow의 Job이 실제로 실행되는 가상 머신(GitHub 호스팅 또는 자체 호스팅) | `runs-on: ubuntu-latest` |
| MySQL 호환 모드(H2 `MODE=MySQL`) | H2가 MySQL 문법을 흉내 내어 로컬·테스트에서 실제 MySQL 없이 검증하게 해주는 설정 | 실제 MySQL은 SQL 주석(`--Java는`)을 거부했지만 H2 호환 모드는 통과시킴 |
| SQL Error 1064 | MySQL 문법 오류를 나타내는 표준 에러 코드 | `V1__init.sql:7`의 `--Java는`(공백 없는 `--`)이 유발 |
| `docker compose up -d --wait` | 컨테이너들을 백그라운드로 띄우고 각 서비스의 시작 조건이 충족될 때까지 커맨드 자체가 대기하는 옵션 | `docker` 잡의 컨테이너 기동 단계 |

## 1차 실행 실패와 원인

```text
1차 실행(run 36250928356)
test 잡 ✓ (71 tests green)
docker 잡 ✗ — Wait for /health 단계에서 30회 재시도 모두 실패

원인: V1__init.sql:7의 SQL 주석 `--Java는 CamelCase ...`
→ MySQL은 `--` 뒤에 공백이 반드시 있어야 주석으로 인식
→ 공백 없는 `--Java는`은 주석이 아니라 SQL 토큰으로 파싱 → Error 1064
→ H2 MODE=MySQL(로컬·테스트)은 이 형태도 주석으로 받아줘서 Week B부터 한 번도 드러나지 않았음
```

## 수정과 2차 실행

`-- Java는`(공백 한 글자 추가, 커밋 `4b58652`)으로 고치자 2차 실행(run 36251196912)에서 `test` ✓, `docker` ✓ 모두 통과했고, `/health` 응답이 `OKhealth check passed`로 확인됐다. Node.js 20·`setup-java@v4` deprecated 경고는 annotation으로 남아 기술부채로 등록한다.
