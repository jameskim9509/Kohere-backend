# Error Response Guide

> 본 문서는 모든 API가 공유하는 **표준 에러 응답 형식 / 에러 코드 체계**와, 그것을 만들어내는 **서버 내부 처리 전략(예외 분류·계층·전역 핸들러·로깅·재시도)** 을 함께 정의하는 **에러 처리 정본**이다.
> 예시는 **Spring Boot 3.x / `@RestControllerAdvice`** 기준이며, 스택이 확정되면 핸들러 코드만 교체하고 응답 스키마·코드 체계·정책은 그대로 재사용한다.

관련 문서: [api-design-guide](./api-design-guide.md) · [versioning-policy](./versioning-policy.md) · [observability](../architecture/observability.md) · [external-integration](../architecture/external-integration.md)

---

## 목적

- 클라이언트가 **단일한 구조**로 모든 에러를 파싱·처리하도록 보장한다.
- HTTP status와 도메인 에러 코드를 분리해, 같은 status 안에서도 원인을 구분한다.
- `traceId`로 로그·트레이스와 응답을 연결해 장애 분석 시간을 줄인다.
- 예외를 분류·계층화하고 **전역 핸들러 한 곳**에서 표준 응답으로 변환한다.

---

## 1. 표준 에러 응답 스키마

모든 에러 응답은 다음 **평면(flat) JSON 구조**를 따른다. 성공 응답 형식은 [api-design-guide](./api-design-guide.md) 참고.

```jsonc
{
  "code": "MEETING_CAPACITY_EXCEEDED",   // 도메인/시스템 에러 코드 (string, 안정적 식별자)
  "message": "정원이 가득 차 참가할 수 없습니다.", // 사람이 읽는 메시지 (클라이언트 노출 가능)
  "status": 422,                          // HTTP status (참고용 중복 필드)
  "path": "/api/v1/meetings/8a1f2c34/participants", // 요청 경로
  "timestamp": "2026-06-09T12:34:56.789+09:00",  // ISO-8601, 오프셋 포함
  "traceId": "b7c1a0e2f3d4567890abcdef12345678", // 로그/트레이스 상관관계 ID
  "errors": [                              // 필드 단위 상세(검증 실패 등). 없으면 빈 배열
    { "field": "capacity", "rejectedValue": 0, "reason": "정원은 1 이상이어야 합니다." }
  ]
}
```

### 필드 정의

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `code` | string | O | 안정적 에러 식별자. 클라이언트 분기 처리의 기준. 절대 임의 변경 금지 |
| `message` | string | O | 사용자/개발자용 메시지. 다국어 필요 시 클라이언트가 `code`로 매핑 |
| `status` | int | O | HTTP status 코드(헤더와 동일 값, 디버깅 편의용) |
| `path` | string | O | 요청 URI |
| `timestamp` | string | O | 에러 발생 시각(ISO-8601) |
| `traceId` | string | O | 분산 추적 ID. 로그 검색 키. 미사용 환경에서는 요청 UUID |
| `errors[]` | array | X | 필드 단위 오류 상세. 검증 실패 시 채움 |
| `errors[].field` | string | X | 문제가 된 필드 경로(`location.latitude`처럼 중첩 표현) |
| `errors[].rejectedValue` | any | X | 거부된 입력값(민감정보는 마스킹) |
| `errors[].reason` | string | X | 해당 필드가 거부된 이유 |

> **보안 주의**: 스택 트레이스, 내부 SQL, 시스템 경로, 토큰/비밀번호 등은 응답에 **절대 노출하지 않는다**.
> 상세 원인은 `traceId`로 서버 로그에서 확인한다. → [security-policy](../security/security-policy.md)

---

## 2. 예외 분류

| 분류 | 의미 | 예시 | HTTP | 누구 책임 | 재시도 |
| --- | --- | --- | --- | --- | --- |
| 도메인 예외 | 비즈니스 규칙 위반 | 정원 초과, 중복 가입, 상태 전이 불가 | 409 / 422 | 호출자(데이터) | 불가 |
| 검증 예외 | 입력 형식/필수값 오류 | 빈 필드, 형식 불일치 | 400 | 호출자(요청) | 불가(수정 후 재요청) |
| 인증/인가 예외 | 미인증·권한 없음 | 토큰 만료, 권한 부족 | 401 / 403 | 호출자 | 토큰 재발급 후 가능 |
| 리소스 없음 | 대상 부재 | 존재하지 않는 모임 | 404 | 호출자 | 불가 |
| 시스템/외부 예외 | 인프라·외부 장애 | DB 다운, 외부 PG 타임아웃 | 502 / 503 / 504 | 서버/외부 | **가능**(백오프) |
| 미분류 예외 | 예상 못한 오류 | NPE 등 버그 | 500 | 서버 | 불가 |

원칙: **예상 가능한 실패는 의미 있는 예외 타입**으로, **예상 못한 실패는 500**으로 흘려보내되 절대 스택트레이스를 클라이언트에 노출하지 않는다.

---

## 3. HTTP Status 매핑 표

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

## 4. 도메인 에러 코드 표 (예시)

에러 코드는 `<도메인>_<사유>` 또는 공통 접두어 형태로 관리하고, **한 곳(`ErrorCode` enum)에서만 정의**한다.
새 코드를 추가하면 반드시 이 표(코드 카탈로그)에 등록한다. 클라이언트는 HTTP status가 아니라 **`code` 문자열로 분기**한다(메시지는 변경될 수 있음).

### 4.1 공통/시스템 코드

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

### 4.2 도메인 코드 — `meeting` (예시)

| 코드 | HTTP | 설명 |
| --- | --- | --- |
| `MEETING_NOT_FOUND` | 404 | 존재하지 않는 모임 |
| `MEETING_CAPACITY_EXCEEDED` | 422 | 정원 초과로 참가 불가 |
| `MEETING_ALREADY_CLOSED` | 409 | 마감된 모임에 대한 변경 시도 |
| `MEETING_ALREADY_JOINED` | 409 | 이미 참가한 모임 재신청 |
| `MEETING_NOT_HOST` | 403 | 주최자만 가능한 작업을 비주최자가 시도 |
| `MEETING_INVALID_STATE_TRANSITION` | 422 | 허용되지 않은 상태 전이(예: CLOSED → OPEN) |

---

## 5. 예시 응답

### 5.1 검증 실패 — `400`

```json
{
  "code": "VALIDATION_ERROR",
  "message": "요청 값이 올바르지 않습니다.",
  "status": 400,
  "path": "/api/v1/meetings",
  "timestamp": "2026-06-09T12:34:56.789+09:00",
  "traceId": "b7c1a0e2f3d4567890abcdef12345678",
  "errors": [
    { "field": "title", "rejectedValue": "", "reason": "제목은 필수입니다." },
    { "field": "capacity", "rejectedValue": 0, "reason": "정원은 1 이상이어야 합니다." }
  ]
}
```

### 5.2 비즈니스 규칙 위반 — `422`

```json
{
  "code": "MEETING_CAPACITY_EXCEEDED",
  "message": "정원이 가득 차 참가할 수 없습니다.",
  "status": 422,
  "path": "/api/v1/meetings/8a1f2c34-5b6d-4e7f-8901-23456789abcd/participants",
  "timestamp": "2026-06-09T12:40:00.123+09:00",
  "traceId": "0f9e8d7c6b5a4938271605f4e3d2c1b0",
  "errors": []
}
```

### 5.3 인증 만료 — `401`

```json
{
  "code": "AUTH_TOKEN_EXPIRED",
  "message": "인증 토큰이 만료되었습니다. 다시 로그인해 주세요.",
  "status": 401,
  "path": "/api/v1/meetings",
  "timestamp": "2026-06-09T12:45:10.000+09:00",
  "traceId": "11aa22bb33cc44dd55ee66ff77001122",
  "errors": []
}
```

---

## 6. 예외 계층 (예시)

모든 비즈니스 예외는 공통 베이스를 상속하고 **에러코드(ErrorCode)** 를 갖는다. 베이스는 `RuntimeException`(unchecked)으로 하여 트랜잭션 롤백 기본 동작과 맞춘다([transaction-policy](../database/transaction-policy.md) §8).

```text
RuntimeException
 └─ BusinessException (abstract, ErrorCode 보유)
     ├─ DomainException
     │   ├─ MeetingCapacityExceededException  (MEETING_CAPACITY_EXCEEDED)
     │   └─ MeetingInvalidStateException      (MEETING_INVALID_STATE_TRANSITION)
     ├─ ResourceNotFoundException
     │   └─ MeetingNotFoundException          (MEETING_NOT_FOUND)
     └─ AuthorizationException                (ACCESS_DENIED)
```

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

public abstract class BusinessException extends RuntimeException {
    private final transient ErrorCode errorCode;
    protected BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }
    public ErrorCode getErrorCode() { return errorCode; }
}
```

---

## 7. 전역 핸들러 (`@RestControllerAdvice`)

모든 예외는 한 곳(전역 핸들러)에서 위 표준 응답으로 변환한다. 컨트롤러/서비스에서 직접 응답을 만들지 않는다.

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1) 비즈니스 예외 - 예상된 실패 (WARN, 스택트레이스 미포함)
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e, HttpServletRequest req) {
        ErrorCode ec = e.getErrorCode();
        log.warn("business error: code={}, path={}", ec.name(), req.getRequestURI());
        return ResponseEntity.status(ec.getStatus())
            .body(ErrorResponse.of(ec, req.getRequestURI(), List.of()));
    }

    // 2) Bean Validation 실패 (@Valid) - 필드 에러 목록 포함
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<FieldErrorDetail> details = e.getBindingResult().getFieldErrors().stream()
            .map(f -> new FieldErrorDetail(f.getField(), f.getRejectedValue(), f.getDefaultMessage()))
            .toList();
        log.warn("validation error: path={}, fields={}", req.getRequestURI(), details);
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, req.getRequestURI(), details));
    }

    // 3) 인가 실패
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e, HttpServletRequest req) {
        log.warn("access denied: path={}", req.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse.of(ErrorCode.ACCESS_DENIED, req.getRequestURI(), List.of()));
    }

    // 4) 미처리 예외 → 500 (메시지/스택 트레이스 비노출, 스택은 로그에만)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest req) {
        log.error("unexpected error: path={}, traceId={}", req.getRequestURI(), MDC.get("traceId"), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, req.getRequestURI(), List.of()));
    }
}
```

- `traceId`는 요청 필터에서 `MDC`에 넣고, `ErrorResponse.of(...)` 내부에서 `MDC.get("traceId")`로 읽어 채운다.
- 트레이싱(OpenTelemetry) 사용 시 trace context의 traceId를 그대로 사용한다 → [observability](../architecture/observability.md).

---

## 8. 로깅 레벨 / 재시도 정책

**로깅 레벨**

| 예외 분류 | 로그 레벨 | 스택트레이스 | 비고 |
| --- | --- | --- | --- |
| 검증/도메인(4xx) | WARN | 미포함 | 정상적 사용자 실수 |
| 인증/인가 | WARN | 미포함 | 보안 이벤트는 별도 감사 로그 |
| 외부/시스템(5xx) | ERROR | 포함 | 알림 연동 대상 |
| 미분류(500) | ERROR | 포함 | 알림 + 원인 추적 |

> 민감정보(비밀번호, 토큰, 카드번호 등)는 로그에 절대 남기지 않는다. 마스킹 규칙은 [security-policy](../security/security-policy.md) 참고.

**재시도 정책**

| 상황 | 재시도 | 방법 |
| --- | --- | --- |
| 4xx(검증/도메인) | 불가 | 요청 수정 후 재요청 |
| 외부 5xx/타임아웃 | 가능(멱등 연산만) | 지수 백오프 + 서킷브레이커 — [external-integration](../architecture/external-integration.md) |
| DB 일시 오류(deadlock 등) | 제한적 가능 | 짧은 재시도, 트랜잭션 재실행 |
| 결제 등 비멱등 | 신중 | idempotency key로 중복 방지 후 재시도 |

---

## 9. 클라이언트 처리 가이드

- 클라이언트는 **HTTP status로 큰 분기**, **`code`로 세부 분기**한다.
- `message`를 그대로 노출하기보다 `code`를 다국어 메시지로 매핑하는 것을 권장한다.
- `401 AUTH_TOKEN_EXPIRED` 수신 시 토큰 재발급 플로우를 트리거한다.
- 장애 신고 시 사용자에게 `traceId`를 함께 안내하면 디버깅이 빨라진다.

---

## 체크리스트

- [ ] 모든 에러가 표준 평면 스키마(`code`/`message`/`status`/`path`/`timestamp`/`traceId`/`errors[]`)를 따르는가
- [ ] HTTP status와 도메인 코드 매핑이 표와 일치하는가
- [ ] 새 에러 코드를 코드 카탈로그(4절)에 등록했는가(`ErrorCode` enum 단일 정의)
- [ ] 예외를 도메인/검증/인증·인가/시스템으로 분류하고 의미 있는 타입으로 던지는가
- [ ] 스택 트레이스·내부 정보가 응답에 노출되지 않는가(500은 로그에만)
- [ ] 전역 핸들러(`@RestControllerAdvice`)로 일원화했는가
- [ ] 로깅 레벨·재시도 가능 여부를 분류 기준에 맞췄는가
- [ ] `traceId`가 로그/트레이스와 연결되는가
