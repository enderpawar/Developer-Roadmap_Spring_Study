# Day09 글 재작성 브리프 (자기완결형)

이 파일만 규칙으로 쓴다. **템플릿, 다른 명세, 다른 Day 글은 열지 않는다**(필요한 건 아래에 다 있다).

## 대상과 근거 (이것만 읽는다)

- 수정 대상: `days/WeekB/Day09_0802/velog_post.md` (현재 206줄, 이 파일 하나만 수정)
- 노트: 같은 폴더의 `vocab.md` · `quiz.md` · `explain-log.md`
- 커밋 `293d260`의 코드. `git show 293d260:<path>`로 읽는다.
  - `app/src/main/java/com/example/studyroom/repository/JdbcReservationRepository.java`
  - `app/src/main/java/com/example/studyroom/repository/InMemoryReservationRepository.java`
  - `app/build.gradle.kts`
  - 설정 파일이 필요하면 `git show 293d260:app/src/main/resources/application.yml`
- 명령은 `app/study_docs`에서 실행한다. 경로에 한글이 있으므로 PowerShell에서 `-Encoding utf8`을 쓴다.

## 서두 (제목과 도입 문단은 그대로 둔다)

요약 인용블록 바로 뒤에 다음을 넣고 곧바로 `## 1. 개념 설명`으로 이어간다. 「이 글의 순서」 목록은 만들지 않는다.

```
> **오늘의 흐름** `ReservationService → ReservationRepository → JdbcReservationRepository → DataSource(Connection Pool) → PreparedStatement → DB → ResultSet → Reservation`
>
> 이전 Day: `V1__init.sql`로 `reservation` 테이블을 만들고 Flyway Checksum 검증을 확인 (Day8)
> 다음 Day: 반복되는 JDBC 코드를 Spring Data JPA와 Entity 매핑으로 교체 (Day10)
```

흐름 문자열은 실제 코드에 맞게 다듬어도 된다.

## 1절 개념 설명

- 현재 1절 앞쪽의 `day09-overview-connection-pool.gif` 블록(소개 문장, 이미지, 출처 줄)은 **4절 1)로 옮긴다.** 1절에서는 지운다.
- 1절 앞에 용어표가 있으면 지우고, 그 뜻은 각 소절 정의나 용어 한줄뜻으로 옮긴다.
- 현재 소절이 9개다. **7개로 합친다.** 추천하는 방식: 3) PreparedStatement, 4) ResultSet, 5) try-with-resources를 "JDBC 호출의 세 단계"류의 소절 하나로 합친다. 내용은 버리지 않는다.
- 각 개념 H3는 아래 순서를 따른다.
  1. `> **English Term** = 한 문장 정의`
  2. "우리 코드에서는 …" 문장과, 커밋 `293d260`의 실제 코드 3~6줄. 핵심 줄에는 `// 주석`을 단다.
  3. 동작 순서 설명. 순서가 있으면 `text` 코드블록에 `→`로 적는다.
  4. 필요할 때만 비교 표.
  5. `> **보장 범위** — …` 박스 하나. 오늘 확인한 것과 미검증인 것을 구분한다. 소절 중간에 흩어진 한계와 단서는 이 박스로 모은다.
- 비유는 쓰지 않는다. 기존 글에 비유가 있으면 코드와 연결되는 문장으로 바꾼다.
- 제목은 명사형으로 쓰고 H3 번호는 `N)`으로 단다. 개념어는 영어로만 쓰고 괄호 안에 한글을 병기하지 않는다.
- 마지막 H3는 `### 8) 용어 한줄뜻`이다.
  - 제목 바로 아래 설명 문장 없이 `| 용어 | 한줄뜻 |` 표만 둔다.
  - 표 뒤에 기존의 `> **더 볼 것**`을 두고, 없으면 공식 문서 링크 2~3개를 넣는다.
  - 표에 넣는 용어는 5~8개로, JDBC·DB 접근·Spring의 이론·구조 용어만 넣는다. 예: JDBC, DataSource, Connection Pool, Parameter Binding, ResultSet Cursor, Checked Exception, Bean 후보 모호성, Integration Test.
  - 제외할 것:
    - 예외 클래스
    - `ResponseEntity`나 `Optional` 같은 단순 API 타입, 개별 메서드
    - H2·HikariCP 같은 라이브러리 이름
    - Java 문법과 SQL 키워드

## 2절 코드 구현

- 대표 코드블록 바로 뒤에 `**한 줄씩 보기**` 불릿을 단다. 핵심 줄마다 "무엇을 하고 언제 동작하는가"를 한 줄로 쓴다.
- 마지막 소절 `자동 검증 결과`의 형식:
  - 자동 테스트, 수동 확인, 미검증을 구분한다.
  - 커밋 링크는 `https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/293d260`이다.
  - 기존 결과는 그대로 보존한다.

## 3절 스스로 답한 질문

- 형식은 `### N) 명사형 제목` → `**질문.** …` → `**A1.** …`이다.
- 오답 원문은 **굵게 인용한 그대로** 보존한다.
- 새 질문은 만들지 않는다.

## 4절 학습 정리와 다음 범위

- `### 1) 전체 흐름 다시 보기`: 1절에서 옮겨 온 overview 블록에 소개 1~2문장을 붙인다.
- `### 2) 이해의 변화와 남은 것`: 기존 4절 내용을 여기로 모은다.

## 공통 규칙

- 이미지는 `https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/<file>` 형식을 유지하고, 이미지 아래에 주석을 달지 않는다. 기존 이미지는 지우지 않고 옮기기만 한다.
- 코드 발췌는 `git show 293d260:<path>`로 읽은 것만 쓴다.
- 실행하지 않은 결과나 수치를 만들지 않는다.
- 커버리지: `vocab`·`quiz`·`explain-log`의 항목이 1절에 빠짐없이 들어갔는지 확인한다.
- 본문의 "3절 2)" 같은 교차 참조는 새 번호에 맞춘다.
- 금지: 다른 파일 수정, 커밋, 외부 요청, `app/src` 수정.

## 작업 방식 (토큰 절약)

1. 위 근거 파일을 한 번씩만 읽는다.
2. 새 글 전체를 **Write 한 번**으로 쓴다. Edit를 여러 번 나눠 하지 않는다.
3. 검수는 전체를 다시 읽지 말고 Grep 한 번으로 한다. 패턴: `^## |^### |보장 범위|오늘의 흐름|이 글의 순서|overview`

## 완료 보고 (10줄 이내)

1. 새 H3 목록
2. 합치거나 옮긴 요소
3. 용어 한줄뜻에 넣은 용어
4. 발견한 기존 글의 오류
5. 커밋 근거가 없는 코드의 위치
