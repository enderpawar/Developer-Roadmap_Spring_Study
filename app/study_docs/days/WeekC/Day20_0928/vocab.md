# Day20 (9/28, Week C D6) 용어

주제: 누적시험 A+B+C — 새 용어 없이 Week A~C 개념을 재정리

| 용어 | 한줄뜻 | 오늘 재확인 |
|---|---|---|
| DTO(record) vs Domain | DTO는 경계를 넘는 불변 데이터, Domain은 상태와 그 변경 규칙을 가진 가변 객체 | 문항 1 — 힌트 없이 인과 순서까지 재현 |
| Constructor Injection | 필요한 의존성을 생성자 매개변수로 받는 DI 방식 | 문항 2 — 구현 교체·테스트 교체 두 이유 재현 |
| JPA의 추상화 대상 | 커넥션 풀이 아니라 매핑·SQL 생성을 대신 처리하는 것 | 문항 3 |
| 1차 캐시 / 영속성 컨텍스트 | 같은 트랜잭션·같은 id 조회를 캐시로 대체해 같은 참조를 돌려주는 저장소 | 문항 4 |
| 변경 감지(Dirty Checking) vs Flyway 체크섬 | 전자는 Entity 필드를 flush 시점에 로드 스냅샷과 비교, 후자는 마이그레이션 파일 해시를 기동 시점에 비교 — 감지 대상·시점·결과가 전부 다름 | 문항 5 |
| self-invocation | target 객체 내부의 `this.메서드()` 호출은 프록시를 다시 통과하지 않아, 그 메서드의 `@Transactional`이 적용되지 않는 현상 | 문항 6 — **3회 연속 근거 수준에서 실패** |
| 트랜잭션 전파(`REQUIRED`/`REQUIRES_NEW`) | 진행 중인 트랜잭션에 합류할지(REQUIRED), 보류하고 새로 시작할지(REQUIRES_NEW) 정하는 규칙 | 문항 7·8 |
| Spring AOP 프록시 vs Hibernate LAZY 프록시 | 이름은 같은 "프록시"지만 AOP는 메서드 호출을, Hibernate는 필드 접근을 가로채는 별개 장치 | 문항 9 — 오답 |
| Fetch Join의 적용 범위 | 매핑을 바꾸는 게 아니라 그 쿼리 메서드 하나에만 적용되는 쿼리 수준 지시 | 문항 10 — 오답 |

## 이번 시험에서 드러난 패턴

Week A·B 항목(1~5번)은 이미 여러 차례 재시험을 거쳐 힌트 없이 안정적으로 통과했다. 흔들린 항목은 전부 Week C(6·8·9·10번)였고, 그중 두 개(6·9번)는 "프록시"라는 같은 단어를 서로 다른 장치에 겹쳐 쓰는 지점에서 반복적으로 무너졌다.

> **더 볼 것**
> - [Proxying Mechanisms — Spring Framework Reference](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html#aop-understanding-aop-proxies): self-invocation이 프록시를 우회하는 이유의 공식 근거
> - [Hibernate ORM User Guide — Fetching](https://docs.hibernate.org/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#fetching): fetch join이 매핑이 아니라 쿼리 단위로 적용된다는 근거
