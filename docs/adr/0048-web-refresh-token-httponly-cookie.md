# ADR-0048. 웹 refresh 토큰은 HttpOnly 쿠키로 내리고 같은 엔드포인트에 쿠키 채널을 얹는다

| 항목 | 값 |
|---|---|
| 번호 | ADR-0048 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-08-16 |
| 기준 코드 | `feature/229-web-landlord-auth` @ `86654fb`. 본 ADR의 파일·경로 참조는 전부 이 시점 기준이며, 재검증 없이 인용하지 않는다 |
| 관련 문서 | [ADR-0003](./0003-jwt-auth-after-oauth-login.md), [ADR-0006](./0006-refresh-token-store-redis.md), [ADR-0010](./0010-jwt-authentication-filter.md), [ADR-0011](./0011-token-lifetime-and-secret-policy.md), [ADR-0014](./0014-withdrawal-pii-anonymization.md), [ADR-0021](./0021-cost-optimization-profile.md), [ADR-0022](./0022-dev-https-caddy.md), [ADR-0047](./0047-web-local-credentials-and-phone-based-account-linking.md), [US-1-12](../requirements/user-stories.md), [01-auth-onboarding §개요(웹 임대인 트랙)·§6·§7·§10](../api/specs/01-auth-onboarding.md), [api-design-guide §2-1](../api/api-design-guide.md), [system-overview](../architecture/system-overview.md) |

## Status

Proposed

## Context

[ADR-0003](./0003-jwt-auth-after-oauth-login.md)이 정한 토큰 모델은 **모바일 앱 전제**다 — access는 `Authorization: Bearer` 헤더, refresh는 불투명 토큰을 **응답 본문**으로 주고받는다. 그 ADR의 대안 표는 "헤더 토큰이라 CSRF 표면이 작다"를 채택 근거의 하나로 들었고, `SecurityConfig`가 `csrf.disable()`인 것도 그 전제 위에 서 있다.

이제 **브라우저 클라이언트가 붙는다**([ADR-0047](./0047-web-local-credentials-and-phone-based-account-linking.md)). 브라우저는 앱과 위협 모델이 다르다.

- 앱은 refresh를 Keychain/Keystore에 넣지만, **브라우저에서 JS가 읽을 수 있는 저장소**(`localStorage`·`sessionStorage`·HttpOnly 아닌 쿠키)에 두면 **XSS 한 번에 14일짜리 세션이 통째로 넘어간다.** access가 짧은 것은 위로가 되지 않는다 — 공격자가 refresh를 들면 원하는 만큼 새 access를 뽑는다.
- 반대로 브라우저는 쿠키를 **자동으로 붙이는** 클라이언트라, 쿠키를 쓰면 크로스사이트 위조(CSRF) 표면이 새로 열린다. 지금 서버에는 CSRF 방어가 없다.

한편 refresh의 **회전·재사용 탐지·사용자 전체 무효화**는 [ADR-0006](./0006-refresh-token-store-redis.md)이 정한 보안 규칙이고 `reissue`·`logout` 한 벌에만 존재한다. 앱은 이 계약을 그대로 쓰고 있으며 바뀔 이유가 없다.

따라서 **① 웹 refresh를 어디에 둘지 ② 그 채널을 기존 엔드포인트에 얹을지 새로 팔지 ③ CSRF를 어떻게 다룰지**를 결정해야 한다.

## Decision

**웹 refresh 토큰은 `HttpOnly` 쿠키로 내리고, `reissue`·`logout`은 새 엔드포인트를 파지 않고 같은 경로에서 쿠키 우선·본문 fallback으로 읽는다. access는 종전대로 헤더다.**

### 1. `localStorage`가 아니라 `HttpOnly` 쿠키다

`HttpOnly`면 `document.cookie`로 읽히지 않는다. XSS가 나도 공격자가 할 수 있는 일은 **그 브라우저를 빌려 쓰는 것**까지이고 토큰 자체는 나가지 않는다 — 세션을 자기 기기로 복제해 오래 쓰는 경로가 막힌다. 저장 위치를 바꾸는 것만으로 얻는 방어라 비용이 거의 없다.

**옮기는 것은 refresh 하나뿐이다.** access는 여전히 메모리에 두고 `Authorization: Bearer`로 보낸다 — [ADR-0003](./0003-jwt-auth-after-oauth-login.md)·[ADR-0010](./0010-jwt-authentication-filter.md)의 검증 경로가 그대로다. 웹 로그인·가입 응답 본문에는 **`refreshToken` 필드가 아예 없다.**

### 2. 쿠키 속성 — `Path`를 좁히는 것이 핵심이다

```
Set-Cookie: refreshToken=<opaque>; HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth; Max-Age=1209600
```

| 속성 | 값 | 이유 |
|---|---|---|
| `HttpOnly` | 고정 | §1 |
| `Secure` | `true` | 평문 구간으로 새지 않게 한다. **`application-local.yml`에서만 `false`** 로 내린다(로컬은 http) |
| `SameSite` | `Lax` | 크로스사이트 POST에 쿠키가 실리지 않는다(§5) |
| `Path` | `/api/v1/auth` | 아래 |
| `Max-Age` | `1209600` | §4 |

`Path`를 좁히는 것이 이 절의 요점이다. refresh 쿠키가 매물 조회·사진 업로드·진단 같은 **모든 요청에 실릴 이유가 없다.** `/api/v1/auth` 아래로 좁히면 쿠키가 나가는 요청이 로그인·가입·재발급·로그아웃 정도로 한정되어, 다른 경로의 프록시·로그·확장 프로그램에 refresh가 닿는 표면이 사라진다. 대역폭 절감은 부수 효과일 뿐이다.

값은 `app.auth.web.refresh-cookie`(`name`·`path`·`same-site`·`secure`)로 뺀다. base에 안전한 기본값(`secure: true`)을 두고 로컬 프로파일에서만 내린다 — 반대로 두면 운영에 `false`가 새어 나갈 수 있다.

### 3. `/auth/web/*`를 새로 파지 않는다 — 회전 규칙을 두 벌로 만들지 않는다

`reissue`의 본질은 "토큰을 바꿔 준다"가 아니라 **회전 + 폐기 토큰 재사용 탐지 + 탐지 시 사용자 전체 무효화**다([ADR-0006](./0006-refresh-token-store-redis.md) §3). 이건 보안 규칙이고, 두 벌이 되는 순간 **한쪽만 고친 버그가 조용히 살아남는다.**

웹과 앱이 실제로 다른 것은 **토큰을 어디서 읽고 어디로 내리는가**뿐이다. 그건 전송 채널이지 규칙이 아니고, 컨트롤러 한 겹에서 끝난다. 그래서 기존 엔드포인트에 채널을 얹는다.

**계약 변경**(`POST /api/v1/auth/reissue` · `POST /api/v1/auth/logout`)

- refresh를 **쿠키(`refreshToken`) 우선 · 요청 본문 fallback**으로 읽는다. 본문은 선택(`@RequestBody(required = false)`)이 되고 `ReissueRequest`·`LogoutRequest`의 `@NotBlank`를 푼다.
- 쿠키·본문 **둘 다 없거나 공백**이면 `400 INVALID_INPUT`, `errors[].field = "refreshToken"`.
- 본문 JSON이 **깨졌으면** 종전대로 `400 MALFORMED_REQUEST`.
- 요청이 **쿠키로 왔으면** 회전된 refresh를 다시 쿠키로 내리고, **본문으로 왔으면** 종전대로 본문에 담는다. 응답 모양이 요청 채널을 따라간다.
- `logout`은 쿠키로 왔을 때 **`Max-Age=0` 삭제 쿠키**를 함께 내린다.

**쿠키를 지우는 자리는 로그아웃 하나가 아니다 — 회원 탈퇴(`DELETE /api/v1/users/me`)도 같은 삭제 쿠키를 내린다.** 탈퇴는 서버에서 그 사용자의 refresh를 전부 `REVOKED`로 만들므로(`UserWithdrawnEvent` 구독, [ADR-0014](./0014-withdrawal-pii-anonymization.md)) **보안 구멍은 아니지만**, 지우지 않으면 죽은 쿠키가 최대 14일(§4) 브라우저에 남아 재발급을 재시도하는 화면이 설명 불가능한 `401`을 받는다. 세션을 끊는 두 경로의 동작이 갈릴 이유가 없다.

다만 **탈퇴는 조건 없이 내린다** — 로그아웃의 「요청이 쿠키로 왔을 때만」 판정을 그대로 쓰면 항상 거짓이다. 쿠키 `Path`가 `/api/v1/auth`로 좁혀져(§2) 브라우저가 `/api/v1/users/me` 요청에 refresh 쿠키를 **애초에 싣지 않기** 때문이다. 경로를 좁힌 것의 대가이며, 요청만 봐서는 보유 여부를 알 수 없으니 항상 내리는 쪽이 옳다. `Set-Cookie`의 `Path`는 요청 경로와 무관하게 지정할 수 있어(`Domain`과 다르다) 다른 경로의 응답으로 `/api/v1/auth` 쿠키를 지우는 것은 정상 동작이고, **쿠키를 가진 적 없는 앱 클라이언트에는 아무 영향이 없다**(`Max-Age=0`은 「지금 만료」라 지울 것이 없다). 응답 본문·status·에러 계약이 그대로라 하위 호환이다.

> **누가 그 헤더를 쓰는가** — 탈퇴 엔드포인트는 `user` 모듈(`UserController`)에 있고 refresh 쿠키는 `auth` 채널의 관심사지만, `RefreshTokenCookies`는 `auth`가 아니라 **공유 커널 `common.security`** 에 있다(§2 — 쿠키는 도메인 규칙이 아니라 HTTP 전송 수단이라 `JwtTokenService` 옆에 뒀다). `user`의 허용 의존은 `{"common"}`이고 `common`은 OPEN 모듈이라 경계 위반이 아니며, 그 컨트롤러는 이미 같은 패키지의 `AuthPrincipal`을 쓰고 있다. **auth의 `UserWithdrawnEventListener`가 내리는 대안은 기술적으로 가능하지만**(이벤트가 같은 요청 스레드에서 동기 발행되므로 `RequestContextHolder`로 응답에 닿는다) 채택하지 않았다 — 응용 계층이 서블릿 타입을 만지게 되고, 그 리스너가 예고한 대로 `@ApplicationModuleListener`(비동기)로 바뀌는 순간 **삭제 쿠키가 조용히 증발**하며, 리스너는 탈퇴 트랜잭션 안이라 롤백되면 일어나지 않은 탈퇴의 삭제 쿠키가 나간다. 컨트롤러는 서비스가 커밋을 마치고 돌아온 뒤에 헤더를 쓰므로 그 창이 없다.

**앱은 항상 본문에 refresh를 담아 보내므로 정상 경로의 동작이 바뀌지 않는다.** 하위 호환이 깨지지 않으니 [api-design-guide §2-1](../api/api-design-guide.md)의 v2 기준에 미달한다 — **v1을 유지한다.**

다만 **잘못된 요청 하나의 에러 코드가 바뀐다**: 본문 없는 `reissue`·`logout`이 `MALFORMED_REQUEST`에서 `INVALID_INPUT`이 된다. 원래 거절되던 요청이고 새 코드가 더 정확하다(본문이 깨진 것이 아니라 값이 빠진 것이다). 계약 파기로 보지 않되, 기존 REST Docs 스니펫 두 개(`auth-reissue-malformed`·`auth-logout-malformed`)와 `AuthDocsFields`의 에러코드 목록 상수를 함께 갱신한다.

### 4. TTL은 앱과 같은 14일이다

`app.auth.refresh-ttl-seconds`(`1209600`, [ADR-0011](./0011-token-lifetime-and-secret-policy.md))를 그대로 쓰고 **새 설정키를 만들지 않는다.** 쿠키 `Max-Age`가 그 값을 따라간다.

공용 PC를 이유로 웹만 짧게 두는 선택지가 있었으나 채택하지 않는다. 값이 갈리면 **"쿠키 `Max-Age`와 서버 TTL 중 무엇이 진짜 만료인가"가 두 벌**이 되어 어긋날 여지가 생기고, 웹 세션만 자주 끊겨 로그인 빈도가 오른다. 회전·재사용 탐지가 이미 걸려 있고 로그아웃이 즉시 무효화하므로 TTL 단축으로 더 얻는 것이 적다. 공용 PC 위험은 `Max-Age`가 아니라 로그아웃 유도로 다룬다.

### 5. CSRF — 동일 오리진 + `SameSite=Lax`를 근거로 `csrf.disable()`을 유지한다

`SameSite=Lax`면 **크로스사이트에서 시작된 POST에 쿠키가 실리지 않는다.** `reissue`·`logout`·`login`·`signup`이 전부 `POST`라 Lax가 열어 두는 예외(top-level GET 내비게이션)에 걸리지 않는다. 즉 공격자 페이지가 브라우저를 시켜 재발급·로그아웃을 부를 수 없다.

웹과 API가 **같은 오리진**이면 CORS 설정도 필요 없다. 그래서 **CSRF 토큰을 구현하지 않고 `SecurityConfig`의 `csrf.disable()`을 그대로 둔다.**

### 6. 전제 조건 — 동일 오리진에는 아직 인프라 근거가 없다

> **§5의 안전성은 배포 형태에 통째로 의존하는데, 그 배포 형태가 이 저장소 어디에도 없다.**
>
> - [docker-compose.yml](../../docker-compose.yml)의 서비스는 `mysql`·`mongo`·`redis`·`minio`·`minio-init`·`mailhog`·`app` 일곱이고 **웹 클라이언트 서비스가 없다.**
> - dev Caddy([Caddyfile.tftpl](../../infra/terraform/modules/dev/host/Caddyfile.tftpl))는 `${caddy_site} { reverse_proxy app:8080 }` 한 줄이다([ADR-0022](./0022-dev-https-caddy.md)·[ADR-0021](./0021-cost-optimization-profile.md)). **웹 정적 자산을 서빙하는 경로도, 별도 업스트림도 선언돼 있지 않다.**
>
> 즉 "동일 오리진"은 **합의된 전제일 뿐 배포로 증명된 사실이 아니다.**

웹이 다른 오리진(별도 도메인·서브도메인·S3/CloudFront 등)으로 배포되면 두 가지가 순서대로 일어난다.

1. **먼저 깨진다.** `SameSite=Lax`가 크로스사이트 요청에 쿠키를 싣지 않아 재발급이 아예 동작하지 않는다.
2. **고치려다 뚫린다.** 그걸 고치려고 `SameSite=None; Secure`로 내리는 순간 **`csrf.disable()` + 쿠키로 실리는 refresh**가 그대로 CSRF 구멍이 된다 — 공격자 페이지가 브라우저를 시켜 `/api/v1/auth/reissue`·`/logout`을 부를 수 있고, 브라우저는 쿠키를 자동으로 붙인다.

> **배포 전 체크(이 ADR을 먼저 개정한다).** 웹을 별도 오리진에 올리려면 아래 셋을 **함께** 처리해야 하며, 하나라도 빠지면 깨지거나 뚫린다.
> 1. CSRF 방어 도입 — `CookieCsrfTokenRepository` 기반 토큰, 또는 최소한 `Origin`/`Sec-Fetch-Site` 검사.
> 2. CORS 화이트리스트(`allowCredentials=true` + 오리진 명시. 와일드카드는 쓸 수 없다).
> 3. 쿠키를 `SameSite=None; Secure`로 전환.

**코드가 이 전제를 강제하지 않는다 — 문서로 지킨다.** 그래서 여기에 못박는다.

## Alternatives

| 대안 | 장점 | 단점 | 채택 안 한 이유 |
|---|---|---|---|
| **A. `HttpOnly` 쿠키 + 기존 엔드포인트에 채널 추가(채택)** | XSS로 refresh가 유출되지 않고, 회전·재사용 탐지가 한 벌로 남으며, 앱 계약이 그대로다 | 쿠키라 CSRF 표면이 열리고 그 방어가 배포 전제에 의존한다(§6) | — (채택) |
| B. `localStorage`에 두고 본문 방식 그대로 | 서버 변경이 0이다 | **XSS 한 번에 14일 세션이 유출**된다. `HttpOnly`로 막을 수 있는 것을 안 막는 선택이다 | 브라우저를 붙이며 생기는 가장 큰 새 위험을 정면으로 받는다 |
| C. `/api/v1/auth/web/reissue`·`/web/logout` 신설 | 앱·웹 계약이 완전히 분리되고 각자 단순하다 | **회전·재사용 탐지·전체 무효화 규칙이 두 벌**이 된다 | 보안 규칙의 중복은 한쪽만 고친 버그로 되돌아온다(§3) |
| D. `/api/v2/auth/reissue`로 버전을 올린다 | 계약이 깔끔하게 갈린다 | 앱 동작이 안 바뀌는데 앱까지 v2로 이전시켜야 하고 그동안 두 버전을 유지한다 | 하위 호환이 깨지지 않아 v2 기준([api-design-guide §2-1](../api/api-design-guide.md))에 미달한다 |
| E. access도 쿠키로 내린다 | 웹 프론트가 토큰을 아예 만지지 않는다 | [ADR-0003](./0003-jwt-auth-after-oauth-login.md)의 헤더 모델과 갈라지고, **모든 API 경로에 쿠키가 실려 CSRF 표면이 서비스 전체로 넓어진다**(§2의 `Path` 좁히기가 불가능해진다) | refresh 하나만 옮겨도 XSS 위험의 대부분이 사라진다 |
| F. 지금 CSRF 토큰을 도입한다 | 오리진이 갈라져도 안전하다 | 동일 오리진에서는 순수 비용이고, 앱 요청 경로까지 영향을 받는다 | 전제가 깨질 때 도입한다 — 대신 **그 판단 시점을 §6에 못박았다** |

## Consequences

- **긍정**
  - 웹 refresh가 **JS에서 읽히지 않는다.** XSS의 피해가 그 브라우저 세션으로 한정된다.
  - 회전·재사용 탐지·전체 무효화 규칙이 **한 벌로 남는다.** 웹이 붙어도 [ADR-0006](./0006-refresh-token-store-redis.md)의 계약이 그대로다.
  - **앱 정상 경로가 바뀌지 않아 v1을 유지**한다. 클라이언트 배포 순서 제약이 없다.
  - 새 TTL 설정키 없이 기존 값을 그대로 쓴다.
  - `Path=/api/v1/auth`라 refresh가 대부분의 요청에 실리지 않는다.
  - [system-overview](../architecture/system-overview.md) 구성도에 **웹 클라이언트가 앱과 나란히 반영됐다** — 이 쿠키 채널이 어느 클라이언트를 위한 것인지 그림에서 바로 드러난다.
- **부정/트레이드오프**
  - **동일 오리진 전제에 인프라 근거가 없다**(§6). 이 결정의 안전성을 배포 형태가 좌우하는데 그 형태가 아직 존재하지 않는다. **이 ADR의 가장 큰 미결이며, 웹 배포 방식이 정해지는 순간 제일 먼저 확인할 항목이다.**
  - **한 엔드포인트가 두 모양의 응답을 낸다**(쿠키 or 본문). 스펙·REST Docs 스니펫을 두 벌 유지해야 한다.
  - **본문 없는 `reissue`·`logout`의 에러 코드가 바뀐다**(`MALFORMED_REQUEST` → `INVALID_INPUT`). 기존 스니펫 둘과 `AuthDocsFields` 상수를 갱신한다.
  - **로컬 프로파일 오버라이드를 빠뜨리면 로컬에서만 조용히 깨진다.** http에 `Secure=true` 쿠키는 브라우저가 저장하지 않아 재발급이 안 되는데, 서버 로그에는 "refresh 없음"으로만 남는다.
  - **`Path`가 `/api/v1/auth`에 고정된다.** 나중에 `/api/v2/auth`가 생기면 그 경로로는 쿠키가 실리지 않는다 — 버전을 올릴 때 함께 봐야 한다. 같은 이유로 **`/api/v1/auth` 밖에서 세션을 끊는 엔드포인트는 요청 쿠키를 볼 수 없어 삭제 쿠키를 조건 없이 내려야 한다**(§3의 탈퇴). 그런 엔드포인트가 늘어나면 "쿠키를 지우는 자리"가 흩어진다 — 지금은 로그아웃·탈퇴 둘이고, 셋째가 생기면 공통화를 다시 검토한다.
  - **다중 탭 재발급이 재사용 탐지에 걸릴 수 있다.** 쿠키는 브라우저가 자동으로 붙이므로 프론트가 refresh를 "들고 있지 않고", 두 탭이 거의 동시에 만료를 감지하면 **회전된 옛 토큰이 한 번 더 제출**될 수 있다. [ADR-0006](./0006-refresh-token-store-redis.md) §3의 규칙상 그건 재사용 정황이라 **정상 사용자가 전체 무효화로 로그아웃**된다. 프론트가 재발급을 단일화(탭 간 락·in-flight 공유)해야 한다.
- **후속 작업**
  - 웹을 **별도 오리진**에 올릴 계획이 서면 §6의 체크 셋을 이행하고 이 ADR을 개정한다. 그 전까지는 동일 오리진 배치가 전제다.
  - `docker-compose.yml`·Caddy에 웹 서비스가 실제로 들어오면 오리진을 확정하고 §6을 닫는다.
  - 다중 탭 재발급 직렬화 방식을 프론트와 합의한다.

## Validation

- 웹 로그인·가입 응답 **본문에 `refreshToken`이 없고**, `Set-Cookie`에 `HttpOnly`·`Secure`·`SameSite=Lax`·`Path=/api/v1/auth`·`Max-Age=1209600`이 붙는다.
- 쿠키만 붙이고 **본문 없이** `reissue`를 부르면 `200`이고, 회전된 refresh가 **다시 쿠키로만** 온다(본문에 없다).
- 본문에 `refreshToken`을 담아 `reissue`를 부르면(앱 경로) 종전대로 **본문에** 새 refresh가 오고 `Set-Cookie`가 붙지 않는다.
- 쿠키·본문 둘 다 없으면 `400 INVALID_INPUT` + `errors[].field="refreshToken"`, 깨진 JSON 본문은 `400 MALFORMED_REQUEST`다.
- 쿠키로 `logout`하면 서버에서 refresh가 `REVOKED`로 전이되고 **`Max-Age=0` 삭제 쿠키**가 함께 온다. 이후 그 쿠키로 `reissue`가 실패한다.
- `DELETE /api/v1/users/me`(탈퇴) 응답에도 **같은 속성의 `Max-Age=0` 삭제 쿠키**가 온다 — 요청에 쿠키가 실리지 않는 경로이므로 **채널과 무관하게 항상** 온다(앱 경로 응답에도 붙지만 본문·status는 그대로다).
- 회전된 옛 refresh를 쿠키로 다시 제출하면 [ADR-0006](./0006-refresh-token-store-redis.md)대로 **사용자 전체 무효화**가 일어난다(앱 경로와 같은 결과).
- 로컬 프로파일(http)에서 브라우저가 쿠키를 실제로 저장·전송한다(`secure: false` 오버라이드 확인).
- 기존 앱 통합 테스트(본문 방식 회전·재사용 탐지·로그아웃)가 **그대로 통과한다.**
- **재검토 시점**: 웹 배포 오리진이 확정될 때(§6), `/api/v2/auth`가 생길 때, 다중 탭 재발급이 실사용에서 전체 무효화를 유발할 때.
