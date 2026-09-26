# Week A Day02~07 확정 템플릿 적용 명세 (2026-09-23)

## 1. 목적

2026-09-23에 확정한 Day01 형식을 Week A의 나머지 글(Day02~Day07)에 적용한다.

- **기준 예시:** `app/study_docs/days/WeekA/Day01_0725/velog_post.md` — 작업 전에 **처음부터 끝까지 읽는다.**
- **규칙 원문:** `app/study_docs/VELOg_POST_TEMPLATE.md`의 `# 제목` 절부터 끝까지 (특히 개념 소절 순서, 용어 한줄뜻, 품질 게이트)

담당 파일은 `days/WeekA/DayNN_*/velog_post.md` **한 개**다. Day06·07도 이미 일반 H2 네 개 구조로 쓰여 있으므로 **일반 템플릿**을 그대로 적용한다(D6·D7 통합 구조로 바꾸지 않는다).

## 2. 적용할 변경 (Day01과 같은 모양이 될 때까지)

1. **서두**
   - 요약 인용블록 뒤에 `> **오늘의 흐름** \`A → B → …\``, `>` 빈 줄, `> 이전 Day: …`, `> 다음 Day: …`를 넣는다. 이전·다음 Day 내용은 실제 인접 글(`Day(N-1)`, `Day(N+1)`)의 제목·요약을 읽고 쓴다. Day07의 다음 Day는 Week B 첫 글(`days/WeekB/` 첫 Day)이다.
   - 이어서 `**이 글의 순서**` 번호 목록 4줄(1. 개념 설명 — 1) … · 2) … · N) 용어 한줄뜻 / 2. 코드 구현 — … / 3. 스스로 답한 질문 — … / 4. 학습 정리 — 전체 흐름 다시 보기와 남은 것).
2. **1절 개념 설명**
   - 절 맨 앞 용어표(있다면)를 삭제한다. 거기 있던 뜻은 각 소절 정의나 마지막 용어 한줄뜻으로 옮긴다.
   - 각 개념 H3를 **한 줄 정의 인용블록(`> **Term** = …`) → "우리 코드에서는 …" + 그날 커밋 실제 코드 3~6줄 발췌(주석으로 핵심 표시) → 동작 순서·설명 → (필요시) 비교 표 → `> **보장 범위** — …` 박스 하나** 순서로 재구성한다. 소절 중간에 흩어진 한계·단서는 보장 범위 박스로 모은다.
   - 비유 금지(냉장고·웨이터·택배 등). 기존 글에 비유가 있으면 코드 연결 문장으로 바꾼다.
   - 마지막 H3는 `### N) 용어 한줄뜻`. 제목 아래 **설명 문장 없이** `| 용어 | 한줄뜻 |` 표만 둔다. **Spring·Spring Boot·JPA 이론/구조 용어만**(IoC, DI, Bean, ApplicationContext, DispatcherServlet, HandlerMethodArgumentResolver, Bean Validation, Singleton Scope 등). Java 문법(record, `==`, 오토박싱 문법 등)과 단순 API 타입(`ResponseEntity`, `Optional` 등)은 넣지 않는다. 표 뒤에 기존 `> **더 볼 것**` 묶음을 둔다(없으면 공식 문서 링크 2~3개로 만든다).
3. **2절 코드 구현**
   - 대표 코드 블록 바로 뒤에 `**한 줄씩 보기**` 불릿(애노테이션·핵심 줄마다 "무엇을 하고 언제 동작하는가" 한 줄).
   - 마지막 소절은 `자동 검증 결과`(자동 테스트 / 수동 확인 / 미검증 구분, 커밋 permalink). 이미 있으면 형식만 맞춘다.
4. **3절 스스로 답한 질문** — `### N) 명사형 제목` + `**질문.**` + `**A1.**` 형식. 실제 오답 원문 보존. 새 질문 창작 금지.
5. **4절**
   - `### 1) 전체 흐름 다시 보기` — 그날 전체 흐름 그림 1장 + 소개 1~2문장.
     - Day02·04·06·07: 이미 1절 앞쪽에 있는 `dayNN-overview-*` 그림 블록(소개 문장·이미지·출처 줄)을 **여기로 옮긴다.** 1절에는 남기지 않는다.
     - Day03·05: overview 그림이 없으므로 **직접 그린다** (6절 참조).
   - `### 2) 이해의 변화와 남은 것` — 기존 학습 정리 내용을 여기로 모은다.
6. **번호·제목:** H2 `N.`, H3 `N)`. 모든 제목은 명사형(질문형·서술형 금지), 개념어는 영어 표기(괄호 병기 금지). 본문 속 "3절 2)" 같은 교차 참조를 새 번호에 맞춘다.
7. **이미지:** 전부 `https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/<file>` 형식. 이미지 아래 주석 금지. 기존 자체 UML·`-web-` 그림은 삭제하지 않고 알맞은 소절에 유지한다.

## 3. 사실 근거 (지어내지 않기)

- 코드 발췌는 **그날 커밋의 실제 코드**만 쓴다. `git log --oneline -- app/src` 와 해당 Day의 `progress.md`/`explain-log.md`에서 커밋을 찾고 `git show <sha>:<path>`로 읽는다. 현재 HEAD 코드가 아니다.
- 커버리지 대조: `vocab.md`·`quiz.md`·`explain-log.md`·커밋 소스 주석의 항목이 1절에 빠짐없이 들어갔는지 확인한다.
- 기존 글의 사실(검증 결과, 오답 원문, 커밋 링크)은 보존한다. 형식을 바꾸되 내용을 잃지 않는다.
- 실행하지 않은 결과·수치를 만들지 않는다. 미실행 대조 코드는 "실행하지 않음"으로 표시한다.

## 4. Day03·05 overview 그림 제작

- 참고 원본: `app/study_docs/assets/day01-overview-request-flow.svg` — `<defs>`·스타일·색(`#334155`, 오류 경로 `#b91c1c`)·그룹 점선 박스·번호 원을 그대로 복사해 쓴다. 폭 900, 높이 500~600.
- **Day03:** `day03-overview-validation-flow` — Client → DispatcherServlet → 인자 변환(HttpMessageConverter) → `@Valid` Bean Validation → 통과 시 Controller → 200/201, 실패 시 `MethodArgumentNotValidException` → `@RestControllerAdvice` → 400 오류 본문. 실제 Day03 코드의 클래스·메서드 이름을 쓴다.
- **Day05:** `day05-overview-ioc-container` — 애플리케이션 시작 → Component Scan → Bean 정의 등록 → ApplicationContext가 Repository → Service → Controller 순으로 생성하며 생성자 주입 → Singleton Bean 하나를 공유 → 요청 처리. 실제 Day05 클래스 이름을 쓴다.
- 그림 안에 설명 문단 금지, 하단 주석 한 줄까지.
- 렌더링: 한글 경로 문제로 스크래치패드의 `dayNN-tpl/` 폴더에서 Edge headless로 렌더링한다(명령은 `reviews/2026-09-23-velog-textbook-rewrite-spec.md` 6절). 렌더링한 PNG를 **Read로 열어** 겹침·잘림·선이 글자를 가로지르는지 확인하고, 문제가 있으면 고쳐 다시 렌더링한다.
  - 스크래치패드: `C:/Users/jinwoo/AppData/Local/Temp/claude/C--Users-jinwoo-OneDrive-------Spring-Study/35305389-4ef2-43e8-a9f0-01c7e0e8f05b/scratchpad`
- SVG·PNG를 `app/study_docs/assets/`에 저장한다. 출처 줄은 넣지 않는다(자체 제작).

## 5. 금지 사항

- 담당 `velog_post.md`(Day03·05는 자기 overview SVG/PNG 포함) 외 파일 수정 금지. 템플릿·다른 Day·노트 파일 수정 금지.
- 커밋·푸시 금지.
- 외부 요청에 이메일 등 개인정보 금지(기본 UA 또는 `Mozilla/5.0`). 이번 작업은 외부 이미지 검색이 필요 없다.
- `app/src`의 코드 수정 금지.

## 6. 완료 보고 (짧게)

1. 새 H3 목록(1~4절)
2. 삭제·이동한 요소(용어표, overview 이동 등)
3. 용어 한줄뜻에 넣은 용어
4. 새로 만든 그림(Day03·05) 파일명과 검수 한 줄
5. 사실 확인 중 발견한 기존 글의 오류와 처리 내용
