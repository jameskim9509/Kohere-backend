# User Story & Acceptance Criteria Template

> 예시(Spring Boot 기준)입니다. 아래 스토리/시나리오는 "모임(meeting) 도메인" **샘플**이며, 실제 도메인 용어/역할/규칙/에러 코드로 교체하세요.
> 에러 응답 형식은 [error-response-guide](../api/error-response-guide.md)를 따른다고 가정합니다.

## 목적

기능 요구사항을 **사용자 관점의 작은 단위(유저 스토리)** 로 표현하고, 그것이 "완료"되었다고 판단하는 **객관적·검증 가능한 인수 조건(AC)** 을 한 쌍으로 정의한다. 스토리는 "누가·무엇을·왜"의 합의이고, AC는 그대로 **테스트 케이스로 전환**될 수 있어야 한다.

---

## Part A. 유저 스토리

### A.1 표준 템플릿

```text
제목: [기능 요약]

As a   <역할/페르소나>
I want <원하는 기능/행동>
So that <얻고자 하는 가치/이유>
```

| 메타데이터 | 설명 | 예시 |
|---|---|---|
| ID | 추적용 식별자 | `US-MEETING-001` |
| 우선순위 | MoSCoW | Must / Should / Could / Won't |
| 추정 | 상대 추정치 | 3 SP (Story Point) |
| 상태 | 진행 상태 | Draft / Ready / In Progress / Done |
| 관련 NFR | 품질 제약 | [non-functional-requirements](./non-functional-requirements.md) |
| 인수 조건 | 아래 Part B의 해당 시나리오 | §예시 |

### A.2 작성 규칙

1. **INVEST 원칙**을 만족하는가: Independent / Negotiable / Valuable / Estimable / Small / Testable.
2. 역할(As a)은 모호하지 않게 **구체적 페르소나**로(예: "모임 호스트").
3. 하나의 스토리는 하나의 가치에 집중한다("그리고/또한"이 많으면 분할).
4. 화면/버튼/테이블 같은 **구현 디테일은 넣지 않는다**(그건 AC와 설계에서).
5. "왜(So that)"가 없는 스토리는 가치 없는 작업일 수 있으므로 반드시 채운다.

---

## Part B. 인수 조건 (Acceptance Criteria)

### B.1 표준 템플릿 (Given / When / Then)

```text
시나리오: [무엇을 검증하는가]

Given <사전 조건/상태>
When  <사용자/시스템 행위>
Then  <관찰 가능한 기대 결과>
```

| 절 | 의미 | 작성 팁 |
|---|---|---|
| Given | 테스트 시작 시 시스템 상태 | 데이터/인증 상태 등 전제. "이미 ~인 상태" |
| When | 검증 대상 행위(1개 권장) | 하나의 트리거. 동사로 시작 |
| Then | 외부에서 관찰 가능한 결과 | 상태코드, 응답 필드, DB 변화, 이벤트 발행 |

### B.2 작성 규칙

1. 하나의 시나리오는 하나의 동작/결과에 집중한다(When이 여러 개면 분리).
2. Then은 **내부 구현이 아니라 관찰 가능한 결과**로 쓴다(응답 코드/본문/부수효과).
3. **happy path만 작성하지 않는다.** 실패/경계/권한/동시성 시나리오를 반드시 포함한다([testing-strategy](../testing/testing-strategy.md) 규칙과 일치).
4. 에러는 표준 에러 코드/형식으로 명시한다([error-response-guide](../api/error-response-guide.md)).
5. 모호한 단어("적절히", "빠르게")를 금지하고 수치/코드로 명시한다.

---

## 예시 1 — 모임 생성 (`US-MEETING-001`)

**유저 스토리**

```text
As a   로그인한 모임 호스트
I want 모임 이름, 일시, 정원, 장소를 입력해 모임을 생성하고 싶다
So that 다른 사용자를 초대해 함께 일정을 진행할 수 있다
```

| 항목 | 값 |
|---|---|
| 우선순위 | Must · 추정 3 SP · 상태 Ready |
| 관련 NFR | 쓰기 API p95 ≤ 400ms ([non-functional-requirements](./non-functional-requirements.md)) |

비고: 정원은 2~100명 범위로 가정. 인증 필요(JWT), 비로그인 사용자는 범위 밖.

**인수 조건**

```text
시나리오 1-1 (정상): 유효한 입력으로 모임을 생성한다
Given 호스트 권한 사용자가 유효한 JWT로 인증되어 있고
And   요청 본문 { "name": "주말 등산", "capacity": 10, "startAt": "2026-07-01T09:00:00Z" } 의 값이 유효 범위(정원 2~100)일 때
When  POST /v1/meetings 를 호출하면
Then  HTTP 201 Created + 응답에 meetingId·status="OPEN" 포함, meetings 테이블에 1건 저장

시나리오 1-2 (검증 실패): 정원이 허용 범위를 벗어난다
Given 호스트가 인증되어 있고, capacity 가 1 (최소 2 미만) 일 때
When  POST /v1/meetings 를 호출하면
Then  HTTP 400 + 에러 코드 "VALIDATION_ERROR", field="capacity", 레코드 미저장

시나리오 1-3 (인증 없음): 비로그인 사용자가 생성을 시도한다
Given 유효한 인증 토큰이 없을 때
When  POST /v1/meetings 를 호출하면
Then  HTTP 401 Unauthorized
```

에러 응답 예시(1-2) — 평면 표준 스키마:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "정원은 2명 이상 100명 이하여야 합니다.",
  "status": 400,
  "errors": [ { "field": "capacity", "reason": "must be between 2 and 100" } ],
  "traceId": "aaaa1111bbbb2222cccc3333dddd4444"
}
```

---

## 예시 2 — 모임 참가 신청 (`US-MEETING-002`, 경계/동시성 포함)

**유저 스토리**

```text
As a   로그인한 일반 사용자
I want 공개된 모임에 참가 신청을 하고 싶다
So that 관심 있는 모임에 참여 의사를 전달할 수 있다
```

| 항목 | 값 |
|---|---|
| 우선순위 | Must · 추정 2 SP · 상태 Draft |
| 관련 NFR | 정원 초과/중복 신청 동시성 처리 ([non-functional-requirements](./non-functional-requirements.md) §7) |

**인수 조건**

```text
시나리오 2-1 (정상): 정원 여유가 있는 모임에 참가 신청한다
Given 인증된 사용자, meetingId=42 가 status="OPEN" 이며 정원 여유가 있을 때
When  POST /v1/meetings/42/applications 를 호출하면
Then  HTTP 201 Created + 참가 상태 "APPLIED" 저장

시나리오 2-2 (중복): 이미 신청한 모임에 다시 신청한다
Given 사용자가 meetingId=42 에 이미 "APPLIED" 상태일 때
When  POST /v1/meetings/42/applications 를 호출하면
Then  HTTP 409 Conflict + 에러 코드 "ALREADY_APPLIED"

시나리오 2-3 (정원 초과·동시성): 마지막 1자리를 두 명이 동시에 신청한다
Given meetingId=42 의 남은 정원이 1명일 때
When  두 사용자가 거의 동시에 참가 신청을 보내면
Then  정확히 1건만 201, 나머지는 409 "CAPACITY_EXCEEDED", 참가자 수가 정원을 초과하지 않음
```

---

## 테스트 매핑 가이드

| AC 유형 | 권장 테스트 레벨 | 참고 |
|---|---|---|
| 입력 검증/도메인 규칙 | 단위 테스트 | [testing-strategy §6](../testing/testing-strategy.md) |
| API 동작/상태코드/DB 변화 | 통합 테스트(Testcontainers) | [testing-strategy §7](../testing/testing-strategy.md) |
| 사용자 흐름 전체 | E2E | [testing-strategy §8](../testing/testing-strategy.md) |
| 동시성/멱등성 | 통합 + 동시성 테스트 | [non-functional-requirements §7](./non-functional-requirements.md) |

---

## 체크리스트

- [ ] 역할(As a)이 구체적 페르소나이고 가치(So that)가 비어 있지 않다
- [ ] 요구(WHAT/WHY)로 기술되고 구현(HOW)이 들어가지 않았다(INVEST, 특히 Small/Testable)
- [ ] 각 스토리가 Given/When/Then AC와 짝을 이룬다
- [ ] AC가 happy path 외 실패/경계/권한/동시성 케이스를 포함한다
- [ ] 에러는 표준 코드/형식([error-response-guide](../api/error-response-guide.md))을 사용한다
- [ ] 관련 NFR을 참조 링크했다
- [ ] 프로젝트 확정 후 갱신
