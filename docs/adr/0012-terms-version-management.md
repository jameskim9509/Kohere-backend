# ADR-0012. 약관 버전(termsVersion)은 서버 설정값을 정본으로 기록한다

| 항목 | 값 |
|---|---|
| 번호 | ADR-0012 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-06-17 |
| 관련 문서 | [01-auth-onboarding §2](../api/specs/01-auth-onboarding.md), [domain-model §2](../architecture/domain-model.md), [database-design §4-2](../database/database-design.md), [ADR-0004](./0004-api-response-envelope.md) |

## Status

Accepted

## Context

- [domain-model](../architecture/domain-model.md) `User`의 VO `Consent`는 동의 3종·동의 시점(`agreedAt`)과 함께 **약관 버전(`termsVersion`)** 을 기록하도록 정의돼 있다.
- 그러나 온보딩 요청 바디(`POST /api/v1/auth/onboarding`)에는 동의 boolean 3종만 있고 **`termsVersion` 필드가 없다**([01-auth-onboarding §2](../api/specs/01-auth-onboarding.md)). 값을 어디서 채울지 미정이라 `Consent` 생성이 막힌다.
- 약관 버전은 "사용자가 **어느 버전**에 동의했는가"를 남겨, 추후 약관 개정 시 **재동의 필요 판단·감사**의 근거가 된다.

## Decision

**약관 버전의 정본은 서버 설정값으로 두고, 온보딩 완료 시 서버가 기록한다.**

1. **정본 = 서버 설정**: `application.yml`의 `app.terms.version`(예: `"v1.0"`). 클라이언트가 보내지 않는다(위변조 방지).
2. **기록**: 온보딩 완료 처리에서 서버가 현재 `app.terms.version`을 `Consent.termsVersion`에 `agreedAt`과 함께 채운다.
3. **형식**: 단순 문자열(`vMAJOR.MINOR` 권장). 약관 본문이 개정되면 운영자가 버전을 올린다.
4. **범위 밖(후속)**: 기존 ACTIVE 사용자의 **신버전 재동의 워크플로**, 약관 본문 다국어/보관은 본 ADR 범위가 아니다.
5. **컬럼**: `users.terms_version`은 NULL 허용을 유지하되(레거시·마케팅 단독 동의 등), **온보딩 경로는 항상 채운다**([database-design §4-2](../database/database-design.md)).

## Alternatives

| 대안 | 장점 | 단점 | 채택 |
|---|---|---|---|
| **A. 서버 설정값 기록** | 단순, 위변조 불가, 감사 가능 | 약관 본문과 버전을 운영자가 수기 동기화 | **채택** |
| B. 클라이언트가 버전 전송 | 클라이언트가 본 버전 그대로 | **위변조·신뢰 불가** | 미채택 |
| C. 별도 약관(terms) 테이블 + 이력 | 약관 본문·버전 정합 정확 | MVP 과함(테이블·관리 화면) | 미채택(트리거 시) |
| D. 버전 미기록 | 가장 단순 | 동의 버전 감사 불가 | 미채택 |

## Consequences

- **긍정**: 동의 시점의 약관 버전이 남아 감사·재동의 판단이 가능하고, 구현이 단순하다.
- **부정/트레이드오프**: 약관 개정 시 버전 갱신·재동의 흐름을 별도로 설계해야 한다(현재는 기록만).
- **후속 작업**: `application.yml`에 `app.terms.version` 추가, 온보딩 서비스에서 주입, [01-auth-onboarding](../api/specs/01-auth-onboarding.md)·[domain-model](../architecture/domain-model.md) 주석 갱신.

## Validation

- 온보딩 완료 레코드의 `terms_version`이 설정값과 일치하는지 확인.
- 설정값을 바꾼 뒤 신규 동의에 새 버전이 기록되는지 확인.
