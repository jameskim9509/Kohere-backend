# Migration Policy

> **예시(Spring Boot 기준)**: 이 문서는 **Flyway**(`org.flywaydb:flyway-core`)와 **PostgreSQL**을 가정한 *예시*입니다.
> Liquibase, Alembic(Python), golang-migrate, Prisma Migrate 등을 쓴다면 파일 네이밍과 명령만 다를 뿐 **원칙(되돌릴 수 있는 변경, expand-contract, NOT NULL 단계적 추가, 리뷰/배포 흐름)은 그대로 적용**하세요. 실제 도구가 확정되면 [tech-stack 규칙](../../.claude/rules/tech-stack.md)을 채우고 이 문서를 갱신합니다.

## 목적

- 스키마 변경을 **코드처럼 버전 관리**하고, 모든 환경(local → dev → staging → prod)에서 **재현 가능**하게 한다.
- "수동으로 prod DB를 고친다"를 금지하고, 변경을 **리뷰 가능한 PR**로 만든다.
- 무중단 배포 환경에서 **구버전/신버전 앱이 같은 스키마로 동시에 동작**할 수 있도록 한다(expand-contract).
- 위험한 변경(NOT NULL 추가, 인덱스 추가, 컬럼 삭제)을 **안전한 절차**로 수행한다.

## 내용

### 1. 기본 규칙

- 스키마 변경은 **항상 마이그레이션 파일로** 한다. 운영 DB에 `ALTER`를 직접 치지 않는다.
- 한 번 머지/배포된 마이그레이션 파일은 **절대 수정하지 않는다(immutable)**. 잘못됐으면 새 버전으로 고친다.
- 마이그레이션은 **앞으로만 간다(forward-only)**. 자동 down 스크립트에 의존하지 않고, 되돌림이 필요하면 보정 마이그레이션을 새로 만든다(§4 참고).
- 하나의 마이그레이션은 **하나의 논리적 변경**만 담는다(테이블 추가 + 대량 데이터 백필을 한 파일에 섞지 않는다).
- DDL과 대량 DML(백필)은 **분리**한다. DDL은 빠르게, 백필은 배치/별도 단계로.
- JPA `spring.jpa.hibernate.ddl-auto`는 운영에서 **반드시 `validate` 또는 `none`**. `update`/`create`는 로컬 실험용으로도 지양한다. 실제 스키마의 소스 오브 트루스는 Flyway다.

권장 설정 예시(`application.yml`):

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false   # 신규 프로젝트는 false
  jpa:
    hibernate:
      ddl-auto: validate         # 운영: validate 또는 none
```

### 2. 파일 네이밍 & 배치

위치: `src/main/resources/db/migration/`

Flyway 네이밍 규칙: `V<버전>__<설명>.sql` (버전과 설명 사이는 **언더스코어 2개**)

| 종류 | 접두 | 예시 | 실행 시점 |
| --- | --- | --- | --- |
| Versioned(순서대로 1회 실행) | `V` | `V1__init_meeting_domain.sql` | 버전 순서대로, 1회 |
| Repeatable(체크섬 변경 시 재실행) | `R` | `R__create_meeting_summary_view.sql` | 뷰/함수처럼 멱등한 객체 |
| Undo(상용 Flyway 전용, 본 정책 미사용) | `U` | (사용 안 함) | — |

```text
src/main/resources/db/migration/
├── V1__init_meeting_domain.sql        # 최초 스키마
├── V2__add_meeting_description.sql     # 컬럼 추가
├── V3__add_participants_unique.sql     # 제약 추가
├── V20260609__add_meeting_status_idx.sql  # 날짜 버전 방식도 가능(팀이 하나로 통일)
└── R__meeting_active_view.sql          # 반복 실행 객체
```

버전 번호 충돌 가이드:

- 여러 명이 동시에 작업하면 `V5__a.sql`이 둘 생길 수 있다. **머지 시점에 PR이 늦게 들어온 쪽이 번호를 올린다.**
- 충돌을 줄이려면 **날짜·시간 기반 버전**(`V20260609120000__...`)을 팀 컨벤션으로 잡는 것도 좋다. 단 한 가지 방식으로만 통일한다.

설명(description)은 동사로 시작하고 무엇을 하는지 드러낸다: `add`, `create`, `drop`, `rename`, `backfill`, `make_not_null`.

### 3. 마이그레이션 파일 작성 예시

```sql
-- V2__add_meeting_description.sql
-- 목적: meetings에 설명 컬럼 추가 (NULL 허용 → 안전)
ALTER TABLE meetings ADD COLUMN description TEXT;
```

```sql
-- V3__add_participants_unique.sql
-- 목적: 한 모임에 동일 사용자 중복 참여 방지
-- 주의: 기존 중복 데이터가 있으면 제약 추가가 실패한다. 사전 정리 필요(§5).
ALTER TABLE participants
    ADD CONSTRAINT uq_participants_meeting_user
    UNIQUE (meeting_id, user_id);
```

### 4. 되돌릴 수 있는 변경(Reversibility)

자동 down 스크립트에 의존하는 대신 **"되돌리는 마이그레이션을 새로 작성할 수 있는가"** 를 항상 자문한다.

| 변경 | 되돌리기 난이도 | 안전한 접근 |
| --- | --- | --- |
| 컬럼 추가(NULL 허용) | 쉬움 | 반대로 `DROP COLUMN` |
| 컬럼 삭제 | **위험(데이터 손실)** | 즉시 삭제 금지. expand-contract로 단계화(§6) |
| 컬럼 이름 변경 | 위험(구버전 앱이 깨짐) | rename 대신 add + 백필 + drop |
| NOT NULL 추가 | 위험 | 단계적 절차(§5) |
| 타입 변경 | 위험 | 신규 컬럼 추가 → 백필 → 교체 |
| 인덱스 추가 | 중간(락) | `CONCURRENTLY`(§7) |

> 핵심: **파괴적 변경(drop/rename/type change)은 "한 번의 PR"로 끝내지 않는다.** 배포된 구버전 앱이 살아있는 동안에도 깨지지 않도록 여러 릴리스에 걸쳐 나눈다(expand-contract).

### 5. NOT NULL 컬럼 추가 절차

기존 데이터가 있는 테이블에 `NOT NULL` 컬럼을 바로 추가하면, 기존 행에 값이 없어 **실패하거나 잠금이 길어진다**. 다음 4단계로 나눈다.

```text
[릴리스 N]   1) NULL 허용으로 컬럼 추가 (+ 필요 시 DEFAULT)
             V10__add_meeting_region_nullable.sql

[릴리스 N]   2) 애플리케이션이 새 컬럼에 값을 채워 쓰기 시작 (코드 배포)

[릴리스 N+1] 3) 기존 행 백필 (배치/마이그레이션으로 빈 값 채움)
             V11__backfill_meeting_region.sql

[릴리스 N+1] 4) NOT NULL 제약으로 승격
             V12__make_meeting_region_not_null.sql
```

```sql
-- V10__add_meeting_region_nullable.sql
ALTER TABLE meetings ADD COLUMN region VARCHAR(50);

-- V11__backfill_meeting_region.sql
-- 대용량이면 한 번에 UPDATE하지 말고 배치로 쪼갠다(락/WAL 부담).
UPDATE meetings SET region = 'UNKNOWN' WHERE region IS NULL;

-- V12__make_meeting_region_not_null.sql
-- 검증: 백필 누락 시 이 마이그레이션이 실패하므로 안전망 역할도 한다.
ALTER TABLE meetings ALTER COLUMN region SET NOT NULL;
```

> PostgreSQL에서 `DEFAULT` 있는 컬럼 추가는 빠르지만(메타데이터만 변경), 그래도 NOT NULL 승격은 검증 스캔을 유발한다. 큰 테이블에선 단계 분리가 더 중요하다.

### 6. Expand-Contract 패턴

무중단 배포에서 **스키마와 코드가 항상 호환**되도록 변경을 "확장(expand) → 이행 → 축소(contract)"로 나눈다. 예: `meetings.location`(자유 텍스트) → `meetings.region_code`(코드)로 전환.

```text
           ┌── EXPAND ──────────────────────────────────────────┐
릴리스 A   │ 새 컬럼 region_code 추가(NULL 허용).                  │
           │ 앱은 location, region_code 둘 다 쓴다(dual-write).   │
           └────────────────────────────────────────────────────┘
                                  │
           ┌── MIGRATE ─────────────────────────────────────────┐
릴리스 B   │ 기존 행 백필. 읽기는 region_code로 전환.             │
           │ 이 시점 구·신버전 앱이 둘 다 동작 가능.              │
           └────────────────────────────────────────────────────┘
                                  │
           ┌── CONTRACT ────────────────────────────────────────┐
릴리스 C   │ 더 이상 아무도 location을 안 쓰는 것 확인 후         │
           │ location 컬럼 DROP. region_code NOT NULL 승격.       │
           └────────────────────────────────────────────────────┘
```

핵심 규칙:

- **하나의 배포 안에서 "구버전 앱"과 "새 스키마"가 동시에 살아있어도 동작**해야 한다(롤링 배포/롤백 대비).
- 컬럼 삭제·이름 변경은 **반드시 마지막 릴리스(Contract)에서만**, 그것도 "아무도 안 쓴다"는 확인 후에 한다.

### 7. 인덱스 추가 시 락 주의

```sql
-- 나쁨: 큰 테이블에서 쓰기를 막는다(ACCESS EXCLUSIVE / SHARE 락).
CREATE INDEX ix_meetings_status_start_at ON meetings (status, start_at);

-- 좋음: 락을 최소화. 단 트랜잭션 블록 안에서는 실행 불가 → Flyway 설정 주의.
CREATE INDEX CONCURRENTLY ix_meetings_status_start_at ON meetings (status, start_at);
```

> `CONCURRENTLY`는 트랜잭션 안에서 실행할 수 없다. Flyway 단일 스크립트는 기본적으로 트랜잭션으로 감싸므로, 해당 마이그레이션에 트랜잭션 비활성 설정(예: 스크립트 헤더 또는 `executeInTransaction = false` 콜백/설정)을 적용한다. 실패 시 `INVALID` 인덱스가 남을 수 있으니 모니터링한다. 인덱스 설계 가이드는 [database-design](./database-design.md#6-인덱스-가이드) 참고.

### 8. 리뷰 & 배포 흐름

```text
1. 작성   feature 브랜치에서 Vn__*.sql 추가 + (필요 시) 엔티티/코드 변경
2. 로컬   ./gradlew flywayMigrate  또는 앱 기동으로 마이그레이션 적용 확인
          Testcontainers 통합 테스트로 실제 PostgreSQL에 적용되는지 검증
3. PR     아래 "마이그레이션 PR 체크리스트" 충족, 리뷰어 1+ 승인
4. CI     테스트 DB에 마이그레이션 전체 재생(from scratch) 성공 확인
5. 배포   dev → staging → prod 순서. 앱 기동 시 Flyway가 자동 적용
          (또는 배포 파이프라인에서 flyway:migrate 단계 분리)
6. 검증   flyway_schema_history 테이블에 success=true 확인, 모니터링 관찰
```

배포 순서 원칙(앱 코드 ↔ 스키마):

- **하위호환 스키마 변경(컬럼 추가 등)**: 스키마 먼저 → 앱 나중. 구버전 앱이 새 컬럼을 무시해도 동작한다.
- **파괴적 변경(컬럼 삭제 등)**: 앱에서 그 컬럼 사용을 먼저 제거·배포 → 충분히 안정화 후 스키마에서 삭제.
- 배포·롤백 절차 전반은 [deployment-guide](../operations/deployment-guide.md)를 따른다.

### 9. 마이그레이션 PR 체크리스트(리뷰어용)

```text
[ ] 파일명이 V<버전>__<설명>.sql 규칙을 따른다 (언더스코어 2개)
[ ] 이미 머지된 마이그레이션 파일을 수정하지 않았다 (immutable)
[ ] 하나의 논리적 변경만 담는다
[ ] NOT NULL / 컬럼 삭제 / 타입 변경이면 단계적 절차(expand-contract)를 따른다
[ ] 대량 백필이 있으면 배치로 쪼개거나 별도 단계로 분리했다
[ ] 큰 테이블 인덱스 추가면 락 영향을 검토했다(CONCURRENTLY 등)
[ ] 운영 데이터와의 호환성(기존 행)을 확인했다
[ ] 롤백/되돌리기 방안을 PR 설명에 적었다
[ ] Testcontainers/통합 테스트가 새 스키마로 통과한다
```

## 체크리스트

- [ ] (프로젝트 확정 후) 실제 마이그레이션 도구(Flyway/Liquibase/Alembic 등)에 맞게 네이밍·명령을 교체했다.
- [ ] `db/migration` 디렉터리 위치와 CI 검증 단계가 셋업됐다.
- [ ] 운영 DB 직접 수정 금지가 팀 규칙으로 합의됐다([security 규칙](../../.claude/rules/security.md)).
- [ ] `ddl-auto`가 운영에서 `validate`/`none`으로 설정됐다.

## 관련 문서

- [database-design](./database-design.md)
- [transaction-policy](./transaction-policy.md)
- [deployment-guide](../operations/deployment-guide.md)
- [deployment-guide §10](../operations/deployment-guide.md)
- [testing-strategy](../testing/testing-strategy.md)
- [database 규칙](../../.claude/rules/database.md)
