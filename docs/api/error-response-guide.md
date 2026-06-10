# Error Response Guide

> 본 문서는 모든 API가 공유하는 **표준 에러 응답 형식**과 **에러 코드 체계**를 정의한다.
> 예시는 **Spring Boot 3.x / `@RestControllerAdvice`** 기준으로 작성했다.
> 스택이 확정되면 핸들러 코드만 교체하고, 응답 스키마와 코드 체계는 그대로 재사용한다.

관련 문서: [api-design-guide](./api-design-guide.md) · [versioning-policy](./versioning-policy.md) · [error-handling](../architecture/error-handling.md) · [observability](../architecture/observability.md)

---

## 목적

- 클라이언트가 **단일한 구조**로 모든 에러를 파싱·처리하도록 보장한다.
- HTTP status와 도메인 에러 코드를 분리해, 같은 status 안에서도 원인을 구분한다.
- `traceId`로 로그·트레이스와 응답을 연결해 장애 분석 시간을 줄인다.

---

## 1. 표준 에러 응답 스키마

모든 에러 응답은 다음 JSON 구조를 따른다. 성공 응답 형식은 [api-design-guide](./api-design-guide.md) 참고.

```jsonc
{
  "error": {
    "code": "MEETING_CAPACITY_EXCEEDED",   // 도메인/시스템 에러 코드 (string, 안정적 식별자)
    "message": "정원이 가득 차 참가할 수 없습니다.", // 사람이 읽는 메시지 (클라이언트 노출 가능)
    "status": 409,                          // HTTP status (참고용 중복 필드)
    "traceId": "b7c1a0e2f3d4567890abcdef12345678", // 로그/트레이스 상관관계 ID
    "timestamp": "2026-06-09T12:34:56.789+09:00",  // ISO-8601, 오프셋 포함
    "path": "/api/v1/meetings/8a1f2c34/participants", // 요청 경로
    "errors": [                              // 필드 단위 상세(검증 실패 등). 없으면 빈 배열 또는 생략
      {
        "field": "capacity",
        "rejectedValue": 0,
        "reason": "정원은 1 이상이어야 합니다."
      }
    ]
  }
}
```

### 필드 정의

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `error.code` | string | O | 안정적 에러 식별자. 클라이언트 분기 처리의 기준. 절대 임의 변경 금지 |
| `error.message` | string | O | 사용자/개발자용 메시지. 다국어 필요 시 클라이언트가 `code`로 매핑 |
| `error.status` | int | O | HTTP status 코드(헤더와 동일 값, 디버깅 편의용) |
| `error.traceId` | string | O | 분산 추적 ID. 로그 검색 키. 미사용 트레이싱 환경에서는 요청 UUID |
| `error.timestamp` | string | O | 에러 발생 시각(ISO-8601) |
| `error.path` | string | O | 요청 URI |
| `error.errors[]` | array | X | 필드 단위 오류 상세. 검증 실패 시 채움 |
| `error.errors[].field` | string | X | 문제가 된 필드 경로(`location.latitude`처럼 중첩 표현) |
| `error.errors[].rejectedValue` | any | X | 거부된 입력값(민감정보는 마스킹) |
| `error.errors[].reason` | string | X | 해당 필드가 거부된 이유 |

> **보안 주의**: 스택 트레이스, 내부 SQL, 시스템 경로, 토큰/비밀번호 등은 응답에 **절대 노출하지 않는다**.
> 상세 원인은 `traceId`로 서버 로그에서 확인한다. → [security-policy](../security/security-policy.md)

---

## 2. HTTP Status 매핑 표

| HTTP status | 의미 | 사용 시점 | 대표 코드 예시 |
| --- | --- | --- | --- |
| `400 Bad Request` | 요청 형식/검증 오류 | 필수값 누락, 타입 불일치, 형식 오류 | `VALIDATION_ERROR`, `INVALID_REQUEST` |
| `401 Unauthorized` | 인증 실패 | 토큰 없음/만료/위조 | `AUTH_TOKEN_EXPIRED`, `AUTH_TOKEN_INVALID` |
| `403 Forbidden` | 권한 없음 | 인증은 됐으나 리소스 접근 권한 없음 | `ACCESS_DENIED` |
| `404 Not Found` | 리소스 없음 | 존재하지 않는 ID 조회 | `MEETING_NOT_FOUND` |
| `405 Method Not Allowed` | 미지원 메서드 | 허용되지 않은 HTTP 메서드 | `METHOD_NOT_ALLOWED` |
| `409 Conflict` | 상태 충돌 | 중복 생성, 동시성 충돌, 상태 전이 불가 | `MEETING_ALREADY_CLOSED`, `DUPLICATE_RESOURCE` |
| `422 Unprocessable Entity` | 비즈니스 규칙 위반 | 문법은 맞으나 도메인 규칙 위반 | `MEETING_CAPACITY_EXCEEDED` |
| `429 Too Many Requests` | 요청 과다 | rate limit 초과 | `RATE_LIMIT_EXCEEDED` |
| `500 Internal Server Error` | 서버 오류 | 처리되지 않은 예외 | `INTERNAL_ERROR` |
| `502 / 503 / 504` | 외부/가용성 | 업스트림 장애, 점검, 타임아웃 | `UPSTREAM_UNAVAILABLE`, `SERVICE_UNAVAILABLE` |

> `400` vs `422`: **형식 오류**(파싱/타입/필수값)는 `400`, **형식은 맞지만 비즈니스 규칙 위반**은 `422`로 구분한다.
> 팀이 단순화를 원하면 `400`으로 통합할 수 있으나, **하나로 통일**하는 것이 핵심이다.

---

## 3. 도메인 에러 코드 표 (예시)

에러 코드는 `<도메인>_<사유>` 또는 공통 접두어 형태로 관리한다.
새 코드를 추가하면 반드시 이 표(또는 코드 카탈로그)에 등록한다.

### 3.1 공통/시스템 코드

| 코드 | HTTP | 설명 |
| --- | --- | --- |
| `VALIDATION_ERROR` | 400 | 요청 필드 검증 실패(`errors[]` 동반) |
| `INVALID_REQUEST` | 400 | 잘못된 요청(파라미터/바디 형식 오류) |
| `AUTH_TOKEN_INVALID` | 401 | 토큰 위조/형식 오류 |
| `AUTH_TOKEN_EXPIRED` | 401 | 토큰 만료 |
| `ACCESS_DENIED` | 403 | 권한 부족 |
| `RESOURCE_NOT_FOUND` | 404 | 일반 리소스 미존재 |
| `DUPLICATE_RESOURCE` | 409 | 중복 생성 시도 |
| `RATE_LIMIT_EXCEEDED` | 429 | 요청 한도 초과 |
| `INTERNAL_ERROR` | 500 | 서버 내부 오류 |
| `UPSTREAM_UNAVAILABLE` | 503 | 외부 시스템 장애 |

### 3.2 도메인 코드 — `meeting` (예시)

| 코드 | HTTP | 설명 |
| --- | --- | --- |
| `MEETING_NOT_FOUND` | 404 | 존재하지 않는 모임 |
| `MEETING_CAPACITY_EXCEEDED` | 422 | 정원 초과로 참가 불가 |
| `MEETING_ALREADY_CLOSED` | 409 | 마감된 모임에 대한 변경 시도 |
| `MEETING_ALREADY_JOINED` | 409 | 이미 참가한 모임 재신청 |
| `MEETING_NOT_HOST` | 403 | 주최자만 가능한 작업을 비주최자가 시도 |
| `MEETING_INVALID_STATE_TRANSITION` | 422 | 허용되지 않은 상태 전이(예: CLOSED → OPEN) |

---

## 4. 예시 응답

### 4.1 검증 실패 — `400`

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "요청 값이 올바르지 않습니다.",
    "status": 400,
    "traceId": "b7c1a0e2f3d4567890abcdef12345678",
    "timestamp": "2026-06-09T12:34:56.789+09:00",
    "path": "/api/v1/meetings",
    "errors": [
      { "field": "title", "rejectedValue": "", "reason": "제목은 필수입니다." },
      { "field": "capacity", "rejectedValue": 0, "reason": "정원은 1 이상이어야 합니다." }
    ]
  }
}
```

### 4.2 비즈니스 규칙 위반 — `422`

```json
{
  "error": {
    "code": "MEETING_CAPACITY_EXCEEDED",
    "message": "정원이 가득 차 참가할 수 없습니다.",
    "status": 422,
    "traceId": "0f9e8d7c6b5a4938271605f4e3d2c1b0",
    "timestamp": "2026-06-09T12:40:00.123+09:00",
    "path": "/api/v1/meetings/8a1f2c34-5b6d-4e7f-8901-23456789abcd/participants",
    "errors": []
  }
}
```

### 4.3 인증 만료 — `401`

```json
{
  "error": {
    "code": "AUTH_TOKEN_EXPIRED",
    "message": "인증 토큰이 만료되었습니다. 다시 로그인해 주세요.",
    "status": 401,
    "traceId": "11aa22bb33cc44dd55ee66ff77001122",
    "timestamp": "2026-06-09T12:45:10.000+09:00",
    "path": "/api/v1/meetings",
    "errors": []
  }
}
```

---

## 5. Spring `@RestControllerAdvice` 매핑 (예시)

> 모든 예외는 한 곳(전역 핸들러)에서 표준 응답으로 변환한다. 컨트롤러/서비스에서 직접 응답을 만들지 않는다.

### 5.1 에러 코드 enum

```java
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "인증 토큰이 만료되었습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    MEETING_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 모임입니다."),
    MEETING_CAPACITY_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "정원이 가득 차 참가할 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;
    // 생성자/getter 생략
}
```

### 5.2 도메인 예외

```java
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }
    public ErrorCode getErrorCode() { return errorCode; }
}
```

### 5.3 전역 핸들러

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 도메인 예외
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(
            BusinessException ex, HttpServletRequest req) {
        ErrorCode code = ex.getErrorCode();
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, req.getRequestURI(), List.of()));
    }

    // Bean Validation 실패 (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<FieldErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldErrorDetail(
                        fe.getField(), fe.getRejectedValue(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, req.getRequestURI(), details));
    }

    // 미처리 예외 → 500 (메시지/스택 트레이스 비노출)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception. traceId={}", MDC.get("traceId"), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, req.getRequestURI(), List.of()));
    }
}
```

- `traceId`는 요청 필터에서 `MDC`에 넣고, `ErrorResponse.of(...)` 내부에서 `MDC.get("traceId")`로 읽어 채운다.
- 트레이싱(OpenTelemetry/Sleuth) 사용 시 trace context의 traceId를 그대로 사용한다 → [observability](../architecture/observability.md).
- 보안·재시도·외부 장애 매핑은 [error-handling](../architecture/error-handling.md)을 함께 참고한다.

---

## 6. 클라이언트 처리 가이드

- 클라이언트는 **HTTP status로 큰 분기**, **`error.code`로 세부 분기**한다.
- `error.message`를 그대로 노출하기보다 `code`를 다국어 메시지로 매핑하는 것을 권장한다.
- `401 AUTH_TOKEN_EXPIRED` 수신 시 토큰 재발급 플로우를 트리거한다.
- 장애 신고 시 사용자에게 `traceId`를 함께 안내하면 디버깅이 빨라진다.

---

## 체크리스트

- [ ] 모든 에러가 표준 스키마(`code`/`message`/`traceId`/`timestamp`/`errors[]`)를 따르는가
- [ ] HTTP status와 도메인 코드 매핑이 표와 일치하는가
- [ ] 새 에러 코드를 코드 카탈로그(3절)에 등록했는가
- [ ] 스택 트레이스·내부 정보가 응답에 노출되지 않는가
- [ ] `traceId`가 로그/트레이스와 연결되는가
- [ ] 검증 실패 시 `errors[]`에 필드 단위 상세를 채우는가
- [ ] 전역 핸들러(`@RestControllerAdvice`)로 일원화했는가
