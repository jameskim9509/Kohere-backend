# Error Handling

> **예시(Spring Boot 기준) 문서입니다.** 예외 계층/핸들러 코드는 **예시 스택(Spring Boot 3.x / Java 17 / Spring MVC)** 기준입니다. 실제 스택이 확정되면 어노테이션·클래스명을 교체하세요. 에러 응답 **포맷 정본**은 [error-response-guide](../api/error-response-guide.md)이며, 이 문서는 그 포맷을 만들어내는 **서버 내부 처리 전략**을 다룹니다.

## 목적

예외를 **분류**하고, **계층 구조**로 모델링하며, **전역 핸들러**에서 일관된 응답으로 변환하는 방법을 정의한다. 또한 에러코드 체계, 로깅 레벨, 재시도 가능 여부를 명확히 한다.

- 예외를 도메인/검증/시스템(외부)으로 분류한다.
- 예외 계층 예시를 제공한다.
- `@RestControllerAdvice` 전역 핸들러 코드 예시를 제공한다.
- 에러코드/로깅/재시도 정책을 표로 정의한다.

---

## 1. 예외 분류

| 분류 | 의미 | 예시 | HTTP | 누구 책임 | 재시도 |
| --- | --- | --- | --- | --- | --- |
| 도메인 예외 | 비즈니스 규칙 위반 | 잔액 부족, 중복 가입, 상태 전이 불가 | 409 / 422 | 호출자(데이터) | 불가 |
| 검증 예외 | 입력 형식/필수값 오류 | 빈 필드, 형식 불일치 | 400 | 호출자(요청) | 불가(수정 후 재요청) |
| 인증/인가 예외 | 미인증·권한 없음 | 토큰 만료, 권한 부족 | 401 / 403 | 호출자 | 토큰 재발급 후 가능 |
| 리소스 없음 | 대상 부재 | 존재하지 않는 주문 | 404 | 호출자 | 불가 |
| 시스템/외부 예외 | 인프라·외부 장애 | DB 다운, 외부 PG 타임아웃 | 502 / 503 / 504 | 서버/외부 | **가능**(백오프) |
| 미분류 예외 | 예상 못한 오류 | NPE 등 버그 | 500 | 서버 | 불가 |

원칙: **예상 가능한 실패는 의미 있는 예외 타입**으로, **예상 못한 실패는 500**으로 흘려보내되 절대 스택트레이스를 클라이언트에 노출하지 않는다.

---

## 2. 예외 계층 예시

모든 비즈니스 예외는 공통 베이스를 상속하고 **에러코드(ErrorCode)** 를 갖는다. 베이스는 `RuntimeException`(unchecked)으로 하여 트랜잭션 롤백 기본 동작과 맞춘다.

```text
RuntimeException
 └─ BusinessException (abstract, ErrorCode 보유)
     ├─ DomainException
     │   ├─ InsufficientBalanceException   (PAYMENT_INSUFFICIENT_BALANCE)
     │   └─ InvalidOrderStateException     (ORDER_INVALID_STATE)
     ├─ ResourceNotFoundException
     │   └─ OrderNotFoundException          (ORDER_NOT_FOUND)
     └─ AuthorizationException              (AUTH_FORBIDDEN)
```

```java
public enum ErrorCode {
    // 공통
    INVALID_INPUT("COMMON_400", HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INTERNAL_ERROR("COMMON_500", HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다."),
    // 도메인
    ORDER_NOT_FOUND("ORDER_404", HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    ORDER_INVALID_STATE("ORDER_409", HttpStatus.CONFLICT, "현재 상태에서 처리할 수 없습니다."),
    PAYMENT_INSUFFICIENT_BALANCE("PAY_422", HttpStatus.UNPROCESSABLE_ENTITY, "잔액이 부족합니다."),
    // 외부
    EXTERNAL_UNAVAILABLE("EXT_503", HttpStatus.SERVICE_UNAVAILABLE, "외부 서비스를 일시적으로 사용할 수 없습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;
    // 생성자/getter 생략
}

public abstract class BusinessException extends RuntimeException {
    private final transient ErrorCode errorCode;
    protected BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
    public ErrorCode getErrorCode() { return errorCode; }
}

public class OrderNotFoundException extends BusinessException {
    public OrderNotFoundException(Long orderId) {
        super(ErrorCode.ORDER_NOT_FOUND);
    }
}
```

---

## 3. 전역 핸들러 (`@RestControllerAdvice`)

모든 예외를 한 곳에서 잡아 [error-response-guide](../api/error-response-guide.md)의 표준 포맷으로 변환한다.

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1) 비즈니스 예외 - 예상된 실패 (WARN, 스택트레이스 미포함)
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e, HttpServletRequest req) {
        ErrorCode ec = e.getErrorCode();
        log.warn("business error: code={}, path={}, msg={}", ec.getCode(), req.getRequestURI(), e.getMessage());
        return ResponseEntity.status(ec.getStatus())
            .body(ErrorResponse.of(ec, req.getRequestURI(), traceId()));
    }

    // 2) Bean Validation 실패 - 필드 에러 목록 포함
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<FieldErrorDetail> details = e.getBindingResult().getFieldErrors().stream()
            .map(f -> new FieldErrorDetail(f.getField(), f.getDefaultMessage()))
            .toList();
        log.warn("validation error: path={}, fields={}", req.getRequestURI(), details);
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, req.getRequestURI(), traceId(), details));
    }

    // 3) 인가 실패
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e, HttpServletRequest req) {
        log.warn("access denied: path={}", req.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse.of(ErrorCode.valueOf("AUTH_FORBIDDEN"), req.getRequestURI(), traceId()));
    }

    // 4) 미분류 예외 - 버그/예상 외 (ERROR, 스택트레이스 로깅, 내부 메시지 비노출)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest req) {
        log.error("unexpected error: path={}", req.getRequestURI(), e); // 스택트레이스는 로그에만
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, req.getRequestURI(), traceId()));
    }

    private String traceId() {
        return MDC.get("traceId"); // 분산추적 연계, observability 참고
    }
}
```

표준 에러 응답 예시(JSON) — 정본 포맷은 [error-response-guide](../api/error-response-guide.md):

```json
{
  "timestamp": "2026-06-09T12:34:56.789Z",
  "code": "ORDER_404",
  "message": "주문을 찾을 수 없습니다.",
  "path": "/api/v1/orders/1001",
  "traceId": "a1b2c3d4e5f6g7h8",
  "errors": []
}
```

검증 실패 시 `errors` 배열 예시:

```json
{
  "code": "COMMON_400",
  "message": "입력값이 올바르지 않습니다.",
  "errors": [
    { "field": "email", "reason": "이메일 형식이 아닙니다." },
    { "field": "amount", "reason": "0보다 커야 합니다." }
  ]
}
```

---

## 4. 에러코드 / 로깅 / 재시도 정책

**에러코드 규칙**

- 형식: `{도메인}_{HTTP상태}` (예: `ORDER_404`, `PAY_422`). 한 곳(`ErrorCode` enum)에서만 정의하고 중복 금지.
- 클라이언트는 HTTP status가 아니라 **`code` 문자열로 분기**하도록 안내한다(메시지는 변경될 수 있음).

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
| 외부 5xx/타임아웃 | 가능(멱등 연산만) | 지수 백오프 + 서킷브레이커 — [external-integration](external-integration.md) §2 |
| DB 일시 오류(deadlock 등) | 제한적 가능 | 짧은 재시도, 트랜잭션 재실행 |
| 결제 등 비멱등 | 신중 | idempotency key로 중복 방지 후 재시도 |

---

## 관련 문서

- 에러 응답 표준 포맷(정본): [error-response-guide](../api/error-response-guide.md)
- 외부 연동 재시도/서킷: [external-integration](external-integration.md)
- 로깅/traceId 전파: [observability](observability.md)
- 계층/트랜잭션: [backend-architecture](backend-architecture.md)
- 보안/로그 마스킹: [security-policy](../security/security-policy.md)

---

## 체크리스트

- [ ] `ErrorCode` enum 단일 정의, 중복/누락 점검
- [ ] 응답 포맷이 [error-response-guide](../api/error-response-guide.md)와 일치
- [ ] 500 응답에 내부 메시지/스택트레이스가 노출되지 않음
- [ ] 민감정보 로그 마스킹 적용
- [ ] 외부/DB 일시 오류 재시도 가능 여부 분류 완료
