# Runbook

> **예시 스택 안내**: 명령/엔드포인트 예시는 **Spring Boot 3.x + Actuator / PostgreSQL / Redis(캐시)** 기준입니다.
> 실제 스택이 확정되면 명령을 교체하세요. 미정이면 [tech-stack 규칙](../../.claude/rules/tech-stack.md) 참고.

## 목적

운영 중 **자주 수행하는 작업**의 표준 절차를 모아둔다.
누구나 같은 방식으로, 안전하게 수행할 수 있도록 명령·권한·주의사항을 함께 기록한다.

> 운영 데이터 변경, 시크릿 접근 등 민감 작업은 [Safety Rules](../../CLAUDE.md) / [security 규칙](../../.claude/rules/security.md)을 따른다.
> 장애 상황이면 먼저 [incident-response](./incident-response.md)를, 배포/롤백은 [deployment-guide](./deployment-guide.md)를 본다.

---

## 1. 운영 작업 빠른 표

| 작업 | 언제 | 권한 | 위험도 | 절차 |
| --- | --- | --- | --- | --- |
| 서비스 상태 확인 | 상시 | 읽기 | 낮음 | [§2.1](#21-서비스-상태-확인) |
| 애플리케이션 재시작 | 행/메모리 누수/배포 후 | 배포 권한 | 중간 | [§2.2](#22-애플리케이션-재시작) |
| 로그 확인 | 디버깅/장애 | 로그 읽기 | 낮음 | [§2.3](#23-로그-확인) |
| 로그 레벨 임시 변경 | 심층 디버깅 | 운영 권한 | 중간 | [§2.4](#24-로그-레벨-임시-변경) |
| 캐시 비우기 | 잘못된 캐시/데이터 갱신 | 운영 권한 | 중간 | [§2.5](#25-캐시-비우기) |
| 인스턴스 수 조정(스케일) | 트래픽 급증/포화 | 운영 권한 | 중간 | [§2.6](#26-스케일-조정) |
| 트래픽 차단/제외 | 특정 인스턴스 이상 | 운영 권한 | 중간 | [§2.7](#27-인스턴스-트래픽-제외) |
| DB 연결/슬로우 쿼리 점검 | DB 포화/지연 | DB 읽기 | 중간 | [§2.8](#28-db-점검) |

권한 표기: **읽기** = 조회만, **운영 권한** = 운영 환경 변경 가능, **배포 권한** = 롤아웃 트리거 가능.
운영 권한 작업은 **2인 확인 또는 변경 기록**을 권장한다.

---

## 2. 작업 절차

### 2.1 서비스 상태 확인

```bash
# 헬스/상태 (예시: Actuator)
curl -s https://app.example.com/actuator/health | jq .

# 인스턴스 목록/상태 (오케스트레이터 명령은 환경에 맞게 교체)
deploy get instances app
```

기대값: `{"status":"UP"}`, 모든 인스턴스 `READY`.
주의: 외부에 노출되는 actuator 엔드포인트는 인증/내부망 제한이 되어 있는지 확인한다.

### 2.2 애플리케이션 재시작

```bash
# 롤링 재시작 (무중단 지향) — 한 번에 모두 내리지 않는다
deploy rollout restart app
deploy rollout status app    # readiness UP 까지 대기
```

주의:

- **전체 동시 재시작 금지**(다운타임 발생). 롤링으로 수행한다.
- 재시작 전 원인을 기록한다(메모리 누수/행 등). 반복되면 [incident-response](./incident-response.md)로 승격.
- 재시작은 근본 원인을 가릴 수 있으므로 사후 분석 항목으로 남긴다.

### 2.3 로그 확인

```bash
# 최근 로그 스트리밍 (예시)
deploy logs app --since 15m --follow

# 특정 요청 추적: trace id로 검색 (관측 도구/로그 시스템에서)
# query 예: traceId = "abc123def456"
```

연계: 분산 추적/로그/지표 표준은 [observability](../architecture/observability.md).
주의: 로그에 PII/시크릿이 남지 않도록 한다. 노출 발견 시 보안 사고로 처리.

### 2.4 로그 레벨 임시 변경

> 예시(Actuator loggers). 재배포 없이 일시적으로 레벨을 올린다. 디버깅 후 반드시 원복.

```bash
# 특정 패키지 DEBUG로 변경
curl -s -X POST https://app.example.com/actuator/loggers/com.example.app \
  -H 'Content-Type: application/json' \
  -d '{"configuredLevel":"DEBUG"}'

# 원복
curl -s -X POST https://app.example.com/actuator/loggers/com.example.app \
  -H 'Content-Type: application/json' \
  -d '{"configuredLevel":null}'
```

주의: DEBUG는 로그량/성능 영향이 크다. 좁은 패키지에만, 짧게 적용한다.

### 2.5 캐시 비우기

> 예시(Redis 캐시). 캐시 비우기는 일시적 부하 급증(캐시 스탬피드)을 유발할 수 있다.

```bash
# 특정 키 패턴만 삭제 (전체 FLUSH 지양)
# 주의: KEYS는 운영에서 블로킹. SCAN 기반으로 삭제한다.
redis-cli --scan --pattern 'user:profile:*' | xargs -r redis-cli DEL
```

주의:

- **`FLUSHALL`/`FLUSHDB` 전체 삭제 금지**(전 사용자 영향, 스탬피드). 필요 시 IC 승인.
- 비운 직후 DB/원본 부하가 급증할 수 있으니 지표를 관찰한다([observability](../architecture/observability.md)).
- 가능하면 키 패턴 단위로 최소 범위만 무효화한다.

### 2.6 스케일 조정

```bash
# 인스턴스 수 증가 (트래픽 급증/포화 시)
deploy scale app --replicas 6
deploy rollout status app
```

주의: 인스턴스 증가가 DB 커넥션 풀 한계를 넘지 않는지 확인한다(앱당 풀 × 인스턴스 수 ≤ DB max).

### 2.7 인스턴스 트래픽 제외

특정 인스턴스만 이상할 때 트래픽에서 제외하고 조사한다.

```bash
# readiness를 내려 라우터에서 제외하거나, 인스턴스를 cordon 처리
deploy drain instance app-7
```

주의: 제외 후 남은 인스턴스가 트래픽을 감당하는지 확인(포화 주의).

### 2.8 DB 점검

> 예시(PostgreSQL). 운영 DB는 **읽기 조회만** 기본. 변경은 [Safety Rules](../../CLAUDE.md)에 따라 승인 필요.

```sql
-- 현재 활성 커넥션 수
SELECT count(*) FROM pg_stat_activity;

-- 오래 실행 중인 쿼리(예: 30초 이상)
SELECT pid, now() - query_start AS dur, state, query
FROM pg_stat_activity
WHERE state != 'idle' AND now() - query_start > interval '30 seconds'
ORDER BY dur DESC;
```

주의:

- `pg_terminate_backend()`로 쿼리를 강제 종료하는 것은 영향이 크므로 **IC/DBA 판단 하에만** 수행한다.
- 운영 DB에 대한 UPDATE/DELETE/DDL은 자동 수행 금지. 변경 윈도우와 백업을 전제로 한다.

---

## 3. 공통 주의사항

- 운영 변경 작업은 **무엇을/왜/언제/누가** 했는지 기록한다.
- 영향 범위가 넓은 작업(전체 재시작, 전체 캐시 삭제, 쿼리 강제 종료)은 단독 수행하지 않는다.
- 작업이 장애로 번지면 즉시 [incident-response](./incident-response.md) 흐름으로 전환한다.
- 절차가 바뀌면 이 Runbook을 갱신하고, 새 작업 유형이 생기면 §1 표에 추가한다.
