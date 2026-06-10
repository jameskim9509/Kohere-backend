# Observability

> **예시(Spring Boot 기준) 문서입니다.** 로깅/메트릭/추적 코드는 **예시 스택(Spring Boot 3.x / Java 17 / Logback + Micrometer + Prometheus + Grafana + Loki + Tempo)** 기준입니다. 실제 스택이 확정되면 라이브러리·대시보드를 교체하세요. 운영 지표 **정본**은 [monitoring-metrics](../operations/monitoring-metrics.md)이며, 이 문서는 그 지표를 **만들어내는 애플리케이션 측 기준**을 다룹니다.

## 목적

관측성의 세 기둥(**로그·메트릭·추적**)을 통해 "무슨 일이 일어나는가"를 빠르게 파악한다. 구조화 로그, traceId 전파, RED/USE 메트릭, 분산추적, 알림 기준을 정의한다.

- 구조화 로그 포맷과 traceId 전파 방법을 정한다.
- RED/USE 기반 메트릭과 Micrometer 노출 예시를 제공한다.
- 분산추적(W3C Trace Context) 전파를 설명한다.
- 알림(Alert) 기준을 표로 정의한다.

---

## 1. 로깅: 구조화 로그 + traceId 전파

사람이 grep 하는 텍스트 로그가 아니라 **JSON 구조화 로그**로 출력하여 수집/검색/집계가 가능하게 한다. 모든 로그 라인에 `traceId`를 포함해 추적과 연결한다.

```json
{
  "timestamp": "2026-06-09T12:34:56.789Z",
  "level": "INFO",
  "logger": "com.example.app.order.application.OrderService",
  "message": "order placed",
  "traceId": "a1b2c3d4e5f6g7h8",
  "spanId": "1234567890abcdef",
  "userId": "u_1001",
  "orderId": "o_5001",
  "durationMs": 42
}
```

**traceId 전파 (MDC + 분산추적 연동)** — Spring Boot 3는 Micrometer Tracing이 자동으로 `traceId`/`spanId`를 MDC에 넣는다. 인입 시점에 보강 필드를 추가한다.

```java
@Component
public class MdcEnrichmentFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            // traceId/spanId는 추적 라이브러리가 이미 MDC에 주입. 도메인 식별자만 보강
            Optional.ofNullable(req.getHeader("X-User-Id")).ifPresent(v -> MDC.put("userId", v));
            chain.doFilter(req, res);
        } finally {
            MDC.remove("userId");   // 스레드 재사용 누수 방지
        }
    }
}
```

```xml
<!-- logback-spring.xml: JSON 인코더 + MDC 포함 (예시) -->
<appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
  <encoder class="net.logstash.logback.encoder.LogstashEncoder">
    <includeMdcKeyName>traceId</includeMdcKeyName>
    <includeMdcKeyName>spanId</includeMdcKeyName>
    <includeMdcKeyName>userId</includeMdcKeyName>
  </encoder>
</appender>
```

**로깅 규칙**

| 항목 | 규칙 |
| --- | --- |
| 레벨 | ERROR(즉시 대응), WARN(예상된 실패), INFO(주요 이벤트), DEBUG(개발) |
| 민감정보 | 비밀번호·토큰·카드번호 등 **절대 로깅 금지** (마스킹) — [security-policy](../security/security-policy.md) |
| 비동기 경계 | 스레드/큐를 넘을 때 traceId를 전파(컨텍스트 복사) |
| 예외 | 미분류 예외만 스택트레이스 포함 — [error-handling](error-handling.md) §4 |

---

## 2. 메트릭: RED / USE

두 모델을 함께 본다. **RED는 요청(서비스) 관점**, **USE는 자원 관점**.

| 모델 | 대상 | 지표 |
| --- | --- | --- |
| **RED** | 요청 처리(API, 외부호출) | **R**ate(처리량), **E**rrors(에러율), **D**uration(지연) |
| **USE** | 자원(CPU/메모리/풀/디스크) | **U**tilization(사용률), **S**aturation(포화/대기), **E**rrors(오류) |

**Micrometer 예시** — 표준 메트릭(HTTP 서버, JVM, DB 풀)은 자동 수집되고, 도메인 카운터/타이머는 직접 추가한다.

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final MeterRegistry registry;

    @Transactional
    public OrderResult placeOrder(PlaceOrderCommand cmd) {
        Timer.Sample sample = Timer.start(registry);
        try {
            OrderResult result = doPlace(cmd);
            registry.counter("order.placed", "result", "success").increment();   // RED: Rate
            return result;
        } catch (BusinessException e) {
            registry.counter("order.placed", "result", "failure",
                              "code", e.getErrorCode().getCode()).increment();    // RED: Errors
            throw e;
        } finally {
            sample.stop(registry.timer("order.place.duration"));                  // RED: Duration
        }
    }
}
```

```yaml
# application.yml — Prometheus 노출 (예시)
management:
  endpoints.web.exposure.include: health,info,prometheus,metrics
  metrics.tags.application: example-app
  endpoint.health.probes.enabled: true   # /actuator/health/liveness, /readiness
```

대표 PromQL(예시) — 상세 임계/대시보드는 [monitoring-metrics](../operations/monitoring-metrics.md):

```promql
# 에러율(5xx) - RED Errors
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
  / sum(rate(http_server_requests_seconds_count[5m]))

# p95 지연 - RED Duration
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri))

# DB 커넥션 풀 포화 - USE Saturation
hikaricp_connections_pending
```

---

## 3. 분산추적 (Distributed Tracing)

요청이 여러 서비스/외부 호출을 거칠 때 **하나의 traceId**로 전 구간을 연결한다. 표준은 **W3C Trace Context**(`traceparent` 헤더).

```text
클라이언트 ──traceparent──► [API] ──traceparent──► [외부 PG]
   trace=abc...               span=1     │             span=2
                                         └─► [DB]  span=3

  Tempo/Jaeger에서 trace=abc... 로 조회하면 1→2,3 스팬이 하나의 타임라인으로 보임
```

- Spring Boot 3 + Micrometer Tracing(Bridge: OpenTelemetry/Brave)이 인입/아웃바운드 호출에 `traceparent`를 자동 전파한다.
- 로그의 `traceId` ↔ 추적 백엔드(Tempo) ↔ 메트릭을 상호 연결하면 "느린 요청 1건"을 로그·스팬·지표로 한 번에 추적할 수 있다.
- 외부 어댑터([external-integration](external-integration.md))도 같은 traceId를 전파해 외부 호출 지연을 추적에 포함한다.

---

## 4. 알림(Alert) 기준

"무엇이 깨지면 누구를 깨우는가". 임계치는 예시이며 SLO에 맞게 조정한다. 정본은 [monitoring-metrics](../operations/monitoring-metrics.md), 대응은 [incident-response](../operations/incident-response.md).

| 알림 | 조건(예시) | 심각도 | 채널/대응 |
| --- | --- | --- | --- |
| 높은 에러율 | 5xx 비율 > 5% (5분) | P1 | 온콜 호출, 즉시 대응 |
| 지연 급증 | p95 > 1s (10분) | P2 | 채널 알림, 원인 조사 |
| 서킷 OPEN | PG 서킷 OPEN 지속 | P1 | 외부 장애 런북 |
| DB 풀 포화 | pending 커넥션 > 0 지속 | P2 | 쿼리/풀 점검 |
| 헬스 실패 | readiness 연속 실패 | P1 | 인스턴스 재기동/롤백 |
| 디스크/메모리 | 사용률 > 85% | P3 | 용량 점검 |

알림 원칙: **증상(SLO 위반) 기준으로 알림**한다. 단순 자원 수치보다 사용자 영향 지표를 우선한다. 노이즈 알림은 비활성화/임계 조정으로 줄인다.

---

## 관련 문서

- 운영 모니터링 지표(정본): [monitoring-metrics](../operations/monitoring-metrics.md)
- 장애 대응 프로세스: [incident-response](../operations/incident-response.md)
- 예외/로깅 레벨: [error-handling](error-handling.md)
- 외부 호출 추적: [external-integration](external-integration.md)
- 로그 마스킹/보안: [security-policy](../security/security-policy.md)
- 비기능 요구사항: [non-functional-requirements](../requirements/non-functional-requirements.md)

---

## 체크리스트

- [ ] 모든 로그가 JSON 구조화 + traceId 포함
- [ ] 비동기 경계에서 traceId 전파 확인
- [ ] RED/USE 핵심 메트릭이 Prometheus로 노출
- [ ] traceparent가 인입/외부호출에 전파되는지 확인
- [ ] 알림 임계치를 SLO와 일치시키고 노이즈 정리
- [ ] 민감정보 로그 마스킹 적용
