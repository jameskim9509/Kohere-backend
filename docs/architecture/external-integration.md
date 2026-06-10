# External Integration

> **예시(Spring Boot 기준) 문서입니다.** 인터페이스/회복탄력성 코드는 **예시 스택(Spring Boot 3.x / Java 17 / Resilience4j / Redis)** 기준입니다. 실제 스택이 확정되면 라이브러리·어노테이션을 교체하세요. 외부 시스템 이름·주소는 전부 가짜 예시(`example.com`)입니다.

## 목적

외부 시스템(결제 PG, 메일/SMS, OAuth, 타사 API 등) 연동을 **adapter/gateway로 격리**하고, **타임아웃·재시도·서킷브레이커·idempotency**로 외부 장애가 우리 시스템 전체로 번지지 않게 한다.

- 외부 연동을 포트(인터페이스) + 어댑터로 격리하는 패턴을 정의한다.
- 타임아웃/재시도(지수 백오프)/서킷브레이커/idempotency 적용 예시를 제공한다.
- 외부 장애 시 graceful degradation 전략을 제시한다.

---

## 1. 격리 패턴: Port & Adapter

도메인/애플리케이션은 **포트 인터페이스**만 알고, 실제 HTTP 호출은 `infra`의 어댑터가 구현한다. 외부 응답 모델을 도메인에 그대로 들이지 않고 **번역(translate)** 한다.

```text
  application                      infra (어댑터)                외부 시스템
 ┌──────────────┐   호출   ┌──────────────────────┐  HTTPS  ┌──────────────┐
 │ OrderService │ ───────► │  PaymentGateway(Port)│ ──────► │  결제 PG     │
 │              │          │  TossPaymentAdapter  │ ◄────── │ example.com  │
 └──────────────┘          │  (타임아웃/재시도/CB) │         └──────────────┘
        ▲                  └──────────┬───────────┘
        │   도메인 모델                │  외부 DTO ↔ 도메인 변환
        └──────────────────────────────┘
```

```java
// application.port — 도메인 용어로 정의된 포트(외부 세부사항 없음)
public interface PaymentGateway {
    PaymentResult pay(PaymentCommand command);   // command에 idempotencyKey 포함
}

// infra.client — 실제 어댑터 구현
@Component
@RequiredArgsConstructor
public class PgPaymentAdapter implements PaymentGateway {

    private final RestClient pgClient;  // baseUrl=https://api.example.com, 타임아웃 설정됨

    @Override
    @CircuitBreaker(name = "pg", fallbackMethod = "payFallback")
    @Retry(name = "pg")
    public PaymentResult pay(PaymentCommand command) {
        PgPayRequest body = PgRequestMapper.toRequest(command);      // 도메인 → 외부 DTO
        PgPayResponse res = pgClient.post()
            .uri("/v1/payments")
            .header("Idempotency-Key", command.idempotencyKey())     // 멱등 보장
            .body(body)
            .retrieve()
            .body(PgPayResponse.class);
        return PgResponseMapper.toResult(res);                       // 외부 DTO → 도메인
    }

    // 서킷 오픈/최종 실패 시 fallback (graceful degradation)
    private PaymentResult payFallback(PaymentCommand command, Throwable t) {
        log.error("PG fallback: idemKey={}, cause={}", command.idempotencyKey(), t.toString());
        throw new ExternalUnavailableException(ErrorCode.EXTERNAL_UNAVAILABLE);
    }
}
```

원칙: 외부 라이브러리 타입(`PgPayResponse` 등)이 `application`/`domain`에 새지 않게 한다. 의존 규칙은 [backend-architecture](backend-architecture.md) §3 참고.

---

## 2. 회복탄력성: 타임아웃 · 재시도 · 서킷브레이커

세 가지를 **함께** 적용한다. 순서는 바깥에서부터 `CircuitBreaker → Retry → TimeLimiter/타임아웃`.

```text
요청 ─► [서킷브레이커] ─► [재시도(지수백오프)] ─► [타임아웃] ─► 외부 호출
         (열리면 즉시       (일시 오류만 N회        (느린 응답
          fallback)          멱등 연산만)            차단)
```

**(1) 타임아웃** — 무한 대기 금지. connect/read 분리.

```yaml
# application.yml (예시 값 — 외부 SLA에 맞게 조정)
external:
  pg:
    base-url: https://api.example.com
    connect-timeout: 1s
    read-timeout: 3s
```

**(2) 재시도(지수 백오프 + 지터)** — **멱등 연산에만** 적용. POST 결제는 idempotency key가 있을 때만.

```yaml
resilience4j:
  retry:
    instances:
      pg:
        max-attempts: 3                 # 최초 1 + 재시도 2
        wait-duration: 200ms
        exponential-backoff-multiplier: 2   # 200ms → 400ms → 800ms
        enable-randomized-wait: true         # 지터로 thundering herd 방지
        retry-exceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
        ignore-exceptions:
          - com.example.app.common.error.BusinessException  # 4xx는 재시도 안 함
```

**(3) 서킷브레이커** — 실패율이 임계치를 넘으면 회로를 열어 빠르게 실패시키고 외부를 보호한다.

```yaml
resilience4j:
  circuitbreaker:
    instances:
      pg:
        sliding-window-size: 20
        failure-rate-threshold: 50           # 50% 실패 시 OPEN
        slow-call-duration-threshold: 2s
        slow-call-rate-threshold: 80
        wait-duration-in-open-state: 10s     # 10초 후 HALF_OPEN
        permitted-number-of-calls-in-half-open-state: 3
```

| 상태 | 의미 | 동작 |
| --- | --- | --- |
| CLOSED | 정상 | 요청 통과, 실패율 집계 |
| OPEN | 차단 | 즉시 fallback, 외부 호출 안 함 |
| HALF_OPEN | 탐색 | 일부 요청만 통과시켜 회복 확인 |

---

## 3. Idempotency (멱등성)

네트워크 타임아웃 후 재시도하면 **같은 요청이 두 번 처리**될 수 있다. 비멱등 연산(결제/주문 등)은 **idempotency key**로 중복을 막는다.

```text
클라이언트 ──(Idempotency-Key: 7f3a-...)──► 우리 서버
                                              │
                                  ┌───────────┴────────────┐
                                  │ Redis: SETNX key 처리중 │
                                  └───────────┬────────────┘
                          이미 있음◄──────────┴──────────►없음(최초)
                              │                              │
                         저장된 결과 반환                실제 처리 후 결과 저장(TTL)
```

```java
public PaymentResult pay(PaymentCommand cmd) {
    String key = "idem:pay:" + cmd.idempotencyKey();
    // 최초 요청만 선점(SETNX). 이미 처리 중/완료면 저장된 결과 반환
    Boolean first = redis.opsForValue().setIfAbsent(key, "IN_PROGRESS", Duration.ofMinutes(10));
    if (Boolean.FALSE.equals(first)) {
        return loadStoredResult(key)             // 같은 결과를 그대로 반환 → 중복 처리 방지
            .orElseThrow(() -> new DuplicateInProgressException());
    }
    PaymentResult result = paymentGateway.pay(cmd);
    storeResult(key, result, Duration.ofHours(24));
    return result;
}
```

- 우리가 **호출당하는** API: 클라이언트에 `Idempotency-Key` 헤더를 요구하고 위처럼 처리.
- 우리가 **호출하는** 외부 API: 외부가 idempotency를 지원하면 키를 전달, 미지원이면 자체 중복 방지(주문번호 등)로 보완.

---

## 4. 외부 장애 대비 (Graceful Degradation)

외부가 죽어도 우리 서비스의 핵심 기능은 살아 있어야 한다.

| 외부 의존 | 장애 시 전략 |
| --- | --- |
| 알림(메일/SMS) | 비동기 큐/아웃박스에 적재 후 나중에 발송. 사용자 흐름은 막지 않음 |
| 추천/부가 정보 API | 캐시된 값 또는 기본값 반환(fallback), 화면은 정상 |
| 결제 PG | 즉시 실패 + 명확한 에러코드(`EXT_503`), 재시도 안내, 사용자 데이터 보존 |
| OAuth Provider | 일시 장애 시 재시도 안내, 로컬 세션은 유지 |

- 외부 호출은 **트랜잭션 밖**에서 수행한다(커넥션 점유·롤백 불가 방지). 필요 시 **Transactional Outbox** 패턴으로 "DB 커밋 + 외부 발행"을 분리한다.
- 모든 외부 호출은 메트릭(성공/실패/지연)과 traceId를 남긴다 → [observability](observability.md).
- 외부 장애가 곧 사용자 영향인 경로는 [incident-response](../operations/incident-response.md)에 런북을 둔다.

---

## 관련 문서

- 어댑터 격리/의존 규칙: [backend-architecture](backend-architecture.md)
- 예외 변환/재시도 분류: [error-response-guide](../api/error-response-guide.md)
- 외부 호출 관측: [observability](observability.md)
- 장애 대응 런북: [incident-response](../operations/incident-response.md)

---

## 체크리스트

- [ ] 모든 외부 호출에 connect/read 타임아웃 설정
- [ ] 재시도는 멱등 연산에만, 4xx는 재시도 제외
- [ ] 서킷브레이커 + fallback 구성 및 알림 연동
- [ ] 비멱등 연산에 idempotency key 적용
- [ ] 외부 호출이 트랜잭션 밖에서 수행되는지 점검
- [ ] 외부 DTO가 domain/application에 누수되지 않는지 점검
