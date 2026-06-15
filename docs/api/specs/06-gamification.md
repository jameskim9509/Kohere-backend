# 게이미피케이션 (퀴즈 · 포인트) API Spec

> [api-design-guide](../api-design-guide.md) · [error-response-guide](../error-response-guide.md)를 따른다. 모든 응답은 공통 래퍼.
> 관련 유저 스토리: [user-stories](../../requirements/user-stories.md)

## 개요

오늘의 퀴즈(하루 1개, 4지선다)를 조회하고 정답을 제출하면 서버가 정답을 판정한다. 정답 시 `QUIZ_CORRECT` 사유로 포인트가 적립되며, 사용자는 자신의 포인트 합계와 적립 내역을 조회할 수 있다.

- **정답 판정은 서버 전용**: 저장된 정답과 대조해 판정하고, 클라이언트가 보낸 정답 여부는 신뢰하지 않는다. 정답·해설은 **제출을 마친 사용자에게만** 공개한다.
- **하루 1회 제한**: 제출은 하루 1회로 제한된다. 동시 중복 제출은 `(userId, quizDate)` 유니크 제약으로 1건만 성공하며, 채점·적립은 단일 트랜잭션에서 처리한다(포인트 1회만 적립).
- **인증**: 모든 엔드포인트는 인증 주체별 상태에 종속되므로 인증 필수다. 조회는 인증 주체로만 필터링되어 타인 데이터를 반환하지 않는다.

### 핵심 개념·enum

| 개념 | 값 | 설명 |
| --- | --- | --- |
| 보기 키 `choice` / `selectedChoice` / `correctChoice` | `A`, `B`, `C`, `D` | 4지선다 보기 식별 키. 단일 대문자이며, 요청 시 이 네 값 중 하나여야 한다(그 외 값은 검증 실패) |
| 적립 사유 `reason` (enum) | `QUIZ_CORRECT` | 포인트 적립 사유. UPPER_SNAKE_CASE. 현재 범위에서는 정답 적립만 발생 |

- 날짜만 표기는 `YYYY-MM-DD`(예: `quizDate`), 시각은 ISO-8601 UTC(예: `2026-06-15T01:12:30Z`).
- 포인트(`totalPoint`/`amount`/`earnedPoint`)는 **포인트 정수**이며 KRW 금액이 아니다. 사용처·차감(음수)은 본 범위 밖.
- 보기 키 `A`~`D`는 4지선다 식별용 단일 대문자 키이며, 의미를 갖는 도메인 enum이 아니다(적립 사유 `QUIZ_CORRECT`만 UPPER_SNAKE enum).

---

## 엔드포인트 요약

| Method | Path | 설명 | 인증 | 성공 status |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/quizzes/today` | 오늘의 퀴즈 조회(제출 여부 포함) | 필수 | 200 |
| POST | `/api/v1/quizzes/{quizId}/submission` | 정답 제출 및 즉시 채점·적립 | 필수 | 201 |
| GET | `/api/v1/points/summary` | 내 현재 포인트 합계 조회 | 필수 | 200 |
| GET | `/api/v1/points/histories` | 내 포인트 적립 내역 조회(오프셋 페이지) | 필수 | 200 |

---

## 상세

### 1. GET `/api/v1/quizzes/today` — 오늘의 퀴즈 조회

서버 기준 오늘 날짜(UTC date)에 해당하는 퀴즈 1개를 조회한다. 사용자가 오늘 이미 제출했으면 제출 결과(정답·해설 포함)를, 아직이면 정답·해설을 **가린** 문제만 반환한다.

- **인증**: 필수
- Path 파라미터: 없음
- Query 파라미터: 없음
- Request Body: 없음

#### 성공 Response — 200 OK (미제출)

```jsonc
{
  "success": true,
  "data": {
    "quizId": 4021,
    "quizDate": "2026-06-15",
    "question": "한국에서 전세 계약 시 임차인을 보호하는 제도는?",
    "choices": [
      { "key": "A", "text": "확정일자" },
      { "key": "B", "text": "관리비 정산" },
      { "key": "C", "text": "중도금 대출" },
      { "key": "D", "text": "재산세 납부" }
    ],
    "submitted": false
  },
  "error": null
}
```

#### 성공 Response — 200 OK (이미 제출)

```jsonc
{
  "success": true,
  "data": {
    "quizId": 4021,
    "quizDate": "2026-06-15",
    "question": "한국에서 전세 계약 시 임차인을 보호하는 제도는?",
    "choices": [
      { "key": "A", "text": "확정일자" },
      { "key": "B", "text": "관리비 정산" },
      { "key": "C", "text": "중도금 대출" },
      { "key": "D", "text": "재산세 납부" }
    ],
    "submitted": true,
    "result": {
      "selectedChoice": "A",
      "correct": true,
      "correctChoice": "A",
      "explanation": "확정일자를 받으면 대항력과 우선변제권을 확보할 수 있습니다.",
      "earnedPoint": 10,
      "submittedAt": "2026-06-15T01:12:30Z"
    }
  },
  "error": null
}
```

> `correctChoice`/`explanation`/`result`는 **제출을 마친 사용자에게만** 내려간다. 미제출 응답에는 포함되지 않는다.

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 없음/만료/위조 |
| 404 | `QUIZ_NOT_FOUND` | 오늘 날짜에 매칭되는 퀴즈가 없음 |

---

### 2. POST `/api/v1/quizzes/{quizId}/submission` — 정답 제출 및 즉시 채점·적립

사용자가 선택한 보기를 제출한다. 서버가 저장된 정답과 대조해 정답 여부를 판정하고, 정답이면 `QUIZ_CORRECT` 사유로 포인트를 적립한다. 제출은 하루 1회만 허용되며, 같은 사용자의 같은 날짜 제출은 `(userId, quizDate)` 유니크 제약으로 1건만 성공한다(동시 요청 멱등 보장). 채점·적립은 단일 트랜잭션에서 처리한다.

- **인증**: 필수

#### Path 파라미터

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `quizId` | Long | 필수 | 제출 대상 퀴즈 ID. 오늘의 퀴즈여야 한다 |

Query 파라미터: 없음

#### Request Body

```jsonc
{
  "selectedChoice": "B"
}
```

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `selectedChoice` | string | 필수 | 보기 키 `A`/`B`/`C`/`D` 중 하나(단일 대문자). 그 외 값·빈 값·누락 시 `INVALID_INPUT` |

#### 성공 Response — 201 Created

`Location: /api/v1/quizzes/{quizId}/submission`

```jsonc
{
  "success": true,
  "data": {
    "quizId": 4021,
    "selectedChoice": "B",
    "correct": false,
    "correctChoice": "A",
    "explanation": "확정일자를 받으면 대항력과 우선변제권을 확보할 수 있습니다.",
    "earnedPoint": 0,
    "totalPoint": 120,
    "submittedAt": "2026-06-15T01:12:30Z"
  },
  "error": null
}
```

> 정답인 경우 `correct=true`, `earnedPoint=10`(적립 포인트 — 정책값, 확인 필요), `totalPoint`는 적립 반영 후 합계. 오답인 경우 `earnedPoint=0`이고 적립 로그는 생성되지 않는다.

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `selectedChoice` 누락/빈값/허용 외 값 |
| 400 | `MALFORMED_REQUEST` | JSON 파싱 불가/타입 불일치 |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 없음/만료/위조 |
| 404 | `QUIZ_NOT_FOUND` | 경로의 `quizId`가 존재하지 않음 |
| 409 | `QUIZ_ALREADY_SUBMITTED` | 오늘 퀴즈를 이미 제출함(하루 1회 초과/동시 중복 제출) |
| 422 | `QUIZ_NOT_TODAY` | 존재하나 오늘의 퀴즈가 아님(과거/미래 분 제출 불가) |

---

### 3. GET `/api/v1/points/summary` — 내 포인트 합계 조회

인증 주체의 현재 포인트 합계를 반환한다. 적립 로그(현재 `QUIZ_CORRECT`만)의 집계 결과다.

- **인증**: 필수
- Path 파라미터: 없음
- Query 파라미터: 없음
- Request Body: 없음

#### 성공 Response — 200 OK

```jsonc
{
  "success": true,
  "data": {
    "totalPoint": 120
  },
  "error": null
}
```

> `totalPoint`는 포인트 정수(KRW 금액 아님).

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 없음/만료/위조 |

---

### 4. GET `/api/v1/points/histories` — 내 포인트 적립 내역 조회

인증 주체의 포인트 적립 내역을 **오프셋 기반 페이지네이션**(api-design-guide §4-1)으로 반환한다. 본인 내역만 조회되며, 타 사용자 내역은 노출하지 않는다.

- **인증**: 필수
- Path 파라미터: 없음

#### Query 파라미터

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `page` | int | 선택 | `0` | 0-base 페이지 번호 |
| `size` | int | 선택 | `20` | 페이지 크기(최대 100). 0/음수·100 초과는 `INVALID_INPUT` |
| `sort` | string | 선택 | `createdAt,desc` | 정렬 키`,`방향. 허용 키: `createdAt` |

Request Body: 없음

#### 성공 Response — 200 OK

```jsonc
{
  "success": true,
  "data": {
    "content": [
      {
        "historyId": 9001,
        "amount": 10,
        "reason": "QUIZ_CORRECT",
        "createdAt": "2026-06-15T01:12:30Z"
      },
      {
        "historyId": 8800,
        "amount": 10,
        "reason": "QUIZ_CORRECT",
        "createdAt": "2026-06-14T02:03:11Z"
      }
    ],
    "page": {
      "number": 0,
      "size": 20,
      "totalElements": 12,
      "totalPages": 1,
      "hasNext": false
    }
  },
  "error": null
}
```

> `reason`은 적립 사유 enum(UPPER_SNAKE). 현재 범위에서는 `QUIZ_CORRECT`만 발생한다. `amount`는 적립 포인트 정수(양수)이며 차감(음수)은 본 범위 밖.

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | 잘못된 `page`/`size`/`sort` |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 없음/만료/위조 |

---

## 도메인 에러 코드

> 공통 코드(`INVALID_INPUT`, `MALFORMED_REQUEST`, `UNAUTHENTICATED`, `TOKEN_EXPIRED`, `INTERNAL_ERROR` 등)는 [error-response-guide](../error-response-guide.md) §4의 정의를 그대로 쓰며 여기서 재정의하지 않는다. 5xx(`INTERNAL_ERROR` 등)는 전 엔드포인트에 공통 적용되므로 개별 표에 반복 기재하지 않는다. 아래는 본 도메인 고유 코드만 정의한다(prefix `QUIZ`). 포인트 조회는 도메인 고유 에러가 없어 `POINT_*` 코드를 정의하지 않는다(공통 코드만 사용).

| code | status | 의미 |
| --- | --- | --- |
| `QUIZ_NOT_FOUND` | 404 | 오늘의 퀴즈가 없거나, 제출 대상 `quizId`가 존재하지 않음 |
| `QUIZ_NOT_TODAY` | 422 | 요청한 퀴즈가 존재하나 오늘의 퀴즈가 아님(과거/미래 분 제출 불가) |
| `QUIZ_ALREADY_SUBMITTED` | 409 | 오늘 퀴즈를 이미 제출함(하루 1회 제한 / 동시 중복 제출 차단) |
