# 신고 처리 API Spec

> [api-design-guide](../api-design-guide.md) · [error-response-guide](../error-response-guide.md)를 따른다. 모든 응답은 공통 래퍼.
> 관련 유저 스토리: [user-stories](../../requirements/user-stories.md)

게시글(POST)·댓글(COMMENT)·채팅 메시지(MESSAGE)에 대한 신고를 접수·저장하고, 신고 사유 enum 메타를 노출한다. MVP 범위는 신고 접수/저장 및 사유 메타 조회까지이며, 운영자(관리자 액터)의 검토·제재·상태 전이 흐름은 **(확인 필요)** 로 본 스펙에 포함하지 않는다. 신고는 접수 시 `status=RECEIVED` 로 고정 저장한다.

> **예약(Booking) 신고는 본 스펙이 아니라 [04-booking-inquiry-chat](04-booking-inquiry-chat.md)이 접수한다** — 신고 접수는 대상 존재·참여자 권한 검증이 필요해 대상을 아는 모듈이 담당하며, 본 스펙의 대상(`POST`/`COMMENT`/`MESSAGE`)과 겹치지 않는다.

## 엔드포인트 요약

| Method | Path | 설명 | 인증 | 성공 status |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/reports/reasons` | 신고 사유 enum 메타 목록 조회 | 불필요 | 200 |
| POST | `/api/v1/reports` | 콘텐츠 신고 접수 | 필수 | 201 |

> 신고 목록/단건 조회(`GET /api/v1/reports`, `GET /api/v1/reports/{reportId}`)는 운영자 검토 흐름에 속하므로 본 MVP에서 정의하지 않는다 **(확인 필요)**. POST 응답의 `reportId` 는 향후 단건 조회 도입을 위한 식별자로 미리 노출하되, `Location` 헤더는 대상 엔드포인트(`GET /api/v1/reports/{reportId}`)가 실제 도입된 뒤에만 부여한다(api-design-guide §3-2의 `Location` 은 선택 사항).

## 상세

### GET /api/v1/reports/reasons

신고 사유 enum 목록을 메타로 반환한다. 클라이언트는 이 목록으로 신고 사유 선택지를 구성한다.

- **인증**: 불필요
- **페이지네이션**: 없음. 사유는 고정·소규모 집합이므로 전체를 한 번에 반환한다(오프셋/커서 미적용, api-design-guide §4 비적용).

#### Path / Query 파라미터

없음.

#### Request Body

없음.

#### 성공 Response (200, 공통 래퍼)

```jsonc
{
  "success": true,
  "data": {
    "reasons": [
      { "code": "SPAM",             "label": "스팸/광고" },
      { "code": "ABUSE",            "label": "욕설/괴롭힘" },
      { "code": "SEXUAL_CONTENT",   "label": "성적 콘텐츠" },
      { "code": "EXTERNAL_CONTACT", "label": "외부 연락처 유도" },
      { "code": "FALSE_INFO",       "label": "허위 정보" },
      { "code": "ETC",              "label": "기타" }
    ]
  },
  "error": null
}
```

> `label` 은 서버 기본 한국어 문구이며 다국어 표기는 클라이언트가 `code` 로 매핑한다(error-response-guide §7과 동일 원칙). `ETC` 선택 시 신고 접수에서 `detail` 입력을 권장한다 **(확인 필요: ETC 시 detail 필수화 여부)**.

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 405 | `METHOD_NOT_ALLOWED` | 미허용 메서드(공통) |
| 500 | `INTERNAL_ERROR` | 서버 내부 오류(공통) |

---

### POST /api/v1/reports

콘텐츠 신고 1건을 접수·저장한다. 신고자는 JWT subject 로 식별하며, 동일 사용자의 동일 대상 중복 신고는 거부한다.

- **인증**: 필수 (`Authorization: Bearer <accessToken>`)
- **권한**: `targetType=MESSAGE` 신고는 **요청자가 해당 채팅방 참여자일 때만** 허용한다(타인 대화 열람·신고 차단). 참여자가 아니면 `403 FORBIDDEN`([04-booking-inquiry-chat](04-booking-inquiry-chat.md)의 채팅방 접근 규약과 일치). 본인 작성 콘텐츠 자기 신고 차단 여부는 **(확인 필요)** — 차단 정책 적용 시 `422 REPORT_SELF_TARGET`.
- **멱등성**: 동일 사용자·동일 대상은 DB 유니크 제약 `(reporterId, targetType, targetId)` 으로 1건만 허용한다. 중복 시 신규 생성 없이 `409 REPORT_ALREADY_EXISTS` 를 반환한다.
- **레이트리밋**: `reporterId` 기준 호출 한도를 적용한다(임계값 **(확인 필요)**). 초과 시 `429 TOO_MANY_REQUESTS` + `Retry-After`.

#### Path / Query 파라미터

없음.

#### Request Body (JSON, 래퍼 없이)

```jsonc
{
  "targetType": "POST",        // 필수. enum: POST | COMMENT | MESSAGE (UPPER_SNAKE)
  "targetId": 101,             // 필수. Long. 신고 대상 리소스 ID
  "reason": "SPAM",            // 필수. enum: SPAM | ABUSE | SEXUAL_CONTENT | EXTERNAL_CONTACT | FALSE_INFO | ETC
  "detail": "광고 링크 도배"    // 선택. 자유 텍스트(최대 길이 (확인 필요: 예 500자))
}
```

| 필드 | 타입 | 필수 | 제약 |
| --- | --- | --- | --- |
| `targetType` | string(enum) | 필수 | `POST` / `COMMENT` / `MESSAGE` 중 하나 |
| `targetId` | number(Long) | 필수 | 양의 정수, 존재하는 대상 |
| `reason` | string(enum) | 필수 | `GET /api/v1/reports/reasons` 의 `code` 중 하나 |
| `detail` | string | 선택 | 최대 길이 제한 적용(초과 시 `INVALID_INPUT`) |

#### 성공 Response (201, 공통 래퍼)

```jsonc
{
  "success": true,
  "data": {
    "reportId": 9001,
    "targetType": "POST",
    "targetId": 101,
    "reason": "SPAM",
    "status": "RECEIVED",
    "createdAt": "2026-06-15T08:30:00Z"   // UTC ISO-8601
  },
  "error": null
}
```

> 응답에는 `reporterId` 등 신고자 식별 정보와 `detail` 원문을 노출하지 않는다(민감정보·프라이버시 보호, error-response-guide §6). `status` 는 접수 시 항상 `RECEIVED`. `Location` 헤더는 단건 조회 엔드포인트 도입 후에만 부여한다(위 엔드포인트 요약 참조).

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | 필수값 누락, 미정의 enum, `detail` 길이 초과 등(공통). 필드 상세는 `errors[]` |
| 400 | `MALFORMED_REQUEST` | JSON 파싱 불가/타입 불일치(공통) |
| 401 | `UNAUTHENTICATED` | 토큰 없음/위조(공통) |
| 401 | `TOKEN_EXPIRED` | 액세스 토큰 만료, 재발급 유도(공통) |
| 403 | `FORBIDDEN` | `MESSAGE` 신고 시 요청자가 해당 채팅방 참여자가 아님(공통) |
| 404 | `REPORT_TARGET_NOT_FOUND` | 신고 대상(`targetType`+`targetId`)이 존재하지 않음 |
| 409 | `REPORT_ALREADY_EXISTS` | 동일 사용자가 동일 대상을 이미 신고함(중복) |
| 422 | `REPORT_SELF_TARGET` | 본인 콘텐츠 신고(자기 신고 차단 정책 적용 시 **(확인 필요)**) |
| 429 | `TOO_MANY_REQUESTS` | 신고 도배 한도 초과(공통). `Retry-After` 포함 |
| 500 | `INTERNAL_ERROR` | 서버 내부 오류(공통) |

> 검증·권한 평가 순서: 인증(401) → 레이트리밋(429) → 입력 검증(400) → 대상 존재(404) → 참여 권한·자기 신고(403/422) → 중복(409).

## 도메인 에러 코드

> 공통 코드(`INVALID_INPUT`, `MALFORMED_REQUEST`, `UNAUTHENTICATED`, `TOKEN_EXPIRED`, `FORBIDDEN`, `METHOD_NOT_ALLOWED`, `TOO_MANY_REQUESTS`, `INTERNAL_ERROR` 등)는 [error-response-guide](../error-response-guide.md) §4 카탈로그를 따르며 여기서 재정의하지 않는다. 아래는 본 기능 고유 코드만 정의한다. prefix 는 `REPORT`.

| code | status | 의미 |
| --- | --- | --- |
| `REPORT_TARGET_NOT_FOUND` | 404 | 신고 대상 리소스(`targetType`+`targetId`)가 존재하지 않음 |
| `REPORT_ALREADY_EXISTS` | 409 | 동일 신고자가 동일 대상을 이미 신고함. DB 유니크 제약 `(reporterId, targetType, targetId)` 위반 |
| `REPORT_SELF_TARGET` | 422 | 본인이 작성한 콘텐츠를 신고함(자기 신고 차단 정책 적용 시) **(확인 필요)** |

> `MESSAGE` 대상 신고의 참여자 권한 위반은 공통 코드 `FORBIDDEN`(403)을 사용하며 도메인 코드를 신설하지 않는다(error-response-guide §3 인가 매핑 준수).
