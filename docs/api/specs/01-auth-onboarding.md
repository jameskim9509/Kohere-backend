# 소셜 로그인 · 온보딩 API Spec

> [api-design-guide](../api-design-guide.md) · [error-response-guide](../error-response-guide.md)를 따른다. 모든 응답은 공통 래퍼.
> 관련 유저 스토리: [user-stories](../../requirements/user-stories.md)

## 개요

소셜 로그인(Apple/Google) 검증 후 서버 자체 JWT(access+refresh)를 발급하고, 신규 회원의 온보딩 필수정보 수집·약관 동의, 토큰 재발급/로그아웃, 회원 탈퇴, 내 프로필 조회·수정을 다룬다. 인증 헤더는 `Authorization: Bearer <accessToken>`, 토큰 갱신은 `POST /api/v1/auth/reissue`다.

상태 모델: 사용자는 `PENDING`(소셜 검증만 완료, 온보딩 미완료) → `ACTIVE`(온보딩 완료) → `WITHDRAWN`(탈퇴)로 전이한다.

### 핵심 개념·enum

| 개념 | 값 | 설명 |
| --- | --- | --- |
| 사용자 상태 `status` | `PENDING`, `ACTIVE`, `WITHDRAWN` | 소셜 검증만 완료 → 온보딩 완료 → 탈퇴 |
| provider | `APPLE`, `GOOGLE` | 소셜 로그인 제공자 |
| 성별 `gender` | `MALE`, `FEMALE` | 온보딩 필수 |
| 비자정보 `visaType` | `VISA_STUDENT`(유학·연수), `VISA_WORK`(취업), `VISA_RESIDENCE`(거주·가족동반), `VISA_WORKING_HOLIDAY`(워킹홀리데이), `VISA_TOURISM`(관광), `VISA_ETC`(기타) | 온보딩 필수 |

- 날짜만 표기는 `YYYY-MM-DD`(예: `birthDate`), 시각은 ISO-8601 UTC(예: `2026-06-15T08:30:00Z`).
- enum은 모두 UPPER_SNAKE_CASE 문자열로 노출한다.
- **민감정보(토큰 원문·비자정보·전화번호)는 응답·로그에서 마스킹**한다(error-response-guide §6).
- **토큰 모델**: `accessToken`은 **JWT**(stateless — 매 요청 서명·만료를 검증, 저장 안 함). `refreshToken`은 **불투명(opaque) 랜덤 토큰**으로 발급하고 서버 저장소에 **해시로 보관**한다(회전·재사용 탐지·무효화 목적). 예시의 `rt_…`는 불투명 토큰을, `eyJ…`는 JWT를 나타낸다.

---

## 엔드포인트 요약

| Method | Path | 설명 | 인증 | 성공 status |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/auth/social-login` | 소셜 `idToken` 검증 후 서버 JWT 발급(기존 로그인/신규 온보딩 분기) | 불필요 | 200 |
| POST | `/api/v1/auth/onboarding` | 온보딩 필수정보·약관 동의 제출, 가입 완료(ACTIVE 전이) | 필수(PENDING 토큰) | 200 |
| POST | `/api/v1/auth/reissue` | refresh 토큰으로 access 토큰 재발급 | 불필요(본문 refresh) | 200 |
| POST | `/api/v1/auth/logout` | 현재 세션 refresh 토큰 무효화 | 필수 | 204 |
| GET | `/api/v1/users/me` | 내 프로필 조회 | 필수 | 200 |
| PATCH | `/api/v1/users/me` | 내 프로필 부분 수정 | 필수 | 200 |
| DELETE | `/api/v1/users/me` | 회원 탈퇴(WITHDRAWN 전이, 토큰 일괄 무효화) | 필수 | 204 |

> `auth/onboarding`은 신규 리소스 생성이 아니라 social-login 단계에서 만든 PENDING 사용자를 ACTIVE로 전이하는 상태 액션이므로 `200`을 쓴다(api-design-guide §1 — "생성 아닌 액션").
> 인증 "필수" 엔드포인트는 access 토큰 만료 시 `401 TOKEN_EXPIRED`로 재발급을 유도한다. 온보딩 미완료(PENDING) 토큰으로 `GET`/`PATCH /users/me` 보호 API에 접근하면 `403 AUTH_ONBOARDING_REQUIRED`를 반환한다(단, `DELETE /users/me`(탈퇴)는 PENDING 사용자도 허용).

---

## 상세

### 1. POST `/api/v1/auth/social-login` — 소셜 로그인/온보딩 분기

앱이 provider(Apple/Google)에서 받은 `idToken`을 서버가 서명·`aud`·`iss`·`exp`로 검증한다. 기존 `ACTIVE` 회원이면 로그인 처리하고 access+refresh 토큰을 발급한다. 신규면 `PENDING` 사용자 레코드를 만들고 온보딩 전용 access 토큰(`onboardingCompleted=false` 클레임)과 `onboardingRequired=true`로 응답한다(refresh 토큰은 발급하지 않음).

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
| `provider` | string(enum) | 필수 | `APPLE` \| `GOOGLE` 중 하나(외 값은 `INVALID_INPUT`) |
| `idToken` | string | 필수 | provider 발급 OIDC ID 토큰. 빈 문자열 불가 |

#### 성공 Response — 기존 회원 (200 OK)

```json
{
  "success": true,
  "data": {
    "onboardingRequired": false,
    "tokenType": "Bearer",
    "accessToken": "eyJ...access",
    "refreshToken": "rt_9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2",
    "expiresIn": 3600
  },
  "error": null
}
```

#### 성공 Response — 신규 회원 (200 OK)

```json
{
  "success": true,
  "data": {
    "onboardingRequired": true,
    "tokenType": "Bearer",
    "accessToken": "eyJ...onboarding-scope",
    "refreshToken": null,
    "expiresIn": 1800
  },
  "error": null
}
```

> `expiresIn`은 access 토큰 만료까지의 초(seconds). 신규 회원에게 주는 access 토큰은 온보딩 API만 통과시킨다(클레임 `onboardingCompleted=false`, refresh 미발급). 온보딩 전용 임시 토큰 만료 1800초(30분), 정식 access 3600초(1시간) — [ADR-0011](../../adr/0011-token-lifetime-and-secret-policy.md)에서 확정.

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `provider` 누락/enum 불일치(`APPLE`/`GOOGLE` 외), `idToken` 누락/빈값 |
| 400 | `MALFORMED_REQUEST` | JSON 파싱 불가/타입 불일치 |
| 401 | `AUTH_INVALID_SOCIAL_TOKEN` | 소셜 `idToken`의 서명/`aud`/`iss`/`exp` 검증 실패 |
| 502 | `UPSTREAM_ERROR` | provider 공개키 조회/검증 연동 실패 |
| 503 | `SERVICE_UNAVAILABLE` | provider 일시 불가(타임아웃 등, error-response-guide §3) |

---

### 2. POST `/api/v1/auth/onboarding` — 온보딩 제출(가입 완료)

`PENDING` 사용자가 필수 프로필과 약관 동의를 제출해 가입을 완료한다. 성공 시 `ACTIVE`로 전이하고 정식 access/refresh 토큰을 발급한다. 사용자 단위로 멱등 처리해 동시 요청은 한 건만 성공한다.

> 약관 버전(termsVersion)은 서버 설정값을 온보딩 완료 시 서버가 기록한다([ADR-0012](../../adr/0012-terms-version-management.md)).

- **인증**: 필수 — 소셜 로그인 단계에서 받은 온보딩 토큰(`onboardingCompleted=false`).
- Path/Query 파라미터: 없음.

#### Request Body

```json
{
  "firstName": "Minh",
  "lastName": "Nguyen",
  "gender": "MALE",
  "birthDate": "1998-04-12",
  "countryCode": "+84",
  "phoneNumber": "1012345678",
  "visaType": "VISA_STUDENT",
  "termsOfServiceAgreed": true,
  "privacyPolicyAgreed": true,
  "marketingAgreed": false
}
```

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `firstName` | string | 필수 | 이름. 빈 문자열 불가 |
| `lastName` | string | 필수 | 성. 빈 문자열 불가 |
| `gender` | string(enum) | 필수 | `MALE` \| `FEMALE` |
| `birthDate` | string(date) | 필수 | `YYYY-MM-DD`, 과거 날짜만 허용(미래 불가) |
| `countryCode` | string | 필수 | 국가번호(예: `+84`) |
| `phoneNumber` | string | 필수 | 전화번호(국가번호 제외 숫자) |
| `visaType` | string(enum) | 필수 | `VISA_STUDENT` \| `VISA_WORK` \| `VISA_RESIDENCE` \| `VISA_WORKING_HOLIDAY` \| `VISA_TOURISM` \| `VISA_ETC` |
| `termsOfServiceAgreed` | boolean | 필수 | 이용약관 동의. `false`면 `AUTH_REQUIRED_AGREEMENT_MISSING`(422) |
| `privacyPolicyAgreed` | boolean | 필수 | 개인정보처리방침 동의. `false`면 `AUTH_REQUIRED_AGREEMENT_MISSING`(422) |
| `marketingAgreed` | boolean | 선택 | 마케팅 수신 동의(기본 `false`) |

#### 성공 Response — 200 OK

```json
{
  "success": true,
  "data": {
    "user": {
      "id": 1024,
      "firstName": "Minh",
      "lastName": "Nguyen",
      "gender": "MALE",
      "birthDate": "1998-04-12",
      "countryCode": "+84",
      "phoneNumber": "1012345678",
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
| 400 | `INVALID_INPUT` | 필드 누락/형식·enum·날짜 위반(`gender`/`visaType` 불일치, `birthDate` 형식·미래, `firstName`/`lastName` 빈값 등) |
| 400 | `MALFORMED_REQUEST` | JSON 파싱 불가/타입 불일치 |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 누락/위조 / 만료 |
| 409 | `AUTH_ONBOARDING_ALREADY_COMPLETED` | 이미 `ACTIVE`인 사용자의 온보딩 재요청(동시 요청 포함) |
| 422 | `AUTH_REQUIRED_AGREEMENT_MISSING` | 필수 약관(이용약관/개인정보처리방침) 미동의 |

---

### 3. POST `/api/v1/auth/reissue` — 토큰 재발급

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

### 4. POST `/api/v1/auth/logout` — 로그아웃

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

---

### 5. GET `/api/v1/users/me` — 내 프로필 조회

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
    "gender": "MALE",
    "birthDate": "1998-04-12",
    "countryCode": "+84",
    "phoneNumber": "1012345678",
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

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 누락/위조 / 만료 |
| 403 | `AUTH_ONBOARDING_REQUIRED` | 온보딩 미완료(PENDING) 토큰으로 접근 |
| 404 | `USER_NOT_FOUND` | 사용자가 `WITHDRAWN`이거나 삭제되어 없음 |

---

### 6. PATCH `/api/v1/users/me` — 내 프로필 부분 수정

본인 프로필을 부분 수정한다. 전송한 필드만 변경하고, 미전송 필드는 유지한다(미전송 ≠ 값 비움 — 현재 수정 대상 필드는 비움 불가).

- **인증**: 필수(ACTIVE 사용자). PENDING 토큰 접근은 `403 AUTH_ONBOARDING_REQUIRED`.
- Path/Query 파라미터: 없음.

#### Request Body (모든 필드 선택)

```json
{
  "phoneNumber": "1099998888",
  "countryCode": "+82",
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
| `countryCode` | string | 선택 | 국가번호 |
| `phoneNumber` | string | 선택 | 전화번호 |
| `visaType` | string(enum) | 선택 | 비자정보 enum(위 목록과 동일) |
| `marketingAgreed` | boolean | 선택 | 마케팅 수신 동의 |

> 필수 약관 동의(`termsOfServiceAgreed`/`privacyPolicyAgreed`)는 이 엔드포인트로 철회할 수 없다(탈퇴 경로로만 처리). (확인 필요: 동의 철회 정책)

#### 성공 Response — 200 OK

수정된 프로필 전체를 `GET /users/me`와 동일 스키마의 공통 래퍼로 반환한다.

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `gender`/`visaType` enum 불일치, `birthDate` 형식·범위 위반, `phoneNumber` 형식 오류 |
| 400 | `MALFORMED_REQUEST` | JSON 파싱 불가/타입 불일치 |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 누락/위조 / 만료 |
| 403 | `AUTH_ONBOARDING_REQUIRED` | 온보딩 미완료(PENDING) 토큰으로 접근 |
| 404 | `USER_NOT_FOUND` | 사용자가 `WITHDRAWN`이거나 삭제되어 없음 |

---

### 7. DELETE `/api/v1/users/me` — 회원 탈퇴

본인 계정을 탈퇴 처리한다. 사용자 상태를 `WITHDRAWN`으로 전이하고 모든 refresh 토큰을 무효화한다. PENDING(온보딩 미완료) 사용자도 탈퇴할 수 있다(온보딩 중단·정리 목적).

- **인증**: 필수.
- Path/Query 파라미터: 없음.
- Request Body: 없음.

#### 성공 Response — 204 No Content

본문 없음. 개인정보(이름·전화·비자)는 탈퇴 시 즉시 익명화, social_accounts 매핑 삭제([ADR-0014](../../adr/0014-withdrawal-pii-anonymization.md)).

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 누락/위조 / 만료 |
| 409 | `USER_ALREADY_WITHDRAWN` | 이미 `WITHDRAWN`된 사용자의 탈퇴 재요청 |
| 404 | `USER_NOT_FOUND` | 사용자가 삭제되어 없음 |

---

## 도메인 에러 코드

> 공통 코드(`INVALID_INPUT`, `MALFORMED_REQUEST`, `UNAUTHENTICATED`, `TOKEN_EXPIRED`, `FORBIDDEN`, `RESOURCE_NOT_FOUND`, `UPSTREAM_ERROR`, `SERVICE_UNAVAILABLE` 등)는 [error-response-guide](../error-response-guide.md) §3·§4를 따르며 여기서 재정의하지 않는다. provider enum 불일치 등 입력 형식 위반은 공통 `INVALID_INPUT`을 쓰고 별도 도메인 코드를 만들지 않는다. 아래는 auth/user 도메인 고유 코드만 정의한다. prefix는 `AUTH` / `USER`.

| code | status | 의미 |
| --- | --- | --- |
| `AUTH_INVALID_SOCIAL_TOKEN` | 401 | 소셜 `idToken`의 서명/`aud`/`iss`/`exp` 검증 실패(위조·만료·앱 불일치) |
| `AUTH_REQUIRED_AGREEMENT_MISSING` | 422 | 필수 약관(이용약관/개인정보처리방침) 미동의 |
| `AUTH_ONBOARDING_REQUIRED` | 403 | 온보딩 미완료(PENDING) 상태로 보호 API 접근 |
| `AUTH_ONBOARDING_ALREADY_COMPLETED` | 409 | 이미 온보딩 완료(ACTIVE)된 사용자가 온보딩 재요청 |
| `AUTH_INVALID_REFRESH_TOKEN` | 401 | refresh 토큰 만료/위조/무효화/재사용 탐지 |
| `USER_NOT_FOUND` | 404 | 대상 사용자가 없거나 탈퇴되어 조회 불가 |
| `USER_ALREADY_WITHDRAWN` | 409 | 이미 탈퇴(WITHDRAWN)된 사용자에 대한 탈퇴 재요청 |
