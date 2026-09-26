# [Spring Study Day 8] Schema Migration — Migration 파일과 Checksum 검증

Day4에서 `InMemoryReservationRepository`가 예약을 `ArrayList`에 담게 만들면서, 프로세스를 재시작하면 데이터가 사라진다는 부채를 기술부채 원장에 남겨뒀다. 오늘은 그 저장소를 교체하기 전에 데이터가 들어갈 테이블부터 만들었다. 스키마를 SQL 파일로 정의하고 Flyway가 그 파일을 어떻게 다루는지까지만 봤다. Entity 매핑과 JPA는 다음 Day들의 범위라 손대지 않았다.

> `V1__init.sql`에 `reservation` 테이블을 직접 쓰고 H2 파일 DB에 적용했다. 이미 적용된 그 파일에 주석 한 줄을 추가하고 다시 띄웠더니 체크섬 불일치로 컨텍스트가 뜨지 못해 테스트 10개 중 6개가 함께 깨졌고, 앱을 거치지 않고 DB에 직접 넣은 빈 문자열은 `NOT NULL` 컬럼을 그대로 통과했다. 두 결과 모두 예측과 달랐다. `@Transactional`과 Entity 매핑은 아직 도입하지 않은 상태다.

> **오늘의 흐름** `V1__init.sql 작성 → Flyway 기동 시 스키마 히스토리 대조·적용 → Checksum 검증으로 변경 차단 → NOT NULL·AUTO_INCREMENT를 H2 콘솔에서 직접 확인`
>
> 이전 Day: 저장 계약을 신규·기존으로 나누고, 값 부재를 `Optional` → 도메인 예외 → HTTP 404로 단계별 변환해 저장소·조회·예외 처리의 경계를 확정했다 (Day7)
> 다음 Day: 이 테이블 위에 JDBC로 `DataSource`·`PreparedStatement`를 직접 써서 `ArrayList` 저장소를 교체한다 (Day9)

## 1. 개념 설명

### 1) Migration 파일과 스키마의 원본

> **Migration** = 스키마를 바꾸는 작업 하나를 버전 붙은 SQL 파일 하나에 담아 고정하는 것

우리 코드에서는 이 파일이 `db/migration/V1__init.sql`이고, 파일명의 언더바 두 개가 버전과 설명을 가른다.

```sql
-- db/migration/V1__init.sql
CREATE TABLE reservation (
     id BIGINT NOT NULL AUTO_INCREMENT,
     ...
);
```

지금까지 예약은 `ArrayList`에 있었다. 테이블로 옮기면 데이터는 재시작을 넘어 남지만, 대신 테이블 구조 자체가 새로운 관리 대상이 된다. H2 콘솔에서 `CREATE TABLE`을 손으로 한 번 실행해도 테이블은 생긴다. 문제는 그 사실이 내 로컬 DB 파일에만 남는다는 점이다.

- 새 환경에서는 어떤 SQL을 어떤 순서로 실행해야 지금 구조가 되는지 코드만 보고 알 수 없다.
- 이미 쓰던 DB에 같은 `CREATE TABLE`을 또 실행하면 실패한다. 어디까지 적용됐는지 기록이 없기 때문이다.
- 누군가 한 환경에서만 컬럼을 바꾸면, 환경마다 다른 스키마가 조용히 공존한다.

Flyway 공식 문서는 같은 소프트웨어와 DB가 개발자 PC, CI, 테스트, 운영 환경마다 한 벌씩 따로 존재하는 상황을 다음처럼 그린다.

![개발자 두 명의 PC(Axel's Machine, Christian's Machine)에 각각 Shiny Soft와 Shiny DB가 있고, 두 PC에서 Continuous Integration 환경으로 화살표가 모인 뒤 Test, Production 환경으로 이어진다. 네 종류의 환경마다 소프트웨어와 DB가 한 벌씩 따로 있다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day08-web-flyway-environments.png)

*출처: [Why database migrations — Redgate Flyway Documentation](https://documentation.red-gate.com/fd/why-database-migrations-184127574.html) — Copyright 1999 - 2026 Red Gate Software Ltd. All rights reserved.*

마이그레이션은 스키마 변경을 **파일 하나, 버전 하나**로 고정한다. 스키마의 원본은 DB 파일이 아니라 저장소에 커밋된 SQL이다. 그래서 `.gitignore`에도 `app/data/`를 추가하면서 "스키마 원본은 `V1__init.sql`이지 DB 파일이 아니다"라는 주석을 남겼다.

이 판단을 다른 각도에서 보여주는 것이 Hibernate의 `ddl-auto`다. `ddl-auto`는 Entity 클래스를 보고 테이블을 만들거나 검사하는 옵션이고, 이 방식에서는 자바가 원본이고 DB가 결과물이다. Flyway를 쓰면 방향이 반대다. SQL 파일이 원본이고 자바 Entity는 그 테이블에 맞춘다.

| 방식 | 스키마의 원본 | 변경 기록 |
|---|---|---|
| Flyway Migration | `db/migration/*.sql` | `flyway_schema_history` |
| Hibernate `ddl-auto` 생성 | `@Entity` 클래스 | 별도 버전 기록 없음 |

두 도구가 동시에 스키마를 바꾸면 Flyway 장부에 없는 변경이 DB에 생긴다. 이 충돌은 Day10에서 `ddl-auto: none`을 선택하는 근거가 됐다.

> **보장 범위** — 오늘은 두 방식을 실제로 같이 켜서 충돌을 재현하지 않았다. `ddl-auto`는 이번 Day에서는 미사용이고, 방향 비교는 공식 문서와 설계 판단을 근거로 했다.

### 2) Flyway 기동 시 동작 순서와 실패 기록

> **Flyway** = classpath의 `db/migration/` 아래 버전 붙은 SQL 파일을 스캔해, 아직 적용되지 않은 파일만 순서대로 실행하고 그 이력과 체크섬을 DB 안의 장부에 남기는 도구

우리 코드에서는 `application.yml`의 이 설정이 컨텍스트 기동 도중 Flyway를 실행하게 한다.

```yaml
flyway:
  enabled: true
```

```text
Spring Boot 컨텍스트 기동
→ DataSource 생성 (H2 파일 DB 연결)
→ Flyway가 classpath의 db/migration/ 스캔 → V1__init.sql 발견
→ 각 파일의 체크섬 계산
→ "flyway_schema_history" 조회
   ├ 장부에 V1 없음      → V1 실행 → 장부에 (version 1, 체크섬, success) 기록
   ├ 체크섬이 장부와 같음 → 건너뜀 ("No migration necessary")
   └ 체크섬이 장부와 다름 → FlywayValidateException → 컨텍스트 기동 실패
```

첫 기동 로그가 이 순서를 그대로 보여줬다. `Schema history table does not exist yet` → `Creating Schema History table ...` → `Current version of schema "PUBLIC": << Empty Schema >>` → `Migrating schema "PUBLIC" to version "1 - init"` 순이었다. 두 번째 분기는 Day09 재기동 때 `No migration necessary`로, 세 번째 분기는 오늘의 체크섬 실험(1절 3))으로 확인했다.

![시퀀스 다이어그램. 참여자는 Spring Boot 컨텍스트 기동, Flyway, H2 파일 DB의 flyway_schema_history, db/migration의 V1__init.sql이다. Spring Boot가 migrate()를 호출하면 Flyway는 V<버전>__<설명>.sql 파일을 스캔해 V1__init.sql을 받고, 파일 전체의 체크섬을 계산한 뒤 장부를 조회해 적용 이력을 받는다. alt 프레임의 첫 경우인 장부에 V1이 없는 첫 기동에서는 장부 테이블 생성, CREATE TABLE reservation 실행, success = TRUE 이력 기록 후 version "1 - init"을 돌려준다. 둘째 경우인 파일 체크섬과 장부 체크섬이 같으면 V1을 건너뛰고 No migration necessary를 돌려준다. 셋째 경우인 체크섬이 다르면 붉은 경로로 FlywayValidateException이 전달되고 기동이 실패한다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day08-flyway-startup.png)

같은 SQL이 두 번 돌지 않고, 빈 DB든 쓰던 DB든 결국 같은 상태에 도달하고, 이미 적용된 파일이 바뀌면 기동이 멈춘다. 세 성질은 모두 장부 하나에서 나온다.

첫 번째 분기 안에서도 한 번 더 갈린다. 실제로 첫 기동은 SQL 문법 오류로 실패했는데(2절 2)), 그 실패한 시도도 장부에 남았다.

```text
installed_rank | version | description | success
-1             | null    | 장부 생성    | TRUE
1              | 1       | init        | FALSE
```

`success = FALSE` 행이 남아 있으면 다음 기동이 다시 막힌다. 불확정 상태에서 진행하지 않는 fail-fast 동작이다. 운영이라면 `flyway repair`로 실패 기록을 정리해야 하지만, 데이터가 한 행도 없는 개발 DB라서 DB 파일을 지우고 처음부터 돌렸다.

Flyway는 SQL을 이해하는 도구가 아니라, "어느 파일을 어떤 내용으로 적용했는가"를 DB 안에 기록하고 매 기동마다 대조하는 도구다.

> **보장 범위** — `flyway repair`는 실행하지 않았다. 데이터가 없는 개발 DB라서 파일을 지우는 방법으로 우회했고, 운영 환경에서의 복구 절차는 확인하지 않았다.

### 3) Checksum 검증과 Migration 불변 규칙

> **Checksum** = 파일 내용에서 계산한 값으로, 이미 적용한 파일이 그때와 같은 내용인지 다음 기동마다 대조하는 값

우리 코드에서는 이미 적용된 `V1__init.sql`에 주석 한 줄을 추가하고 다시 띄우자 이 예외가 났다(원복해 다시 검증했으므로 그 상태는 커밋에 남아 있지 않다).

```text
FlywayValidateException:
  Validate failed: Migrations have failed validation
  Migration checksum mismatch for migration version 1
```

Flyway 공식 문서는 SQL 마이그레이션을 실행할 때 체크섬(CRC32)을 장부에 저장하고, 로컬 파일의 체크섬이 여전히 같은지 검사한다고 설명한다. 비교 대상은 SQL의 의미가 아니라 파일 내용에서 계산한 값이다. Flyway는 "이 변경은 주석이라 무해하다" 같은 판단을 하지 않는다. 이 융통성 없음이 기능이다. 주석을 봐주면 다음은 공백, 그다음은 컬럼 순서, 길이 변경이 되고, 어디까지 봐줄지 재는 순간 "내 DB와 다른 환경의 DB가 같은 상태"라는 보장이 사라진다.

| 구분 | 이미 적용된 V1 수정 | 새 파일 V2 추가 |
|---|---|---|
| 장부와의 관계 | 기록된 체크섬과 어긋남 | 장부에 없는 새 버전 |
| 다음 기동 | 검증 실패로 기동 거부 | V2만 실행하고 기록 |
| 다른 환경 | 이미 V1을 적용한 DB와 파일이 달라짐 | 모든 환경이 V1 → V2 순서를 밟음 |

규칙은 하나다. 적용된 파일은 고치지 않고 `V2__widen_room_name.sql`처럼 새 버전을 쌓는다. push한 커밋을 rebase하지 않는 것과 같은 규칙이다.

CS 관점에서 체크섬은 "내용이 같은가"를 값 하나로 빠르게 판정하는 장치다. 다만 CRC32는 오류 검출용 체크섬이지, 비밀번호 저장에 쓰는 BCrypt 같은 보안용 해시와는 목적이 다르다. 여기서 필요한 성질은 "파일이 달라지면 값이 달라진다"는 것 하나다.

> **보장 범위** — 위 표의 V2 경로는 인출 문항에서 답한 설계이고, 오늘 실제로 V2를 만들어 실행하지는 않았다.

### 4) Integrity Constraint의 검증 층 구분과 AUTO_INCREMENT 번호 소실

> **Integrity Constraint** = 애플리케이션이 거치는 경로와 무관하게, DB가 행을 저장하는 시점에 직접 강제하는 제약

우리 코드에서는 `V1__init.sql`의 이 제약이 Day3의 `@NotBlank`와 비슷해 보이지만, 검사하는 주체·시점·대상이 모두 다르다.

```sql
room_name VARCHAR(100) NOT NULL,
requester_name VARCHAR(50) NOT NULL,
```

```text
HTTP 요청 → @Valid + @NotBlank (Spring, 요청 바인딩 시점) → Service → Repository → DB
H2 콘솔의 직접 INSERT ───────────────────────────────────────────────→ DB
                                                        NOT NULL (DB, 행 저장 시점)
```

`@NotBlank`는 HTTP 요청 경로에만 서 있다. H2 콘솔, 배치, 다른 클라이언트처럼 앱을 거치지 않는 경로는 보지 못한다. 반대로 `NOT NULL`은 모든 경로에서 동작하지만 "값이 있는가" 하나만 본다.

| 값 | `@NotBlank` | `NOT NULL` |
|---|---|---|
| `null` | 막음 | 막음 |
| `""` / `"   "` | 막음 | 통과 |
| DB 직접 접근 | 못 봄 | 막음 |

`NULL`은 값의 부재이고, `''`는 길이 0인 값이 **있는** 상태다. 둘은 다른 상태라서 `NOT NULL`은 빈 문자열을 통과시킨다. 앱 검증은 사용자에게 400과 설명을 주는 UX 장치이고, DB 제약은 경로와 무관하게 데이터를 지키는 무결성 장치다. 계층별 방어(defense in depth)의 한 예다.

`final`도 같은 질문으로 정리된다. `Reservation`의 `roomName`이 `final`이어도, 그 규칙은 자바 컴파일러가 JVM 메모리 안의 변수 재대입을 검사하는 것이다. 디스크의 `data/studyroom.mv.db`에 있는 행은 검사 대상이 아니다(3절 3)).

같은 H2 콘솔 실험에서 `AUTO_INCREMENT`의 성질도 함께 드러났다. `AUTO_INCREMENT`는 `InMemoryReservationRepository`의 `nextId++`가 하던 일을 DB로 옮긴 것이다. `NULL`을 넣은 INSERT가 거부된 뒤, 다음 INSERT로 들어간 행의 `id`는 1이 아니라 2였다. 실패한 INSERT도 번호를 하나 소비하고, DB는 그 번호를 되돌리지 않았다.

번호를 되돌리려면 번호를 뽑는 구간을 직렬화해 앞선 INSERT의 성공 여부가 확정될 때까지 다른 INSERT를 기다리게 해야 한다. 그러면 동시 INSERT가 모두 대기한다. 그래서 `AUTO_INCREMENT`는 "빠짐없이 연속"이 아니라 "겹치지 않음"을 보장하는 장치로 이해했다. 같은 실험에서 `confirmed`를 INSERT에 넣지 않았는데 `DEFAULT FALSE`가 채워지는 것도 확인했다.

> **보장 범위** — 번호 소실에 대한 설명은 관찰한 현상의 해석이고, 동시 INSERT 상황을 직접 실험하지는 않았다. `final`이 DB 값을 막지 못한다는 사실은 확인했지만, 트리거·권한처럼 DB 수준에서 불변성을 강제하는 별도 장치는 이번에 다루지 않았다.

### 5) Identifier 대소문자 접힘과 Naming Strategy

> **Naming Strategy** = Hibernate가 자바의 camelCase 필드 이름을 DB의 snake_case 컬럼 이름으로 바꾸는 규칙

우리 코드에서는 `V1__init.sql`의 주석이 이 경계를 미리 적어 뒀다.

```sql
requester_name VARCHAR(50) NOT NULL, --Java는 CamelCase 이름을 사용하지만 SQL은 snake case로, 어차피 Spring Boot의 Hibernate 기본설정이 자동변환해줌.
```

최종 상태를 조회하니 한 DB 안에 대문자 테이블과 소문자 테이블이 함께 있었다.

```text
TABLE_NAME
RESERVATION                 ← 따옴표 없이 만들어 대문자로 접힘
flyway_schema_history       ← Flyway가 따옴표로 감싸 소문자 유지
```

따옴표 없는 SQL 식별자는 대문자로 접히고, `"이름"`처럼 따옴표로 감싸면 대소문자가 보존된다. 이 차이 때문에 조회 중 두 번 막혔다. `FLYWAY_SCHEMA_HISTORY not found (candidates are: "flyway_schema_history")`가 났고, `information_schema`에서 `table_name='reservation'`으로 찾으면 0행이 나왔다.

자바 쪽 이름과의 경계도 있다. 자바는 `requesterName`처럼 camelCase를 쓰고, 컬럼은 `requester_name`처럼 snake_case로 만들었다. Hibernate의 기본 Naming Strategy는 camelCase를 단어 단위로 나눠 snake_case로 바꾼다. 단순 소문자화가 아니므로, 컬럼을 camelCase로 만들어 두면 이후 Entity 매핑에서 이름이 맞지 않는다.

> **보장 범위** — 이 매핑은 Day10에서 실제 SQL 로그로 확인했다. 오늘은 컬럼 이름 규칙과 식별자 접힘의 원인만 관찰했고, Naming Strategy의 나머지 규칙(복수형 처리 등)은 다루지 않았다.

### 6) Flyway 실행 전제 — DataSource와 의존성

> **DataSource** = DB 커넥션을 빌려주고 반납받는 통로 역할을 하는 표준 인터페이스

우리 코드에서는 이 세 의존성이 그 통로를 만든다.

```kotlin
implementation("org.springframework.boot:spring-boot-starter-jdbc")
implementation("org.flywaydb:flyway-core")
runtimeOnly("com.h2database:h2")
```

Flyway가 마이그레이션을 실행하려면 DB 커넥션이 필요하다. 그래서 JPA보다 먼저 `spring-boot-starter-jdbc`를 추가했다. jdbc 스타터와 H2 드라이버가 classpath에 있으면 Spring Boot가 `application.yml`의 URL로 `DataSource`를 자동 구성하고, 기본 커넥션 풀인 HikariCP가 함께 올라온다.

`jdbc:h2:file:./data/studyroom`은 DB를 파일에 저장하는 모드라 재시작해도 데이터가 남는다. `MODE=MySQL`은 타입·함수·NULL 처리 같은 동작 일부만 맞춰주고 파서는 H2 그대로다. 첫 기동의 `#` 주석 오류가 이 한계를 보여줬다.

의존성 추가 중에도 한 번 막혔다. `org.flywaydb:flyway-database-h2`를 추가하라는 지시를 따랐는데 존재하지 않는 아티팩트였다. H2 지원은 `flyway-core` 안에 남아 있었다(jar 안의 `H2Database.class`로 확인). 처음에는 `dependencies` 리포트의 `FAILED` 한 줄만 보고 "버전 미지정"으로 추정했다. `dependencyInsight`를 실행하자 `Could not find ...`로 실제 원인인 아티팩트 부재가 드러났다.

> **보장 범위** — Flyway 10에서 DB별 모듈이 분리된 것은 맞지만 전부는 아니라는 사실은 jar 안의 클래스 확인으로 검증했다. 어떤 DB가 `flyway-core`에 남고 어떤 DB가 분리됐는지 전체 목록은 확인하지 않았다.

### 7) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| Schema Migration | 스키마 변경을 버전 붙은 SQL 파일로 고정해 관리하는 방식 |
| Migration | 스키마를 바꾸는 작업 하나를 담은 SQL 파일 |
| Checksum | 파일 내용에서 계산해 적용 시점과 지금이 같은 내용인지 대조하는 값 |
| Integrity Constraint | 애플리케이션 경로와 무관하게 DB가 행 저장 시점에 강제하는 제약 |
| Naming Strategy | Hibernate가 camelCase 필드 이름을 snake_case 컬럼 이름으로 바꾸는 규칙 |
| DataSource | DB 커넥션을 빌리고 반납하는 통로 역할을 하는 표준 인터페이스 |

> **더 볼 것**
> - [Database Initialization :: Spring Boot](https://docs.spring.io/spring-boot/how-to/data-initialization.html): Boot가 Flyway를 언제 실행하고 어떤 속성을 읽는지
> - [Migrations - Redgate Flyway](https://documentation.red-gate.com/flyway/flyway-concepts/migrations): 마이그레이션과 스키마 히스토리 테이블의 역할
> - [Validate - Redgate Flyway](https://documentation.red-gate.com/flyway/reference/commands/validate): 적용된 마이그레이션의 체크섬 대조 방식
> - 아직 안 본 것 — `flyway repair`, `CHECK` 제약, 커넥션 풀 설정

## 2. 코드 구현

### 1) `reservation` — 자바 필드 4개와 컬럼 4개

```sql
CREATE TABLE reservation (
     id BIGINT NOT NULL AUTO_INCREMENT,
     room_name VARCHAR(100) NOT NULL,
     requester_name VARCHAR(50) NOT NULL, --Java는 CamelCase 이름을 사용하지만 SQL은 snake case로, 어차피 Spring Boot의 Hibernate 기본설정이 자동변환해줌.
     confirmed BOOLEAN NOT NULL DEFAULT FALSE,
     PRIMARY KEY (id)
);
```

**한 줄씩 보기**

- `id BIGINT NOT NULL AUTO_INCREMENT` — DB가 행마다 번호를 붙이고, 값이 없으면 저장을 거부한다.
- `room_name VARCHAR(100) NOT NULL` — 방 이름은 최대 100자 계약이고, INSERT 시점에 부재를 거부한다.
- `requester_name VARCHAR(50) NOT NULL` — 자바의 camelCase 필드명이 Naming Strategy를 거쳐 snake_case 컬럼명이 되는 자리다.
- `confirmed BOOLEAN NOT NULL DEFAULT FALSE` — INSERT에 값을 생략해도 DB가 기본값을 채운다.
- `PRIMARY KEY (id)` — 행을 유일하게 식별하는 컬럼을 지정한다.

커밋된 `V1__init.sql`에서 빈 줄만 빼고 옮겼다. `Reservation`의 `roomName`, `requesterName`은 자바에서 `final`이지만 컬럼에는 그 사실을 옮길 자리가 없었다.

`VARCHAR`의 길이는 저장 공간이 아니라 계약으로 봤다. 이보다 긴 값은 잘못된 데이터라고 선언하는 쪽이 나중에 원인을 찾기 쉽다고 판단해서 방 이름 100, 사람 이름 50으로 뒀다. H2는 이 타입을 표준명인 `CHARACTER VARYING`으로 보고했다.

### 2) `#` 주석의 파싱 실패

처음에는 같은 주석을 `#`으로 달았다. `MODE=MySQL`을 켜뒀으니 MySQL 방언인 `#`도 받아줄 거라고 봤는데 파싱 단계에서 멈췄다.

```text
Error Code : 42000
Message : Syntax error in SQL statement
"... requester_name VARCHAR(50) NOT NULL, [*]#Java는 CamelCase ..."
```

`[*]`가 파서가 막힌 지점이다. 표준 SQL 주석인 `--`로 바꾸자 적용됐다. 호환 모드는 동작 일부를 맞춰줄 뿐 파서까지 MySQL로 바꾸지는 않는다.

### 3) 자동 검증 결과

| 확인 | 방법 | 결과 |
|---|---|---|
| `V1` 적용과 컬럼 정의 | `information_schema` 조회 | `RESERVATION` 4컬럼, 장부 `success = TRUE` |
| `NOT NULL`이 `NULL`을 막는지 | H2 콘솔에서 직접 INSERT | 거부(`23502`) |
| 체크섬 불일치 시 기동 | 주석 추가 후 `./gradlew test` | 컨텍스트 기동 실패, 10개 중 6개 실패 |

원복 후 테스트는 전원 통과했다. 커밋: [d2e9d55](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/d2e9d557255a4bd3c9870f355383b3c04847ce7d)

H2 콘솔 조회와 INSERT 실험은 수동 확인이고 자동 테스트로 고정하지는 않았다. 실험 데이터는 모두 삭제해 `reservation`을 0행으로 남겼다. 앱이 이 테이블을 실제로 읽고 쓰는 경로는 아직 없다.

## 3. 스스로 답한 질문

### 1) 적용된 Migration에 추가한 주석 한 줄의 영향

**질문.** 주석 한 줄은 SQL 동작에 영향이 없는데, 이미 적용된 `V1__init.sql`에 추가하면 무슨 일이 생기는가?

**A1.** 처음에는 "주석은 SQL 동작에 영향이 없으니 통과"라고 예측했다. 결과는 반대였다.

```text
FlywayValidateException:
  Validate failed: Migrations have failed validation
  Migration checksum mismatch for migration version 1
```

틀린 이유는 Flyway가 SQL을 해석해서 비교한다고 생각한 데 있었다. 실제로는 파일 내용에서 계산한 체크섬 하나를 장부값과 대조한다. 그래서 "이건 주석이라 의미 없음" 같은 판단은 애초에 하지 않는다.

깨진 테스트 6개에 `reservationServiceBeanIsSingleton`, `reserveReturns400ForBlankBodyFields`처럼 SQL과 무관한 것이 섞여 있었던 것도 같은 이야기다. Flyway가 멈추면 Spring 컨텍스트 자체가 뜨지 못하므로 그 위에 얹힌 테스트가 전부 함께 죽는다. 재발 방지 규칙은 하나다. 적용된 파일은 고치지 않고 `V2`를 새로 쌓는다.

### 2) `@NotBlank`와 `NOT NULL`의 검사 범위

**질문.** `@NotBlank`가 있는데 `NOT NULL`을 또 거는 건 중복이 아닌가? `NOT NULL` 컬럼에 `''`를 넣으면 막히는가?

**A2.** 처음 답은 "`@NotBlank`는 공백이 없음을 검사하는 거고 `NOT NULL`은 아예 값이 없음을 의미하는 거지"였다. 검사 대상이 다르다는 것까지는 맞았지만, 실험 전 예측에서는 `''`도 막힌다고 봤다.

앱을 거치지 않고 H2 콘솔에서 직접 넣어봤다.

```sql
INSERT INTO reservation (room_name, requester_name) VALUES (NULL, '이진우');
-- NULL not allowed for column "ROOM_NAME"

INSERT INTO reservation (room_name, requester_name) VALUES ('', '이진우');
-- (Update count: 1)
```

빈 문자열은 들어갔고 `CHAR_LENGTH(room_name) = 0`이었다. 반대로 `@NotBlank`는 `''`를 막지만 HTTP를 거치지 않은 이 INSERT는 보지도 못했다. 교정된 기준은 둘이 중복이 아니라 **못 막는 구멍이 서로 다르다**는 것이다.

### 3) `final` 필드와 DB 값 변경

**질문.** `final String roomName`은 DB에 저장된 값을 못 바꾸게 막는가? 앱이 꺼진 상태에서 H2 콘솔의 `UPDATE`는 (A) 컴파일 에러 (B) 런타임 예외 (C) 그냥 바뀐다 중 무엇인가?

**A3.** 처음에는 "막아준다. 불변성을 정의하는 것이니"라고 답했고, 선택지 문항에서도 "A"를 골랐다. 같은 오답을 두 번 연속 했다.

```sql
UPDATE reservation SET room_name = 'B202';
-- 3 | B202
```

그냥 바뀌었다. `Reservation.java`는 컴파일도 실행도 되지 않았으니 `final`이 개입할 지점이 없었다. `final`은 JVM 메모리 안 변수의 재대입만 검사하는 규칙이고, 디스크에 있는 행은 검사 대상이 아니다. DB에서 "한 번 쓰면 못 바꿈"을 강제하려면 트리거나 권한 같은 별개의 DB 장치가 필요하고, 이 트랙의 범위 밖이다.

재발 방지로 "이 규칙을 강제하는 주체가 누구이고 언제 검사하는가"를 먼저 묻기로 했다. 이 질문은 3절 2)의 `@NotBlank`와 `NOT NULL`에도 그대로 통했다.

### 4) camelCase 필드의 컬럼 이름

**질문.** `requesterName` 필드가 컬럼이 될 때 어떤 이름이 되고, 그 이유는 무엇인가?

**A4.** "Hibernate 때문에 자동으로 소문자로 변환되므로 snake_case가 좋다"라고 답해 부분 정답을 받았다. `V1__init.sql`의 주석에도 "Hibernate 기본설정이 자동변환해줌"이라고 적어뒀다.

빠진 것은 두 가지였다. Hibernate Naming Strategy는 단순 소문자화가 아니라 camelCase의 **단어를 분리**해 `requester_name`으로 만든다. 또 DB는 따옴표 없는 식별자를 대문자로 접는다. 그래서 snake_case는 권장이 아니라 필수다. 컬럼을 camelCase로 만들면 Entity 매핑 단계에서 Hibernate가 컬럼을 찾지 못한다.

## 4. 학습 정리와 다음 범위

### 1) 전체 흐름 다시 보기

오늘 다룬 Flyway가 `db/migration`의 버전 파일을 스키마 히스토리 장부와 대조해 아직 적용되지 않은 버전만 순서대로 실행하는 전체 흐름을 한 장으로 모으면 다음과 같다.

![왼쪽 Migrations 폴더에 V1__CreatingBaseTables.sql, V2__ConstraintsAndFKs.sql, V3__AddIndexes.sql, V4__ViewsAndFunctions.sql 네 파일이 있다. Flyway가 Execute 화살표로 Current version V2 데이터베이스에 적용하고, 점선을 따라 New Version V4 데이터베이스가 된다. V2 위의 Schema History table에는 V1·V2가 SUCCESS, V3·V4가 PENDING으로 적혀 있고, V4 위의 표에는 V1~V4가 모두 SUCCESS로 기록돼 있다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day08-overview-flyway-migrate.png)

*출처: [The Flyway Migrate Command Explained Simply](https://www.red-gate.com/hub/product-learning/flyway/the-flyway-migrate-command-explained-simply/) — Phil Factor, Redgate Hub Product Learning. 저작권은 원저작자에게 있습니다. Copyright © Red Gate Software Limited. All rights reserved.*

### 2) 이해의 변화와 남은 것

오늘 바뀐 이해는 두 개다. 하나는 스키마 관리 도구가 똑똑할 필요가 없다는 것이다. Flyway는 SQL을 이해하지 못하고 체크섬만 비교하는데, 그 단순함이 "모든 환경이 같은 순서로 같은 상태에 도달한다"를 보장한다.

다른 하나는 검증과 제약이 작동하는 층을 구분하게 된 것이다. `final`이면 DB 값도 고정될 거라고 봤고, `@NotBlank`가 있으면 빈 문자열은 어디로도 못 들어올 거라고 봤다. 둘 다 "어떤 층에서 누가 검사하는가"를 묻지 않아서 생긴 오해였다. `MODE=MySQL`인데 `#` 주석이 막힌 것도 같은 구분이 필요한 자리였다.

**아직 남은 것**은 `NOT NULL`이 `room_name = ''`인 행을 막지 못한다는 점이다. 지금 앱에는 그 경로가 없지만 배치나 다른 클라이언트가 붙으면 그대로 뚫린다. DB 수준에서 막으려면 `CHECK` 제약이 필요하므로 **나중에 고칠 것**으로 분류해 Week B D7에 배정했다. 이후 Day14에서 `V2__add_name_check_constraints.sql`로 해결했다. `ArrayList` 저장소를 이 테이블로 교체하는 일은 Day9(JDBC)와 Day10(JPA)에서 이어진다.

면접에서 다시 답해볼 항목을 남긴다.

- 운영 중인 서비스에서 이미 배포된 마이그레이션의 컬럼 타입을 바꿔야 할 때의 무중단 변경 순서
- `AUTO_INCREMENT`가 실패한 INSERT의 번호를 되돌리지 않는 이유와 그 성질이 문제가 되는 상황

---

오늘 공부한 소스코드: `app/src/main/resources/db/migration/V1__init.sql`, `app/src/main/resources/application.yml`, `app/build.gradle.kts`
