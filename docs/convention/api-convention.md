# API Convention

> **예시(Spring Boot 기준) 안내**
> 이 문서의 모든 코드/경로/응답 예시는 **Spring Boot 3.x / Java 17 / Spring Data JPA / PostgreSQL** 스택을 가정한 **예시**입니다.
> 실제 프로젝트 스택이 다르면(NestJS, FastAPI, Go 등) 표의 *규칙*은 유지하고 *예시 값*만 프로젝트에 맞게 교체하세요.
> 관련 규칙: [.claude/rules/api-design.md](../../.claude/rules/api-design.md), [.claude/rules/backend-architecture.md](../../.claude/rules/backend-architecture.md)

## 목적

REST API의 URL, 네이밍, HTTP method, 상태 코드, 페이지네이션/정렬/필터, 에러 응답을 **팀 전체가 동일하게** 설계하도록 한다.
일관된 contract는 클라이언트 통합 비용을 낮추고, breaking change 위험을 조기에 드러낸다.

---

## 1. URL 설계 규칙

| 규칙 | 설명 | 좋은 예 | 나쁜 예 |
| --- | --- | --- | --- |
| 리소스는 명사, 복수형 | 동작이 아닌 자원을 표현 | `GET /api/v1/users` | `GET /api/v1/getUsers` |
| 소문자 + 하이픈(kebab-case) | 단어 구분은 `-` | `/api/v1/order-items` | `/api/v1/orderItems`, `/api/v1/order_items` |
| 계층은 경로로 표현 | 하위 리소스는 중첩 | `/api/v1/users/{userId}/orders` | `/api/v1/orders?user=42` (식별 목적일 때) |
| 행위는 동사 하위 리소스로 예외 허용 | CRUD로 표현 불가한 동작 | `POST /api/v1/orders/{id}/cancel` | `POST /api/v1/cancelOrder` |
| 경로 변수는 식별자, 쿼리는 옵션 | 필수 식별 vs 선택 조건 | `/users/{userId}?include=profile` | `/users?userId=42` (단건 조회 시) |
| 버전은 경로 prefix | `/api/v{n}` | `/api/v1/users` | `/api/users?version=1` |
| trailing slash 미사용 | 끝에 `/` 붙이지 않음 | `/api/v1/users` | `/api/v1/users/` |

```text
형식:  /api/v{버전}/{리소스(복수)}/{식별자}/{하위리소스(복수)}
예시:  /api/v1/users/42/orders/1001
```

---

## 2. HTTP Method 규칙

| Method | 용도 | 멱등성 | 예시 |
| --- | --- | --- | --- |
| `GET` | 조회 (목록/단건) | O | `GET /api/v1/users/42` |
| `POST` | 생성, 비CRUD 액션 | X | `POST /api/v1/users` |
| `PUT` | 전체 교체 (모든 필드 필요) | O | `PUT /api/v1/users/42` |
| `PATCH` | 부분 수정 | X | `PATCH /api/v1/users/42` |
| `DELETE` | 삭제 | O | `DELETE /api/v1/users/42` |

> **가정:** 부분 수정은 `PATCH`를 기본으로 한다. 전체 교체 시맨틱이 명확할 때만 `PUT`을 쓴다.

---

## 3. HTTP 상태 코드 규칙

| 상황 | 상태 코드 | 비고 |
| --- | --- | --- |
| 조회/수정 성공 | `200 OK` | 응답 바디 포함 |
| 생성 성공 | `201 Created` | `Location` 헤더에 생성 리소스 URI |
| 성공, 응답 바디 없음 | `204 No Content` | 주로 `DELETE` |
| 비동기 수락 | `202 Accepted` | 처리 진행 중 |
| 잘못된 요청(검증 실패) | `400 Bad Request` | 필드 검증 오류 |
| 인증 실패(미인증) | `401 Unauthorized` | 토큰 없음/만료 |
| 인가 실패(권한 없음) | `403 Forbidden` | 인증됐으나 권한 부족 |
| 리소스 없음 | `404 Not Found` | 존재하지 않는 식별자 |
| 충돌(중복/동시성) | `409 Conflict` | 유니크 위반, 낙관적 락 충돌 |
| 검증은 통과했으나 처리 불가 | `422 Unprocessable Entity` | 비즈니스 규칙 위반 |
| 요청 과다 | `429 Too Many Requests` | rate limit |
| 서버 오류 | `500 Internal Server Error` | 예상치 못한 예외 |
| 업스트림 장애 | `502 / 503 / 504` | 게이트웨이/타임아웃 |

```http
# 생성 성공 예시
POST /api/v1/users
HTTP/1.1 201 Created
Location: /api/v1/users/42
```

---

## 4. Request / Response 스키마 규칙

- 필드는 **camelCase**로 통일한다. (예: `createdAt`, 절대 `created_at` 아님)
- 날짜/시간은 **ISO-8601 + UTC**(`2026-06-09T12:34:56Z`)를 사용한다.
- 금액은 정수(최소 화폐 단위) 또는 문자열로 표현해 부동소수 오차를 피한다.
- Boolean 필드는 `is`/`has` prefix를 권장한다. (예: `isActive`)
- 응답은 **enveloped(공통 래퍼)** 또는 **plain** 중 하나로 통일한다. 본 컨벤션은 **plain + 에러 전용 포맷**을 기본으로 한다.

### 단건 응답 예시

```json
{
  "id": 42,
  "email": "user@example.com",
  "nickname": "swyp-user",
  "isActive": true,
  "createdAt": "2026-06-09T12:34:56Z"
}
```

### 목록 응답(페이지네이션) 예시

```json
{
  "content": [
    { "id": 1001, "amount": 15000, "status": "PAID" },
    { "id": 1002, "amount": 32000, "status": "PENDING" }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 137,
    "totalPages": 7,
    "hasNext": true
  }
}
```

---

## 5. 페이지네이션 / 정렬 / 필터 규칙

| 항목 | 쿼리 파라미터 | 기본값 | 예시 |
| --- | --- | --- | --- |
| 페이지 번호 | `page` (0-base) | `0` | `?page=2` |
| 페이지 크기 | `size` | `20` (최대 `100`) | `?size=50` |
| 정렬 | `sort=필드,방향` | `id,desc` | `?sort=createdAt,desc&sort=id,asc` |
| 필터 | `필드=값` | 없음 | `?status=PAID&minAmount=10000` |
| 검색 | `q` | 없음 | `?q=swyp` |

```text
GET /api/v1/orders?status=PAID&minAmount=10000&page=0&size=20&sort=createdAt,desc
```

- `size`에는 상한(예: `100`)을 두어 과도한 조회를 막는다.
- 정렬 가능한 필드는 **화이트리스트**로 제한한다(임의 컬럼 정렬 금지).
- 커서 기반이 필요한 대용량/실시간 목록은 `cursor` 파라미터를 별도 정의한다.

---

## 6. 에러 응답 규칙 (통일 포맷)

모든 4xx/5xx 응답은 아래 단일 포맷을 따른다. `code`는 기계 판독용, `message`는 사람 판독용이다.

```json
{
  "code": "USER_NOT_FOUND",
  "message": "해당 사용자를 찾을 수 없습니다.",
  "status": 404,
  "path": "/api/v1/users/9999",
  "timestamp": "2026-06-09T12:34:56Z",
  "errors": [
    { "field": "email", "reason": "이메일 형식이 올바르지 않습니다." }
  ]
}
```

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `code` | O | 도메인 에러 코드(대문자 스네이크). 클라이언트 분기용 |
| `message` | O | 사용자/개발자에게 보여줄 메시지 |
| `status` | O | HTTP 상태 코드 |
| `path` | O | 요청 경로 |
| `timestamp` | O | 발생 시각(UTC) |
| `errors` | X | 필드 단위 검증 오류 목록(검증 실패 시) |

> **보안 주의:** 에러 메시지에 스택트레이스, 내부 쿼리, secret을 노출하지 않는다. 자세한 내용은 [docs/security](../security/index.md) 참조.

### Spring 예시: 전역 예외 핸들러

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            UserNotFoundException ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                "USER_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }
}
```

---

## 7. 버저닝 / Breaking Change 규칙

- 호환 불가 변경(필드 삭제, 타입 변경, 의미 변경)은 **새 버전(`/api/v2`)** 으로 분리한다.
- 필드 **추가**는 하위 호환으로 간주하며 버전을 올리지 않는다(클라이언트는 미지의 필드를 무시해야 함).
- Deprecated API는 `Deprecation`, `Sunset` 헤더로 폐기 일정을 알린다.

| 변경 유형 | breaking? | 조치 |
| --- | --- | --- |
| 필드 추가 | No | 그대로 배포 |
| 선택 파라미터 추가 | No | 그대로 배포 |
| 필드 삭제/이름 변경 | **Yes** | 새 버전 + migration plan |
| enum 값 의미 변경 | **Yes** | 새 버전 + 공지 |
| 응답 구조 변경 | **Yes** | 새 버전 |

> 변경 시 [.claude/rules/api-design.md](../../.claude/rules/api-design.md)의 "breaking change 시 versioning 또는 migration plan 검토"를 따른다.

---

## 체크리스트

- [ ] URL이 리소스 중심(명사/복수/kebab-case)인가
- [ ] HTTP method와 상태 코드가 의미에 맞는가
- [ ] 요청/응답 필드가 camelCase, 시간은 ISO-8601(UTC)인가
- [ ] 페이지네이션/정렬/필터 파라미터가 컨벤션과 일치하는가
- [ ] 에러 응답이 통일 포맷(`code`/`message`/`status`/...)을 따르는가
- [ ] breaking change 여부를 판단하고 필요한 경우 versioning/migration plan을 남겼는가
- [ ] 에러 응답에 secret/스택트레이스가 노출되지 않는가
