# Day28 (2026-10-10, Week D D7 버퍼) 진행 기록

> 주제: 기술부채 상환 — 401/403 오류 응답 형식 통일
> 상태: **완료.** 기술부채 원장에 명시된 "401/403 일관성" 항목을 해소했다. 400/404/409/500 응답은 이번 범위 밖으로 명확히 남겨뒀다.

## 1. 완료한 것

| 항목 | 내용 |
|---|---|
| `GlobalExceptionHandler.handleInvalidCredentials()` | 반환 타입 `Map<String,String>` → `Map<String,Object>`, `code`(`"UNAUTHORIZED"`)·`timestamp` 추가 |
| `CustomAuthenticationEntryPoint` | 401 JSON에 `code`(`"UNAUTHORIZED"`)·`timestamp` 추가 |
| `CustomAccessDeniedHandler` | 403 JSON에 `code`(`"FORBIDDEN"`)·`timestamp` 추가 |
| `ReservationControllerHttpTest`·`AuthControllerHttpTest` | 기존 3개 테스트에 `$.code`/`$.timestamp` 단언 추가(신규 테스트 없음) |

## 2. 겪은 오류

이번 유닛은 `./gradlew test`가 첫 실행부터 초록으로 끝났다 — 컴파일/런타임 오류가 없었다. `error` 필드를 유지하고 필드만 추가했기 때문에 기존 단언이 전부 그대로 통과했다. 오류를 지어내지 않고, 실제로 안 났다는 사실을 그대로 남긴다.

## 3. 상태 확인

| 시점 | 결과 |
|---|---|
| Day27 종료 시점 | `./gradlew test` BUILD SUCCESSFUL, 51/51 |
| 최종 | `./gradlew test --console=plain` BUILD SUCCESSFUL, 51/51(테스트 개수 변화 없음, 단언만 추가) |

## 4. 남은 한계·부채

- `timestamp`가 `Instant.now().toString()`(UTC, ISO-8601)이라 서버 타임존과 무관하게 항상 UTC로 찍힌다 — 클라이언트가 로컬 타임존으로 변환해야 한다는 점을 문서화하지 않았다.
- 400(Validation)/404(NotFound)/409(Duplicate)/500(Unexpected) 응답에는 `code`/`timestamp`가 없다 — 이번 부채 항목의 범위(401/403)를 벗어나므로 의도적으로 남겨뒀다.
- `CustomAuthenticationEntryPoint`/`CustomAccessDeniedHandler`는 여전히 문자열 직접 조합 방식이다 — Jackson `ObjectMapper`로 직렬화하는 방식이 이스케이핑 안전성 면에서 더 낫지만, 메시지에 특수문자가 없어 당장 위험은 낮다고 보고 남겨뒀다.
- 기술부채 원장(`app/study_docs/기술부채.md`) 갱신은 이 커밋에 포함하지 않았다 — 문서화 단계 담당자가 이 기록과 커밋 해시를 근거로 반영한다.

## 5. [직접 작성] 오늘 배운 것을 내 문장으로

<!-- 아래는 학습자가 직접 채운다. 비워두지 말 것. -->

- 응답 형식을 통일할 때 기존 필드를 지우지 않고 추가만 하는 것이 왜 더 안전한 변경인지:
- 401과 403을 서로 다른 컴포넌트가 만들면서도 같은 모양으로 응답하게 만드는 것이 왜 가능한지:

## 6. 다음 시작점

Week D 전체 완료. 다음은 Week E D1 — 로깅과 설정관리.

---

커밋 해시: `d38c973` — `study(day28): 401/403 응답에 code·timestamp 필드 추가(일관성)`
