# Day20 (9/28, Week C D6) 예측과 교정 기록

새 코드 실험은 없다. 이 기록은 시험 중 답이 무너진 두 문항(6·9번)의 근거가 어떻게 재구성됐는지를 남긴다.

## 재구성 1 — self-invocation(문항 6)

- 1차 답: "프록시가 메서드 호출을 가로채서 트랜잭션을 시작·종료하고, `this.inner()`로 부르면 프록시를 다시 안 거쳐서 적용이 안 됨" — 결론은 맞지만 "왜 다시 안 거치는가"를 묻자 "그냥 프록시가 아니니까"로 순환논리에 빠짐
- 교정: `this`는 자바 언어 수준에서 항상 **호출을 시작한 객체 자기 자신**을 가리킨다. `target.inner()`를 외부에서 부를 때만 그 호출이 프록시 인스턴스에서 시작되고, 프록시는 자기 내부의 `TransactionInterceptor` → target 위임을 거친다. target 내부의 `this.inner()`는 애초에 프록시 인스턴스에서 시작된 호출이 아니므로, "프록시를 우회한다"가 아니라 "그 호출 경로 자체에 프록시가 없다"가 더 정확한 표현이다.
- 차이: 이전 세 번의 답변(Day17·18·19)은 전부 "적용 안 됨"이라는 결과를 외우고 있었고, 이번 시험에서도 근거를 대라는 질문에 다시 결과로 답하는 패턴이 반복됐다. 결과와 근거를 분리해서 인출하는 연습이 필요하다는 게 이번 시험의 결론이다.

## 재구성 2 — AOP 프록시 vs Hibernate 프록시(문항 9)

- 1차 답: "둘 다 프록시니까 결국 같은 원리 아니에요?"
- 교정: `ReservationService$$SpringCGLIB$$0`(Day16)와 `Member$HibernateProxy`(Day18)를 나란히 놓고 "어느 쪽이 SQL을 지연시키느냐"는 질문을 받은 뒤, 앞쪽은 **메서드 호출**을 가로채 트랜잭션 같은 부가기능을 끼워 넣는 장치이고 뒤쪽은 **필드 접근**을 가로채 SELECT를 지연시키는 장치라는 걸 재구성했다.
- 차이: "프록시"라는 이름의 공통점이 오히려 학습을 방해한 사례다. Day18 velog_post의 4절(AOP Proxy와의 구분)에서 이미 표로 정리해뒀던 내용인데, 시험이라는 다른 맥락에서 다시 물으니 처음부터 다시 헷갈렸다.

## 검증 근거

- `src/test/java/com/example/studyroom/service/TransactionPropagationTest.java` (self-invocation·전파 관련 기존 테스트)
- `src/test/java/com/example/studyroom/repository/MemberLazyProxyTest.java`, `NPlusOneTest.java` (Hibernate 프록시·fetch join 근거)
- `app/study_docs/days/WeekC/Day16_0920/velog_post.md` 4절, `Day18_0925/velog_post.md` 4절 (기존 정리 재확인)

## [직접 작성] 오늘 배운 것을 내 문장으로

<!-- 아래는 학습자가 직접 채운다. 비워두지 말 것. -->

- self-invocation을 "결과"가 아니라 "근거"로 설명하려면 어디서부터 시작해야 하는지:
- "프록시"라는 이름이 붙은 두 장치를 헷갈리지 않으려면 무엇을 기준으로 구분해야 하는지:

## 다음 시작점

Week C D7 — 버퍼(기술부채 상환 + 독립과제).
