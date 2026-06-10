# Rollback Guide

> **예시 스택 안내**: 명령/마이그레이션 예시는 **Spring Boot 3.x / Flyway / PostgreSQL** 기준입니다.
> 실제 스택이 확정되면 도구/명령을 교체하세요. 미정이면 [tech-stack 규칙](../../.claude/rules/tech-stack.md) 참고.

## 목적

배포 후 문제가 발생했을 때 **빠르고 안전하게 이전 상태로 되돌리는** 절차를 정의한다.
특히 **애플리케이션 롤백**과 **DB 마이그레이션 롤백**을 분리해서 다루며,
DB는 단순 되돌리기가 위험하므로 **expand-contract** 원칙을 강제한다.

연관 문서: [deployment-guide](./deployment-guide.md) · [incident-response](./incident-response.md) · [runbook](./runbook.md) · [database 규칙](../../.claude/rules/database.md)

---

## 1. 롤백 판단 기준

다음 중 하나라도 해당하면 롤백을 **우선 검토**한다(고치는 것보다 되돌리는 것이 빠를 때).

| 신호 | 임계 예시 | 판단 |
| --- | --- | --- |
| 에러율 급증 | 배포 전 대비 5xx 비율 +X%p 이상 | 즉시 롤백 검토 |
| 지연 급증 | p99 지연이 SLO(예: 800ms) 초과 지속 | 롤백 검토 |
| 핵심 기능 장애 | 로그인/결제 등 주요 플로우 실패 | 즉시 롤백 |
| 헬스체크 실패 | readiness가 다수 인스턴스에서 지속 실패 | 자동/수동 롤백 |
| 데이터 정합성 위험 | 잘못된 쓰기·손상 감지 | 롤백 + 데이터 검토(주의: §3) |

원칙: **"고칠 수 있다"는 확신이 없으면 먼저 롤백해 영향을 멈추고, 원인 분석은 그 다음에 한다(완화 우선).**
심각도와 대응 흐름은 [incident-response](./incident-response.md) 참고.

---

## 2. 애플리케이션 롤백

### 2.1 롤링 환경

이전 이미지 태그로 재배포한다.

```bash
# 직전 안정 버전의 불변 태그를 명시 (latest 금지)
PREV_SHA="abc1234"
IMAGE="registry.example.com/team/app"

# 예시: 배포 도구에서 이미지 태그를 이전 값으로 교체 후 롤아웃
# (오케스트레이터/배포 도구 명령은 프로젝트 환경에 맞게 교체)
deploy set-image app="${IMAGE}:${PREV_SHA}"
deploy rollout status app   # readiness UP 확인까지 대기
```

### 2.2 블루그린 환경

트래픽을 직전 색으로 되돌린다(가장 빠름).

```text
현재: router ─▶ green(v2)   (문제 발생)
롤백: router ─▶ blue(v1)    (트래픽만 전환, 수초 내 복구)
```

- blue(구버전)를 **즉시 제거하지 말고** 롤백 안정화까지 유지한다.
- DB는 공유되므로 §3의 호환성 점검이 여전히 필요하다.

### 2.3 애플리케이션 롤백 체크리스트

- [ ] 되돌릴 직전 안정 버전(SHA/태그) 확인
- [ ] 해당 버전이 **현재 DB 스키마와 호환**되는지 확인(§3)
- [ ] 롤백 배포 → readiness `UP` 확인
- [ ] 에러율/지연 정상 복귀 확인([monitoring-metrics](./monitoring-metrics.md))
- [ ] 사용자 영향/타임라인 기록([incident-response](./incident-response.md) 포스트모템)

---

## 3. DB 마이그레이션 롤백

### 3.1 왜 단순 "down 마이그레이션"이 위험한가

이미 적용된 스키마/데이터 변경은 **되돌릴 때 데이터 손실**이 발생할 수 있다.
예: 컬럼 DROP을 되돌려도 **삭제된 값은 복구되지 않는다.**

> 핵심 원칙: **앞으로만 가는 마이그레이션(forward-only)** 을 기본으로 하고,
> 롤백 가능성은 **expand-contract 패턴**으로 미리 확보한다. 즉시 down 마이그레이션에 의존하지 않는다.

### 3.2 expand-contract 패턴

스키마 변경을 3단계로 나눠 **구/신 애플리케이션이 동시에 동작 가능**하게 만든다.
이렇게 하면 애플리케이션을 롤백해도 DB는 그대로 두면 된다.

```text
  ┌─ EXPAND ─────────────┐   ┌─ MIGRATE ──────────┐   ┌─ CONTRACT ─────────┐
  │ 새 컬럼/테이블 추가    │   │ 코드가 양쪽을 모두   │   │ 구 컬럼/구 경로     │
  │ (nullable, 기본값)    │──▶│ 읽고, 신규로 씀      │──▶│ 제거 (안정화 후)    │
  │ 구버전과 호환 유지     │   │ 백필(backfill) 수행 │   │ 모든 트래픽 신규 전환│
  └──────────────────────┘   └────────────────────┘   └────────────────────┘
        배포1                       배포2                     배포3 (이후)
   여기까지는 롤백 안전          롤백 시 신규컬럼 무시 가능     contract 후에는 구버전 롤백 불가
```

### 3.3 예시: 컬럼 이름 변경 (`username` → `login_id`)

잘못된 방식(파괴적, 롤백 불가):

```sql
-- ❌ 한 번에 rename: 구버전 애플리케이션이 즉시 깨지고 되돌리기 어려움
ALTER TABLE users RENAME COLUMN username TO login_id;
```

권장 방식(expand-contract):

```sql
-- V10__expand_add_login_id.sql  (EXPAND: 구버전과 호환)
ALTER TABLE users ADD COLUMN login_id VARCHAR(50);
-- 애플리케이션은 username/login_id 둘 다 쓰도록 배포(MIGRATE)
UPDATE users SET login_id = username WHERE login_id IS NULL; -- backfill

-- (안정화 후, 모든 인스턴스가 login_id만 사용하는 것을 확인한 뒤)
-- V12__contract_drop_username.sql  (CONTRACT)
ALTER TABLE users DROP COLUMN username;
```

EXPAND/MIGRATE 단계에서는 애플리케이션 롤백이 안전하다. CONTRACT 이후에는 구버전으로 롤백할 수 없으므로 **CONTRACT는 충분히 안정화된 뒤** 별도 배포로 분리한다.

### 3.4 Flyway 운영 참고

```bash
# 적용 이력/대기 상태 확인
./gradlew flywayInfo

# (사고 시) 잘못 적용된 마이그레이션 기록 정리 — 위험, 신중히
# ./gradlew flywayRepair   # 체크섬/실패 기록 복구용. 데이터 자동 복원 아님.
```

주의:

- Flyway Community에는 자동 down 마이그레이션이 없다. 되돌리기는 **새 forward 마이그레이션**으로 작성한다.
- 데이터 손실 가능 작업(DROP/TRUNCATE) 전에는 **백업/스냅샷**을 확보한다.
- 운영 DB 직접 수정은 [Safety Rules](../../CLAUDE.md)에 따라 자동 수행하지 않는다.

---

## 4. 통합 롤백 의사결정 흐름

```text
문제 감지
   │
   ▼
DB 스키마가 변경되었나? ──아니오──▶ 애플리케이션만 롤백(§2) ──▶ 지표 정상화 확인
   │ 예
   ▼
변경이 EXPAND/MIGRATE 단계인가?
   ├─ 예 ─▶ 애플리케이션 롤백 안전(§2). DB는 그대로 둠.
   └─ 아니오(CONTRACT 완료) ─▶ 구버전 롤백 불가.
                                   → 앞으로 가는 hotfix 작성(forward fix)
                                   → 데이터 손상 시 백업 복구 검토(영향 큼: IC 소집)
```

---

## 5. 롤백 후 체크리스트

- [ ] 핵심 지표(에러율/지연/포화)가 배포 전 수준으로 복귀했는지 확인
- [ ] 스모크 테스트(주요 시나리오) 통과
- [ ] 마이그레이션 상태와 애플리케이션 버전의 **호환성** 재확인
- [ ] 사용자 영향 범위/기간 집계
- [ ] 타임라인·원인·재발방지 항목을 [incident-response](./incident-response.md) 포스트모템 템플릿에 기록
- [ ] 동일 사고 예방을 위한 [deployment-check](../../.claude/skills/deployment-check/SKILL.md) 항목 보강 검토
