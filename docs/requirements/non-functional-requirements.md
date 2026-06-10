# Non-Functional Requirements

> 예시(Spring Boot 기준)입니다. 아래 목표값/측정방법은 **샘플 수치**이므로 실제 서비스의
> SLA, 트래픽 규모, 비용 예산에 맞춰 반드시 교체하세요.
> 기준 예시 스택: Spring Boot 3.x / Java 17 / PostgreSQL / Flyway / Spring Security(JWT).

## 목적

기능 요구사항(무엇을 한다)과 구분되는 **품질 속성 요구사항**(얼마나 잘 동작해야 하는가)을
측정 가능한 형태로 정의한다. 모든 항목은 "느낌"이 아니라 **수치 + 측정방법**으로 기술하여,
릴리스 전후로 충족 여부를 객관적으로 판정할 수 있게 한다.

- 모든 NFR은 `목표값 / 측정방법 / 현재값` 3요소를 가진다.
- 측정할 수 없는 요구사항은 NFR로 인정하지 않는다. ("빠르게", "안정적으로" 금지)
- 현재값은 측정 전이라면 `TBD`로 두되, 측정 책임자와 측정 시점을 명시한다.

---

## 측정 용어 정의

| 용어 | 정의 |
|---|---|
| p95 / p99 | 응답시간 분포의 95/99 백분위수. p95 200ms = 요청의 95%가 200ms 이내 응답 |
| RPS | 초당 요청 수(Requests Per Second). 처리량 지표 |
| 가용성 | (전체 시간 - 다운타임) / 전체 시간. 99.9% = 월 약 43분 다운 허용 |
| 에러율 | 5xx 응답 수 / 전체 응답 수 |
| RTO / RPO | 복구 목표 시간 / 복구 시점 목표(데이터 손실 허용 범위) |

---

## 1. 성능 (Performance)

| 항목 | 목표값(예시) | 측정방법 | 현재값 |
|---|---|---|---|
| API 응답시간 (읽기) | p95 ≤ 200ms, p99 ≤ 500ms | APM(예: Micrometer + Prometheus) 히스토그램, k6 부하 테스트 | TBD |
| API 응답시간 (쓰기) | p95 ≤ 400ms | 동일 | TBD |
| 처리량 | 단일 인스턴스 ≥ 300 RPS (에러율 < 1%) | k6 ramp-up 시나리오 | TBD |
| DB 쿼리 시간 | 단건 쿼리 p95 ≤ 50ms | `pg_stat_statements`, slow query log(≥ 100ms) | TBD |
| 콜드 스타트 | 앱 기동 ≤ 30s | 배포 로그 timestamp 차이 | TBD |

측정 예시(k6 시나리오 발췌):

```js
// load-test/read-api.js
import http from 'k6/http';
import { check } from 'k6';

export const options = {
  stages: [
    { duration: '1m', target: 50 },   // ramp-up
    { duration: '3m', target: 300 },  // sustained 300 RPS 목표
    { duration: '1m', target: 0 },    // ramp-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<200', 'p(99)<500'], // 성능 목표를 임계값으로 강제
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const res = http.get('https://api.example.com/v1/meetings');
  check(res, { 'status is 200': (r) => r.status === 200 });
}
```

---

## 2. 가용성 (Availability)

| 항목 | 목표값(예시) | 측정방법 | 현재값 |
|---|---|---|---|
| 월간 가용성 | 99.9% (월 다운타임 ≤ 43분) | 헬스체크 모니터링(예: Uptime probe 1분 간격) | TBD |
| 에러율(5xx) | < 0.5% (5분 이동평균) | Prometheus `rate(http_5xx) / rate(http_total)` | TBD |
| 헬스체크 | `/actuator/health` 200, 1분 내 복구 감지 | Liveness/Readiness probe | TBD |
| 무중단 배포 | 배포 중 가용성 저하 0건 | 롤링/블루그린 배포 + 배포 중 합성 모니터링 | TBD |

가용성 등급 참고표:

| SLA | 연간 다운타임 | 월간 다운타임 |
|---|---|---|
| 99% | ~3.65일 | ~7.3시간 |
| 99.9% (예시 목표) | ~8.77시간 | ~43.8분 |
| 99.99% | ~52.6분 | ~4.4분 |

> 참고: 장애 대응 절차는 [incident-response](../operations/incident-response.md),
> 복구는 [rollback-guide](../operations/rollback-guide.md)를 따른다.

---

## 3. 확장성 (Scalability)

| 항목 | 목표값(예시) | 측정방법 | 현재값 |
|---|---|---|---|
| 수평 확장 | 인스턴스 추가 시 처리량 선형 증가(2대 → ~1.9배) | 단계별 부하 테스트 | TBD |
| 무상태(Stateless) | 세션/캐시를 외부화(JWT, Redis)하여 인스턴스 종속 0 | 코드 리뷰 + 임의 인스턴스 종료 테스트 | TBD |
| DB 커넥션 | HikariCP 풀 크기 = `(코어수 * 2) + 디스크수` 기준 산정 | 커넥션 풀 메트릭 모니터링 | TBD |
| 데이터 증가 대응 | 1천만 row에서도 p95 쿼리 ≤ 50ms 유지(인덱스/파티셔닝) | 대용량 시드 데이터로 회귀 테스트 | TBD |

> 아키텍처 원칙은 [backend-architecture](../architecture/backend-architecture.md),
> [system-overview](../architecture/system-overview.md) 참고.

---

## 4. 보안 (Security)

| 항목 | 목표값(예시) | 측정방법 | 현재값 |
|---|---|---|---|
| 전송 구간 암호화 | 모든 외부 트래픽 TLS 1.2+ | TLS 스캔(예: testssl.sh) | TBD |
| 인증 | 보호 리소스 100% 인증 필요(JWT 검증) | Spring Security 통합 테스트 | TBD |
| 권한 | 인가 위반 시 403, 수직/수평 권한 상승 0건 | 인가 시나리오 테스트 | TBD |
| 비밀 관리 | Secret 코드/로그 하드코딩 0건 | gitleaks 등 시크릿 스캐너 CI | TBD |
| 취약점 | High/Critical 의존성 취약점 0건 유지 | 의존성 스캔(예: OWASP Dependency-Check) CI | TBD |
| 입력 검증 | 모든 외부 입력 검증(Bean Validation) | 코드 리뷰 + 경계값 테스트 | TBD |

> 상세 정책은 [security-policy](../security/security-policy.md),
> [access-control](../security/access-control.md) 참고. Secret은 절대 문서/코드에 기재하지 않는다.

---

## 5. 관측성 (Observability)

| 항목 | 목표값(예시) | 측정방법 | 현재값 |
|---|---|---|---|
| 구조화 로그 | 모든 로그 JSON + `traceId`/`spanId` 포함 | 로그 샘플 검사 | TBD |
| 분산 추적 | 요청 전 구간 trace 연결(W3C traceparent) | 추적 백엔드(예: OTel collector) 확인 | TBD |
| 핵심 메트릭 | RED(Rate/Error/Duration) 대시보드 상시 | Grafana 대시보드 존재 여부 | TBD |
| 알림 | SLA 위반 시 5분 내 알림 발송 | 알림 룰 테스트(합성 장애 주입) | TBD |
| 로그 보존 | 운영 로그 ≥ 30일 보존 | 로그 저장소 설정 확인 | TBD |

> 메트릭/대시보드/관측 기준은 [observability](../architecture/observability.md) 참고.

---

## 6. 유지보수성 (Maintainability)

| 항목 | 목표값(예시) | 측정방법 | 현재값 |
|---|---|---|---|
| 테스트 커버리지 | [testing-strategy §3](../testing/testing-strategy.md) 목표 준수(전체 ≥70%, 도메인·서비스 ≥80%) | JaCoCo 리포트 | TBD |
| 빌드 시간 | CI 전체 ≤ 10분 | CI 파이프라인 소요 시간 | TBD |
| 코드 스타일 | 포매터/린트 위반 0건(CI gate) | Spotless/Checkstyle CI | TBD |
| 문서 신선도 | API 변경 시 동일 PR에서 문서 갱신 | PR 리뷰 체크리스트 | TBD |
| 마이그레이션 | 모든 스키마 변경 Flyway 버전 관리 + 롤백 가능성 검토 | 마이그레이션 리뷰 | TBD |

> 테스트 기준은 [testing-strategy](../testing/testing-strategy.md),
> 마이그레이션은 [migration-policy](../database/migration-policy.md) 참고.

---

## 7. 신뢰성 / 복구 (Reliability & Recovery)

| 항목 | 목표값(예시) | 측정방법 | 현재값 |
|---|---|---|---|
| RTO | ≤ 1시간 | 복구 리허설 측정 | TBD |
| RPO | ≤ 15분 | 백업 주기/PITR 설정 확인 | TBD |
| 백업 | 일 1회 풀백업 + 지속 WAL 아카이빙 | 백업 작업 로그 | TBD |
| 멱등성 | 결제/외부 호출 재시도 시 중복 처리 0건 | idempotency key 테스트 | TBD |

---

## 체크리스트

- [ ] 모든 NFR 항목에 `목표값 / 측정방법` 이 채워졌다 (TBD는 현재값에만 허용).
- [ ] 예시 수치를 실제 SLA/트래픽 기준으로 교체했다.
- [ ] 각 측정방법에 사용할 도구/대시보드가 실제로 존재하거나 도입 계획이 있다.
- [ ] 성능/가용성 위반 시 알림 룰이 정의되어 있다.
- [ ] 보안 항목에 실제 Secret/주소가 들어가지 않았다 (가짜 예시 값만).
- [ ] 관련 문서([observability](../architecture/observability.md), [security-policy](../security/security-policy.md), [testing-strategy](../testing/testing-strategy.md))와 상호 링크되어 있다.
- [ ] 프로젝트 확정 후 갱신
