# 소셜 로그인 · 온보딩 API Spec

> [api-design-guide](../api-design-guide.md) · [error-response-guide](../error-response-guide.md)를 따른다. 모든 응답은 공통 래퍼.
> 관련 유저 스토리: [user-stories](../../requirements/user-stories.md)

## 개요

소셜 로그인(Apple/Google) 검증 후 서버 자체 JWT(access+refresh)를 발급하고, 신규 회원의 **온보딩 중 이메일 인증**(인증번호 발송·확인), 온보딩 필수정보 수집·약관 동의, 토큰 재발급/로그아웃, 회원 탈퇴, 내 프로필 조회·수정을 다룬다. 인증 헤더는 `Authorization: Bearer <accessToken>`, 토큰 갱신은 `POST /api/v1/auth/reissue`다.

상태 모델: 사용자는 `PENDING`(소셜 검증만 완료) → `TERMS_AGREED`(약관 동의 완료) → `ACTIVE`(온보딩 완료) → `WITHDRAWN`(탈퇴)로 전이한다. **약관 동의와 온보딩은 분리된 단계**로, 약관 동의(`POST /auth/terms`)가 온보딩 제출(`POST /auth/onboarding`)을 선행한다.

### 핵심 개념·enum

| 개념 | 값 | 설명 |
| --- | --- | --- |
| 사용자 상태 `status` | `PENDING`, `TERMS_AGREED`, `ACTIVE`, `WITHDRAWN` | 소셜 검증만 완료 → 약관 동의 완료 → 온보딩 완료 → 탈퇴 |
| provider | `APPLE`, `GOOGLE` | 소셜 로그인 제공자 |
| 성별 `gender` | `MALE`, `FEMALE` | 온보딩 필수 |
| 직업 `occupation` | `STUDENT`(학생), `EMPLOYEE`(직장인), `SELF_EMPLOYED`(자영업), `JOB_SEEKER`(구직 중), `ETC`(기타) | 온보딩 필수 · **임시 분류값**(요구사항 드롭다운 항목 미확정 — 확인 필요) |
| 비자정보 `visaType` | `VISA_STUDENT`(유학·연수), `VISA_WORK`(취업), `VISA_RESIDENCE`(거주·가족동반), `VISA_WORKING_HOLIDAY`(워킹홀리데이), `VISA_TOURISM`(관광), `VISA_ETC`(기타) | 온보딩 필수 |
| 국적 `country` | ISO 3166-1 alpha-2 코드(예: `VN`) | 온보딩 필수 · 클라이언트는 국가만 전송, 표시명·국기는 서버가 `countries` 참조로 확보(응답에 `countryName`·`countryFlag` 포함, **`countryFlag`는 국기 이미지 URL**) |
| 이메일 `email` | 이메일 문자열 | 온보딩 필수 · **인증번호로 사전 검증** 필요 |
| 닉네임 `nickname` | `형용사 + 사물` 문자열 | 서버가 자동 배정(사용자 입력·수정 불가), 전역 유니크 |

- 날짜만 표기는 `YYYY-MM-DD`(예: `birthDate`), 시각은 ISO-8601 UTC(예: `2026-06-15T08:30:00Z`).
- enum은 모두 UPPER_SNAKE_CASE 문자열로 노출한다.
- **민감정보(토큰 원문·인증번호 원문·비자정보·이메일)는 로그·타 사용자 노출 시 마스킹**한다(error-response-guide §6). 본인 `GET /users/me`는 이메일을 평문으로 반환한다.
- **토큰 모델**: `accessToken`은 **JWT**(stateless — 매 요청 서명·만료를 검증, 저장 안 함). `refreshToken`은 **불투명(opaque) 랜덤 토큰**으로 발급하고 서버 저장소에 **해시로 보관**한다(회전·재사용 탐지·무효화 목적). 예시의 `rt_…`는 불투명 토큰을, `eyJ…`는 JWT를 나타낸다.

---

## 엔드포인트 요약

| Method | Path | 설명 | 인증 | 성공 status |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/auth/social-login` | 소셜 `idToken` 검증 후 서버 JWT 발급(기존 로그인/신규 온보딩 분기) | 불필요 | 200 |
| POST | `/api/v1/auth/terms` | 약관 동의 제출(이용약관·개인정보처리방침·마케팅), 약관 동의 완료(TERMS_AGREED 전이) | 필수(온보딩 토큰) | 200 |
| POST | `/api/v1/auth/email/verification-code` | 온보딩 중 입력 이메일로 인증번호 발송 | 필수(온보딩 토큰) | 200 |
| POST | `/api/v1/auth/email/verify` | 인증번호 확인 → 이메일 검증 완료 처리 | 필수(온보딩 토큰) | 200 |
| POST | `/api/v1/auth/onboarding` | 온보딩 필수정보 제출(약관 동의·이메일 검증 선행), 가입 완료(ACTIVE 전이) | 필수(온보딩 토큰, TERMS_AGREED) | 200 |
| POST | `/api/v1/auth/reissue` | refresh 토큰으로 access 토큰 재발급 | 불필요(본문 refresh) | 200 |
| POST | `/api/v1/auth/logout` | 현재 세션 refresh 토큰 무효화 | 필수 | 204 |
| GET | `/api/v1/users/me` | 내 프로필 조회 | 필수 | 200 |
| PATCH | `/api/v1/users/me` | 내 프로필 부분 수정 | 필수 | 200 |
| DELETE | `/api/v1/users/me` | 회원 탈퇴(WITHDRAWN 전이, 토큰 일괄 무효화) | 필수 | 204 |

> `auth/onboarding`은 신규 리소스 생성이 아니라 약관 동의를 마친 `TERMS_AGREED` 사용자를 `ACTIVE`로 전이하는 상태 액션이므로 `200`을 쓴다(api-design-guide §1 — "생성 아닌 액션").
> 인증 "필수" 엔드포인트는 access 토큰 만료 시 `401 TOKEN_EXPIRED`로 재발급을 유도한다. **온보딩 토큰**(`ROLE_ONBOARDING` — `onboardingCompleted=false`, 상태 `PENDING`/`TERMS_AGREED` 공통)으로 `GET`/`PATCH /users/me`·`POST /auth/logout`(모두 `ROLE_USER` 필요) 보호 API에 접근하면 `403 AUTH_ONBOARDING_REQUIRED`를 반환한다(단, `DELETE /users/me`(탈퇴)·`POST /auth/terms`(약관 동의)·`POST /auth/email/verification-code`·`POST /auth/email/verify`(이메일 인증)·`POST /auth/onboarding`은 온보딩 흐름이라 온보딩 토큰도 허용). 상태 전이 순서는 `POST /auth/terms`(PENDING→TERMS_AGREED) → `POST /auth/onboarding`(TERMS_AGREED→ACTIVE)이며, 약관 미동의 상태(`PENDING`)에서 온보딩을 제출하면 `422 AUTH_TERMS_AGREEMENT_REQUIRED`다.

---

## 상세

### 1. POST `/api/v1/auth/social-login` — 소셜 로그인/온보딩 분기

앱이 provider(Apple/Google)에서 받은 `idToken`을 서버가 서명·`aud`·`iss`·`exp`로 검증한다. 기존 `ACTIVE` 회원이면 로그인 처리하고 access+refresh 토큰을 발급한다(`status=ACTIVE`, `onboardingRequired=false`). 신규이거나 **가입을 끝내지 못한 회원(`PENDING`·`TERMS_AGREED`)** 이면 온보딩 전용 access 토큰(`onboardingCompleted=false` 클레임)과 `onboardingRequired=true`로 응답한다(refresh 토큰은 발급하지 않음). 신규면 `PENDING` 레코드를 새로 만든다.

응답의 **`status`로 클라이언트가 다음 화면을 분기**한다 — `PENDING`(소셜 로그인만 하고 약관 미동의)이면 **약관 동의 화면(§2)**, `TERMS_AGREED`(약관 동의했으나 온보딩 미완료)이면 **온보딩 화면(§5)**, `ACTIVE`이면 홈. 온보딩 토큰으로는 `GET /users/me`(ROLE_USER)가 `403`이라 상태를 따로 조회할 수 없으므로, 재개 지점은 이 응답의 `status`로 판단한다.

- **인증**: 불필요.
- Path/Query 파라미터: 없음.

#### Request Body

```json
{
  "provider": "GOOGLE",
  "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6..."
}
```

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `provider` | string(enum) | 필수 | `APPLE` \| `GOOGLE` 중 하나(누락은 `INVALID_INPUT`, 허용 외 값은 역직렬화 실패로 `MALFORMED_REQUEST`) |
| `idToken` | string | 필수 | provider 발급 OIDC ID 토큰. 빈 문자열 불가 |

#### 성공 Response — 기존 회원(ACTIVE) (200 OK)

```json
{
  "success": true,
  "data": {
    "onboardingRequired": false,
    "status": "ACTIVE",
    "tokenType": "Bearer",
    "accessToken": "eyJ...access",
    "refreshToken": "rt_9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2",
    "expiresIn": 3600
  },
  "error": null
}
```

#### 성공 Response — 신규·미완료 회원(PENDING·TERMS_AGREED) (200 OK)

```json
{
  "success": true,
  "data": {
    "onboardingRequired": true,
    "status": "PENDING",
    "tokenType": "Bearer",
    "accessToken": "eyJ...onboarding-scope",
    "refreshToken": null,
    "expiresIn": 1800
  },
  "error": null
}
```

> 신규 가입과 약관 미동의 상태로 재로그인한 회원은 `status="PENDING"`(→ 약관 동의 화면 §2). 약관까지 동의하고 온보딩만 못 끝낸 채 재로그인한 회원은 같은 형태로 `status="TERMS_AGREED"`(→ 온보딩 화면 §5)를 받는다. 두 경우 모두 `onboardingRequired=true`·`refreshToken=null`이다.

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `onboardingRequired` | boolean | 가입 미완료 여부(`status != ACTIVE`). 편의 플래그 |
| `status` | string(enum) | 사용자 현재 상태 `PENDING` \| `TERMS_AGREED` \| `ACTIVE`. 클라이언트는 이 값으로 다음 화면을 분기(PENDING→약관 동의 §2, TERMS_AGREED→온보딩 §5, ACTIVE→홈) |
| `refreshToken` | string \| null | `ACTIVE` 로그인에서만 발급, 미완료(`PENDING`/`TERMS_AGREED`)는 `null` |

> `expiresIn`은 access 토큰 만료까지의 초(seconds). 미완료 회원에게 주는 access 토큰은 온보딩 흐름(약관 동의·이메일 인증·온보딩) API만 통과시킨다(클레임 `onboardingCompleted=false`, refresh 미발급). 온보딩 전용 임시 토큰 만료 1800초(30분), 정식 access 3600초(1시간) — [ADR-0011](../../adr/0011-token-lifetime-and-secret-policy.md)에서 확정.

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `provider` 누락(null), `idToken` 누락/빈값 (Bean Validation: `@NotNull`/`@NotBlank`) |
| 400 | `MALFORMED_REQUEST` | JSON 파싱 불가/타입 불일치. **`provider`가 허용 외 enum 문자열(`APPLE`/`GOOGLE` 외)이면 역직렬화 단계에서 거부되어 이 코드로 처리**된다 |
| 401 | `AUTH_INVALID_SOCIAL_TOKEN` | 소셜 `idToken`의 서명/`aud`/`iss`/`exp` 검증 실패. **provider JWKS 조회 실패 등 OIDC 연동 오류도 현재 구현은 이 코드로 통합 처리**한다(아래 노트) |

> **연동 실패 처리(현행)**: `OidcTokenVerifierImpl`은 JWKS 조회 실패·provider 응답 오류를 포함한 모든 OIDC 검증 실패를 `401 AUTH_INVALID_SOCIAL_TOKEN`으로 변환한다. 따라서 이 엔드포인트는 `502 UPSTREAM_ERROR`/`503 SERVICE_UNAVAILABLE`를 반환하지 않는다(시퀀스 [US-1-1](../../architecture/sequence-diagrams/01-auth-onboarding/us-1-1-social-login.md)·REST Docs 스니펫과 정합). 외부 연동 견고화(타임아웃·재시도·서킷브레이커) 도입 시 연동 실패를 `502`/`503`으로 분리하는 것을 검토한다([error-response-guide](../error-response-guide.md) §3).

---

### 2. POST `/api/v1/auth/terms` — 약관 동의

소셜 로그인 후 `PENDING` 사용자가 약관에 동의해 가입 흐름의 첫 단계를 마친다. 성공 시 `TERMS_AGREED`로 전이하고, 서버가 동의 시각(`agreedAt`)과 약관 버전(`termsVersion`)을 기록한다([ADR-0012](../../adr/0012-terms-version-management.md)). 이 단계 이후에야 온보딩 정보 제출(§5)이 가능하다.

- **인증**: 필수 — 소셜 로그인 단계에서 받은 온보딩 토큰(`onboardingCompleted=false`). 토큰은 갱신하지 않는다(상태만 전이).
- Path/Query 파라미터: 없음.

#### Request Body

```json
{
  "termsOfServiceAgreed": true,
  "privacyPolicyAgreed": true,
  "marketingAgreed": false
}
```

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `termsOfServiceAgreed` | boolean | 필수 | 이용약관 동의. `false`면 `AUTH_REQUIRED_AGREEMENT_MISSING`(422) |
| `privacyPolicyAgreed` | boolean | 필수 | 개인정보처리방침 동의. `false`면 `AUTH_REQUIRED_AGREEMENT_MISSING`(422) |
| `marketingAgreed` | boolean | 선택 | 마케팅 수신 동의(기본 `false`). 세분화된 마케팅 동의 항목은 고도화 예정(확인 필요) |

> `termsVersion`은 클라이언트가 보내지 않고 서버가 설정값(`app.terms.version`)을 기록한다. 약관 버전·문구 변경 시 재동의 정책은 [ADR-0012](../../adr/0012-terms-version-management.md).

#### 성공 Response — 200 OK

```json
{
  "success": true,
  "data": {
    "status": "TERMS_AGREED",
    "termsOfServiceAgreed": true,
    "privacyPolicyAgreed": true,
    "marketingAgreed": false,
    "agreedAt": "2026-06-15T08:25:00Z"
  },
  "error": null
}
```

> `PENDING`의 **최초 동의**만 `TERMS_AGREED`로 전이한다. 이미 `TERMS_AGREED`인 사용자가 (네트워크 재시도 등으로) 다시 호출하면 상태·동의를 바꾸지 않고 멱등하게 현재 상태(`200`)를 반환한다 — 의도적 재동의가 아닌 중복 요청 방어다. 동의 후 **마케팅 수신 동의 변경은 `PATCH /users/me`(§9)** 로 처리하며, 약관 버전 변경에 따른 재동의 정책은 [ADR-0012](../../adr/0012-terms-version-management.md)(확인 필요).

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `termsOfServiceAgreed`/`privacyPolicyAgreed` 누락(`@NotNull`) |
| 400 | `MALFORMED_REQUEST` | JSON 파싱 불가/타입 불일치 |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 온보딩 토큰 누락/위조 / 만료 |
| 409 | `AUTH_ONBOARDING_ALREADY_COMPLETED` | 이미 온보딩 완료(`ACTIVE`)된 사용자의 약관 동의 재요청 |
| 422 | `AUTH_REQUIRED_AGREEMENT_MISSING` | 필수 약관(이용약관/개인정보처리방침) 미동의 |

---

### 3. POST `/api/v1/auth/email/verification-code` — 이메일 인증번호 발송

온보딩 중인 사용자가 입력한 이메일 주소로 인증번호를 발송한다. **약관 동의(§2, `TERMS_AGREED`)가 선행**되어야 한다 — 약관 미동의(`PENDING`)면 `422 AUTH_TERMS_AGREEMENT_REQUIRED`로 거절하고 약관 동의(§2)를 먼저 유도한다. 같은 사용자에 미검증 인증 시도가 남아 있으면 새 인증번호로 대체한다. 인증번호는 서버에 **해시로만 보관**하고 일정 시간(예: 5분 — 확인 필요) 후 만료한다. 재발송은 레이트리밋으로 보호한다.

메일은 아웃바운드 포트 `VerificationEmailSender`(인프라 어댑터: SES/SMTP — 확인 필요)로 **동기 발송**하며, **발송에 성공한 뒤에만** 인증번호 챌린지를 저장한다. provider 장애·타임아웃 등 발송 실패 시 챌린지를 만들지 않고 `502 UPSTREAM_ERROR`로 응답해 클라이언트가 재시도하도록 한다(메일 템플릿·다국어, 동기/비동기 정책은 확인 필요).

- **인증**: 필수 — 소셜 로그인 단계에서 받은 온보딩 토큰(`onboardingCompleted=false`).
- Path/Query 파라미터: 없음.

#### Request Body

```json
{
  "email": "minh@example.com"
}
```

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `email` | string | 필수 | 이메일 형식(`@Email`). 빈 문자열 불가 |

#### 성공 Response — 200 OK

```json
{
  "success": true,
  "data": {
    "email": "mi***@example.com",
    "expiresIn": 300
  },
  "error": null
}
```

> `expiresIn`은 인증번호 만료까지의 초(seconds). `email`은 마스킹해 반환한다. 인증번호 원문은 응답·로그에 노출하지 않는다.

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `email` 누락/빈값/형식 위반(`@NotBlank`/`@Email`) |
| 400 | `MALFORMED_REQUEST` | JSON 파싱 불가/타입 불일치 |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 온보딩 토큰 누락/위조 / 만료 |
| 422 | `AUTH_TERMS_AGREEMENT_REQUIRED` | 약관 미동의(`PENDING`) 상태의 요청(약관 동의 §2 선행 필요) |
| 409 | `AUTH_ONBOARDING_ALREADY_COMPLETED` | 이미 온보딩 완료(ACTIVE)된 사용자의 요청(이메일 인증은 온보딩 단계 전용) |
| 429 | `TOO_MANY_REQUESTS` | 재발송 레이트리밋 초과(확인 필요: 임계값) |
| 502 | `UPSTREAM_ERROR` | 메일 발송 실패(provider 장애·타임아웃). 챌린지 미저장, 클라이언트 재시도 유도(공통 코드 — [error-response-guide](../error-response-guide.md) §3) |

---

### 4. POST `/api/v1/auth/email/verify` — 이메일 인증번호 확인

발송된 인증번호를 검증한다. 성공하면 해당 사용자의 이메일을 **검증 완료(VERIFIED)** 로 표시하고, 이후 온보딩 제출 시 같은 이메일을 통과시킨다. 검증 시도는 횟수 상한으로 보호한다.

> **챌린지 부재(미발송·만료·이미 검증)**: 해당 사용자의 인증 챌린지(`email-verify:code:{userId}`)가 없으면 — 인증번호를 한 번도 요청하지 않았거나, TTL 만료, 이미 검증 완료로 소멸, 발송 실패(`502`)로 미저장 — 올릴 `attempts` 레코드 자체가 없으므로 **즉시 `422 AUTH_EMAIL_VERIFICATION_FAILED`** 로 거절하고 인증번호 (재)요청(§3)을 유도한다. `attempts`는 **챌린지가 존재하는데 코드가 불일치**할 때만 증가하며, 상한 초과 시 `429 TOO_MANY_REQUESTS`다.

- **인증**: 필수 — 온보딩 토큰(`onboardingCompleted=false`).
- Path/Query 파라미터: 없음.

#### Request Body

```json
{
  "email": "minh@example.com",
  "code": "482915"
}
```

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `email` | string | 필수 | 인증번호를 발송한 이메일과 일치해야 함 |
| `code` | string | 필수 | 발송된 인증번호. 빈 문자열 불가 |

#### 성공 Response — 200 OK

```json
{
  "success": true,
  "data": {
    "email": "mi***@example.com",
    "verified": true
  },
  "error": null
}
```

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `email`/`code` 누락/빈값 |
| 400 | `MALFORMED_REQUEST` | JSON 파싱 불가/타입 불일치 |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 온보딩 토큰 누락/위조 / 만료 |
| 422 | `AUTH_EMAIL_VERIFICATION_FAILED` | 코드 불일치, 또는 챌린지 부재(미발송·만료·이미 검증) — 부재 시 `attempts` 증가 없이 즉시 거절 |
| 429 | `TOO_MANY_REQUESTS` | 챌린지 존재 + 코드 불일치 누적으로 검증 시도 상한 초과(확인 필요: 임계값) |

---

### 5. POST `/api/v1/auth/onboarding` — 온보딩 제출(가입 완료)

`TERMS_AGREED` 사용자가 필수 프로필을 제출해 가입을 완료한다. **약관 동의(§2)와 이메일 인증(§3·§4)이 선행**되어야 한다 — 약관 미동의(`PENDING`)면 `422 AUTH_TERMS_AGREEMENT_REQUIRED`, 제출 `email`이 미검증·불일치면 `422 AUTH_EMAIL_NOT_VERIFIED`. 성공 시 `ACTIVE`로 전이하고, 닉네임을 자동 배정하며 정식 access/refresh 토큰을 발급한다. 사용자 단위로 멱등 처리해 동시 요청은 한 건만 성공한다.

> 약관 동의·`termsVersion`은 §2(약관 동의)에서 이미 기록되므로 이 요청 본문에는 약관 필드를 담지 않는다. `nickname`은 서버가 형용사 풀·사물 풀의 active 단어에서 골라 `형용사 + 사물`로 조합하고 전역 유니크를 보장(충돌 시 재조합 재시도, 상한 초과 시 fallback 예: 숫자 접미사)해 자동 배정하므로 요청 본문에 담지 않는다(사용자 입력·수정 불가). `email`은 §3·§4로 검증 완료된 값과 일치해야 한다. 응답의 `countryName`·`countryFlag`는 서버가 `country`(코드)로 `countries`에서 resolve한 값이다(저장은 `country` 코드만). `countryFlag`는 **국기 이미지 URL**(flagcdn.com SVG)이다.

- **인증**: 필수 — 소셜 로그인 단계에서 받은 온보딩 토큰(`onboardingCompleted=false`). 상태는 `TERMS_AGREED`여야 한다.
- Path/Query 파라미터: 없음.

#### Request Body

```json
{
  "firstName": "Minh",
  "lastName": "Nguyen",
  "gender": "MALE",
  "birthDate": "1998-04-12",
  "country": "VN",
  "occupation": "STUDENT",
  "email": "minh@example.com",
  "visaType": "VISA_STUDENT"
}
```

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `firstName` | string | 필수 | 이름. 빈 문자열 불가 |
| `lastName` | string | 필수 | 성. 빈 문자열 불가 |
| `gender` | string(enum) | 필수 | `MALE` \| `FEMALE` |
| `birthDate` | string(date) | 필수 | `YYYY-MM-DD`, 과거 날짜만 허용(미래 불가) |
| `country` | string | 필수 | 국적 ISO 3166-1 alpha-2 코드(예: `VN`). `countries`에 존재해야 함(없으면 `INVALID_INPUT`) |
| `occupation` | string(enum) | 필수 | `STUDENT` \| `EMPLOYEE` \| `SELF_EMPLOYED` \| `JOB_SEEKER` \| `ETC`(임시 분류값 — 확인 필요) |
| `email` | string | 필수 | 이메일 형식. **§3·§4로 사전 검증된 값과 일치**해야 함(미검증·불일치 `AUTH_EMAIL_NOT_VERIFIED` 422) |
| `visaType` | string(enum) | 필수 | `VISA_STUDENT` \| `VISA_WORK` \| `VISA_RESIDENCE` \| `VISA_WORKING_HOLIDAY` \| `VISA_TOURISM` \| `VISA_ETC` |

> 약관 동의(`termsOfServiceAgreed`·`privacyPolicyAgreed`·`marketingAgreed`)는 이 요청에 포함하지 않는다 — 앞선 `POST /auth/terms`(§2)에서 처리·기록된다.

#### 성공 Response — 200 OK

```json
{
  "success": true,
  "data": {
    "user": {
      "id": 1024,
      "firstName": "Minh",
      "lastName": "Nguyen",
      "nickname": "BraveOtter",
      "gender": "MALE",
      "birthDate": "1998-04-12",
      "country": "VN",
      "countryName": "Vietnam",
      "countryFlag": "https://flagcdn.com/vn.svg",
      "occupation": "STUDENT",
      "email": "minh@example.com",
      "visaType": "VISA_STUDENT",
      "status": "ACTIVE",
      "marketingAgreed": false,
      "createdAt": "2026-06-15T08:30:00Z"
    },
    "tokenType": "Bearer",
    "accessToken": "eyJ...access",
    "refreshToken": "rt_9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2",
    "expiresIn": 3600
  },
  "error": null
}
```

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | 필드 누락/형식·enum·날짜 위반(`gender`/`visaType`/`occupation` 불일치, `birthDate` 형식·미래, `firstName`/`lastName`/`country`/`email` 빈값·형식 등) |
| 400 | `MALFORMED_REQUEST` | JSON 파싱 불가/타입 불일치 |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 누락/위조 / 만료 |
| 409 | `AUTH_ONBOARDING_ALREADY_COMPLETED` | 이미 `ACTIVE`인 사용자의 온보딩 재요청(동시 요청 포함) |
| 422 | `AUTH_TERMS_AGREEMENT_REQUIRED` | 약관 미동의(`PENDING`) 상태에서 온보딩 제출(약관 동의 §2 선행 필요) |
| 422 | `AUTH_EMAIL_NOT_VERIFIED` | 제출 `email`이 미검증이거나 검증한 이메일과 불일치 |

---

### 6. POST `/api/v1/auth/reissue` — 토큰 재발급

유효한 refresh 토큰으로 새 access 토큰을 재발급한다. 항상 회전한다 — 새 refresh 토큰도 함께 발급하고 제출한 refresh는 무효화(ROTATED)한다([ADR-0006](../../adr/0006-refresh-token-store-redis.md)). 폐기된 토큰을 다시 제출하는 재사용이 탐지되면 해당 사용자의 모든 refresh 토큰을 무효화한다.

- **인증**: 불필요(헤더 access 토큰 없이 본문 refresh 토큰으로 처리). 만료된 access 토큰 보유 클라이언트가 이 엔드포인트로 갱신한다.
- Path/Query 파라미터: 없음.

#### Request Body

```json
{
  "refreshToken": "rt_9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2"
}
```

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `refreshToken` | string | 필수 | 서버가 발급·보관(해시) 중인 **불투명(opaque) refresh 토큰**. 빈 문자열 불가 |

#### 성공 Response — 200 OK

```json
{
  "success": true,
  "data": {
    "tokenType": "Bearer",
    "accessToken": "eyJ...new-access",
    "refreshToken": "rt_3b1e7c5a2f9d04e8b6c1a07f5d2e93b4c8a16f0d",
    "expiresIn": 3600
  },
  "error": null
}
```

> reissue는 항상 회전한다: 제출한 refresh는 무효화(ROTATED)하고 새 access·refresh를 함께 발급한다([ADR-0006](../../adr/0006-refresh-token-store-redis.md)).

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `refreshToken` 누락/빈값 |
| 400 | `MALFORMED_REQUEST` | JSON 파싱 불가/타입 불일치 |
| 401 | `AUTH_INVALID_REFRESH_TOKEN` | refresh 토큰 만료/위조/무효화/재사용 탐지 |

---

### 7. POST `/api/v1/auth/logout` — 로그아웃

전달된 refresh 토큰을 서버에서 무효화해 더는 재발급에 쓰지 못하게 한다. 이미 무효화된 토큰이면 멱등하게 `204`로 처리한다.

- **인증**: 필수.
- Path/Query 파라미터: 없음.

#### Request Body

```json
{
  "refreshToken": "rt_9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2"
}
```

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `refreshToken` | string | 필수 | 무효화할 refresh 토큰. 빈 문자열 불가 |

#### 성공 Response — 204 No Content

본문 없음. 이미 무효화된 토큰으로 재호출해도 멱등하게 `204`를 반환한다.

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `refreshToken` 누락/빈값 |
| 400 | `MALFORMED_REQUEST` | JSON 파싱 불가/타입 불일치 |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | access 토큰 누락/위조 / 만료 |
| 403 | `AUTH_ONBOARDING_REQUIRED` | 온보딩 미완료(PENDING·TERMS_AGREED) 토큰으로 접근(logout은 `ROLE_USER` 필요) |

---

### 8. GET `/api/v1/users/me` — 내 프로필 조회

인증된 본인의 프로필을 조회한다.

- **인증**: 필수(ACTIVE 사용자). PENDING 토큰 접근은 `403 AUTH_ONBOARDING_REQUIRED`.
- Path/Query 파라미터: 없음.

#### 성공 Response — 200 OK

```json
{
  "success": true,
  "data": {
    "id": 1024,
    "firstName": "Minh",
    "lastName": "Nguyen",
    "nickname": "BraveOtter",
    "gender": "MALE",
    "birthDate": "1998-04-12",
    "country": "VN",
    "countryName": "Vietnam",
    "countryFlag": "https://flagcdn.com/vn.svg",
    "occupation": "STUDENT",
    "email": "minh@example.com",
    "visaType": "VISA_STUDENT",
    "status": "ACTIVE",
    "termsOfServiceAgreed": true,
    "privacyPolicyAgreed": true,
    "marketingAgreed": false,
    "createdAt": "2026-06-15T08:30:00Z"
  },
  "error": null
}
```

> 본인 프로필이므로 `email`은 평문으로 반환한다(로그·타 사용자 노출 시에만 마스킹).

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 누락/위조 / 만료 |
| 403 | `AUTH_ONBOARDING_REQUIRED` | 온보딩 미완료(PENDING·TERMS_AGREED) 토큰으로 접근 |
| 404 | `USER_NOT_FOUND` | 사용자가 `WITHDRAWN`이거나 삭제되어 없음 |

---

### 9. PATCH `/api/v1/users/me` — 내 프로필 부분 수정

본인 프로필을 부분 수정한다. 전송한 필드만 변경하고, 미전송 필드는 유지한다(미전송 ≠ 값 비움 — 현재 수정 대상 필드는 비움 불가).

- **인증**: 필수(ACTIVE 사용자). PENDING 토큰 접근은 `403 AUTH_ONBOARDING_REQUIRED`.
- Path/Query 파라미터: 없음.

#### Request Body (모든 필드 선택)

```json
{
  "country": "KR",
  "occupation": "EMPLOYEE",
  "visaType": "VISA_WORK",
  "marketingAgreed": true
}
```

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `firstName` | string | 선택 | 이름 |
| `lastName` | string | 선택 | 성 |
| `gender` | string(enum) | 선택 | `MALE` \| `FEMALE` |
| `birthDate` | string(date) | 선택 | `YYYY-MM-DD`, 과거 날짜만 |
| `country` | string | 선택 | 국적 ISO 코드(예: `KR`). `countries`에 존재해야 함 |
| `occupation` | string(enum) | 선택 | 직업 enum(위 목록과 동일, 임시) |
| `visaType` | string(enum) | 선택 | 비자정보 enum(위 목록과 동일) |
| `marketingAgreed` | boolean | 선택 | 마케팅 수신 동의 |

> 필수 약관 동의(`termsOfServiceAgreed`/`privacyPolicyAgreed`)는 이 엔드포인트로 철회할 수 없다(탈퇴 경로로만 처리). (확인 필요: 동의 철회 정책)
> `nickname`은 시스템 배정값이라 수정 대상이 아니다. `email` 변경은 재인증(§3·§4)이 필요하므로 이 엔드포인트로는 수정하지 않는다(별도 흐름 — 확인 필요).

#### 성공 Response — 200 OK

수정된 프로필 전체를 `GET /users/me`와 동일 스키마의 공통 래퍼로 반환한다.

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `birthDate` 미래(`@Past` 위반), `country` 미존재(`countries`에 없음) 등 값 검증 위반 |
| 400 | `MALFORMED_REQUEST` | JSON 파싱 불가/타입 불일치. **`gender`/`visaType`/`occupation` 허용 외 enum 문자열·`birthDate` 형식 불가**는 역직렬화 단계에서 거부되어 이 코드로 처리(요청 DTO가 enum/날짜 타입이라 매핑 실패 → onboarding(§5)은 String 수집·서버 파싱이라 `INVALID_INPUT`인 점과 다름) |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 누락/위조 / 만료 |
| 403 | `AUTH_ONBOARDING_REQUIRED` | 온보딩 미완료(PENDING·TERMS_AGREED) 토큰으로 접근 |
| 404 | `USER_NOT_FOUND` | 사용자가 `WITHDRAWN`이거나 삭제되어 없음 |

---

### 10. DELETE `/api/v1/users/me` — 회원 탈퇴

본인 계정을 탈퇴 처리한다. 사용자 상태를 `WITHDRAWN`으로 전이하고 모든 refresh 토큰을 무효화한다. PENDING(온보딩 미완료) 사용자도 탈퇴할 수 있다(온보딩 중단·정리 목적).

- **인증**: 필수.
- Path/Query 파라미터: 없음.
- Request Body: 없음.

#### 성공 Response — 204 No Content

본문 없음. 개인정보(이름·생년월일·국적·직업·이메일·비자·닉네임)는 탈퇴 시 즉시 익명화, social_accounts 매핑 삭제([ADR-0014](../../adr/0014-withdrawal-pii-anonymization.md)).

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 누락/위조 / 만료 |
| 409 | `USER_ALREADY_WITHDRAWN` | 이미 `WITHDRAWN`된 사용자의 탈퇴 재요청 |
| 404 | `USER_NOT_FOUND` | 사용자가 삭제되어 없음 |

---

## 도메인 에러 코드

> 공통 코드(`INVALID_INPUT`, `MALFORMED_REQUEST`, `UNAUTHENTICATED`, `TOKEN_EXPIRED`, `FORBIDDEN`, `RESOURCE_NOT_FOUND` 등)는 [error-response-guide](../error-response-guide.md) §3·§4를 따르며 여기서 재정의하지 않는다. provider/idToken 등 입력 형식 위반은 별도 도메인 코드 없이 공통 코드로 처리한다 — Bean Validation 위반(누락·빈값)은 `INVALID_INPUT`, 역직렬화 실패(허용 외 enum 문자열 등)는 `MALFORMED_REQUEST`. 아래는 auth/user 도메인 고유 코드만 정의한다. prefix는 `AUTH` / `USER`.

| code | status | 의미 |
| --- | --- | --- |
| `AUTH_INVALID_SOCIAL_TOKEN` | 401 | 소셜 `idToken`의 서명/`aud`/`iss`/`exp` 검증 실패(위조·만료·앱 불일치) |
| `AUTH_EMAIL_VERIFICATION_FAILED` | 422 | 이메일 인증번호 불일치 또는 만료(미발송·만료·오입력) |
| `AUTH_EMAIL_NOT_VERIFIED` | 422 | 온보딩 제출 `email`이 미검증이거나 검증한 이메일과 불일치 |
| `AUTH_REQUIRED_AGREEMENT_MISSING` | 422 | 필수 약관(이용약관/개인정보처리방침) 미동의(약관 동의 `POST /auth/terms`) |
| `AUTH_TERMS_AGREEMENT_REQUIRED` | 422 | 약관 미동의(`PENDING`) 상태로 온보딩 제출(약관 동의 선행 필요) |
| `AUTH_ONBOARDING_REQUIRED` | 403 | 온보딩 미완료(`PENDING`/`TERMS_AGREED`) 상태로 보호 API 접근 |
| `AUTH_ONBOARDING_ALREADY_COMPLETED` | 409 | 이미 온보딩 완료(ACTIVE)된 사용자가 온보딩 재요청 |
| `AUTH_INVALID_REFRESH_TOKEN` | 401 | refresh 토큰 만료/위조/무효화/재사용 탐지 |
| `USER_NOT_FOUND` | 404 | 대상 사용자가 없거나 탈퇴되어 조회 불가 |
| `USER_ALREADY_WITHDRAWN` | 409 | 이미 탈퇴(WITHDRAWN)된 사용자에 대한 탈퇴 재요청 |
