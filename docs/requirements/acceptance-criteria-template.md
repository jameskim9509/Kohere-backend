# Acceptance Criteria Template

> 예시(Spring Boot 기준)입니다. 아래 시나리오는 "모임(meeting) 도메인" 샘플이며,
> 실제 도메인 규칙/에러 코드로 교체하세요. 에러 응답 형식은
> [error-response-guide](../api/error-response-guide.md)를 따른다고 가정합니다.

## 목적

유저 스토리가 "완료"되었다고 판단하는 **객관적이고 검증 가능한 조건**을 정의한다.
인수 조건(AC)은 개발/QA/기획이 같은 기준으로 합의하기 위한 것이며,
그대로 **테스트 케이스로 전환**될 수 있어야 한다.

- 각 AC는 [user-story-template](./user-story-template.md)의 스토리와 짝을 이룬다.
- AC는 Given/When/Then 형식으로 행위와 기대 결과를 명확히 분리한다.
- happy path 뿐 아니라 **실패/경계/권한** 케이스를 포함한다.

---

## 표준 템플릿 (Given / When / Then)

```text
시나리오: [무엇을 검증하는가]

Given <사전 조건/상태>
And   <추가 사전 조건>
When  <사용자/시스템 행위>
Then  <관찰 가능한 기대 결과>
And   <추가 기대 결과>
```

| 절 | 의미 | 작성 팁 |
|---|---|---|
| Given | 테스트 시작 시 시스템 상태 | 데이터/인증 상태 등 전제. "이미 ~인 상태" |
| When | 검증 대상 행위(1개 권장) | 하나의 트리거. 동사로 시작 |
| Then | 외부에서 관찰 가능한 결과 | 상태코드, 응답 필드, DB 변화, 이벤트 발행 |

---

## 작성 규칙

1. 하나의 시나리오는 하나의 동작/결과에 집중한다. When이 여러 개면 분리한다.
2. Then은 **내부 구현이 아니라 관찰 가능한 결과**로 쓴다. (응답 코드, 응답 본문, 부수효과)
3. **happy path만 작성하지 않는다.** 실패/경계/권한 시나리오를 반드시 포함한다
   ([testing](../testing/testing-strategy.md) 규칙과 일치).
4. 에러는 표준 에러 응답 형식과 에러 코드로 명시한다
   ([error-response-guide](../api/error-response-guide.md)).
5. 모호한 단어("적절히", "빠르게")를 금지하고 수치/코드로 명시한다.
6. 동시성/멱등성 같은 비기능 요구는 별도 시나리오로 둔다.

---

## 채워진 예시 1 — 모임 생성

> 대상 스토리: [user-story-template](./user-story-template.md) `US-MEETING-001`

```text
시나리오 1-1 (정상): 유효한 입력으로 모임을 생성한다
Given 호스트 권한을 가진 사용자가 유효한 JWT로 인증되어 있고
And   요청 본문이 { "name": "주말 등산", "capacity": 10, "startAt": "2026-07-01T09:00:00Z" } 이며
And   필수 값이 모두 유효 범위(정원 2~100) 안에 있을 때
When  POST /v1/meetings 를 호출하면
Then  HTTP 201 Created 를 응답하고
And   응답 본문에 생성된 meetingId 와 status="OPEN" 이 포함되며
And   meetings 테이블에 해당 레코드가 1건 저장된다

시나리오 1-2 (검증 실패): 정원이 허용 범위를 벗어난다
Given 호스트가 인증되어 있고
And   요청 본문의 capacity 가 1 (최소 2 미만) 일 때
When  POST /v1/meetings 를 호출하면
Then  HTTP 400 Bad Request 를 응답하고
And   에러 코드 "VALIDATION_ERROR" 와 field="capacity" 가 응답에 포함되며
And   레코드는 저장되지 않는다

시나리오 1-3 (인증 없음): 비로그인 사용자가 생성을 시도한다
Given 유효한 인증 토큰이 없을 때
When  POST /v1/meetings 를 호출하면
Then  HTTP 401 Unauthorized 를 응답한다
```

에러 응답 예시(시나리오 1-2):

```json
{
  "code": "VALIDATION_ERROR",
  "message": "정원은 2명 이상 100명 이하여야 합니다.",
  "errors": [
    { "field": "capacity", "reason": "must be between 2 and 100" }
  ],
  "traceId": "00-aaaa1111bbbb2222cccc3333dddd4444-5555eeee6666ffff-01"
}
```

---

## 채워진 예시 2 — 모임 참가 신청 (경계/동시성 포함)

> 대상 스토리: [user-story-template](./user-story-template.md) `US-MEETING-002`

```text
시나리오 2-1 (정상): 정원 여유가 있는 모임에 참가 신청한다
Given 사용자가 인증되어 있고
And   meetingId=42 모임의 status="OPEN" 이며 정원에 여유가 있을 때
When  POST /v1/meetings/42/applications 를 호출하면
Then  HTTP 201 Created 를 응답하고
And   해당 사용자의 참가 상태가 "APPLIED" 로 저장된다

시나리오 2-2 (중복): 이미 신청한 모임에 다시 신청한다
Given 사용자가 meetingId=42 에 이미 "APPLIED" 상태일 때
When  POST /v1/meetings/42/applications 를 호출하면
Then  HTTP 409 Conflict 를 응답하고
And   에러 코드 "ALREADY_APPLIED" 가 포함된다

시나리오 2-3 (정원 초과, 동시성): 마지막 1자리를 두 명이 동시에 신청한다
Given meetingId=42 의 남은 정원이 1명일 때
When  두 사용자가 거의 동시에 참가 신청을 보내면
Then  정확히 1건만 201 로 성공하고
And   나머지 1건은 409 Conflict, 에러 코드 "CAPACITY_EXCEEDED" 를 응답하며
And   참가자 수가 정원을 초과하지 않는다
```

---

## 테스트 매핑 가이드

| AC 유형 | 권장 테스트 레벨 | 참고 |
|---|---|---|
| 입력 검증/도메인 규칙 | 단위 테스트 | [unit-test-guide](../testing/unit-test-guide.md) |
| API 동작/상태코드/DB 변화 | 통합 테스트(Testcontainers) | [integration-test-guide](../testing/integration-test-guide.md) |
| 사용자 흐름 전체 | E2E | [e2e-test-guide](../testing/e2e-test-guide.md) |
| 동시성/멱등성 | 통합 + 동시성 테스트 | [non-functional-requirements](./non-functional-requirements.md) §7 |

---

## 체크리스트

- [ ] 모든 AC가 Given/When/Then 형식이다.
- [ ] When 절이 단일 행위로 분리되어 있다.
- [ ] Then이 관찰 가능한 결과(코드/본문/부수효과)로 기술되었다.
- [ ] happy path 외 실패/경계/권한/동시성 케이스를 포함한다.
- [ ] 에러는 표준 에러 코드/형식([error-response-guide](../api/error-response-guide.md))을 사용한다.
- [ ] 대상 [user-story-template](./user-story-template.md) 스토리와 상호 링크되어 있다.
- [ ] 프로젝트 확정 후 갱신
