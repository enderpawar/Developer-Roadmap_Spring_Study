# Day32 (10/16, Week E D4) 예측→실행→차이 기록

주제: GitHub Actions CI에 docker 잡 추가, 실제 실행 결과 관찰

## 실험 1 — 로컬 테스트 통과와 실제 MySQL 실행 보장 여부의 관계

- 코드: `V1__init.sql:7` — `requester_name VARCHAR(50) NOT NULL, --Java는 CamelCase 이름을 사용하지만 ...`
- 예측: `./gradlew test`가 계속 통과했으니(Week B부터 지금까지) 실제 MySQL에서도 별문제 없이 마이그레이션이 적용될 것이다
- 실행: 1차 CI 실행(run 36250928356)에서 `docker` 잡이 `/health` 대기 30회 재시도 끝에 실패. `docker compose logs` 원문:

```text
Error Code : 1064
Message    : You have an error in your SQL syntax; check the manual that corresponds to your MySQL server version for the right syntax to use near '--Java는 CamelCase 이름을 사용하지만 SQL은 snake case로, 어차피 Sp' at line 7
```

- 차이 설명: 예측과 완전히 어긋났다. MySQL은 `--` 뒤에 공백(또는 제어문자)이 반드시 있어야 주석으로 인식한다. `--Java는`은 공백이 없어 주석이 아니라 SQL 토큰으로 파싱됐다. 로컬·테스트에서 쓰던 H2의 `MODE=MySQL` 호환 모드는 이 형태도 관대하게 주석으로 받아줘서, Week B부터 지금까지 한 번도 이 문제가 드러나지 않았다. "테스트가 계속 통과했다"는 "호환 모드가 통과시켰다"는 뜻이지 "진짜 MySQL에서도 통과한다"는 뜻이 아니었다.

## 실험 2 — 체크섬 변경의 안전성

- 코드: `V1__init.sql`을 수정하면 Flyway 체크섬이 바뀐다
- 예측: 이미 적용된 마이그레이션 파일을 고치는 것이므로 체크섬 불일치 오류가 날 수도 있다
- 실행: 실제로는 문제없이 통과했다(2차 실행 성공)
- 차이 설명: 체크섬 불일치는 "이미 적용된 이력이 있는 환경에서 파일이 바뀔 때"만 문제가 된다. 이번이 운영 MySQL에 V1이 처음 적용되는 순간이었고(적용 이력 자체가 없음), 테스트는 매번 새로 만들어지는 인메모리 H2라 이전 이력이 없다. 그래서 이번 수정은 안전했다 — 다만 로컬에 H2 파일 DB(`app/data/`)가 이미 V1을 적용한 채 남아있다면 `validate` 단계에서 체크섬 불일치가 날 수 있다는 것은 기록만 하고 실행하지 않았다.

## 판단 로직 교정 과정

- 1차 이해: "로컬에서 통과한 테스트 스위트가 곧 배포 가능성의 증거"
- 교정: 로컬 테스트가 검증하는 것은 "로컬에서 흉내 낸 조건"까지다. 실제 실행 환경(진짜 MySQL 8.4)에만 있는 파서 차이는 그 환경에서 처음 실행해봐야 드러난다. 이번 1064 오류가 "호환 모드는 실제 DB가 아니다"라는 것을 눈으로 확인한 첫 사례였다.

## 검증 근거

- `.github/workflows/ci.yml`
- 1차 실행: https://github.com/enderpawar/Developer-Roadmap_Spring_Study/actions/runs/36250928356 — docker 잡 실패
- 2차 실행: https://github.com/enderpawar/Developer-Roadmap_Spring_Study/actions/runs/36251196912 — test ✓ 34s, docker ✓ 1m34s
- 로컬 `./gradlew test` (수정 후): 71/71 통과

## [직접 작성] 오늘 배운 것을 내 문장으로

- H2 MODE=MySQL 호환 모드가 실제 MySQL과 다르게 동작한 지점:
- `needs: test`가 docker 잡 실행 시점을 제어하는 방식:
- 체크섬 불일치가 문제되는 조건과 안 되는 조건:

## 다음 시작점

Week E D5 — Day33(Allen 구간 대수 기반 예약 겹침 검증)로 이어짐. 다음 주(Week F)는 로드맵의 첫 미완료 Day부터 시작.
