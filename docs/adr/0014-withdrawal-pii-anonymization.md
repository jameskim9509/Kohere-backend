# ADR-0014. 회원 탈퇴는 상태 전이 + PII 즉시 익명화로 처리한다

| 항목 | 값 |
|---|---|
| 번호 | ADR-0014 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-06-17 |
| 관련 문서 | [01-auth-onboarding §7](../api/specs/01-auth-onboarding.md), [domain-model §2](../architecture/domain-model.md), [database-design §4-2](../database/database-design.md), [ADR-0015](./0015-sensitive-column-encryption.md), [ADR-0005](./0005-polyglot-persistence.md), [ADR-0006](./0006-refresh-token-store-redis.md), [ADR-0002](./0002-inter-module-communication-via-events.md), [ADR-0039](./0039-listing-schema-v4-registration-form.md), [system-overview §3-2](../architecture/system-overview.md) |

## Status

Accepted

> [system-overview](../architecture/system-overview.md)는 "소프트삭제·PII 보존 = 정책 결정 필요(선행), ADR 필요"로 남겼다. 본 ADR(탈퇴·PII 보존)과 [ADR-0015](./0015-sensitive-column-encryption.md)(컬럼 암호화)가 그 선행 결정을 닫는다.

## Context

- 탈퇴(`DELETE /api/v1/users/me`)는 `status=WITHDRAWN` 전이 + refresh 토큰 일괄 무효화까지는 확정이나, **PII(이름·전화·비자·소셜 email)의 파기/익명화·보존 기간**이 미정이다([01-auth-onboarding §7](../api/specs/01-auth-onboarding.md)에 "(확인 필요: 보존 기간)").
- `user`는 소프트삭제 컬럼 대신 **상태(WITHDRAWN)** 로 표현한다([database-design §4-2](../database/database-design.md)). 타 모듈은 `userId`를 **값으로 참조**(no-FK, [ADR-0002](./0002-inter-module-communication-via-events.md)/[ADR-0005](./0005-polyglot-persistence.md))하므로 **행을 물리 삭제하면 참조가 깨진다.**
- 개인정보보호 원칙: 목적(서비스 이용)이 끝난 PII는 **지체 없이 파기/익명화**한다.

## Decision

**탈퇴는 상태 전이 + PII 즉시 익명화로 처리하고, 식별자 행은 보존한다.**

1. **상태·메타**: `users.status=WITHDRAWN` 전이 + **`withdrawn_at`(UTC) 기록** + refresh 토큰 일괄 무효화(REVOKED, [ADR-0006](./0006-refresh-token-store-redis.md)). **행 자체는 보존**(`userId` 식별자 유지 → 타 모듈 값 참조 무결성·재가입 분리).
2. **PII 즉시 익명화**: 탈퇴 시 식별 PII(`first_name`/`last_name`, `country_code`/`phone_number`, `visa_type`, `birth_date`)를 **파기성 익명화**(NULL 또는 고정 placeholder로 덮어쓰기)한다. 복구 불가. 대상은 MySQL `users` 컬럼이며, `listings` 문서의 임대인 PII(`contact`·`businessRegistrationNumber` — [ADR-0039](./0039-listing-schema-v4-registration-form.md))는 **임대인 탈퇴 기능 구현 시 함께 설계한다**(아래 후속 작업).
3. **소셜 자격 정리**: `auth`는 `UserWithdrawnEvent`를 구독해 해당 `user`의 `social_accounts` 매핑을 삭제한다([ADR-0002](./0002-inter-module-communication-via-events.md)). `UNIQUE(provider, provider_user_id)`가 풀려 **재가입 시 새 자격으로 분리**된다.
4. **보존 예외**: 법정 보존 의무 항목(전자상거래·통신 등)이 식별되면 **그 항목만** 별도 보존 컬럼/기간으로 예외 처리한다. 현재 MVP의 `users` 보유 PII에는 해당 항목이 없어 **즉시 익명화**가 기본이다.
5. **조회·재가입**: WITHDRAWN·부재 사용자는 `USER_NOT_FOUND(404)`. 재가입은 **신규 회원(PENDING)** 으로 시작하며 이전 데이터는 복원하지 않는다.
6. **일관성**: 소프트삭제 컬럼(`deleted`/`deleted_at`)은 `community` 등 다른 컨텍스트에만 쓰고, `user`는 **상태(WITHDRAWN) + `withdrawn_at`** 으로 통일한다.

## Alternatives

| 대안 | 장점 | 단점 | 채택 |
|---|---|---|---|
| **A. 상태 전이 + PII 즉시 익명화** | 참조 무결성과 개인정보 최소 보존의 균형 | 익명화 비가역 | **채택** |
| B. 물리 삭제(행 제거) | 완전 삭제 | 타 모듈 `userId` 값 참조 깨짐, 복구·감사 불가 | 미채택 |
| C. 소프트삭제만(PII 보존) | 단순·복구 가능 | 불필요 PII 장기 보존(개인정보 원칙 위배) | 미채택 |
| D. 보존기간 후 배치 익명화 | 분쟁 대비 유예 | 스케줄러·기간 관리 복잡(MVP 과함) | 후속 여지 |

## Consequences

- **긍정**: 개인정보 최소 보존, 타 모듈 참조 무결성 유지, 재가입이 이전 계정과 분리된다.
- **부정/트레이드오프**: 익명화는 **비가역** → 오처리 복구 불가, 익명화 대상 컬럼 누락에 주의.
- **후속 작업**
  - [database-design §4-2](../database/database-design.md)에 `users.withdrawn_at` 컬럼 추가(Flyway), 탈퇴 서비스 익명화 로직.
  - `auth`에 `UserWithdrawnEvent` 리스너(소셜 자격 정리·토큰 무효화) 구현.
  - [01-auth-onboarding §7](../api/specs/01-auth-onboarding.md)·[domain-model §2](../architecture/domain-model.md)·[database-design §6](../database/database-design.md) 갱신.
  - 임대인 탈퇴 시 `listings` 문서 PII(`contact`·`businessRegistrationNumber`) 처리 — **임대인 탈퇴 기능 구현 시 함께 설계한다**([ADR-0039](./0039-listing-schema-v4-registration-form.md)).

## Validation

- 탈퇴 후 PII 컬럼이 익명화되고 `withdrawn_at`이 기록되는지 확인.
- `social_accounts` 매핑 제거 후 동일 소셜 계정 **재가입이 신규 회원으로 분리**되는지 확인.
- WITHDRAWN 사용자 조회 시 `USER_NOT_FOUND(404)`, refresh 토큰 무효화 확인.
