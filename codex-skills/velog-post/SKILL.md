---
name: velog-post
description: Convert a day's study artifacts (`app/study_docs/days/WeekX/DayNN_MMDD/`) into a polished, copy-paste-ready Velog-style Markdown tech blog post. Use when the user invokes `$velog-post` with a day such as `day1`, `Day02`, asks for `/velog_post dayN`, or asks to turn a day's Spring study session into a Velog post or Korean technical blog article.
---

# Velog post — 일별 스터디 기록 변환

`app/study_docs/days/WeekX/DayNN_MMDD/`에 쌓인 그날의 학습 산출물(`vocab.md`, `quiz.md`, `explain-log.md`)과 그날 변경된 코드를 근거로, Velog에 바로 붙여 넣을 수 있는 Markdown 기술 블로그 글로 재구성한다.

## 페르소나와 문체

글 전체에서 다음 페르소나를 유지한다.

> 24살 컴퓨터공학과 재학 또는 졸업반 대학생. 백엔드 개발자 취업 포트폴리오를 위해 본인의 Velog를 채워 나가는 중이다.

- 또래 개발자가 읽는 개인 기술 블로그처럼 자연스러운 `~다`, `~했다`, `~였다` 문체를 사용한다.
- 교수, 논문, 사내 문서 같은 딱딱한 격식체나 `~습니다`체 남발을 피한다.
- 포트폴리오로서 신뢰를 잃지 않도록 기술적 정확성과 논리성을 우선한다.
- 완성된 지식만 나열하지 말고 `처음의 오해/오타 → 질문과 실습 → 교정된 이해`라는 성장 과정을 보여준다.

## 필수 제약: 결과물에 한자 사용 금지

완성된 Markdown에는 한자를 단 한 글자도 남기지 않는다.

- 한글 단어에 한자를 병기하지 않는다.
- 제목, 본문, 괄호, 각주 어디에도 한자를 쓰지 않는다.
- 영어 기술 용어와 영문 코드 및 HTTP 예시는 유지한다.
- 최종 저장 직전에 전체 결과를 `[㐀-䶿一-鿿豈-﫿]` 범위로 검사하고, 발견한 글자를 한글 표현으로 바꾼다.
- 검사를 통과하기 전에는 저장하거나 출력하지 않는다.

## 1. Day 인자 해석

다음 입력에서 Day 번호 `N`을 추출한다.

- `day1`, `Day01`, `day 2`
- `1일차`, `2일차`, `1`, `2`
- `/velog_post day1`, `$velog-post day2`

Day 번호가 없거나 모호하면 `app/study_docs/days/`에서 가장 최근(가장 큰 번호) `DayNN_MMDD` 폴더를 후보로 삼는다. 후보가 여러 날짜에 걸쳐 애매하면 목록을 보여주고 하나만 짧게 물어본다. 명확한 인자가 있으면 확인 질문 없이 진행한다.

## 2. 원본 자료 찾기

1. `app/study_docs/days/WeekX/DayNN_MMDD/` 폴더를 찾는다 (`NN`은 2자리, `MMDD`는 실제 날짜 접미사 — 정확한 폴더명은 디렉터리 목록에서 확인한다).
2. 그 안의 `vocab.md`, `quiz.md`, `explain-log.md`를 전부 읽는다.
3. 그날 커밋된 코드(`git log`로 해당 날짜 커밋 확인, 필요하면 `git show`/`git diff`)를 참고해서 실제 코드 스니펫과 에러 메시지를 정확하게 인용한다. 기억이나 추측으로 코드를 재구성하지 않는다.
4. 폴더나 파일을 못 찾으면 사용자에게 Day 번호나 경로를 되묻는다. 빈 원본으로 진행하거나 내용을 지어내지 않는다.

## 3. 제목 규칙 (중요)

**브이로그식 서술형 제목을 쓰지 않는다.** "~한 하루", "~해보며 겪은 이야기", "~을 나눠보며 겪은 오타 소동" 같은 문장형 제목 금지.

형식은 다음과 같다.

```
[백엔드 기본기 DayNN] <핵심 주제 명사구>
```

- 주제 명사구는 `app/study_docs/FUNDAMENTALS_ROADMAP.md`의 해당 Day Full 루프 항목명을 우선 그대로 쓴다 (예: "record DTO vs Domain 분리", "전역 오류처리 @RestControllerAdvice").
- 로드맵 항목명이 그날 실제로 다룬 내용과 안 맞으면, 그날 다룬 핵심 개념 키워드를 `/`나 공백으로 나열한 명사구로 대체한다 (예: "DTO/Domain", "예외처리/@Valid").
- 예시:
  - 좋음: `[백엔드 기본기 Day2] record DTO vs Domain 분리`
  - 나쁨: `[백엔드 기본기 Day2] record DTO와 Domain을 나눠보며 겪은 오타 소동`

## 4. 재구성 원칙

- **사실 보존:** 원본(vocab/quiz/explain-log/코드/커밋)에 없는 경험, 결과, 감정, 다음 계획을 새로 만들지 않는다.
- **기술적 교정:** 틀린 내용을 그대로 싣지 않는다. `explain-log.md`의 "차이 설명"이나 `quiz.md`의 정답을 채택해 정확한 설명으로 정리한다.
- **성장 과정 보존:** 오답·오타·컴파일 에러를 숨기지 말고 "처음엔 이렇게 생각/작성했다 → 이런 이유로 틀렸다 → 교정됐다" 흐름으로 보여준다.
- **코드 블록 보존:** 코드, JSON, HTTP, 터미널 출력은 각각 `java`, `json`, `http`, `text` 언어 태그를 붙여 코드 블록으로 옮긴다. 의미 있는 내용을 임의로 축약하지 않는다.
- **선택적 섹션:** 원본에 없는 실습, 오류, 다음 날 예고 등을 억지로 추가하지 않는다.

## 5. 태그 금지

결과물 끝에 해시태그 줄(`#Spring #Backend #TIL ...` 등)을 **절대 넣지 않는다**. 정리 문단 뒤에는 바로 소스코드 링크로 이어간다.

## 6. 출력 구조

아래 구조로 하나의 Markdown 문서를 작성한다. 실제 소제목은 그날 주제에 맞게 자연스럽게 바꾸고, 원본에 없는 섹션은 생략한다.

```markdown
# [백엔드 기본기 DayNN] <핵심 주제 명사구>

> 그날 다룬 내용과 글에서 정리할 내용을 소개하는 2~3문장

## 1. <완성예제/개념에서 확인한 내용>

개념과 완성예제 코드를 정리한다.

## 2. <실습·독립변형에서 겪은 것>

실제로 겪은 오타·에러·오해와 그 원인을 코드·터미널 출력 블록과 함께 정리한다.

## 3. 스스로 묻고 답한 질문들

### Q. <quiz.md의 질문 1>

<정답/해설을 다듬은 답변>

### Q. <질문 2>

<답변>

## 정리하며

그날 배운 내용을 한 줄로 정리한다.

---

오늘 공부한 소스코드: [Developer-Roadmap_Spring_Study/app](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/tree/master/app)
```

## 7. GitHub 링크 규칙

문서의 마지막 줄에는 반드시 다음 링크를 넣는다.

```markdown
오늘 공부한 소스코드: [Developer-Roadmap_Spring_Study/app](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/tree/master/app)
```

- 정리 문단(`## 정리하며`) 바로 아래, `---` 구분선 다음 줄에 둔다.
- 링크 뒤에는 다른 본문, 주석, 안내, 태그를 추가하지 않는다.

## 8. 검수, 저장, 응답

다음 순서로 마무리한다.

1. 원본 사실(vocab/quiz/explain-log/코드/커밋)과 작성된 글을 대조해 새로 지어낸 내용이 없는지 확인한다.
2. 제목이 서술형이 아니라 명사구인지, 태그 줄이 없는지 확인한다.
3. 기술 설명, 코드 블록, Day 번호, GitHub 링크를 확인한다.
4. 결과물 전체를 검사해 한자를 모두 제거한다.
5. `app/study_docs/days/WeekX/DayNN_MMDD/velog_post.md`에 저장한다.
6. 채팅에도 완성된 Markdown 전체를 출력한다. 본문 속 세 개짜리 코드 펜스와 충돌하지 않도록 최상위 펜스에는 백틱 네 개와 `markdown`을 사용한다.
7. 완성된 Markdown 뒤에 저장 경로를 한 줄로 알린다. 그 밖의 불필요한 설명은 덧붙이지 않는다.
