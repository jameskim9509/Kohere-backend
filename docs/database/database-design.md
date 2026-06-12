# Database Design

> **예시(Spring Boot 기준)**: 이 문서의 모든 DDL, 타입, 명명은 **Spring Boot 3.x / Java 17 / Spring Data JPA / PostgreSQL 15 / Flyway** 스택을 가정한 *예시*입니다.
> 실제 프로젝트의 스택이 다르면(MySQL, MongoDB, MSSQL 등) 데이터 타입(`TIMESTAMPTZ`, `BIGSERIAL`, `JSONB` 등)과 명명 규칙을 그대로 쓰지 말고 **프로젝트에 맞게 교체**하세요.
> 기술 스택이 확정되면 [tech-stack 규칙](../../.claude/rules/tech-stack.md)을 먼저 채우고 이 문서를 갱신합니다.

## 목적

- 팀이 일관된 **테이블/컬럼 명명 규칙**과 **공통 컬럼 표준**을 따르도록 한다.
- 신규 테이블 추가 시 복붙해서 시작할 수 있는 **DDL 템플릿과 예시 스키마**를 제공한다.
- 인덱스, 제약, 데이터 타입 선택에 대한 **합의된 가이드**를 제공해 리뷰 시간을 줄인다.
- 마이그레이션은 [migration-policy](./migration-policy.md), 트랜잭션 경계는 [transaction-policy](./transaction-policy.md)와 함께 본다.

## 내용

### 1. 명명 규칙(Naming Convention)

| 대상 | 규칙 | 예시 | 비고 |
| --- | --- | --- | --- |
| 테이블 | `snake_case`, **복수형** | `meetings`, `participants` | 도메인 단수형(`meeting`)도 팀 합의로 가능, 단 하나로 통일 |
| 컬럼 | `snake_case`, 소문자 | `created_at`, `host_user_id` | JPA에서 `camelCase` 필드 ↔ `snake_case` 컬럼 자동 매핑 |
| 기본 키 | `id` | `id` | 단일 PK는 항상 `id` |
| 외래 키 컬럼 | `<참조테이블단수>_id` | `meeting_id`, `user_id` | 참조 대상이 명확하도록 |
| 외래 키 제약 | `fk_<자식>_<부모>` | `fk_participants_meeting` | |
| 유니크 제약 | `uq_<테이블>_<컬럼...>` | `uq_participants_meeting_user` | |
| 일반 인덱스 | `ix_<테이블>_<컬럼...>` | `ix_meetings_status_start_at` | |
| 체크 제약 | `ck_<테이블>_<설명>` | `ck_meetings_capacity_positive` | |
| Enum 저장 컬럼 | 단수 명사 + 문자열 | `status`, `role` | 코드에서는 `EnumType.STRING` |
| 불리언 컬럼 | `is_` / `has_` 접두 | `is_deleted`, `has_agreed` | |
| 시각 컬럼 | `_at` 접미 (시점) | `created_at`, `canceled_at` | `TIMESTAMPTZ` 사용 |
| 기간/일자 컬럼 | `_date` 접미 (날짜) | `start_date` | 시·분이 필요 없을 때만 |

추가 규칙:

- 예약어(`user`, `order`, `group` 등)는 테이블/컬럼명으로 **피한다**. 불가피하면 따옴표 대신 `users`, `orders`처럼 복수형으로 회피한다.
- 약어는 팀이 합의한 표준 약어만 사용한다(`amt`, `qty` 같은 임의 축약 금지).
- 이름 길이는 PostgreSQL 식별자 한도(63 bytes)를 넘지 않게 한다. 길어지면 의미를 해치지 않는 선에서 줄인다.

### 2. 공통 컬럼 표준(Common Columns)

모든 비즈니스 테이블은 아래 공통 컬럼을 갖는 것을 기본으로 한다. JPA에서는 `@MappedSuperclass` + `@EntityListeners(AuditingEntityListener.class)`로 표준화한다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | `BIGSERIAL` / `BIGINT GENERATED ALWAYS AS IDENTITY` | PK | 대리 키(surrogate key). 외부 노출이 필요하면 별도 `public_id`(UUID) 추가 |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | 생성 시각. 애플리케이션에서 `@CreatedDate`로 주입 |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | 마지막 수정 시각. `@LastModifiedDate` |
| `created_by` | `BIGINT` | NULL 허용 | 생성 주체 사용자 id (감사 추적용, `@CreatedBy`) |
| `updated_by` | `BIGINT` | NULL 허용 | 수정 주체 사용자 id (`@LastModifiedBy`) |
| `version` | `BIGINT` | `NOT NULL DEFAULT 0` | 낙관적 락(`@Version`). 동시 수정 충돌 감지 |

소프트 삭제가 필요한 테이블은 다음을 추가한다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `is_deleted` | `BOOLEAN` | `NOT NULL DEFAULT false` | 소프트 삭제 플래그 |
| `deleted_at` | `TIMESTAMPTZ` | NULL 허용 | 삭제 시각. `is_deleted = true`일 때만 채움 |

> **시간대 원칙**: 저장은 항상 UTC 기준 `TIMESTAMPTZ`. 표시 시점에 사용자 타임존으로 변환한다. `TIMESTAMP`(타임존 없음)는 쓰지 않는다.

`@MappedSuperclass` 예시:

```java
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
```

### 3. 예시 ERD(ASCII)

도메인 예시: "모임(meeting)"과 거기에 참여하는 "참가자(participant)". `users`는 인증 도메인 소유 테이블로 가정한다.

```text
┌──────────────────────────┐
│ users                    │  (인증 도메인 소유 — 본 문서 범위 밖, 참조만)
├──────────────────────────┤
│ PK id            BIGINT  │
│    email         VARCHAR │  UNIQUE
│    nickname      VARCHAR │
│    created_at    TSTZ    │
└──────────┬───────────────┘
           │ 1
           │
           │ host_user_id (N:1)
           │
┌──────────┴───────────────┐
│ meetings                 │
├──────────────────────────┤
│ PK id            BIGINT  │
│ FK host_user_id  BIGINT  │ ──▶ users.id
│    title         VARCHAR │
│    status        VARCHAR │  (OPEN / CLOSED / CANCELED)
│    capacity      INT     │  CHECK > 0
│    start_at      TSTZ    │
│    created_at    TSTZ    │
│    updated_at    TSTZ    │
│    version       BIGINT  │
└──────────┬───────────────┘
           │ 1
           │
           │ meeting_id (1:N)
           │
┌──────────┴───────────────┐
│ participants             │  (meetings ↔ users 다대다 해소 + 참여 상태 보관)
├──────────────────────────┤
│ PK id            BIGINT  │
│ FK meeting_id    BIGINT  │ ──▶ meetings.id
│ FK user_id       BIGINT  │ ──▶ users.id
│    role          VARCHAR │  (HOST / MEMBER)
│    status        VARCHAR │  (JOINED / LEFT / KICKED)
│    joined_at     TSTZ    │
│    created_at    TSTZ    │
│    updated_at    TSTZ    │
│    version       BIGINT  │
│  UNIQUE(meeting_id, user_id)  ── 한 모임에 같은 유저는 1회만
└──────────────────────────┘

관계 요약
  users (1) ──< (N) meetings        : 한 사용자가 여러 모임을 호스팅
  meetings (1) ──< (N) participants : 한 모임에 여러 참가자
  users (1) ──< (N) participants    : 한 사용자가 여러 모임에 참가
```

### 4. 예시 테이블 DDL

> 아래 DDL은 Flyway 마이그레이션 파일(`V1__init_meeting_domain.sql`)에 그대로 넣을 수 있는 형태입니다. 파일 네이밍/배치 규칙은 [migration-policy](./migration-policy.md)를 따르세요.

#### 4.1 `meetings`

```sql
CREATE TABLE meetings (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    host_user_id  BIGINT       NOT NULL,
    title         VARCHAR(100) NOT NULL,
    description   TEXT,
    status        VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    capacity      INT          NOT NULL,
    start_at      TIMESTAMPTZ  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    BIGINT,
    updated_by    BIGINT,
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_meetings_status
        CHECK (status IN ('OPEN', 'CLOSED', 'CANCELED')),
    CONSTRAINT ck_meetings_capacity_positive
        CHECK (capacity > 0)
);

-- 외래 키: users 테이블이 같은 DB/스키마에 있다고 가정.
-- 마이크로서비스로 DB가 분리돼 있으면 물리 FK 대신 애플리케이션 레벨 검증을 쓴다.
ALTER TABLE meetings
    ADD CONSTRAINT fk_meetings_host_user
    FOREIGN KEY (host_user_id) REFERENCES users (id);

-- 조회 패턴: "열린 모임을 시작 시각 순으로" → 복합 인덱스
CREATE INDEX ix_meetings_status_start_at ON meetings (status, start_at);

-- 조회 패턴: "내가 호스팅한 모임"
CREATE INDEX ix_meetings_host_user_id ON meetings (host_user_id);
```

#### 4.2 `participants`

```sql
CREATE TABLE participants (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    meeting_id  BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    role        VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    status      VARCHAR(20) NOT NULL DEFAULT 'JOINED',
    joined_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_participants_role
        CHECK (role IN ('HOST', 'MEMBER')),
    CONSTRAINT ck_participants_status
        CHECK (status IN ('JOINED', 'LEFT', 'KICKED'))
);

ALTER TABLE participants
    ADD CONSTRAINT fk_participants_meeting
    FOREIGN KEY (meeting_id) REFERENCES meetings (id) ON DELETE CASCADE;

ALTER TABLE participants
    ADD CONSTRAINT fk_participants_user
    FOREIGN KEY (user_id) REFERENCES users (id);

-- 한 모임에 같은 사용자는 한 행만 (중복 참여 방지). DB 레벨에서 보장한다.
ALTER TABLE participants
    ADD CONSTRAINT uq_participants_meeting_user
    UNIQUE (meeting_id, user_id);

-- 조회 패턴: "이 모임의 참가자 목록"
CREATE INDEX ix_participants_meeting_id ON participants (meeting_id);

-- 조회 패턴: "내가 참여 중인 모임 목록"
CREATE INDEX ix_participants_user_status ON participants (user_id, status);
```

> `uq_participants_meeting_user` 같은 **유니크 제약은 비즈니스 불변식을 DB가 마지막으로 지켜주는 안전망**이다. 애플리케이션 검증만 믿지 말고 항상 DB 제약을 함께 둔다. 동시 요청으로 인한 중복 참여는 이 제약 위반(`DataIntegrityViolationException`)으로 잡고, 사용자에게는 "이미 참여한 모임" 에러로 변환한다([error-response-guide](../api/error-response-guide.md) 참고).

#### 4.3 매핑되는 JPA 엔티티(발췌)

```java
@Entity
@Table(name = "participants",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_participants_meeting_user",
           columnNames = {"meeting_id", "user_id"}))
public class Participant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING) // 절대 ORDINAL 쓰지 않는다(순서 바뀌면 데이터 깨짐)
    @Column(name = "role", nullable = false, length = 20)
    private ParticipantRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ParticipantStatus status;
}
```

### 5. 데이터 타입 가이드(PostgreSQL 예시)

| 용도 | 권장 타입 | 피해야 할 것 | 이유 |
| --- | --- | --- | --- |
| 정수 PK/FK | `BIGINT` | `INT` | 증가 한계, 마이그레이션 비용 |
| 금액 | `NUMERIC(19, 4)` | `FLOAT`, `DOUBLE` | 부동소수 오차 |
| 시각 | `TIMESTAMPTZ` | `TIMESTAMP` | 타임존 모호성 |
| 짧은 문자열 | `VARCHAR(n)` (n 명시) | `VARCHAR`(무제한) 남용 | 입력 한도/검증 |
| 긴 본문 | `TEXT` | 매우 큰 `VARCHAR(n)` | 의도 명확화 |
| Enum | `VARCHAR(n)` + CHECK | DB `ENUM` 타입 | 값 추가 시 마이그레이션 난이도 |
| 불리언 | `BOOLEAN` | `CHAR(1)`, `0/1 INT` | 명확성 |
| 반정형 데이터 | `JSONB` | `TEXT`에 JSON 문자열 | 인덱싱/쿼리 가능 |
| 외부 노출 식별자 | `UUID` (`public_id`) | 순번 PK 노출 | 추측 공격 방지 |

### 6. 인덱스 가이드

설계 원칙:

1. **쿼리부터 본다.** 인덱스는 "어떤 컬럼으로 조회/정렬/조인하는가"에서 출발한다. 추측으로 만들지 않는다.
2. **복합 인덱스 컬럼 순서**: 같음(`=`) 조건 → 범위(`<`, `>`, `BETWEEN`) → 정렬(`ORDER BY`) 순. 예: `WHERE status = ? AND start_at > ? ORDER BY start_at` → `(status, start_at)`.
3. **선택도(selectivity)가 높은 컬럼**(고유값 많은 컬럼)을 앞쪽에 둔다. `is_deleted`처럼 값이 두세 개뿐인 컬럼을 단독 선두에 두지 않는다.
4. **모든 외래 키 컬럼에 인덱스를 만든다.** FK 컬럼은 부모 삭제/조인 시 풀스캔을 유발하기 쉽다.
5. **유니크 제약/PK는 자동으로 인덱스를 생성**한다. 같은 컬럼에 일반 인덱스를 중복 생성하지 않는다.
6. **부분 인덱스(partial index)**로 핫 데이터만 인덱싱한다. 예: 소프트 삭제 안 된 행만.
7. 인덱스는 쓰기 비용/저장 공간을 늘린다. **읽기 이득과 쓰기 비용을 저울질**한다. 안 쓰는 인덱스는 제거한다.

예시:

```sql
-- 부분 인덱스: 삭제되지 않은 활성 모임만 인덱싱
CREATE INDEX ix_meetings_active_start_at
    ON meetings (start_at)
    WHERE is_deleted = false;

-- 대소문자 무시 이메일 조회 → 표현식(expression) 인덱스
CREATE UNIQUE INDEX uq_users_email_lower
    ON users (LOWER(email));
```

> **운영 환경 인덱스 추가 주의**: 큰 테이블에 인덱스를 만들면 기본적으로 테이블에 락이 걸려 쓰기가 멈춘다. PostgreSQL은 `CREATE INDEX CONCURRENTLY`로 락을 최소화할 수 있다(단 트랜잭션 안에서 실행 불가). 락/성능 영향 검토는 [migration-policy](./migration-policy.md)와 [database 규칙](../../.claude/rules/database.md)을 따른다.

### 7. 제약과 무결성

- **NOT NULL을 기본값으로 생각한다.** NULL을 허용할 명확한 이유가 있을 때만 허용한다.
- **외래 키는 가급적 건다.** DB가 분리된 마이크로서비스라 물리 FK가 불가능하면, 애플리케이션 레벨 검증 + 정합성 배치/이벤트로 보완하고 그 결정을 [ADR](../adr/README.md)에 남긴다.
- **삭제 정책(`ON DELETE`)을 명시적으로 정한다.**: `CASCADE`(자식 함께 삭제), `RESTRICT`(자식 있으면 금지), `SET NULL`. 위 예시는 모임 삭제 시 참가자도 정리하도록 `CASCADE`.
- **CHECK 제약으로 도메인 규칙을 DB에 박는다.** (`capacity > 0`, `status IN (...)`)

## 체크리스트

신규 테이블/컬럼을 추가할 때 PR에서 확인한다.

- [ ] 테이블/컬럼명이 명명 규칙(`snake_case`, 복수형, `_id`/`_at` 접미)을 따른다.
- [ ] 공통 컬럼(`created_at`, `updated_at`, `version`)을 포함한다(또는 미포함 이유가 명확하다).
- [ ] `id`는 `BIGINT`이고, 외부 노출이 필요하면 `public_id`(UUID)를 별도로 둔다.
- [ ] 시각 컬럼은 `TIMESTAMPTZ`다.
- [ ] Enum 컬럼은 `VARCHAR + CHECK`이고 JPA는 `EnumType.STRING`이다.
- [ ] 모든 외래 키 컬럼에 인덱스가 있다.
- [ ] 비즈니스 불변식이 유니크/체크 제약으로 DB에 표현돼 있다.
- [ ] 추가 인덱스는 실제 쿼리 패턴에 근거가 있다(추측 인덱스 아님).
- [ ] 변경이 마이그레이션으로 작성됐고 [migration-policy](./migration-policy.md)를 따른다.
- [ ] (프로젝트 확정 후) 실제 스택에 맞게 타입/명명을 교체했다.

## 관련 문서

- [migration-policy](./migration-policy.md)
- [transaction-policy](./transaction-policy.md)
- [system-overview](../architecture/system-overview.md)
- [backend-architecture](../architecture/backend-architecture.md)
- [error-response-guide](../api/error-response-guide.md)
- [database 규칙](../../.claude/rules/database.md)
