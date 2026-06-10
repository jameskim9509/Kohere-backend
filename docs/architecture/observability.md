# Observability & Monitoring

> **예시(Spring Boot 기준) 문서입니다.** 로깅/메트릭/추적 코드와 지표 이름은 **예시 스택(Spring Boot 3.x / Java 17 / Logback + Micrometer/Actuator + Prometheus + Grafana + Loki + Tempo)** 기준입니다. 실제 스택·임계값(SLO)은 조직/서비스 특성에 맞게 교체하세요. 스택 미정이면 [tech-stack 규칙](../../.claude/rules/tech-stack.md) 참고.

## 목적

관측성의 세 기둥(**로그·메트릭·추적**)으로 "무슨 일이 일어나는가"를 빠르게 파악하고, **핵심 지표·알림 임계값·대시보드**로 서비스 상태를 판단한다. 여기서 정한 임계값은 [incident-response](../operations/incident-response.md)의 감지/SEV 산정과 직접 연결된다.

연관 문서: [incident-response](../operations/incident-response.md) · [runbook](../operations/runbook.md) · [deployment-guide](../operations/deployment-guide.md)

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

**로깅 규칙**

| 항목 | 규칙 |
| --- | --- |
| 레벨 | ERROR(즉시 대응), WARN(예상된 실패), INFO(주요 이벤트), DEBUG(개발) |
| 민감정보 | 비밀번호·토큰·카드번호 등 **절대 로깅 금지** (마스킹) — [security-policy](../security/security-policy.md) |
| 비동기 경계 | 스레드/큐를 넘을 때 traceId를 전파(컨텍스트 복사) |
| 예외 | 미분류 예외만 스택트레이스 포함 — [error-response-guide](../api/error-response-guide.md) §8 |

---

## 2. 메트릭: 골든 시그널 + RED / USE

서비스 상태는 **골든 시그널(Four Golden Signals)** 로 판단하고, 계측은 **RED**(요청 관점)·**USE**(자원 관점)로 모델링한다.

| 골든 시그널 | 의미 | 대표 지표(예시) |
| --- | --- | --- |
| **Latency (지연)** | 요청 처리 시간 | `http_server_requests_seconds` (p50/p95/p99) |
| **Traffic (트래픽)** | 수요/처리량 | RPS(초당 요청 수) |
| **Errors (에러)** | 실패율 | 5xx 비율, 예외율 |
| **Saturation (포화)** | 자원 한계 근접도 | CPU%, 메모리%, DB 풀 사용률, 스레드 풀 |

| 모델 | 대상 | 지표 |
| --- | --- | --- |
| **RED** | 요청 처리(API, 외부호출) | **R**ate(처리량), **E**rrors(에러율), **D**uration(지연) |
| **USE** | 자원(CPU/메모리/풀/디스크) | **U**tilization(사용률), **S**aturation(포화/대기), **E**rrors(오류) |

> 지연은 **성공/실패 요청을 분리**해서 본다(실패가 빨라서 평균이 좋아 보이는 착시 방지).

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
# application.yml — Prometheus 노출 (예시). 엔드포인트는 내부망/인증으로 보호한다.
management:
  endpoints.web.exposure.include: health,info,prometheus,metrics
  metrics.tags.application: example-app
  endpoint.health.probes.enabled: true   # /actuator/health/liveness, /readiness
```

**대표 PromQL (예시)** — 데이터소스가 다르면 동등 쿼리로 교체한다.

```promql
# 5xx 에러율 (RED Errors)
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
  / sum(rate(http_server_requests_seconds_count[5m]))

# p95/p99 지연 (RED Duration)
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri))

# 초당 요청 수 (Traffic)
sum(rate(http_server_requests_seconds_count[1m]))

# DB 커넥션 풀 포화 (USE Saturation)
hikaricp_connections_active / hikaricp_connections_max
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
- 디버깅 순서: **지표 → 해당 시간대 trace → 로그**로 좁힌다([runbook §2.3](../operations/runbook.md#23-로그-확인)).

---

## 4. 알림 임계값 (Alert Thresholds)

"무엇이, 얼마나 나빠지면 누구를 깨우는가". 임계값은 **예시**이며 SLO에 맞춰 조정한다. 단발성 스파이크 오탐을 줄이기 위해 **"X분 지속"** 조건을 함께 둔다. 대응은 [incident-response](../operations/incident-response.md).

| 지표(예시) | 지속 조건 | Warning | Critical | 연결 SEV(예시) |
| --- | --- | --- | --- | --- |
| 5xx 에러율 | 5분 지속 | ≥ 1% | ≥ 5% | SEV2 / SEV1 |
| p99 지연 | 5분 지속 | ≥ 500ms | ≥ 800ms | SEV3 / SEV2 |
| p95 지연 | 5분 지속 | ≥ 300ms | ≥ 500ms | SEV3 |
| 가용성(헬스체크) | 1분 지속 | 1+ 인스턴스 DOWN | 과반 DOWN | SEV2 / SEV1 |
| CPU 사용률 | 10분 지속 | ≥ 70% | ≥ 90% | SEV3 / SEV2 |
| 힙 메모리 사용률 | 10분 지속 | ≥ 75% | ≥ 90% | SEV3 / SEV2 |
| DB 커넥션 풀 사용률 | 5분 지속 | ≥ 70% | ≥ 90% | SEV3 / SEV2 |
| GC 일시정지(Pause) | 5분 지속 | 잦은 minor | 잦은/긴 full GC | SEV3 / SEV2 |
| 외부 연동 에러율 | 5분 지속 | ≥ 2% | ≥ 10% | SEV3 / SEV2 |
| 서킷 OPEN | 지속 | — | OPEN 지속 | SEV2 (외부 장애 런북) |

알림 운영 규칙:

- **증상(SLO 위반) 기준으로 알림**한다. 단순 자원 수치보다 사용자 영향 지표를 우선한다.
- 모든 알림은 **무엇이·어디서·얼마나** 나쁜지와 **다음 행동(runbook 링크)** 을 담는다.
- Critical은 온콜 페이지, Warning은 채널 알림으로 분리해 알림 피로를 줄인다.
- 임계값을 바꾸면 변경 이유를 기록한다(포스트모템 액션과 연결).

---

## 5. 대시보드 구성 (예시)

> 예시(Grafana 기준). 한 화면에서 "건강한가?"를 5초 안에 판단할 수 있게 골든 시그널을 상단에 배치한다.

```text
┌──────────────────────── Service Overview ─────────────────────────┐
│ [Errors] 5xx 에러율 %     [Latency] p50/p95/p99    [Traffic] RPS    │  ← 상단: 골든 시그널 한눈에
├───────────────────────────────────────────────────────────────────┤
│ [Saturation] CPU% / Heap% / DB Pool%        [Health] 인스턴스 UP/DOWN│
├───────────────────────────────────────────────────────────────────┤
│ [Top Endpoints] URI별 요청수/에러/지연    [Dependencies] 외부연동 상태│  ← 중단: 드릴다운
├───────────────────────────────────────────────────────────────────┤
│ [Deploys] 배포 마커(버전/시각)            [GC] GC pause / 빈도        │  ← 하단: 변화점 상관관계
└───────────────────────────────────────────────────────────────────┘
```

- **배포 마커**를 지표 위에 표시해 "배포 직후 악화"를 즉시 상관시킨다([deployment-guide](../operations/deployment-guide.md)).
- URI/엔드포인트별로 드릴다운 가능하게 라벨을 설계한다(카디널리티 폭발 주의).
- 대시보드 링크를 알림 메시지·[runbook](../operations/runbook.md)·포스트모템에 연결한다.

---

## 관련 문서

- 장애 대응 프로세스: [incident-response](../operations/incident-response.md)
- 운영 작업 절차: [runbook](../operations/runbook.md)
- 예외/로깅 레벨: [error-response-guide](../api/error-response-guide.md)
- 외부 호출 추적: [external-integration](external-integration.md)
- 로그 마스킹/보안: [security-policy](../security/security-policy.md)
- 비기능 요구사항(목표값): [non-functional-requirements](../requirements/non-functional-requirements.md)

---

## 체크리스트

- [ ] 모든 로그가 JSON 구조화 + traceId 포함
- [ ] 비동기 경계에서 traceId 전파 확인
- [ ] 골든 시그널 4종(지연/트래픽/에러/포화)을 RED/USE로 수집·노출
- [ ] traceparent가 인입/외부호출에 전파되는지 확인
- [ ] 각 지표에 Warning/Critical 임계값과 "지속 조건"이 있고 SEV([incident-response](../operations/incident-response.md))와 매핑
- [ ] 알림 메시지에 다음 행동(runbook 링크)이 포함
- [ ] 대시보드에 배포 마커가 표시되고 지표/로그/추적이 traceId로 연결
- [ ] 민감정보 로그 마스킹 적용
- [ ] 임계값은 SLO 변화에 따라 주기적으로 재검토
