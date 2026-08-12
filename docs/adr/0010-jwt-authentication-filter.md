# ADR-0010. JWT 검증은 common 모듈의 횡단 보안 필터로 처리한다 (Spring Security 필터 체인·인증 컨텍스트·보호 경로)

| 항목      | 값                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 번호      | ADR-0010                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| 작성자    | Kohere Backend 팀                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| 작성일    | 2026-06-17                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| 관련 문서 | [ADR-0003](./0003-jwt-auth-after-oauth-login.md), [ADR-0009](./0009-jwt-signing-algorithm-hs256.md), [ADR-0011](./0011-token-lifetime-and-secret-policy.md), [ADR-0004](./0004-api-response-envelope.md), [ADR-0002](./0002-inter-module-communication-via-events.md), [system-overview §3-3](../architecture/system-overview.md), [error-response-guide](../api/error-response-guide.md), [01-auth-onboarding](../api/specs/01-auth-onboarding.md), [code-style §3](../convention/code-style.md), [ADR-0039](./0039-listing-schema-v4-registration-form.md) |

## Status

Accepted

> [ADR-0003](./0003-jwt-auth-after-oauth-login.md)은 "매 요청 토큰 파싱/검증은 `auth` BC가 아니라 **횡단 보안 필터의 책임**이며, 그 배치는 별도 ADR로 다룬다"고 후속을 남겼다. 본 ADR이 그 후속을 닫는다 — **검증 필터의 위치·인증 컨텍스트 주입·보호 경로 정책**을 확정한다.

## Context

- **무상태 access 검증**([ADR-0003](./0003-jwt-auth-after-oauth-login.md)): 매 요청 `Authorization: Bearer <access>`를 **서명·만료·클레임**만 검증(저장소 무조회). 서명은 HS256([ADR-0009](./0009-jwt-signing-algorithm-hs256.md)), 수명·시크릿은 [ADR-0011](./0011-token-lifetime-and-secret-policy.md).
- **검증은 `auth` BC 밖의 횡단 관심사**다. 발급·회전·재사용 탐지는 `auth`(BC)가 하지만, 매 요청 검증·인증 주체 주입은 모든 모듈에 걸친 공통 메커니즘이다.
- **온보딩 스코프**: 신규(PENDING)에게는 `onboardingCompleted=false` 클레임의 **스코프 제한 토큰**만 발급된다([ADR-0003](./0003-jwt-auth-after-oauth-login.md) D5). 이 토큰은 **온보딩 API만** 통과해야 하고, 보호 자원(`GET/PATCH /users/me`)엔 접근하면 안 된다.
- **엔드포인트마다 인가 요건이 다르다**: 공개(`social-login`·`reissue`), 온보딩 스코프(`onboarding`·탈퇴), 정식 인증(그 외)([01-auth-onboarding](../api/specs/01-auth-onboarding.md)).
- **에러는 공통 래퍼**([ADR-0004](./0004-api-response-envelope.md))로 통일돼야 한다 — 인증/인가 실패(401/403)도 `{ success, data, error }` + `ErrorCode`로 나가야 한다.

## Decision

**`common` 모듈에 커스텀 `OncePerRequestFilter`(`JwtAuthenticationFilter`)를 두고, Spring Security 필터 체인에서 인증을 횡단 처리한다.** 검증 메커니즘은 `common`이 공유하되, 서명 시크릿은 런타임 주입이라 서명 권한은 `auth`에만 있다([ADR-0009](./0009-jwt-signing-algorithm-hs256.md) D3).

세부 정책:

1. **필터 배치**: `SecurityFilterChain`에서 `JwtAuthenticationFilter`를 `UsernamePasswordAuthenticationFilter` **앞**에 등록한다. 세션은 `STATELESS`, CSRF·폼로그인·httpBasic은 비활성(무상태 Bearer 인증).
2. **검증**: 헤더에서 Bearer 토큰을 추출해 jjwt로 **HS256 서명·만료·클레임**을 검증한다. 토큰이 없거나 검증 실패면 `SecurityContext`를 비운 채(익명) 다음 단계로 넘긴다(차단은 인가 단계가 결정).
3. **인증 컨텍스트 주입**: 검증 성공 시 `Authentication`을 만들어 `SecurityContext`에 넣는다 — **principal = `userId`**, **authorities = 온보딩 스코프**(`onboardingCompleted=true` → `ROLE_USER`, `false` → `ROLE_ONBOARDING`). 다운스트림(컨트롤러·application)은 `SecurityContext`에서 `userId`만 읽어 쓴다(모듈 간 식별자 값 참조, [ADR-0002](./0002-inter-module-communication-via-events.md)).
4. **보호 경로 정책**(`authorizeHttpRequests`):
   - **permitAll**(공개): `POST /api/v1/auth/social-login`, `POST /api/v1/auth/reissue`, `GET /actuator/health`, REST Docs 정적 문서.
   - **온보딩 스코프 이상 허용**(`ROLE_ONBOARDING`도 통과): `POST /api/v1/auth/onboarding`, `DELETE /api/v1/users/me`(PENDING도 탈퇴 허용).
   - **정식 인증 필요**(`ROLE_USER`): 그 외 보호 자원(`GET/PATCH /api/v1/users/me`, `POST /api/v1/auth/logout` 등).
   - **매물 등록 `POST /api/v2/listings`는 `hasRole("USER")` 명시 매처**를 둔다. 명시하지 않고 `anyRequest().authenticated()`에 맡기면 **온보딩 스코프(`ROLE_ONBOARDING`) 토큰도 컨트롤러에 도달한다** — `authenticated()`는 인증 여부만 보고 권한(authority)은 보지 않기 때문이다. 임대인 여부(`userType=LANDLORD`)는 토큰 클레임에 없어 필터가 판정할 수 없으므로 **서비스에서 재검사해 `403 FORBIDDEN`** 으로 거른다 — 필터 체인이 "온보딩을 마친 회원"까지 좁히고 서비스가 "임대인"까지 좁히는 **2단 구조**다([ADR-0039](./0039-listing-schema-v4-registration-form.md)).
   - PENDING(`ROLE_ONBOARDING`) 토큰으로 `ROLE_USER` 자원 접근 시 **403 `AUTH_ONBOARDING_REQUIRED`**.
5. **에러 변환**: `AuthenticationEntryPoint`(미인증·위조 → 401 `UNAUTHENTICATED`, 만료 → 401 `TOKEN_EXPIRED`)와 `AccessDeniedHandler`(권한 부족 → 403 `FORBIDDEN`, 온보딩 미완료 → 403 `AUTH_ONBOARDING_REQUIRED`)가 **공통 래퍼·`ErrorCode`**로 응답한다([error-response-guide](../api/error-response-guide.md) 카탈로그와 일치).
6. **모듈 경계**: 검증 필터·`SecurityConfig`는 `common`(OPEN 공유 커널)에 둔다. `common`은 **검증 코드만** 공유하고 타 모듈을 의존하지 않는다(`ApplicationModules.verify()`로 보장). 발급·회전·재사용 탐지는 `auth` 소관.

## Alternatives

| 대안                                                            | 장점                                                                      | 단점                                                                                                                  | 채택                       |
| --------------------------------------------------------------- | ------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- | -------------------------- |
| **A. `common` 커스텀 `OncePerRequestFilter`**         | 단순·명시적, 온보딩 스코프·불투명 refresh 흐름에 맞춤, 횡단 단일 진입점 | 필터·핸들러 직접 구현 책임                                                                                           | **채택**             |
| **B. Spring Security `oauth2ResourceServer(jwt)` 내장** | 표준·코드량 적음                                                         | HS256·커스텀 클레임(온보딩 스코프)·공통 에러 래퍼 커스터마이즈가 오히려 번거롭고 불투명 refresh 흐름과 결이 안 맞음 | 미채택(과결합)             |
| **C. 컨트롤러/인터셉터 수동 검증**                        | 프레임워크 의존 최소                                                      | 중복·누락 위험, 횡단 책임이 흩어짐                                                                                   | 미채택                     |
| **D. API 게이트웨이 검증**                                | 입구 단일 검증                                                            | 모듈러 모놀리식엔 인프라 과함                                                                                         | 미채택(MSA 전환 시 재검토) |

## Consequences

- **긍정**
  - 인증 진입점이 단일화되어 컨트롤러는 "인증됨·`userId` 존재"를 가정하고 도메인 로직만 작성한다.
  - 온보딩 스코프 강제와 인증/인가 에러 응답이 **전 엔드포인트에서 일관**된다.
- **부정/트레이드오프**
  - `JwtAuthenticationFilter`·`SecurityConfig`·`EntryPoint`/`AccessDeniedHandler` 구현 부담.
  - 보호 경로 화이트리스트를 **코드로 유지** → 엔드포인트 추가 시 정책 갱신 누락 주의.
- **후속 작업**
  - [build.gradle](../../build.gradle)에 `spring-boot-starter-security` 추가, 필터·체인·핸들러 구현.
  - [ADR-0003](./0003-jwt-auth-after-oauth-login.md) 후속(검증 필터 배치) 닫음, [system-overview §3-3](../architecture/system-overview.md) 보안 프레임워크 항목 ✅.

## Validation

- **경로별 통합 테스트**: 공개/온보딩 스코프/정식 인증 경로에서 200/401/403이 의도대로 나오는지.
- PENDING 토큰으로 `GET /users/me` → 403 `AUTH_ONBOARDING_REQUIRED`, 만료 토큰 → 401 `TOKEN_EXPIRED` 확인.
- 온보딩 토큰으로 `POST /api/v2/listings` → 403 `AUTH_ONBOARDING_REQUIRED`(필터 체인에서 차단), 세입자(`userType=TENANT`) 정식 토큰 → 403 `FORBIDDEN`(서비스에서 차단) — 2단 구조가 각각 어디서 끊는지 확인.
- `ApplicationModules.verify()`로 `common` → 타 모듈 의존이 없는지 모듈 경계 검증.
