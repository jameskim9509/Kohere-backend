# 맞춤 진단 & 매물 추천 API Spec

> [api-design-guide](../api-design-guide.md) · [error-response-guide](../error-response-guide.md)를 따른다. 모든 응답은 공통 래퍼.
> 관련 유저 스토리: [user-stories](../../requirements/user-stories.md)

## 개요

5단계 진단(지역 / 입국 목적 / 주거 환경 조건 / 월 예산 / ARC 발급 여부)을 제출·저장하고, 진단 조건으로 매칭한 매물 리스트(매물 탐색 도메인의 요약 DTO 재사용)와 지도용 좌표를 반환한다. 진단 이력·재진단·최근 진단 다시 보기를 제공한다.

공통 규약:

- 경로 프리픽스 `/api/v1`, 경로는 kebab-case, JSON 필드·쿼리 파라미터는 lowerCamelCase.
- enum은 UPPER_SNAKE 문자열, 시각은 UTC ISO-8601(`2026-06-15T08:30:00Z`), 금액은 KRW 정수(소수점 없음), 좌표는 WGS84 십진수(`lat`/`lng`).
- 목록은 **오프셋 기반 페이지네이션**(api-design-guide §4-1: `page` 0-base, `size` 기본 20·최대 100, `sort=field,(asc|desc)`).
- 모든 엔드포인트는 본인 진단만 접근(소유권 검증). 인증은 `Authorization: Bearer <accessToken>`.
- 입력 검증 실패(필수값 누락·enum 불일치·조건 개수 초과·예산 음수·페이지 파라미터 범위·잘못된 `sort` 키)는 공통 코드 `INVALID_INPUT`(400) + `errors[]`로 표현한다(error-response-guide §3·§4). 진단 도메인은 별도 검증 코드를 만들지 않는다.
- 매물 요약(`ListingSummary`)·지도 마커 DTO는 매물 탐색(01) 스펙과 동일 구조를 재사용한다(확인 필요 — 01 스펙 확정 시 동기화).

## 진단 입력 enum 정의

| 단계 | 필드 | 타입 | 허용 값 | 제약 |
| --- | --- | --- | --- | --- |
| ① 지역 | `region` | enum (단일) | `SEOUL`, `BUSAN`, `GYEONGGI` | 필수, 1택. MVP 매물 데이터는 `SEOUL` 기준 |
| ② 입국 목적 | `purposes` | enum 배열 (다중) | `STUDY`, `NON_STUDY` | 필수, 최소 1개, 중복 불가 |
| ③ 주거 환경 조건 | `conditions` | enum 배열 (다중) | `INSTANT_MOVE_IN`, `FEMALE_ONLY`, `PRIVATE_TOILET`, `PRIVATE_BATH`, `ENGLISH_SPEAKING`, `RESIDENT_REGISTRATION`, `NO_MAINTENANCE_FEE`, `MEALS_PROVIDED`, `TWIN_ROOM` | 선택(0개 허용), **최대 3개**, 중복 불가 |
| ④ 월 예산 | `monthlyBudgetMax` | integer (KRW) | 0 이상 정수 | 필수, 0 이상 |
| ⑤ ARC 발급 여부 | `arcStatus` | enum (단일) | `ARC_ISSUED`, `ARC_PENDING` | 필수, 1택 |

> `conditions` 4개 이상은 `INVALID_INPUT`의 `errors[]`(필드 `conditions`, reason "최대 3개까지 선택할 수 있습니다.")로 응답한다. 별도 도메인 코드를 두지 않는다.

## 엔드포인트 요약

| Method | Path | 설명 | 인증 | 성공 status |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/diagnoses` | 진단 제출·저장(재진단 = 새 진단 생성) | 필수 | 201 |
| GET | `/api/v1/diagnoses` | 내 진단 이력 목록(오프셋 페이지네이션) | 필수 | 200 |
| GET | `/api/v1/diagnoses/latest` | 최근 진단 단건(홈 완료 여부 분기용) | 필수 | 200 |
| GET | `/api/v1/diagnoses/{diagnosisId}` | 진단 단건 상세(입력 다시 보기) | 필수 | 200 |
| GET | `/api/v1/diagnoses/{diagnosisId}/recommendations` | 진단 결과: 추천 매물 + 지도 좌표(오프셋 페이지네이션) | 필수 | 200 |

> 추천 결과는 진단에 종속되는 조회이므로 `/diagnoses/{diagnosisId}` 하위 1단계 중첩으로 둔다(api-design-guide §2).

---

## 상세

### 1. POST `/api/v1/diagnoses` — 진단 제출·저장

5단계 진단을 제출해 새 진단 레코드를 생성한다. 재진단도 동일 엔드포인트로, 항상 새 레코드를 만들고 기존 진단을 덮어쓰지 않는다(비멱등 생성). 서버는 클라이언트 입력을 다시 검증한다.

- **인증**: 필수
- **멱등성**: 중복 제출(더블탭·재시도) 방지를 위해 `Idempotency-Key` 헤더 지원을 검토(api-design-guide §6, 확인 필요 — 정책 미확정). 정책 도입 시 같은 키+같은 본문 → 동일 `diagnosisId` 반환, 같은 키+다른 본문 → `409 DIAGNOSIS_IDEMPOTENCY_CONFLICT`.

#### Headers

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `Authorization` | 필수 | `Bearer <accessToken>` |
| `Idempotency-Key` | 선택 | 중복 제출 방지 키(확인 필요 — 정책 미확정) |

#### Request Body (래퍼 없이)

```jsonc
{
  "region": "SEOUL",
  "purposes": ["STUDY"],
  "conditions": ["FEMALE_ONLY", "PRIVATE_BATH"],
  "monthlyBudgetMax": 600000,
  "arcStatus": "ARC_ISSUED"
}
```

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `region` | enum | 필수 | 허용 enum 1택 |
| `purposes` | enum 배열 | 필수 | 최소 1개, 허용 enum, 중복 제거 |
| `conditions` | enum 배열 | 선택 | 최대 3개, 허용 enum, 중복 제거 |
| `monthlyBudgetMax` | integer | 필수 | 0 이상 정수 |
| `arcStatus` | enum | 필수 | 허용 enum 1택 |

> 위반 시 `400 INVALID_INPUT` + `errors[]`(필드별 `field`/`reason`). 예: `monthlyBudgetMax` 음수 → reason "0 이상이어야 합니다.", `conditions` 4개 이상 → reason "최대 3개까지 선택할 수 있습니다.".

#### 성공 Response — 201 Created (공통 래퍼)

`Location: /api/v1/diagnoses/{diagnosisId}` 헤더를 포함한다.

```jsonc
{
  "success": true,
  "data": {
    "diagnosisId": 1024,
    "status": "COMPLETED",
    "submittedAt": "2026-06-15T08:30:00Z"
  },
  "error": null
}
```

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | 필수값 누락, enum 불일치, `conditions` 4개 이상, `purposes` 빈 배열, `monthlyBudgetMax` 음수 |
| 400 | `MALFORMED_REQUEST` | JSON 파싱 불가/타입 불일치(검증 이전) |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 없음·위조 / 만료 |
| 409 | `DIAGNOSIS_IDEMPOTENCY_CONFLICT` | 동일 `Idempotency-Key`로 다른 본문 재제출(멱등성 키 정책 도입 시, 확인 필요) |

---

### 2. GET `/api/v1/diagnoses` — 내 진단 이력 목록

로그인 사용자의 진단 이력을 최신순으로 반환한다. **오프셋 기반 페이지네이션**(api-design-guide §4-1).

- **인증**: 필수. 본인 진단만 반환된다(타인 진단은 애초에 목록에 없음).

#### Query 파라미터

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `page` | integer | 선택 | `0` | 0-base 페이지 번호 |
| `size` | integer | 선택 | `20` | 페이지 크기(최대 100) |
| `sort` | string | 선택 | `submittedAt,desc` | `field,(asc\|desc)`. 허용 키: `submittedAt` |

#### 성공 Response — 200 OK (공통 래퍼)

```jsonc
{
  "success": true,
  "data": {
    "content": [
      {
        "diagnosisId": 1024,
        "region": "SEOUL",
        "purposes": ["STUDY"],
        "conditions": ["FEMALE_ONLY", "PRIVATE_BATH"],
        "monthlyBudgetMax": 600000,
        "arcStatus": "ARC_ISSUED",
        "status": "COMPLETED",
        "submittedAt": "2026-06-15T08:30:00Z"
      }
    ],
    "page": {
      "number": 0,
      "size": 20,
      "totalElements": 3,
      "totalPages": 1,
      "hasNext": false
    }
  },
  "error": null
}
```

> 이력이 0건이면 `content: []`, `totalElements: 0`(에러 아님).

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `size` 범위 초과, 허용되지 않은 `sort` 키 |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 없음·위조 / 만료 |

---

### 3. GET `/api/v1/diagnoses/latest` — 최근 진단 단건

홈 화면의 "진단 시작 / 재진단" 문구 분기를 위해 사용자의 가장 최근 진단 1건을 반환한다. 진단 이력이 없으면 `data.completed=false`(404 아님).

- **인증**: 필수

#### 성공 Response — 200 OK (공통 래퍼) — 이력 있음

```jsonc
{
  "success": true,
  "data": {
    "completed": true,
    "diagnosisId": 1024,
    "region": "SEOUL",
    "purposes": ["STUDY"],
    "conditions": ["FEMALE_ONLY", "PRIVATE_BATH"],
    "monthlyBudgetMax": 600000,
    "arcStatus": "ARC_ISSUED",
    "submittedAt": "2026-06-15T08:30:00Z"
  },
  "error": null
}
```

#### 성공 Response — 200 OK — 이력 없음

```jsonc
{
  "success": true,
  "data": { "completed": false },
  "error": null
}
```

> `completed=false`일 때 진단 요약 필드(`diagnosisId` 등)는 포함하지 않는다. 클라이언트는 `completed` 한 필드로 분기한다.

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 없음·위조 / 만료 |

---

### 4. GET `/api/v1/diagnoses/{diagnosisId}` — 진단 단건 상세

진단 단건의 입력 전체를 반환한다(지난 진단 다시 보기). 본인 소유 진단만 조회 가능.

- **인증**: 필수. **본인 소유가 아니면 `403 FORBIDDEN`.**

#### Path 파라미터

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `diagnosisId` | Long | 필수 | 진단 식별자 |

#### 성공 Response — 200 OK (공통 래퍼)

```jsonc
{
  "success": true,
  "data": {
    "diagnosisId": 1024,
    "region": "SEOUL",
    "purposes": ["STUDY"],
    "conditions": ["FEMALE_ONLY", "PRIVATE_BATH"],
    "monthlyBudgetMax": 600000,
    "arcStatus": "ARC_ISSUED",
    "status": "COMPLETED",
    "submittedAt": "2026-06-15T08:30:00Z"
  },
  "error": null
}
```

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 없음·위조 / 만료 |
| 403 | `FORBIDDEN` | 타인 소유 진단 접근 |
| 404 | `DIAGNOSIS_NOT_FOUND` | 진단이 존재하지 않음 |

---

### 5. GET `/api/v1/diagnoses/{diagnosisId}/recommendations` — 진단 결과(추천 매물 + 지도 좌표)

진단 조건으로 매칭한 매물 요약 리스트와 지도 마커 좌표를 반환한다. 매물 요약은 매물 탐색(01) 도메인의 `ListingSummary`를 재사용한다(확인 필요). 매칭이 0건이면 빈 목록 + 조건/예산/키워드 조정 제안(`suggestions`)을 함께 반환한다(에러 아님).

- **인증**: 필수. **본인 소유가 아니면 `403 FORBIDDEN`.**
- **페이지네이션**: 오프셋 기반(매물 목록, api-design-guide §4-1). 지도 마커(`markers`)는 현재 페이지 매물의 좌표를 함께 제공한다(확인 필요 — 전체 매칭 좌표를 한 번에 줄지, 페이지 단위로 줄지는 01 매물 탐색의 클러스터링 정책과 맞춤).

#### Path 파라미터

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `diagnosisId` | Long | 필수 | 진단 식별자(본인 소유) |

#### Query 파라미터

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `page` | integer | 선택 | `0` | 0-base 페이지 번호 |
| `size` | integer | 선택 | `20` | 페이지 크기(최대 100) |
| `sort` | string | 선택 | `recommended,desc` | `field,(asc\|desc)`. 허용 키: `recommended`(추천순) / `price`(가격순) / `distance`(거리순) |

#### 성공 Response — 200 OK (공통 래퍼) — 결과 있음

```jsonc
{
  "success": true,
  "data": {
    "content": [
      {
        "listingId": 5001,
        "title": "Sinchon Co-living House A",
        "housingType": "CO_LIVING",
        "monthlyRent": 550000,
        "deposit": 1000000,
        "lat": 37.555134,
        "lng": 126.936893,
        "conditions": ["FEMALE_ONLY", "PRIVATE_BATH"],
        "thumbnailUrl": "https://cdn.kohere.app/listings/5001/thumb.jpg"
      }
    ],
    "markers": [
      { "listingId": 5001, "lat": 37.555134, "lng": 126.936893 }
    ],
    "page": {
      "number": 0,
      "size": 20,
      "totalElements": 12,
      "totalPages": 1,
      "hasNext": false
    },
    "suggestions": null
  },
  "error": null
}
```

> `content[]` 항목 스키마(`ListingSummary`)는 매물 탐색(01) 스펙을 정본으로 한다(확인 필요 — 위 필드는 예시).

#### 성공 Response — 200 OK — 결과 0건 (조정 제안 포함)

```jsonc
{
  "success": true,
  "data": {
    "content": [],
    "markers": [],
    "page": {
      "number": 0,
      "size": 20,
      "totalElements": 0,
      "totalPages": 0,
      "hasNext": false
    },
    "suggestions": {
      "reason": "NO_MATCH",
      "message": "조건에 맞는 매물이 없습니다. 조건이나 예산을 조정해 보세요.",
      "actions": [
        { "type": "RELAX_REGION", "detail": "BUSAN/GYEONGGI는 현재 매물 데이터가 준비 중입니다. SEOUL로 변경해 보세요." },
        { "type": "RELAX_CONDITIONS", "detail": "선택한 조건 중 일부를 해제하면 결과가 늘어납니다." },
        { "type": "INCREASE_BUDGET", "detail": "월 예산 상한을 높여 보세요." }
      ]
    }
  },
  "error": null
}
```

> `suggestions.actions[].type` 후보: `RELAX_REGION`, `RELAX_CONDITIONS`, `INCREASE_BUDGET`, `ADJUST_KEYWORD`(확인 필요 — 제안 액션 enum 카탈로그는 기획 확정 필요). `message`는 fallback 문구이며 클라이언트는 `reason`/`type`(enum)으로 다국어 매핑한다.

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `size` 범위 초과, 허용되지 않은 `sort` 키 |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 없음·위조 / 만료 |
| 403 | `FORBIDDEN` | 타인 소유 진단 접근 |
| 404 | `DIAGNOSIS_NOT_FOUND` | 진단이 존재하지 않음 |

---

## 도메인 에러 코드

> 공통 코드(`INVALID_INPUT`, `MALFORMED_REQUEST`, `UNAUTHENTICATED`, `TOKEN_EXPIRED`, `FORBIDDEN`, `INTERNAL_ERROR` 등)는 [error-response-guide](../error-response-guide.md) §4를 따르며 여기서 재정의하지 않는다. 아래는 본 기능 고유 코드만 정의한다. prefix는 `DIAGNOSIS`.

| code | status | 의미 |
| --- | --- | --- |
| `DIAGNOSIS_NOT_FOUND` | 404 | 요청한 진단이 존재하지 않음 |
| `DIAGNOSIS_IDEMPOTENCY_CONFLICT` | 409 | 동일 `Idempotency-Key`로 다른 본문을 재제출함(멱등성 키 정책 도입 시에만 — 확인 필요) |

> 타인 진단 접근은 공통 `FORBIDDEN`(403), 입력 검증 실패(enum 불일치·필수값 누락·조건 4개 이상·예산 음수·페이지 파라미터 범위·잘못된 `sort` 키)는 공통 `INVALID_INPUT`(400) + `errors[]`를 그대로 사용한다. 진단 도메인에서 별도 검증 코드를 만들지 않는다.
