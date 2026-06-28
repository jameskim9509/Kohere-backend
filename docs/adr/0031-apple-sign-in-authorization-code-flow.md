# ADR-0031. Apple 로그인은 authorization code 방식으로 전환해 탈퇴 시 토큰을 폐기한다

| 항목 | 값 |
|---|---|
| 번호 | ADR-0031 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-06-28 |
| 관련 문서 | [01-auth-onboarding §1·§10](../api/specs/01-auth-onboarding.md), [US-1-1](../architecture/sequence-diagrams/01-auth-onboarding/us-1-1-social-login.md), [US-1-4](../architecture/sequence-diagrams/01-auth-onboarding/us-1-4-logout-withdraw.md), [ADR-0003](./0003-jwt-auth-after-oauth-login.md), [ADR-0014](./0014-withdrawal-pii-anonymization.md), [ADR-0015](./0015-sensitive-column-encryption.md), [ADR-0023](./0023-secrets-in-ssm-parameter-store.md), [ADR-0002](./0002-inter-module-communication-via-events.md), [ADR-0006](./0006-refresh-token-store-redis.md) |

## Status

Accepted

## Context

- 현재 소셜 로그인은 Apple·Google 모두 **`idToken`만 검증**(JWKS 서명·`iss`·`aud`·`exp`)하고 신원(`sub`·`email`)만 추출한다([US-1-1](../architecture/sequence-diagrams/01-auth-onboarding/us-1-1-social-login.md)). 서버는 제공자 측 토큰(access/refresh)을 **보관하지 않는다.**
- **App Store 심사 가이드라인 5.1.1(v)**(2022-06-30 시행)는 계정 생성을 지원하는 앱에 **인앱 계정 삭제**와 **소셜 자격 폐기 메커니즘**을 요구한다. Apple 공식 문서(Offering account deletion·TN3194)는 Sign in with Apple의 경우 **`POST https://appleid.apple.com/auth/revoke`로 사용자 토큰을 폐기**하도록 안내한다.
- Apple의 **`idToken`은 폐기(revoke)할 수 없다.** `/auth/revoke`는 **refresh token**(또는 access token)만 받는다(TN3194). refresh token은 **authorization code를 `POST /auth/token`(grant_type=authorization_code)에서 교환**해야만 얻는다. 즉 현재의 idToken 전용 흐름으로는 **탈퇴 시 폐기할 토큰이 없어** 5.1.1(v)를 충족하지 못한다.
- 회원 탈퇴([ADR-0014](./0014-withdrawal-pii-anonymization.md))는 현재 `social_accounts` 매핑 삭제 + 우리 refresh 토큰 무효화까지만 한다 — **Apple 측 앱↔Apple ID 연동은 남는다.**
- **Google**은 백엔드가 영속 grant(refresh token)를 보유하지 않고(신원 scope만 사용, `access_type=offline` 미사용), Apple 같은 토큰 폐기 강제 요건도 없다. Google Play의 계정 삭제 요건은 "우리 앱 내 사용자 데이터 삭제"이며 토큰 폐기가 아니다 — 이미 충족 중.
- 제약: 모듈 내부는 DDD 4계층(도메인 포트·인프라 어댑터), 모듈 간 통신은 도메인 이벤트([ADR-0002](./0002-inter-module-communication-via-events.md)). 스키마는 Flyway 소유(`ddl-auto=validate`). 시크릿은 SSM Parameter Store([ADR-0023](./0023-secrets-in-ssm-parameter-store.md)). 민감 컬럼은 MVP에서 평문 + 저장소 암호화로 갈음([ADR-0015](./0015-sensitive-column-encryption.md)).

## Decision

**Apple 로그인은 authorization code 방식으로 전환하고, refresh token을 저장해 탈퇴 시 `/auth/revoke`로 폐기한다. Google은 기존 idToken 방식을 유지한다.**

1. **Apple 인가 코드 교환**: 앱은 `ASAuthorizationAppleIDCredential.authorizationCode`(1회용·약 5분)를 서버로 전달한다. 서버는 `POST https://appleid.apple.com/auth/token`(grant_type=authorization_code)으로 교환해 `{ id_token, refresh_token }`을 받고, **반환된 `id_token`을 기존 `OidcTokenVerifier`로 검증**(서명·`iss`·`aud`·`exp`)해 `sub`·`email`을 얻는다(교환 응답을 맹신하지 않음). 네이티브 iOS이므로 `redirect_uri`는 보내지 않는다.
2. **Google 불변**: Google은 기존 `idToken` 검증(JWKS) 경로를 그대로 둔다.
3. **요청 계약(A안)**: 단일 엔드포인트 `POST /api/v1/auth/social-login`·단일 응답을 유지하고, 요청 바디만 provider별 선택 필드로 둔다 — `{ provider, idToken?, authorizationCode? }`. Google은 `idToken`, Apple은 `authorizationCode`를 채운다. provider별 필수 여부는 Bean Validation 대신 **application 계층에서 검증**하고 누락 시 `400 AUTH_MISSING_CREDENTIAL`로 응답한다(통합 단일 `credential` 필드(B안)는 의미 오버로드로 비채택).
4. **refresh token 저장**: `social_accounts`에 nullable `apple_refresh_token` 컬럼을 추가(Flyway 전진 마이그레이션)한다. Apple은 refresh token을 **최초 동의/재동의 때만** 반환하므로, **응답에 비어있지 않은 refresh token이 있을 때만 upsert하고 없으면 기존 값을 보존**한다(일반 재로그인이 저장된 토큰을 null로 덮어쓰지 않게 한다). [ADR-0015](./0015-sensitive-column-encryption.md)에 따라 MVP는 평문 컬럼(RDS 저장소 암호화 의존)으로 두되 **로그·응답·`toString`에 절대 노출하지 않는다**(향후 컬럼 암호화 대상 워치리스트).
5. **탈퇴 시 폐기(best-effort)**: `UserWithdrawnEvent` 처리에서 매핑 삭제 **이전에** Apple `apple_refresh_token`을 읽어 `POST /auth/revoke`(`token_type_hint=refresh_token`)를 호출한다. **멱등 처리** — HTTP 200, 그리고 `invalid_grant`/`invalid_token`(이미 폐기)은 성공으로 간주한다. 그 외 실패(타임아웃·5xx)는 **WARN 로그 + 메트릭**만 남기고 **탈퇴를 막지 않는다**([ADR-0014](./0014-withdrawal-pii-anonymization.md): 삭제는 차단되면 안 됨). 로컬 정리(매핑 삭제·우리 refresh 무효화)는 기존대로 탈퇴 트랜잭션 안에서 수행하고, 외부 호출은 짧은 connect/read 타임아웃으로 제한한다. 마이그레이션 이전 Apple 사용자(저장된 토큰 없음)는 다음 로그인 때 백필되며, 그때까지는 폐기를 스킵(WARN 메트릭으로 잔여 갭 가시화)한다.
6. **client_secret**: `/auth/token`·`/auth/revoke` 공용으로 **ES256(P-256) 서명 JWT**를 쓴다 — 헤더 `{alg:ES256, kid:Key ID}`, 클레임 `iss=Team ID`, `sub=client_id`, `aud=https://appleid.apple.com`, `exp≤6개월`. **네이티브 iOS는 `client_id`·`sub`가 App ID(번들 ID)** 이며 Services ID가 아니다(`app.apple.client-id`는 `app.oidc.apple.audience`와 동일한 번들 ID여야 함). `.p8` 개인키·식별자는 SSM에서 주입([ADR-0023](./0023-secrets-in-ssm-parameter-store.md)), JWT는 메모리 캐시 후 만료 전 재생성한다. 이 client_secret은 사용자와 무관한 **앱 단위 단일 값**이라 인스턴스별로 인메모리에 1개만 캐시한다(사용자별 캐시·공유 저장소 불필요). 만료가 임박하면 같은 `.p8`로 새 JWT를 재서명해 교체한다.

## Alternatives

| 대안 | 장점 | 단점 | 채택 |
|---|---|---|---|
| **A. Apple만 authorization code + refresh 저장 + 탈퇴 시 revoke** | 5.1.1(v) 충족, Google 무변경, 변경 범위 최소 | 사인인 시 외부 교환 호출·시크릿(.p8) 관리 추가 | **채택** |
| B. idToken 방식 유지 | 변경 없음 | 폐기할 토큰 없음 → **5.1.1(v) 위반(심사 거절)** | 미채택 |
| C. Google도 authorization code로 통일 | 요청 바디 모양 동일 | Google엔 기능적 이득 0, refresh 보관 시 **불필요한 폐기 의무·시크릿** 신설 | 미채택 |
| D. 통합 단일 `credential` 필드 | 바디 모양 동일 | 한 필드가 provider별로 의미가 달라 오버로드·실수 위험 | 미채택(A의 명시적 선택 필드 채택) |

## Consequences

- **긍정**: Apple 계정 삭제 시 앱↔Apple ID 연동까지 폐기 → App Store 5.1.1(v) 충족. Google은 무변경이라 위험·작업 0. 단일 엔드포인트·단일 응답이 유지돼 프론트 후처리는 동일(요청 바디 한 필드만 분기).
- **부정/트레이드오프**: Apple 사인인 경로에 외부 HTTP(`/auth/token`) 의존·지연·실패 모드가 추가된다. `.p8`/refresh token 등 1급 시크릿 관리·로깅 차단 부담이 늘고, 인가 코드는 1회용·5분이라 즉시 교환해야 한다. 탈퇴 시 revoke는 **best-effort**(durable 재시도 없음 → 앱 크래시/Apple 장애 시 폐기 유실 가능). client_secret 만료(≤6개월) 회전 책임이 생긴다.
- **후속 작업**
  - `SocialLoginRequest`에 `authorizationCode` 추가, `AuthService`에 provider 분기·Apple 교환·refresh 저장(upsert 가드).
  - 도메인 포트 `AppleAuthClient`(exchange·revoke) + 인프라 어댑터(RestClient + ES256 client_secret 서명), `app.apple` 설정·시크릿(SSM, `.p8`).
  - Flyway 전진 마이그레이션으로 `social_accounts.apple_refresh_token` 추가, 엔티티·도메인·매퍼·리포지토리(`findByUserId`) 반영.
  - `UserWithdrawnEventListener`에 revoke 단계 추가(읽기→폐기→삭제 순서, best-effort).
  - 신규 `ErrorCode.AUTH_MISSING_CREDENTIAL`(400), Apple 장애는 기존 `UPSTREAM_ERROR`(502)/`AUTH_INVALID_SOCIAL_TOKEN`(401) 재사용.
  - durable revoke 재시도가 필요하면 `spring-modulith-starter-jpa`(Event Publication Registry) 도입 검토.
  - 문서 정합: [01-auth-onboarding](../api/specs/01-auth-onboarding.md)·[US-1-1](../architecture/sequence-diagrams/01-auth-onboarding/us-1-1-social-login.md)·[US-1-4](../architecture/sequence-diagrams/01-auth-onboarding/us-1-4-logout-withdraw.md)·[domain-model](../architecture/domain-model.md)·[database-design](../database/database-design.md) 갱신.

## Validation

- Apple 신규 로그인 시 `/auth/token` 교환 후 `apple_refresh_token`이 저장되고, 일반 재로그인(refresh token 미반환)에서 기존 값이 보존되는지 확인.
- 교환 응답의 `id_token`이 `aud`(번들 ID) 불일치 시 거부되는지(audience 검증이 비활성화되지 않는지) 확인.
- 탈퇴 시 `/auth/revoke` 호출 후 동일 Apple 계정 재로그인이 재동의를 요구(연동 해제)하는지, Apple 장애/`invalid_grant`에도 탈퇴가 완료되는지 확인.
- refresh token·client_secret·`.p8`이 어떤 로그에도 남지 않는지(마스킹/no-log) 확인.
- Google 로그인이 기존과 동일하게 동작(회귀 없음)하는지 확인.
