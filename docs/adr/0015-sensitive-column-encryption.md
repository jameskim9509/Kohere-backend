# ADR-0015. 민감정보 컬럼 암호화는 MVP에서 도입하지 않고 마스킹·저장소 암호화로 갈음한다

| 항목      | 값                                                                                                                                                                                                                                                                         |
| --------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 번호      | ADR-0015                                                                                                                                                                                                                                                                   |
| 작성자    | Kohere Backend 팀                                                                                                                                                                                                                                                          |
| 작성일    | 2026-06-17                                                                                                                                                                                                                                                                 |
| 관련 문서 | [database-design §6](../database/database-design.md), [database-design §4](../database/database-design.md), [error-response-guide §6](../api/error-response-guide.md), [ADR-0014](./0014-withdrawal-pii-anonymization.md), [ADR-0039](./0039-listing-schema-v4-registration-form.md), [system-overview §3-2](../architecture/system-overview.md) |

## Status

Accepted

> [system-overview](../architecture/system-overview.md)의 "소프트삭제·PII 보존" 선행 결정 중 **컬럼 암호화 여부**를 본 ADR이 닫는다([ADR-0014](./0014-withdrawal-pii-anonymization.md)와 짝).

## Context

- [database-design §6](../database/database-design.md): 민감정보의 **애플리케이션 레벨 컬럼 암호화 도입 여부가 미정**이다 — MySQL은 `user`의 `phone`/`visa`, `social_accounts`의 `email`이고, MongoDB `listings` 문서에는 `businessRegistrationNumber`(원문)와 `contact.managerName`/`phone`/`sms`가 있다([ADR-0039](./0039-listing-schema-v4-registration-form.md)). 도입하면 암호문이 평문보다 길어 **VARCHAR 길이를 재산정**해야 해서 컬럼 DDL이 막힌다(MySQL 한정).
- 일부 컬럼은 **등치 조회**가 필요하다(예: `social_accounts`의 `UNIQUE(provider, provider_user_id)`) → 무작위 암호화면 조회가 깨진다.

## Decision

**MVP는 애플리케이션 레벨 컬럼 암호화를 도입하지 않고, 다층 통제로 갈음한다.**

1. **필드 단위 암호화 미도입**(AES 등).
2. **다층 갈음**: (a) 전송 구간 HTTPS, (b) 저장소 **at-rest 디스크 암호화**(RDS·DocumentDB·ElastiCache 기본 암호화 활성 — MongoDB 호환 저장소는 DocumentDB다, [ADR-0018](./0018-documentdb-for-mongodb-on-aws.md)), (c) 응답·로그 **마스킹**([error-response-guide §6](../api/error-response-guide.md)), (d) 탈퇴 시 **PII 즉시 익명화**([ADR-0014](./0014-withdrawal-pii-anonymization.md)), (e) 접근 통제·최소 권한.
3. **컬럼 타입은 평문 기준 유지**(`phone_number VARCHAR(20)`, `visa_type VARCHAR(32)`, `email VARCHAR(255)`) → 등치 조회·유니크 제약 보존. MongoDB `listings`의 `businessRegistrationNumber`·`contact`도 평문 문자열로 두고, `businessRegistrationNumber`는 응답에서 제외한다([ADR-0039](./0039-listing-schema-v4-registration-form.md)).
4. **전환 트리거**: 규제 요구(특정 PII 암호화 의무)·위협 상승 시 **결정적(deterministic) 암호화**(검색 필요 컬럼)·랜덤 암호화(검색 불요)를 도입하고, 그때 **VARCHAR 길이 재산정 + 마이그레이션**(expand-contract, [migration-policy](../database/migration-policy.md))을 수행한다. 매물 문서 PII 신설([ADR-0039](./0039-listing-schema-v4-registration-form.md))은 **트리거에 해당하지 않는다** — 규제 요구·위협 상승 없이 저장 위치만 늘었고 at-rest 암호화·접근 통제·응답 제외로 갈음된다.
5. [database-design §6](../database/database-design.md)의 "민감정보 암호화" 항목을 **본 결정으로 닫는다**(MVP 미도입).

## Alternatives

| 대안                                | 장점                                        | 단점                                                 | 채택              |
| ----------------------------------- | ------------------------------------------- | ---------------------------------------------------- | ----------------- |
| **A. MVP 미도입 + 다층 갈음** | 단순, 검색·유니크 보존, 길이 재산정 불필요 | 컬럼 평문 저장(디스크 암호화·접근통제 의존)         | **채택**    |
| B. 즉시 필드 단위 AES               | DB 유출 시 평문 노출 방지                   | KMS 키관리·검색 제약(결정적 필요)·길이 재산정 복잡 | 미채택(트리거 시) |
| C. 토큰화(vault)                    | 강한 분리                                   | 인프라·운영 과중                                    | 미채택            |

## Consequences

- **긍정**: MVP가 단순하고, 등치 조회·유니크 제약이 그대로 유지되며 컬럼 길이 재산정이 불필요하다.
- **부정/트레이드오프**: DB에는 평문이 저장되므로 **at-rest 암호화·접근 통제에 의존**한다. 규제가 생기면 재작업이 필요하다.
- **후속 작업**: RDS/DocumentDB/ElastiCache at-rest 암호화 활성 확인, [database-design §6](../database/database-design.md) 항목 닫음, 트리거 발생 시 컬럼 암호화 ADR 작성.

## Validation

- 저장소 **at-rest 암호화 활성** 점검.
- 응답·로그 마스킹 동작 확인([ADR-0014](./0014-withdrawal-pii-anonymization.md)·[error-response-guide §6](../api/error-response-guide.md)).
- 전환 트리거(규제·위협) 발생 시 리뷰 체크리스트.
