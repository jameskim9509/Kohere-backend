# ADR-0033. 임대인 사업자등록번호는 비즈노 API로 동기 검증하고 정상 사업자만 VERIFIED로 마킹한다

| 항목 | 값 |
|---|---|
| 번호 | ADR-0033 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-06-30 |
| 관련 문서 | [US-1-8·US-1-9](../requirements/user-stories.md), [01-auth-onboarding](../api/specs/01-auth-onboarding.md), [error-response-guide](../api/error-response-guide.md), [ADR-0003](./0003-jwt-auth-after-oauth-login.md), [ADR-0005](./0005-polyglot-persistence.md), [ADR-0006](./0006-refresh-token-store-redis.md), [ADR-0015](./0015-sensitive-column-encryption.md), [ADR-0023](./0023-secrets-in-ssm-parameter-store.md) |

## Status

Proposed

## Context

- 온보딩은 두 역할(`TENANT`/`LANDLORD`)이 소셜 로그인·약관 동의까지 공통이고, 이후 본인 확인(세입자 이메일 인증 / 임대인 연락처 SMS 인증·사업자번호 검증)과 **온보딩 제출 엔드포인트에서 분기**한다(세입자 `POST /api/v1/auth/onboarding`, 임대인 `POST /api/v1/auth/landlord/onboarding`). 임대인은 이메일을 수집하지 않는다([ADR-0034](./0034-landlord-phone-sms-verification.md)). `userType`은 온보딩 제출로 확정·이후 불변이다([US-1-9](../requirements/user-stories.md)).
- **US-1-8**: 임대인은 온보딩 전에 **사업자등록번호**를 제출해야 하고, 서버는 그 번호가 **실재하고 정상 영업(계속) 상태인 사업자**인지 확인해야 한다. 미등록·휴업·폐업 사업자나 진위 불일치는 임대인 자격을 받을 수 없다.
- 검증 사실의 출처는 **국세청 사업자등록정보(진위·상태)** 다. 우리 시스템은 사업자 마스터를 보유하지 않으므로 **외부에 위임**해야 한다. 이메일/연락처 인증(`verification-code`/`verify`)이 코드 1건을 서버 외부 채널로 검증한 뒤 마커를 남기는 것과 **대칭 구조**가 자연스럽다(검증 단계 분리 → 온보딩 제출 시 대조).
- 제약: 모듈 내부는 DDD 4계층(도메인 포트 + 인프라 어댑터)이고 외부 연동은 인프라 어댑터로만 새어 나가야 한다([ADR-0001](./0001-bounded-context-module-decomposition.md) 계열). 임대인도 별도 모듈이 아니라 `user` 애그리거트이고, `auth`·`user`는 MySQL, 검증 마커·refresh는 Redis다([ADR-0005](./0005-polyglot-persistence.md), [ADR-0006](./0006-refresh-token-store-redis.md)). 민감 컬럼은 MVP에서 컬럼 암호화 대신 마스킹·저장소 암호화로 갈음한다([ADR-0015](./0015-sensitive-column-encryption.md)). 시크릿(외부 API 키)은 SSM Parameter Store에서 주입한다([ADR-0023](./0023-secrets-in-ssm-parameter-store.md)).
- 따라서 "사업자번호 검증을 어떻게(동기/비동기)·어디서(포트/어댑터)·무엇 기준으로 통과시키고, 검증 사실을 어떻게 보관·대조할지"를 결정해야 한다.

## Decision

**임대인 사업자등록번호는 비즈노 API(국세청 사업자등록정보 기반)로 *동기* 검증하고, 진위가 확인되고 *계속*(정상 영업) 상태인 사업자만 VERIFIED 마커를 남긴다. 검증은 이메일 인증과 대칭으로 별도 엔드포인트에서 선행하고, 온보딩 제출 시 마커를 대조한다.**

1. **검증 엔드포인트(선행)**: `POST /api/v1/auth/business/verify`(임대인 전용, 온보딩 토큰 티어). 요청의 사업자등록번호를 비즈노 API로 **동기 조회**한다. 진위 + **계속(정상) 상태**가 모두 충족될 때만 통과한다. 이메일 인증(`verification-code`/`verify`)과 대칭이다.
2. **아웃바운드 포트**: 도메인에 `BusinessRegistryVerifier` 포트를 둔다. 인프라 어댑터가 **비즈노 fapi**(국세청 사업자등록정보 진위·상태)를 호출한다 — 외부 연동·HTTP 세부는 어댑터 안에만 존재한다([ADR-0003](./0003-jwt-auth-after-oauth-login.md)의 `OidcTokenVerifier`/[ADR-0031](./0031-apple-sign-in-authorization-code-flow.md)의 `AppleAuthClient`와 같은 포트/어댑터 패턴). 요청은 `GET https://bizno.net/api/fapi?key={apiKey}&gb=1&q={사업자번호}&type=json`(RestClient, 필요한 설정은 API Key뿐)이고, 응답 `items[]`에서 조회 번호와 `bno`가 일치하고 폐업(`EndDt` 또는 `bstt` 상태)이 아닌 사업자가 있으면 정상(계속)으로 판정한다(그 외·미등록·4xx는 검증 실패, 5xx/타임아웃은 502).
3. **VERIFIED 마커(Redis)**: 검증 성공 시 `business-verify:verified:{userId}`에 **검증된 사업자번호의 해시**를 값으로 저장하고 **TTL = 온보딩 토큰 만료(~30분)** 로 둔다(확인 필요: TTL 정확값). 이메일 인증 마커 `email-verify:verified:{userId}`와 동일 패턴이다([ADR-0006](./0006-refresh-token-store-redis.md)의 키-값+TTL 적합성). 검증 통과는 `status` enum이 아니라 **이 별도 마커**로 표현한다(상태 모델 `PENDING→TERMS_AGREED→ACTIVE→WITHDRAWN`은 불변).
4. **온보딩 제출 시 대조**: `POST /api/v1/auth/landlord/onboarding`은 검증 게이트를 **약관 미동의 → 연락처 미인증 → 사업자번호 미검증** 우선순위로 통과시킨다([ADR-0034](./0034-landlord-phone-sms-verification.md)). 제출된 사업자번호의 해시가 마커 값과 일치할 때만 통과하고, 성공 시 `TERMS_AGREED→ACTIVE` + `userType=LANDLORD` 확정 + 닉네임 자동배정 + 정식 토큰을 발급한다.
5. **사업자번호 저장**: **원문을 저장하지 않는다.** SHA-256 **해시로만 영속**한다(`business_registration_number_hash` 컬럼, 확인 필요: 컬럼명·해시 솔트/pepper 정책). 응답·로그·`toString`에는 **마스킹**해 노출한다(예 `****567890`). [ADR-0015](./0015-sensitive-column-encryption.md)에 따라 별도 컬럼 암호화는 두지 않고 해시 + 마스킹으로 갈음한다.
6. **에러 매핑**: 신규 도메인 에러코드 2종을 추가한다.
   - `AUTH_BUSINESS_NUMBER_VERIFICATION_FAILED`(422) — 검증 엔드포인트에서 **미등록·휴업·폐업·진위 실패**.
   - `AUTH_BUSINESS_NUMBER_NOT_VERIFIED`(422) — 온보딩 제출 시 **미검증(마커 없음)·불일치**.
   - 비즈노 **외부 장애**(타임아웃·5xx)는 신규 코드를 만들지 않고 기존 공통 `UPSTREAM_ERROR`(502)를 재사용한다.
7. **보안 경로 티어**: 검증·온보딩 두 신규 엔드포인트는 모두 **온보딩 토큰 허용 티어**(약관·이메일 인증·온보딩과 동일)에 둔다. 외부 API 키 등 시크릿은 SSM에서 주입한다([ADR-0023](./0023-secrets-in-ssm-parameter-store.md)).

## Alternatives

| 대안 | 장점 | 단점 | 채택 안 한 이유 |
|---|---|---|---|
| **A. 비즈노 API 동기 검증 + VERIFIED 마커(채택)** | 실재·정상 상태를 즉시 차단(미등록/휴폐업 거절), 이메일 인증과 대칭, 온보딩 제출과 검증 분리 | 외부 HTTP 의존·지연·장애 모드 추가, 외부 API 키 관리 | — |
| B. 형식(체크섬)만 검증, 실재성 미확인 | 외부 의존 없음, 단순 | 휴·폐업·가짜 번호 통과 → US-1-8 충족 불가 | 실재·정상 상태 요구를 못 채움 |
| C. 비동기(검증 후 콜백/배치) 검증 | 사인업 경로에서 외부 지연 제거 | 온보딩 즉시 분기와 불일치(상태 대기), 복잡도↑ | MVP 온보딩은 동기 확정이 단순·일관 |
| D. 사업자번호 원문 평문 저장 | 재조회·관리자 확인 용이 | PII/사업자정보 노출면 확대([ADR-0015](./0015-sensitive-column-encryption.md) 취지에 역행) | 검증 통과 후엔 해시 대조만 필요 → 해시+마스킹으로 충분 |

## Consequences

- **긍정**: 미등록·휴업·폐업 사업자를 온보딩 단계에서 차단해 임대인 자격의 신뢰도를 확보한다. 포트/어댑터로 외부 연동을 인프라에 가두고, 검증 마커(Redis)·대칭 패턴으로 이메일 인증과 일관된 구조를 유지한다. 사업자번호 원문을 보관하지 않아 노출면이 작다.
- **부정/트레이드오프**: 임대인 사인업 경로에 외부 HTTP(비즈노) 의존·지연·실패 모드가 추가되고, 외부 API 키 관리·요청 비용·rate-limit 부담이 생긴다. 운영 저장소 분산(마커 Redis)·외부 장애 시 `UPSTREAM_ERROR` 폴백 동작을 정의·관측해야 한다.
- **후속 작업(구현 PR)**: `ErrorCode`에 두 코드 추가 + 메시지 리소스 번들([ADR-0030](./0030-error-message-i18n-resource-bundle.md)), `BusinessRegistryVerifier` 포트 + 비즈노 어댑터(RestClient·SSM 키), Redis 마커 저장/대조, `SecurityConfig` 경로 티어, Flyway 전진 마이그레이션(`user`에 `user_type`·`name`·`phone_number`·`business_registration_number_hash` 등 — 본 ADR은 목표 스키마이며 현행 코드 미구현). 문서 정합: [01-auth-onboarding](../api/specs/01-auth-onboarding.md)·[error-response-guide](../api/error-response-guide.md)·[domain-model](../architecture/domain-model.md)·[database-design](../database/database-design.md)·시퀀스 다이어그램 갱신.
- **미결(확인 필요)**:
  - 비즈노 호출 **타임아웃·재시도** 수치(connect/read, 재시도 횟수·백오프).
  - 검증 엔드포인트 **rate-limit** 임계값(사업자번호 추측·남용 방지).
  - 비즈노 회신 **상호·대표자명** 등 부가정보의 **저장 여부**(현재는 검증 응답 표시용으로만 사용 가정 — 저장 시 PII 정책 재검토).
  - 사업자등록번호 **유니크 제약**(현재는 앱 레벨, DB 유니크 미적용) 채택 여부.
  - 검증 마커 TTL 정확값(온보딩 토큰 만료와 동기화 방식).

## Validation

- 정상(계속) 사업자번호로 `POST /api/v1/auth/business/verify`가 통과하고 `business-verify:verified:{userId}` 마커(해시)가 TTL과 함께 생성되는지 확인.
- 미등록·휴업·폐업·진위 실패가 `422 AUTH_BUSINESS_NUMBER_VERIFICATION_FAILED`로 거부되는지, 비즈노 장애(타임아웃·5xx)가 `502 UPSTREAM_ERROR`로 매핑되는지 확인.
- 온보딩 제출에서 마커가 없거나 제출 번호 해시가 마커와 불일치할 때 `422 AUTH_BUSINESS_NUMBER_NOT_VERIFIED`로 거부되고, 게이트 우선순위(약관→이메일→사업자번호)대로 첫 위반이 보고되는지 확인.
- 사업자번호 원문이 어떤 로그·응답·`toString`에도 남지 않고 마스킹(`****567890`)되며, 저장은 해시 컬럼에만 들어가는지 확인.
- 검증 성공 후 온보딩 제출 시 `userType=LANDLORD` 확정·정식 토큰 발급(200)이 이루어지고, 확정 후 `userType`이 불변인지 확인.
- **재검토 시점**: 비즈노 장애·rate-limit이 사인업 성공률을 떨어뜨리면 비동기/캐시(대안 C) 또는 다중 검증 제공자를 재검토한다.
