# Monitoring Metrics

> **예시 스택 안내**: 지표 이름/엔드포인트 예시는 **Spring Boot 3.x + Micrometer/Actuator + Prometheus + Grafana** 기준입니다.
> 실제 임계값(SLO)과 도구는 조직/서비스 특성에 맞게 교체하세요. 스택이 미정이면 [tech-stack 규칙](../../.claude/rules/tech-stack.md) 참고.

## 목적

서비스 상태를 판단하는 **핵심 지표(골든 시그널)** 와 **알림 임계값**, **대시보드 구성**을 정의한다.
여기서 정한 임계값은 [incident-response](./incident-response.md)의 감지/SEV 산정과 직접 연결된다.

연관 문서: [architecture/observability](../architecture/observability.md) (로그/추적/메트릭 표준) · [runbook](./runbook.md) · [deployment-guide §8 헬스체크](./deployment-guide.md#8-헬스체크)

---

## 1. 골든 시그널 (Four Golden Signals)

| 시그널 | 의미 | 대표 지표(예시) | 왜 중요한가 |
| --- | --- | --- | --- |
| **Latency (지연)** | 요청 처리 시간 | `http_server_requests_seconds` (p50/p95/p99) | 느려짐은 곧 사용자 체감 저하 |
| **Traffic (트래픽)** | 수요/처리량 | RPS(초당 요청 수) | 부하 추세, 용량 산정 기준 |
| **Errors (에러)** | 실패율 | 5xx 비율, 예외율 | 직접적 장애 신호 |
| **Saturation (포화)** | 자원 한계 근접도 | CPU%, 메모리%, DB 커넥션 풀 사용률, 스레드 풀 | 한계 초과 전 선제 대응 |

> 지연은 **성공 요청과 실패 요청을 분리**해서 본다(실패가 빨라서 평균이 좋아 보이는 착시 방지).

---

## 2. 예시 지표 + 알림 임계값

> 아래 임계값은 **예시**입니다. 서비스의 SLO에 맞춰 조정하고, 알림 피로를 줄이도록 지속 튜닝하세요.
> 단발성 스파이크 오탐을 줄이기 위해 **"X분 지속"** 조건을 함께 둡니다.

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

알림 운영 규칙:

- 모든 알림은 **무엇이, 어디서, 얼마나** 나쁜지와 **다음 행동(runbook 링크)** 을 담는다.
- Critical은 온콜 페이지, Warning은 채널 알림으로 분리해 알림 피로를 줄인다.
- 임계값을 바꾸면 변경 이유를 기록한다(포스트모템 액션과 연결).

---

## 3. 지표 노출 (예시: Micrometer/Actuator)

> 예시(Spring Boot 기준). 엔드포인트는 내부망/인증으로 보호한다.

```bash
# Prometheus 포맷 지표 노출
curl -s https://app.example.com/actuator/prometheus | head

# 예시 지표 라인
# http_server_requests_seconds_count{method="GET",status="200",uri="/api/users"} 1234.0
# jvm_memory_used_bytes{area="heap"} 5.2428e8
# hikaricp_connections_active{pool="HikariPool-1"} 7.0
```

대표 지표 매핑(예시):

| 골든 시그널 | Micrometer 지표(예시) |
| --- | --- |
| Latency | `http_server_requests_seconds{quantile="0.99"}` |
| Traffic | `rate(http_server_requests_seconds_count[1m])` |
| Errors | `http_server_requests_seconds_count{status=~"5.."}` 비율 |
| Saturation | `jvm_memory_used_bytes`, `hikaricp_connections_active`, `system_cpu_usage` |

---

## 4. PromQL 예시

> 예시(Prometheus 기준). 데이터소스가 다르면 동등 쿼리로 교체한다.

```promql
# 5xx 에러율 (전체 요청 대비)
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
  / sum(rate(http_server_requests_seconds_count[5m]))

# p99 지연 (초)
histogram_quantile(0.99,
  sum(rate(http_server_requests_seconds_bucket[5m])) by (le))

# 초당 요청 수 (트래픽)
sum(rate(http_server_requests_seconds_count[1m]))

# DB 커넥션 풀 사용률
hikaricp_connections_active / hikaricp_connections_max
```

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

구성 원칙:

- **배포 마커**를 지표 위에 표시해 "배포 직후 악화"를 즉시 상관시킨다([deployment-guide](./deployment-guide.md), [rollback-guide](./rollback-guide.md)).
- URI/엔드포인트별로 드릴다운 가능하게 라벨을 설계한다(카디널리티 폭발 주의).
- 대시보드 링크를 알림 메시지·[runbook](./runbook.md)·포스트모템에 연결한다.

---

## 6. 추적 / 로그 연계

- 모든 요청에 trace id를 부여하고 로그와 지표를 trace id로 상관시킨다(상세: [architecture/observability](../architecture/observability.md)).
- 지연/에러 알림 발생 시: 지표 → 해당 시간대 trace → 로그 순으로 좁혀 디버깅한다([runbook §2.3](./runbook.md#23-로그-확인)).

---

## 7. 체크리스트

- [ ] 골든 시그널 4종을 모두 수집하고 있다(지연/트래픽/에러/포화)
- [ ] 각 지표에 Warning/Critical 임계값과 "지속 조건"이 있다
- [ ] 알림이 SEV 등급([incident-response](./incident-response.md))과 매핑되어 있다
- [ ] 알림 메시지에 다음 행동(runbook 링크)이 포함되어 있다
- [ ] 대시보드에 배포 마커가 표시된다
- [ ] 지표/로그/추적이 trace id로 연결된다([architecture/observability](../architecture/observability.md))
- [ ] 임계값은 SLO 변화에 따라 주기적으로 재검토한다
