# ADR-0011. 토큰 수명(TTL)과 HS256 시크릿 정책값을 확정한다

| 항목 | 값 |
|---|---|
| 번호 | ADR-0011 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-06-17 |
| 관련 문서 | [ADR-0003](./0003-jwt-auth-after-oauth-login.md), [ADR-0006](./0006-refresh-token-store-redis.md), [ADR-0009](./0009-jwt-signing-algorithm-hs256.md), [ADR-0010](./0010-jwt-authentication-filter.md), [system-overview §3-3](../architecture/system-overview.md), [01-auth-onboarding](../api/specs/01-auth-onboarding.md) |

## Status

Accepted

> [ADR-0003](./0003-jwt-auth-after-oauth-login.md)은 access/refresh 만료를 "정책값"으로만, [ADR-0009](./0009-jwt-signing-algorithm-hs256.md)는 시크릿을 "충분한 길이"로만 남겼다. 토큰 발급/검증 코드에 직접 들어가는 **구체 수치**가 없으면 구현이 막힌다 — 본 ADR이 그 값을 확정한다.

## Context

- **토큰 모델**([ADR-0003](./0003-jwt-auth-after-oauth-login.md)): access는 짧은 만료의 무상태 JWT, 신규 회원에겐 refresh 없는 **온보딩 전용 임시 토큰**만 발급, refresh는 불투명 토큰으로 Redis에 저장하고 TTL = 만료 시각([ADR-0006](./0006-refresh-token-store-redis.md) D1).
- access 만료·refresh 만료·온보딩 임시 토큰 만료의 **수치가 미확정**이고, [01-auth-onboarding](../api/specs/01-auth-onboarding.md)에는 `expiresIn=3600`/`1800` 예시에 "(확인 필요)"가 달려 있다.
- **HS256은 시크릿 길이가 곧 보안 강도**다([ADR-0009](./0009-jwt-signing-algorithm-hs256.md)). HMAC-SHA256은 출력 길이(256bit/32byte) 이상의 키를 권장하며, jjwt는 키가 짧으면 예외를 던진다.
- 환경(로컬·운영)마다 만료값을 조정할 수 있어야 한다.

## Decision

**다음 정책값을 확정하고, 설정(`application.yml`)으로 외부화한다.** 아래는 기본값이다.

1. **access JWT TTL = 3600초(1시간).**
2. **온보딩 전용 임시 토큰 TTL = 1800초(30분)**, refresh 미발급([ADR-0003](./0003-jwt-auth-after-oauth-login.md) D5).
3. **refresh TTL = 14일**(1,209,600초). Redis 키 TTL = 만료 시각이라 만료 시 폐기 레코드까지 자동 소멸([ADR-0006](./0006-refresh-token-store-redis.md)).
4. **refresh 회전 시 새 토큰에 14일을 재부여**(슬라이딩) — 활성 사용자는 재로그인 없이 유지, 장기 미사용은 만료로 재로그인. 절대 상한(슬라이딩 누적 제한)은 운영 후속.
5. **HS256 시크릿**: 최소 **256bit(32byte)** 무작위, Base64 보관. 길이 미달이면 **기동 실패**로 강제한다. `JWT_SECRET` 환경변수 주입(MVP), 추후 AWS Secrets Manager([ADR-0009](./0009-jwt-signing-algorithm-hs256.md)).
6. **refresh 해시 pepper**: access 시크릿과 **분리된 별도 시크릿**(`REFRESH_PEPPER`). `SHA-256(token + pepper)`로 저장([ADR-0006](./0006-refresh-token-store-redis.md) D2).
7. **키 회전 절차**: MVP는 단일 활성 키. 무중단 회전(구·신 키 동시 검증 윈도우)은 운영 후속(별도)으로 남긴다 — 초기 구현은 단일 키로 진행 가능하므로 차단 사항 아님.
8. **외부화**: `app.jwt.access-ttl`, `app.jwt.onboarding-ttl`, `app.jwt.refresh-ttl` 등으로 두고 위 기본값을 채택한다.

## Alternatives

| 대안 | 장점 | 단점 | 채택 |
|---|---|---|---|
| **access 1시간** | 탈취 창과 재발급 빈도의 균형 | — | **채택** |
| access 15분 | 탈취 창 최소 | 재발급 잦음(Redis 부하·UX) | 미채택(운영 데이터로 재조정 여지) |
| access 24시간 | UX 편함 | 무상태라 강제 폐기 어려운데 탈취 창이 큼 | 미채택 |
| **refresh 14일** | 재로그인 빈도와 위험의 균형 | — | **채택** |
| refresh 7일 / 30일 | 7일=안전, 30일=편의 | 7일 재로그인 잦음 / 30일 탈취 위험 | 미채택 |
| 시크릿 256bit | HS256 권장 강도 충족 | — | **채택** |
| 시크릿 384/512bit | 여유 마진 | 이득 미미 | 미채택(불필요) |

## Consequences

- **긍정**: 토큰 발급/검증 구현이 가능해지고, 만료값을 환경별로 조정할 수 있다.
- **부정/트레이드오프**: 만료값은 보안·UX 트레이드오프라 운영 지표(재발급률·탈취 사고)로 재조정이 필요할 수 있다.
- **후속 작업**
  - `application.yml`에 `app.jwt.*` 키 정의, [build.gradle](../../build.gradle) jjwt 배선.
  - [ADR-0003](./0003-jwt-auth-after-oauth-login.md)(만료 정책값)·[ADR-0009](./0009-jwt-signing-algorithm-hs256.md)(시크릿 길이) 후속 닫음.
  - [system-overview §3-3](../architecture/system-overview.md)·[01-auth-onboarding](../api/specs/01-auth-onboarding.md) `expiresIn` 값 확정 반영.

## Validation

- 만료 경계 테스트: access 1시간, 온보딩 30분, refresh 14일 경과 시 동작.
- 시크릿 길이 미달 시 애플리케이션 **기동 실패** 확인.
- Redis refresh 키 TTL이 만료 시각과 일치하고 만료 시 자동 소멸하는지 관측.
