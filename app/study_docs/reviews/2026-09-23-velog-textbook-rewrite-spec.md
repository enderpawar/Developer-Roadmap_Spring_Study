# Day별 Velog 글 교과서형 재작성 명세 (2026-09-23)

## 1. 목적

Day01~Day14의 게시용 글을 Day16(`days/WeekC/Day16_0920/velog_post.md`) 형식으로 맞추되, **개념 설명을 강의자료 수준으로 상세하게** 다시 쓴다. 나중에 글만 다시 읽어도 “아, 이런 거였구나” 하고 그날의 개념을 필요성부터 동작 순서·한계·코드 연결까지 복원할 수 있어야 한다.

Week B D4~D7(Day11~Day14)은 현재 `velog/week-b-persistence-context-and-dirty-checking.md` 한 편에 합쳐져 있다. 이것을 **Day 단위 글 네 편**으로 분할한다.

## 2. 기준 문서 (작업 전 전부 읽는다)

1. `app/CLAUDE.md`: 세션 규칙. 학습자 실습 코드를 대신 짜지 않는다. 글과 그림만 작성한다.
2. `app/study_docs/VELOg_POST_TEMPLATE.md`: **전체를 읽는다.** 제목 규칙, H2 네 개 구조, Q/A 형식, UML 표기법, 품질 게이트를 따른다.
3. `app/study_docs/days/WeekC/Day16_0920/velog_post.md`: 형식 기준 글
4. `app/study_docs/assets/day16-aop-self-invocation.svg`, `day11-first-level-cache.svg`: 그림 스타일 기준

## 3. 템플릿 대비 변경점 (이 작업에 한해 우선 적용)

| 항목 | 템플릿 | 이번 재작성 |
|---|---|---|
| 분량 | Full 120~165줄 | **180~320줄 허용.** 늘어난 분량은 `1. 개념 설명`에 쓴다. 반복이나 잡담으로 채우지 않는다 |
| 개념 설명 깊이 | 다섯 질문에 답함 | 다섯 질문 각각에 **강의자료 수준**으로 답한다(아래 4절) |
| 비코드 글자 수 경고선 | 3,000~4,500자 | 6,000~9,000자까지 허용 |
| Week B D6·D7 | 한 편으로 통합 | 사용자 요청에 따라 **Day13·Day14를 각각 한 편으로 분리**한다. 기존 통합 글은 삭제하지 않는다 |

나머지 규칙은 템플릿 그대로 지킨다.
- 명사형 목차
- `1. 개념 설명 / 2. 코드 구현 / 3. 스스로 답한 질문 / 4. 학습 정리와 다음 범위`
- 3열 이하 표
- 실제 오답만 Q/A로 싣는다
- 미검증 구분
- 이모지와 과장 표현 금지
- `[직접 작성]` 보존

## 4. `1. 개념 설명` 작성 기준 (핵심)

용어표(3열, `vocab.md` 기반)를 맨 위에 두고, 그 아래를 **교과서 단원처럼** 여러 H3 소절로 구성한다. 한 소절은 한 개념 또는 한 개념 경계를 다룬다. 소절 안에서 다음을 채운다.

1. **필요성**: 이 장치가 없으면 이 프로젝트 코드에서 무엇이 깨지는지 구체적으로 쓴다(예: “id가 없으면 같은 값의 새 객체와 기존 예약을 구분할 수 없다”).
2. **동작 순서**: 주체·시점·상태 변화를 `text` 코드블록 화살표 흐름(`→`)으로 쓰고, 산문으로 한 번 풀어 설명한다. “Spring이 알아서”로 끝내지 않는다.
3. **유사 개념 구분**: 혼동하기 쉬운 짝을 비교표(3열 이하)나 대조 문단으로 정리한다. 그날 실제로 혼동한 짝이 있으면 반드시 넣는다.
4. **보장 범위와 한계**: 보장하는 것과 보장하지 않는 것, 성립 조건을 적는다.
5. **현재 코드 연결**: 그날 커밋 기준의 클래스·메서드·테스트 이름을 적는다.
6. **최소 예제와 반례**: 그날 실제로 실행한 예제와 대조군을 넣는다. 실행하지 않은 반례는 “미검증”이라고 표시한다.
7. **CS 연결**: 로드맵에 대응 개념이 있으면 연결한다(HTTP 의미론, 참조 동일성과 동등성, SRP·DIP, 해시·체크섬, ACID 등).
8. 소절 끝이나 중요한 지점에 **핵심 정리 인용 블록**을 둔다. 예: `> **정리.** 1차 캐시는 값이 아니라 (타입, id)로 같은 인스턴스를 돌려준다.` 한 글에 3~5개 정도로 제한한다.

문단은 2~3줄로 끊는다. 4~6줄짜리 문단을 연달아 두지 않는다.

`> **더 볼 것**`에는 공식 문서 링크(docs.spring.io, hibernate.org/docs.jboss.org, flywaydb/redgate docs, jakarta.ee, docs.oracle.com)만 넣는다. 기존 글에 있던 링크는 재사용해도 된다. 새 링크를 넣을 때는 WebFetch로 200 응답과 내용을 확인한다. 확인할 수 없으면 넣지 않는다.

## 5. 사실 근거 규칙

- 근거는 그날 폴더의 `vocab.md`, `quiz.md`, `explain-log.md`, `progress.md`, 기존 `velog_post.md`, 그리고 **그날 커밋의 소스**다. 소스는 `git log`로 해당 날짜 커밋을 찾아 `git show <sha>:<path>`로 읽는다.
  - Week B D4~D7은 기존 통합 글과 Day11~Day14 폴더 자료, 커밋 `9e3dfc3`·`2f870cf`가 근거다.
- **글 속 코드는 그날 시점의 코드다.** 이후 바뀐 현재 `src/`를 그날 코드처럼 쓰지 않는다. 필요하면 “이후 DayN에서 ~로 바뀜”이라고 한 줄 덧붙인다.
- 기존 글에 있는 실제 오답 원문, 컴파일러·런타임 메시지 원문, 테스트 결과 수치, 커밋 permalink는 그대로 보존한다. 새 permalink는 `git`으로 SHA를 확인한 경우에만 쓴다(저장소: `https://github.com/enderpawar/Developer-Roadmap_Spring_Study`).
- 없던 질문, 없던 실험, 측정하지 않은 수치를 만들지 않는다. 설명은 늘려도 **사실은 늘리지 않는다.**
- 기존 글의 좋은 서술은 버리지 말고 확장의 뼈대로 쓴다.

## 6. 그림 규칙

- 글마다 UML 그림을 최소 1개 넣는다. D6·D7 시험 글은 개념 흐름을 다시 설명하는 부분이 있을 때 넣는다.
- 기존 자산이 이미 UML이고 내용이 맞으면 재사용한다: `day01-request-flow`, `day02-dto-domain`, `day03-validation-flow`, `day04-identity-store`, `day05-ioc-di`, `day10-jpa-adapter`, `day11-first-level-cache`
- 새 그림은 `app/study_docs/assets/dayNN-주제.svg`(원본)와 `.png`(게시본)로 만든다.
  - 스타일은 `day11-first-level-cache.svg`와 `day16-aop-self-invocation.svg`의 `<defs>`·색·폰트를 복사해서 쓴다. 흰 배경, 단색 `#334155`, 오류 경로만 두 번째 색을 쓴다. 폭은 860~930px.
  - 종류는 템플릿 표를 따른다: 시퀀스(`alt`/`opt`), 클래스, 객체, 상태 머신
  - **그림 안에 설명 문단을 넣지 않는다.** 노트나 하단 주석은 한 줄까지만 쓴다.
- 렌더링은 한글 경로 문제 때문에 스크래치패드에서 한다.

```bash
S="/c/Users/jinwoo/AppData/Local/Temp/claude/C--Users-jinwoo-OneDrive-------Spring-Study/35305389-4ef2-43e8-a9f0-01c7e0e8f05b/scratchpad"
A="/c/Users/jinwoo/OneDrive/바탕 화면/Spring Study/app/study_docs/assets"
N=dayNN-주제; W=860; H=600   # SVG width/height와 동일하게
cp "$A/$N.svg" "$S/$N.svg"
E="/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"; SW=$(cygpath -m "$S")
"$E" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 --window-size=$W,$H --screenshot="$SW/$N.png" "file:///$SW/$N.svg"
cp "$S/$N.png" "$A/$N.png"
```

  파일명에 한글이 있으면 스크래치 쪽 파일명은 ASCII로 바꿔서 렌더링한다.
- 렌더링한 PNG는 **Read로 반드시 열어** 텍스트 겹침, 잘림, 생명선을 가로지르는 라벨이 없는지 확인한다. 문제가 있으면 고쳐서 다시 렌더링한다.
- 본문 경로는 `../../../assets/dayNN-주제.png`이고, 대체텍스트에는 그림이 말하는 흐름을 문장으로 쓴다.

## 7. 산출물과 작업 분담

| 담당 | 대상 파일 (`app/study_docs/days/...`) | 비고 |
|---|---|---|
| A | `WeekA/Day01_0725`, `Day02_0726`, `Day03_0728`의 `velog_post.md` | 기존 그림 재사용 |
| B | `WeekA/Day04_0728`, `Day05_0729` | 기존 그림 재사용 |
| C | `WeekA/Day06_0730`, `Day07_0731` | 그림 신규 제작 |
| D | `WeekB/Day08_0801`, `Day09_0802`, `Day10_0807` | Day08·09 그림 신규 제작 |
| E | `WeekB/Day11_0822`, `Day12_0822`의 **새** `velog_post.md` | 통합 글에서 분할. Day12 그림 신규 제작 |
| F | `WeekB/Day13_0822`, `Day14_0822`의 **새** `velog_post.md` | 통합 글에서 분할. Day13은 D6 시험 구조, Day14는 D7 구조(템플릿 「D6·D7 주차 마무리 시험 글의 구조」를 Day 단위로 나눠 적용) |

Week B 분할 글 제목 예시:
- `[백엔드 기본기 Day11] 영속성 컨텍스트 — 1차 캐시와 엔티티 동일성`
- `[백엔드 기본기 Day12] 변경 감지 — 로드 스냅샷과 flush 시점`
- `[백엔드 기본기 Day13] 2주차 누적시험 — 오답 교정`
- `[백엔드 기본기 Day14] 2주차 버퍼 — ddl-auto validate와 CHECK 제약`

## 8. 금지 사항

- `vocab.md`, `quiz.md`, `explain-log.md`, `progress.md`, `src/`, 로드맵, 복습큐, CODE_PATTERNS 수정 금지
- 담당 외 파일 수정 금지. 다른 담당의 그림 파일명과 겹치지 않게 `dayNN-` 접두사를 반드시 붙인다
- `velog/` 통합 글 삭제·수정 금지(분할본을 별도로 만든다)
- git commit 금지(최종 커밋은 메인 세션이 한다)
- GCP·서버 관련 명령 실행 금지

## 9. 완료 보고 형식

담당별로 다음을 보고한다.
1. 파일별 줄 수(이전 → 이후)
2. 새로 만들거나 재사용한 그림
3. 렌더링을 눈으로 확인했는지 여부
4. 근거로 삼은 커밋 SHA
5. 사실 확인이 불가능해 뺀 내용이나 “미검증”으로 표시한 내용
6. 품질 게이트 중 지키지 못한 항목
