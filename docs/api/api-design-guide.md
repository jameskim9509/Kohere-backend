# API Design Guide

> 본 문서는 백엔드 base repository의 **API 설계 표준**을 정의한다.
> 예시는 **Spring Boot 3.x / Java 17 / Spring Web MVC** 기준으로 작성했다.
> 실제 스택(NestJS, FastAPI, Go 등)이 확정되면 코드 예시만 교체하고,
> 설계 원칙과 응답 규약은 그대로 재사용한다.

관련 문서: [error-response-guide](./error-response-guide.md) · [versioning-policy](./versioning-policy.md) · [system-overview](../architecture/system-overview.md)

---

## 목적

- 팀 전체가 동일한 규칙으로 REST API를 설계·구현하도록 표준을 제공한다.
- 클라이언트가 예측 가능한 URL, 메서드, 응답 구조를 사용하도록 보장한다.
- 페이지네이션·정렬·필터링·에러 처리 등 반복 결정 사항을 미리 합의한다.

---

## 1. RESTful 설계 원칙

| 원칙 | 설명 | 예시 |
| --- | --- | --- |
| 리소스 중심 URL | URL은 **명사(리소스)**, 행위는 HTTP 메서드로 표현한다. | `POST /api/v1/meetings` (O) / `POST /api/v1/createMeeting` (X) |
| 복수형 컬렉션 | 컬렉션은 복수형, 단건은 식별자로 접근한다. | `/api/v1/meetings`, `/api/v1/meetings/{meetingId}` |
| 계층 표현 | 하위 리소스는 경로 중첩으로 표현하되 2단계 이하로 유지한다. | `/api/v1/meetings/{meetingId}/participants` |
| 소문자 + 하이픈 | 경로는 소문자, 단어 구분은 하이픈(`-`)을 사용한다. | `/api/v1/meeting-rooms` (O) / `/api/v1/meetingRooms` (X) |
| 멱등성 존중 | `GET`/`PUT`/`DELETE`는 멱등, `POST`는 비멱등으로 설계한다. | 같은 `PUT`을 2번 호출 → 동일 결과 |
| 상태 코드 의미 준수 | 결과를 HTTP status로 정확히 표현한다. | 생성=`201`, 수정=`200`, 삭제=`204` |
| 동사가 필요한 액션 | 순수 CRUD로 표현 불가한 액션은 하위 동사 리소스로 둔다. | `POST /api/v1/meetings/{id}/cancel` |

### HTTP 메서드 사용 규약

| 메서드 | 용도 | 요청 바디 | 멱등성 | 성공 status |
| --- | --- | --- | --- | --- |
| `GET` | 조회 | 없음 | O | `200 OK` |
| `POST` | 생성 / 비멱등 액션 | 있음 | X | `201 Created` / `200 OK` |
| `PUT` | 전체 교체 | 있음(전체 필드) | O | `200 OK` |
| `PATCH` | 부분 수정 | 있음(변경 필드만) | X(권장 멱등 설계) | `200 OK` |
| `DELETE` | 삭제 | 없음 | O | `204 No Content` |

> 부분 수정에는 `PATCH`를 권장한다. 단, 클라이언트/스펙 단순화를 위해 `PUT`만 사용하기로 했다면
> 팀 합의로 이 가이드에 명시한다.

---

## 2. 예시 리소스: `meetings`

아래는 "모임(meeting)" 도메인을 예시로 한 표준 엔드포인트 표다.
실제 도메인이 확정되면 리소스명만 교체한다.

| 메서드 | 경로 | 설명 | 성공 status | 인증 |
| --- | --- | --- | --- | --- |
| `GET` | `/api/v1/meetings` | 모임 목록 조회(페이지네이션) | `200` | 필요 |
| `POST` | `/api/v1/meetings` | 모임 생성 | `201` | 필요 |
| `GET` | `/api/v1/meetings/{meetingId}` | 모임 단건 조회 | `200` | 필요 |
| `PUT` | `/api/v1/meetings/{meetingId}` | 모임 전체 수정 | `200` | 필요(소유자) |
| `PATCH` | `/api/v1/meetings/{meetingId}` | 모임 부분 수정 | `200` | 필요(소유자) |
| `DELETE` | `/api/v1/meetings/{meetingId}` | 모임 삭제 | `204` | 필요(소유자) |
| `POST` | `/api/v1/meetings/{meetingId}/cancel` | 모임 취소(상태 전이) | `200` | 필요(소유자) |
| `GET` | `/api/v1/meetings/{meetingId}/participants` | 참가자 목록 조회 | `200` | 필요 |
| `POST` | `/api/v1/meetings/{meetingId}/participants` | 참가 신청 | `201` | 필요 |

인증/인가 정책은 [access-control](../security/access-control.md)을 따른다.

---

## 3. 요청 / 응답 JSON 예시

### 3.1 단일 리소스 응답 래퍼

모든 응답은 일관된 최상위 구조를 사용한다.
성공 응답은 `data`에 페이로드를 담고, 실패 응답은 [error-response-guide](./error-response-guide.md)의 표준 형식을 따른다.

```jsonc
// 성공 응답 공통 형태
{
  "data": { /* 리소스 또는 컬렉션 */ },
  "meta": { /* 페이지네이션 등 부가 정보. 단건 응답에서는 생략 가능 */ }
}
```

> 팀에 따라 래퍼 없이 `data`를 곧바로 루트에 두는 방식도 가능하다.
> **둘 중 하나로 통일**하는 것이 핵심이며, 본 가이드는 래퍼 방식을 기본 예시로 사용한다.

### 3.2 모임 생성 — `POST /api/v1/meetings`

**Request**

```http
POST /api/v1/meetings HTTP/1.1
Host: api.example.com
Authorization: Bearer <ACCESS_TOKEN>
Content-Type: application/json
```

```json
{
  "title": "주말 등산 모임",
  "description": "초보 환영, 가벼운 코스 위주로 진행합니다.",
  "category": "OUTDOOR",
  "capacity": 10,
  "startAt": "2026-07-01T09:00:00+09:00",
  "location": {
    "name": "북한산국립공원 탐방지원센터",
    "latitude": 37.6584,
    "longitude": 126.9779
  }
}
```

**Response — `201 Created`**

```http
HTTP/1.1 201 Created
Location: /api/v1/meetings/8a1f2c34-5b6d-4e7f-8901-23456789abcd
Content-Type: application/json
```

```json
{
  "data": {
    "meetingId": "8a1f2c34-5b6d-4e7f-8901-23456789abcd",
    "title": "주말 등산 모임",
    "description": "초보 환영, 가벼운 코스 위주로 진행합니다.",
    "category": "OUTDOOR",
    "capacity": 10,
    "participantCount": 1,
    "status": "OPEN",
    "startAt": "2026-07-01T09:00:00+09:00",
    "location": {
      "name": "북한산국립공원 탐방지원센터",
      "latitude": 37.6584,
      "longitude": 126.9779
    },
    "hostId": "u_01HZX9P2QK",
    "createdAt": "2026-06-09T12:30:00+09:00",
    "updatedAt": "2026-06-09T12:30:00+09:00"
  }
}
```

### 3.3 부분 수정 — `PATCH /api/v1/meetings/{meetingId}`

```json
// Request: 변경할 필드만 전송
{
  "capacity": 12,
  "description": "코스 변경: 우이령길로 진행합니다."
}
```

```json
// Response 200 OK: 수정된 전체 리소스 반환
{
  "data": {
    "meetingId": "8a1f2c34-5b6d-4e7f-8901-23456789abcd",
    "capacity": 12,
    "description": "코스 변경: 우이령길로 진행합니다.",
    "status": "OPEN",
    "updatedAt": "2026-06-09T13:10:00+09:00"
  }
}
```

### 3.4 삭제 — `DELETE /api/v1/meetings/{meetingId}`

성공 시 바디 없이 `204 No Content`를 반환한다.

```http
HTTP/1.1 204 No Content
```

---

## 4. 페이지네이션 규약

### 4.1 요청 파라미터

목록 조회는 **offset 기반 페이지네이션**을 기본으로 한다.
대용량/무한 스크롤이 필요한 컬렉션은 **cursor 기반**을 선택할 수 있다.

| 파라미터 | 타입 | 기본값 | 설명 | 예시 |
| --- | --- | --- | --- | --- |
| `page` | int | `0` | 0부터 시작하는 페이지 번호 | `?page=2` |
| `size` | int | `20` | 페이지당 항목 수(최대 `100`) | `?size=50` |
| `sort` | string | 리소스별 정의 | `필드,방향` 형식. 다중 정렬은 반복 지정 | `?sort=startAt,asc` |

> Spring Data의 `Pageable`을 그대로 쓰면 `page`/`size`/`sort` 규약과 자연스럽게 맞는다.
> `size` 상한은 서버에서 강제(clamp)하여 과도한 조회를 방지한다.

### 4.2 정렬 규약

- 형식: `sort=<field>,<asc|dir>` (방향 생략 시 `asc`).
- 다중 정렬: `?sort=startAt,asc&sort=createdAt,desc` (먼저 지정한 키 우선).
- **허용 정렬 필드는 화이트리스트로 제한**한다(임의 컬럼 정렬로 인한 풀스캔/인덱스 미스 방지).
  - 예: `meetings` 허용 필드 = `startAt`, `createdAt`, `participantCount`.

### 4.3 목록 응답 — `GET /api/v1/meetings?page=0&size=20&sort=startAt,asc`

```json
{
  "data": [
    {
      "meetingId": "8a1f2c34-5b6d-4e7f-8901-23456789abcd",
      "title": "주말 등산 모임",
      "category": "OUTDOOR",
      "status": "OPEN",
      "participantCount": 7,
      "capacity": 12,
      "startAt": "2026-07-01T09:00:00+09:00"
    }
  ],
  "meta": {
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 137,
      "totalPages": 7,
      "hasNext": true
    }
  }
}
```

### 4.4 (선택) Cursor 기반 페이지네이션

무한 스크롤 피드처럼 항목이 자주 추가/삭제되는 컬렉션에 사용한다.

```http
GET /api/v1/meetings?size=20&cursor=eyJpZCI6IjhhMWYyYzM0In0
```

```json
{
  "data": [ /* ... */ ],
  "meta": {
    "pagination": {
      "size": 20,
      "nextCursor": "eyJpZCI6IjlmMmEzZDQ1In0",
      "hasNext": true
    }
  }
}
```

> `cursor`는 클라이언트가 해석하지 않는 **불투명(opaque) 토큰**이다. 보통 마지막 항목의 정렬 키를
> base64로 인코딩한다. 내부 PK를 그대로 노출하지 않도록 주의한다.

---

## 5. 필터링·검색 규약

- 단순 필터는 쿼리 파라미터로 표현한다: `?status=OPEN&category=OUTDOOR`.
- 날짜 범위: `?startAtFrom=2026-07-01&startAtTo=2026-07-31`.
- 텍스트 검색: `?q=등산` (검색 대상 필드는 도메인별로 문서화).
- 다중 값: 콤마 구분 `?status=OPEN,CLOSED` 또는 반복 `?status=OPEN&status=CLOSED` 중 **하나로 통일**.

---

## 6. 공통 규약

| 항목 | 규약 |
| --- | --- |
| 날짜/시간 | ISO-8601, 타임존 오프셋 포함(`2026-07-01T09:00:00+09:00`). 저장은 UTC 권장 |
| 식별자 | 외부 노출 ID는 UUID 또는 ULID. 내부 auto-increment PK는 노출 금지 |
| enum | 대문자 스네이크/단어(`OPEN`, `IN_PROGRESS`). 화면 라벨은 클라이언트가 매핑 |
| boolean | `isXxx` 명명 지양, 의미 있는 명사형 권장(`active`, `published`) |
| null 처리 | 응답에서 의미 없는 `null` 필드는 생략 가능(팀 합의로 통일) |
| 멱등 키 | 결제 등 중복 방지가 필요한 `POST`는 `Idempotency-Key` 헤더 사용 검토 |
| 인증 헤더 | `Authorization: Bearer <ACCESS_TOKEN>` (JWT). 상세: [access-control](../security/access-control.md) |

---

## 7. Spring Boot 구현 스케치 (예시)

> 아래는 위 규약을 Spring Web MVC로 구현하는 최소 예시다. 스택이 바뀌면 이 절만 교체한다.

```java
@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;

    @PostMapping
    public ResponseEntity<ApiResponse<MeetingResponse>> create(
            @Valid @RequestBody CreateMeetingRequest request) {
        MeetingResponse created = meetingService.create(request);
        URI location = URI.create("/api/v1/meetings/" + created.meetingId());
        return ResponseEntity.created(location).body(ApiResponse.of(created));
    }

    @GetMapping
    public ApiResponse<List<MeetingSummaryResponse>> list(
            @RequestParam(required = false) MeetingStatus status,
            @PageableDefault(size = 20, sort = "startAt") Pageable pageable) {
        Page<MeetingSummaryResponse> page = meetingService.findAll(status, pageable);
        return ApiResponse.ofPage(page);
    }

    @GetMapping("/{meetingId}")
    public ApiResponse<MeetingResponse> getOne(@PathVariable UUID meetingId) {
        return ApiResponse.of(meetingService.findById(meetingId));
    }

    @DeleteMapping("/{meetingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID meetingId) {
        meetingService.delete(meetingId);
    }
}
```

- 요청 검증은 `@Valid` + Bean Validation(`@NotBlank`, `@Positive`, `@Future` 등)으로 처리한다.
- 검증 실패·도메인 예외는 `@RestControllerAdvice`에서 표준 에러 응답으로 변환한다
  → [error-response-guide](./error-response-guide.md).

---

## 체크리스트

- [ ] URL이 리소스 중심(명사 + 복수형)인가
- [ ] HTTP 메서드와 성공 status가 의미에 맞는가(`201`/`204` 등)
- [ ] 요청/응답 JSON 예시를 문서/스펙(OpenAPI)에 추가했는가
- [ ] 목록 API에 페이지네이션과 정렬 화이트리스트를 적용했는가
- [ ] 날짜·식별자·enum 공통 규약을 따랐는가
- [ ] 에러 응답이 [error-response-guide](./error-response-guide.md) 표준을 따르는가
- [ ] breaking change 가능성을 [versioning-policy](./versioning-policy.md)로 검토했는가
- [ ] 인증/인가 요구사항을 [access-control](../security/access-control.md)에 맞췄는가
