# ADR-0001. 주 데이터베이스로 PostgreSQL 선택

> 이 문서는 [0000-adr-template](./0000-adr-template.md)을 실제로 채운 **작성 예시 ADR**입니다.
> 결정 내용/수치는 샘플이므로, 실제 프로젝트에서는 자신의 맥락으로 교체하세요.
> (예시 스택 기준: Spring Boot 3.x / Java 17 / Spring Data JPA / Flyway)

| 항목 | 값 |
|---|---|
| 번호 | ADR-0001 |
| 작성자 | 백엔드 팀 (예시) |
| 작성일 | 2026-06-09 |
| 관련 문서 | [database-design](../database/database-design.md), [migration-policy](../database/migration-policy.md), [non-functional-requirements](../requirements/non-functional-requirements.md) |

## Status

Accepted

## Context

서비스의 주 데이터를 저장할 1차 데이터베이스를 선택해야 한다. 다음 제약과 요구가 있다.

- 핵심 도메인(사용자, 모임, 참가 신청)은 **강한 정합성**이 필요하다.
  참가 신청은 정원 초과를 막아야 하므로 트랜잭션과 락이 중요하다
  ([non-functional-requirements](../requirements/non-functional-requirements.md) §7 멱등성/동시성).
- 데이터는 명확한 관계형 구조를 가지며, 보고/검색을 위한 **유연한 조인/집계**가 필요하다.
- 일부 기능(태그, 메타데이터)은 반정형 데이터(JSON) 저장이 편하다.
- 팀은 SQL과 JPA에 익숙하며, 운영/관리형 서비스 선택지가 풍부해야 한다.
- 라이선스 비용 부담 없이 시작하고, 향후 규모 확장(읽기 복제, 파티셔닝) 여지가 필요하다.
- 예상 초기 규모: 단일 인스턴스, 데이터 수백만 row, 쓰기보다 읽기 비중이 높음.

## Decision

주 데이터베이스로 **PostgreSQL(15+)** 를 채택한다.

- 애플리케이션은 Spring Data JPA(Hibernate)로 접근하고, 스키마 변경은
  Flyway 마이그레이션으로 버전 관리한다([migration-policy](../database/migration-policy.md)).
- 동시성이 중요한 영역(참가 신청 정원 제어)은 트랜잭션 + 행 단위 락 또는
  유니크 제약으로 정합성을 보장한다.
- 반정형 데이터는 `jsonb` 컬럼을 사용하되, 핵심 도메인 필드는 정규화된 컬럼으로 둔다.

## Alternatives

| 대안 | 장점 | 단점 | 채택 안 한 이유 |
|---|---|---|---|
| **PostgreSQL** (채택) | 강한 정합성·트랜잭션, 풍부한 SQL/인덱스, `jsonb`, 무료·관리형 풍부 | 단일 노드 쓰기 확장은 별도 설계 필요 | — (요구를 가장 균형 있게 충족) |
| MySQL | 익숙함, 관리형 풍부, 빠른 단순 읽기 | 복잡 쿼리/제약·확장 기능이 상대적으로 약함, JSON 처리 제한적 | 관계형 + 반정형 + 고급 제약 요구에 PostgreSQL이 유리 |
| MongoDB | 스키마 유연, 수평 확장 용이 | 다중 문서 트랜잭션/조인 한계, 강한 정합성 보장 비용 | 정원 제어 등 강정합성 도메인에 부적합 |
| 인메모리(H2 등) | 개발 편의 | 운영 데이터 영속/확장 부적합 | 운영 DB 후보가 아님(테스트용으로만 사용) |

## Consequences

- 긍정
  - 트랜잭션/제약으로 정원 초과 같은 도메인 불변식을 DB 레벨에서 보장할 수 있다.
  - 복잡한 조인/집계와 `jsonb`를 한 저장소에서 처리해 초기 아키텍처가 단순해진다.
  - 관리형 서비스/도구 생태계가 넓어 운영 부담이 낮다.
- 부정/트레이드오프
  - 쓰기 수평 확장은 기본 제공되지 않아, 규모 증가 시 읽기 복제·파티셔닝·샤딩을
    별도 ADR로 설계해야 한다.
  - JPA/Hibernate 사용 시 N+1, 영속성 컨텍스트 등 ORM 함정에 대한 규율이 필요하다.
- 후속 작업
  - Flyway 마이그레이션 베이스라인 작성([migration-policy](../database/migration-policy.md)).
  - 커넥션 풀(HikariCP) 크기 산정 및 모니터링 지표 추가
    ([observability](../architecture/observability.md)).
  - 백업/PITR 정책 수립(RPO ≤ 15분 목표,
    [non-functional-requirements](../requirements/non-functional-requirements.md) §7).

## Validation

이 결정이 유효한지 다음으로 검증/관측한다.

- **정합성 테스트**: "마지막 1자리를 두 명이 동시에 신청" 시나리오에서 초과 0건
  ([user-story-template](../requirements/user-story-template.md) 예시2)을
  통합/동시성 테스트로 검증한다.
- **성능 지표**: 단건 쿼리 p95 ≤ 50ms를 `pg_stat_statements`/slow query log로 관측한다.
- **운영 지표**: 커넥션 풀 포화율, 복제 지연(읽기 복제 도입 시), 디스크 사용량 대시보드.
- **재검토 시점**: 쓰기 RPS가 단일 노드 한계에 근접하거나 데이터가 1억 row를 넘기면
  확장 전략(파티셔닝/샤딩/읽기 복제)을 새 ADR로 검토한다.
