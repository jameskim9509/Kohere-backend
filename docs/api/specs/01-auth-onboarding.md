# 소셜 로그인 · 온보딩 API Spec

> [api-design-guide](../api-design-guide.md) · [error-response-guide](../error-response-guide.md)를 따른다. 모든 응답은 공통 래퍼.
> 관련 유저 스토리: [user-stories](../../requirements/user-stories.md)

## 개요

소셜 로그인(Apple/Google) 검증 후 서버 자체 JWT(access+refresh)를 발급하고(이때 provider에서 받은 **이름·이메일을 `User`에 즉시 채운다** — 애플 심사 대응, #192), 신규 회원의 온보딩 필수정보 수집·약관 동의, **임대인 온보딩 중 연락처(SMS) 인증**, **정식(ACTIVE) 사용자의 이메일 인증**, 토큰 재발급/로그아웃, 회원 탈퇴, 내 프로필 조회·수정, **내 차단 목록 조회·해제**를 다룬다. 인증 헤더는 `Authorization: Bearer <accessToken>`, 토큰 갱신은 `POST /api/v1/auth/reissue`다.

상태 모델: 사용자는 `PENDING`(소셜 검증만 완료) → `TERMS_AGREED`(약관 동의 완료) → `ACTIVE`(온보딩 완료) → `WITHDRAWN`(탈퇴)로 전이한다. **약관 동의와 온보딩은 분리된 단계**로, 약관 동의(`POST /auth/terms`)가 온보딩 제출(`POST /auth/onboarding`)을 선행한다.

### 임대인 트랙

사용자는 **세입자(`TENANT`, 외국인)** 와 **임대인(`LANDLORD`)** 두 역할로 나뉜다. **소셜 로그인·약관 동의까지는 두 역할이 공통 흐름**이고, **이후 온보딩 단계에서 분기**한다 — 세입자는 곧바로 `POST /auth/onboarding`(§5), 임대인은 연락처 SMS 인증(§4-1·§4-2) 후 `POST /auth/landlord/onboarding`(§5-2)으로 제출한다(세입자의 이메일은 소셜 로그인 시 provider 값으로 확정되므로 온보딩에서 재입력·인증하지 않는다 — #192). **임대인 온보딩은 약관 동의 + 연락처(SMS) 인증만으로 완료**되며, 사업자등록번호는 온보딩 제출에 포함하지 않는다 — 온보딩을 마친(ACTIVE) 임대인이 나중에(매물 등록 시점) 별도 검증 API(§5-1)로 검증한다. **`userType`은 온보딩 제출 엔드포인트로 확정되고 이후 불변**이다(소셜 로그인·약관 단계에서는 미확정). **임대인의 이메일은 세입자와 동일하게 소셜 로그인 시 provider(Apple/Google) 값으로 캡처·보유하되(더는 미수집 아님), 본인 확인은 연락처(휴대폰) SMS 인증으로 한다**([ADR-0034](../../adr/0034-landlord-phone-sms-verification.md) 개정(#192) — 이메일은 인증 대상 아닌 미검증 연락처). 관련 유저 스토리: US-1-8(사업자번호 검증)·US-1-9(임대인 온보딩)·US-1-10(임대인 연락처 인증).

### 핵심 개념·enum

| 개념 | 값 | 설명 |
| --- | --- | --- |
| 사용자 상태 `status` | `PENDING`, `TERMS_AGREED`, `ACTIVE`, `WITHDRAWN` | 소셜 검증만 완료 → 약관 동의 완료 → 온보딩 완료 → 탈퇴 |
| provider | `APPLE`, `GOOGLE` | 소셜 로그인 제공자 |
| 성별 `gender` | `MALE`, `FEMALE` | 온보딩 필수(세입자만) |
| 생년월일 `birthDate` | 날짜 문자열(`YYYY-MM-DD`) | 온보딩 필수(세입자·임대인 공통) · 과거 날짜만 허용(미래 불가) |
| 직업 `occupation` | `UNDERGRADUATE_STUDENT`(학부생), `GRADUATE_STUDENT`(대학원생), `EXCHANGE_STUDENT`(교환학생), `LANGUAGE_TEACHING`(어학·교육), `MANUFACTURING_PRODUCTION`(제조·생산), `BUSINESS_TRADE`(사업·무역), `ETC`(기타) | **온보딩 선택**(#187에서 필수→선택 완화 — 매물 추천·탐색에서 활용하지 않음) · 미전송이면 저장하지 않고(NULL) 응답에서 생략 · 요구사항 확정값(#93, #138 개편) |
| 비자정보 `visaType` | `SHORT_TERM_VISIT`(단기방문), `STUDENTS_TRAINEES`(유학·연수), `NON_PROFESSIONAL_WORKERS`(비전문취업), `WORKING_HOLIDAY_WORK_AND_VISIT`(워킹홀리데이·방문취업), `OVERSEAS_KOREANS`(재외동포), `FAMILY_MARRIAGE_MIGRANTS`(방문동거·거주·결혼이민), `PERMANENT_RESIDENTS`(영주), `PROFESSIONALS`(전문인력), `DIPLOMATIC_OFFICIAL_AND_OTHERS`(외교·공무·기타), `ETC`(기타) | 온보딩 필수 · 요구사항 확정값(#93, #138 개편). API는 상수명, DB 저장은 표시 라벨 |
| 국적 `country` | ISO 3166-1 alpha-2 코드(예: `VN`) | 온보딩 필수 · 클라이언트는 국가만 전송, 표시명·국기는 서버가 `countries` 참조로 확보(응답에 `countryName`·`countryFlag` 포함, **`countryFlag`는 국기 이미지 URL**) |
| 표시 언어 `lang` | ISO 639-1 소문자 코드 — 지원값 `en`, `ko`, `ja` | **세입자 온보딩·프로필 수정 모두 선택** · 사용자가 앱 지구본 아이콘에서 직접 고른다. `users.lang`(사용자가 고른 표시 언어)이 있으면 그 값, 없으면 `en`이다. 지원 목록(`en`·`ko`·`ja`)으로 서버가 검증하고 목록 밖 값은 `INVALID_INPUT`이다(값은 소문자 코드로 주고받되 서버는 내부적으로 `Language` enum으로 모델링한다). **임대인은 서버가 `ko`로 고정 부여하며 변경할 수 없다**([ADR-0034](../../adr/0034-landlord-phone-sms-verification.md) 개정(#141)) |
| 이메일 `email` | 이메일 문자열 | **세입자** 소셜 로그인 시 provider(Apple/Google) 진본으로 확정 — 요청 `email`이 토큰 `email` 클레임과 일치해야 하며(§1) 온보딩에서 재수집·재인증하지 않는다(#192). 정식(ACTIVE) 사용자의 이메일 인증 API는 §3·§4(접근만 ACTIVE 전용, 실제 이메일 변경 반영은 후속 이슈). 임대인도 소셜 로그인 시 provider 값을 `User.email`에 보유한다(더는 미수집·NULL 아님 — [ADR-0034](../../adr/0034-landlord-phone-sms-verification.md)의 "임대인 이메일 미수집" 결정을 개정(#192): 수집 폼이 아니라 소셜 로그인 provider 값 보유이며 인증 대상 아님, 수정은 후속 이슈) |
| 닉네임 `nickname` | `형용사 + 사물` 문자열 | 서버가 자동 배정(사용자 입력·수정 불가), 전역 유니크 |
| 사용자 역할 `userType` | `TENANT`(세입자·외국인), `LANDLORD`(임대인) | 온보딩 제출 엔드포인트(세입자 `/auth/onboarding` · 임대인 `/auth/landlord/onboarding`)로 확정·이후 불변. 소셜·약관 단계에서는 미확정 |
| 이름 `name` | 문자열 | **세입자·임대인 공통** · 성·이름을 합친 **단일 이름**(#192에서 세입자의 `firstName`/`lastName`을 단일 `name`으로 통합해 임대인과 완전히 통일). 빈 문자열 불가. API 필드명·저장 모두 단일 `name`(`FullName` VO의 단일 `name` 속성 · `users.name` 컬럼). 세입자·임대인 모두 소셜 로그인 시 provider 값으로 채우고(§1) 이후 `PATCH /users/me`(§9)로 수정한다(#192에서 임대인도 온보딩 수집을 폐지해 세입자와 수집 시점까지 완전히 통일) |
| 연락처 `phoneNumber` | 전화번호 문자열 | **임대인 온보딩 필수** · SMS 인증번호로 사전 검증(§4-1·§4-2) 필요. 응답·로그 마스킹(예 `010-****-5678`) |
| 사업자등록번호 `businessRegistrationNumber` | 숫자 10자리 문자열 | **임대인 전용** · **온보딩 제출에는 미포함**(온보딩은 약관·연락처 인증만으로 완료). 온보딩 후 매물 등록 시점에 별도 검증 API(§5-1)로 무상태 검증한다. 응답·로그 마스킹 |

- 날짜만 표기는 `YYYY-MM-DD`(예: `birthDate`), 시각은 ISO-8601 UTC(예: `2026-06-15T08:30:00Z`).
- enum은 모두 UPPER_SNAKE_CASE 문자열로 노출한다. **예외: `lang`은 UPPER_SNAKE가 아니라 ISO 639-1 소문자 코드**다(`en`·`ko`·`ja`) — 값은 소문자로 주고받고 저장하되 서버는 닫힌 집합 `Language` enum으로 모델링·검증한다. 신규 언어는 카탈로그 콘텐츠 시드가 선행되어 어차피 배포를 수반하므로 enum이 손해가 아니다([ADR-0029](../../adr/0029-diagnosis-i18n-strategy.md) 개정(#141)).
- **민감정보(토큰 원문·인증번호 원문·비자정보·이메일)는 로그·타 사용자 노출 시 마스킹**한다(error-response-guide §6). 본인 `GET /users/me`는 이메일을 평문으로 반환한다.
- **토큰 모델**: `accessToken`은 **JWT**(stateless — 매 요청 서명·만료를 검증, 저장 안 함). `refreshToken`은 **불투명(opaque) 랜덤 토큰**으로 발급하고 서버 저장소에 **해시로 보관**한다(회전·재사용 탐지·무효화 목적). 예시의 `rt_…`는 불투명 토큰을, `eyJ…`는 JWT를 나타낸다.

---

## 엔드포인트 요약

| Method | Path | 설명 | 인증 | 성공 status |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/auth/social-login` | 소셜 자격 검증 후 서버 JWT 발급(기존 로그인/신규 온보딩 분기) — Google은 `idToken`, **Apple은 `authorizationCode`**([ADR-0031](../../adr/0031-apple-sign-in-authorization-code-flow.md)) | 불필요 | 200 |
| POST | `/api/v1/auth/terms` | 약관 동의 제출(이용약관·개인정보처리방침·마케팅), 약관 동의 완료(TERMS_AGREED 전이) | 필수(온보딩 토큰) | 200 |
| POST | `/api/v1/auth/email/verification-code` | 이메일로 인증번호 발송(세입자) — 정식(ACTIVE) 사용자 전용(#192) | 필수(정식 토큰(ACTIVE, ROLE_USER)) | 200 |
| POST | `/api/v1/auth/email/verify` | 인증번호 확인(세입자) — 접근만 ACTIVE 전용, 실제 이메일 변경 반영은 후속 이슈 | 필수(정식 토큰(ACTIVE, ROLE_USER)) | 200 |
| POST | `/api/v1/auth/phone/verification-code` | 연락처로 SMS 인증번호 발송(임대인 전용) — 온보딩(US-1-10)·프로필 변경(US-1-5) 공용 | 필수(온보딩 토큰/정식 토큰) | 200 |
| POST | `/api/v1/auth/phone/verify` | 인증번호 확인 → 연락처 검증 완료 처리(임대인 전용) — 온보딩·프로필 변경 공용 | 필수(온보딩 토큰/정식 토큰) | 200 |
| POST | `/api/v1/auth/onboarding` | 세입자 온보딩 필수정보 제출(약관 동의 선행), 가입 완료(ACTIVE 전이) | 필수(온보딩 토큰, TERMS_AGREED) | 200 |
| POST | `/api/v1/auth/business/verify` | 사업자등록번호 외부 검증(임대인 전용·온보딩 완료 후 무상태 검증), 결과 미저장·응답 body에만 반환 | 필수(정식 토큰(ACTIVE, ROLE_USER)) | 200 |
| POST | `/api/v1/auth/landlord/onboarding` | 임대인 온보딩 제출(약관·연락처 인증 선행), 가입 완료(ACTIVE 전이 + userType=LANDLORD 확정) | 필수(온보딩 토큰, TERMS_AGREED) | 200 |
| POST | `/api/v1/auth/reissue` | refresh 토큰으로 access 토큰 재발급 | 불필요(본문 refresh) | 200 |
| POST | `/api/v1/auth/logout` | 현재 세션 refresh 토큰 무효화 | 필수 | 204 |
| GET | `/api/v1/users/me` | 내 프로필 조회 | 필수 | 200 |
| PATCH | `/api/v1/users/me` | 내 프로필 부분 수정 | 필수 | 200 |
| DELETE | `/api/v1/users/me` | 회원 탈퇴(WITHDRAWN 전이, 토큰 일괄 무효화) | 필수 | 204 |
| GET | `/api/v1/users/me/blocks` | 내가 차단한 사용자 목록(해제용) | 필수 | 200 |
| DELETE | `/api/v1/users/me/blocks/{userId}` | 차단 해제(멱등) | 필수 | 204 |

> 차단 **생성**은 이 문서에 없다 — 예약 문맥 전용 `POST /api/v1/bookings/{bookingId}/block`([04-booking-inquiry-chat](04-booking-inquiry-chat.md))이 유일한 생성 경로이고, 목록 조회(§11)·해제(§12)만 `user` 모듈이 맡는다(§11 근거 블록쿼트 참조).
> `auth/onboarding`은 신규 리소스 생성이 아니라 약관 동의를 마친 `TERMS_AGREED` 사용자를 `ACTIVE`로 전이하는 상태 액션이므로 `200`을 쓴다(api-design-guide §1 — "생성 아닌 액션").
> 인증 "필수" 엔드포인트는 access 토큰 만료 시 `401 TOKEN_EXPIRED`로 재발급을 유도한다. **온보딩 토큰**(`ROLE_ONBOARDING` — `onboardingCompleted=false`, 상태 `PENDING`/`TERMS_AGREED` 공통)으로 `GET`/`PATCH /users/me`·`POST /auth/logout`·`POST /auth/email/verification-code`·`POST /auth/email/verify`(세입자 이메일 인증 — #192에서 온보딩 단계 전용→정식(ACTIVE) 전용으로 반전)(모두 `ROLE_USER` 필요) 보호 API에 접근하면 `403 AUTH_ONBOARDING_REQUIRED`를 반환한다(단, `DELETE /users/me`(탈퇴)·`POST /auth/terms`(약관 동의)·`POST /auth/phone/verification-code`·`POST /auth/phone/verify`(임대인 연락처 인증)·`POST /auth/onboarding`·`POST /auth/landlord/onboarding`(임대인 온보딩)은 온보딩 흐름이라 온보딩 토큰도 허용). 단 `/auth/phone/**`(연락처 인증)는 프로필 연락처 변경(US-1-5)을 위해 **정식 토큰(`ROLE_USER`)도 함께 허용**한다(온보딩 토큰·정식 토큰 양쪽 — [ADR-0034](../../adr/0034-landlord-phone-sms-verification.md) §6·§8). 반대로 `POST /auth/business/verify`(사업자번호 검증)는 온보딩 흐름이 아니라 **온보딩을 완료한(ACTIVE) 임대인이 정식 토큰(`ROLE_USER`)으로만 호출**하는 무상태 검증 API로, 온보딩 토큰으로 접근하면 `403 AUTH_ONBOARDING_REQUIRED`다(§5-1). 상태 전이 순서는 `POST /auth/terms`(PENDING→TERMS_AGREED) → `POST /auth/onboarding`(TERMS_AGREED→ACTIVE)이며, 약관 미동의 상태(`PENDING`)에서 온보딩을 제출하면 `422 AUTH_TERMS_AGREEMENT_REQUIRED`다.

---

## 상세

### 1. POST `/api/v1/auth/social-login` — 소셜 로그인/온보딩 분기

앱이 provider에서 받은 자격을 서버가 검증한다 — **Google은 `idToken`** 을 서명·`aud`·`iss`·`exp`로 검증하고, **Apple은 `authorizationCode`** 를 `POST https://appleid.apple.com/auth/token`에서 교환해 받은 `id_token`을 같은 방식으로 검증한 뒤 신원(`sub`·`email`)을 얻는다. **요청 본문의 `email`·`name`은 앱이 네이티브 SDK(Apple `ASAuthorization` / Google account)에서 받아 함께 보내는 선택 필드**다 — Apple은 이름·이메일을 **최초 인증 1회만** 클라에 주므로 재로그인 요청엔 없을 수 있다. 그래서 이 둘은 **최초 로그인(=신규 가입) 시에만 `User`에 영구 저장**하고, 재로그인(기존 회원)에서는 **요청 값으로 `User`를 덮지 않고 저장된 값을 쓴다**(사용자 편집 보호 — provider 스냅샷 `SocialAccount`는 로그인마다 갱신). **최초 로그인 시** 서버는 요청 `email`이 토큰의 `email` 클레임과 **일치하는지 교차 검증**하고(불일치 `422 AUTH_EMAIL_MISMATCH`, 토큰·요청 어느 쪽에도 `email`이 없으면 `422 AUTH_EMAIL_REQUIRED`) `email`을 provider 진본으로 확정하며, **`name`은 검증하지 않고 요청 값을 신뢰**한다(없으면 `null` — 이후 `PATCH /users/me`로 수정). Apple은 교환으로 받은 `refresh_token`을 저장해 **탈퇴 시 토큰 폐기**(§10)에 사용한다([ADR-0031](../../adr/0031-apple-sign-in-authorization-code-flow.md)). 기존 `ACTIVE` 회원이면 로그인 처리하고 access+refresh 토큰을 발급한다(`status=ACTIVE`, `onboardingRequired=false`). 신규이거나 **가입을 끝내지 못한 회원(`PENDING`·`TERMS_AGREED`)** 이면 온보딩 전용 access 토큰(`onboardingCompleted=false` 클레임)과 `onboardingRequired=true`로 응답한다(refresh 토큰은 발급하지 않음). **신규면 이 시점에 `PENDING` User 레코드를 새로 만들며 요청 `name`·`email`을 즉시 채운다**(온보딩까지 미루지 않는다). `SocialAccount`(auth)에는 `provider`/`providerUserId`/`email`/`name`/`userId`를 저장한다 — `email`·`name`은 **provider가 준 값의 스냅샷**으로 로그인마다 최신 provider 값으로 upsert한다(Google은 갱신, Apple `name`은 최초값 유지·재로그인 시 미제공). 이는 `User`의 `name`·`email`(사용자 값 — 최초 로그인에만 세팅·이후 사용자 편집)과 **별개로 이중 관리**된다: 사용자 수정은 `User`만 건드리고, provider 변경은 `SocialAccount` 스냅샷에 반영한다(email은 심사계정 매칭 등에 활용).

응답의 **`status`로 클라이언트가 다음 화면을 분기**한다 — `PENDING`(소셜 로그인만 하고 약관 미동의)이면 **약관 동의 화면(§2)**, `TERMS_AGREED`(약관 동의했으나 온보딩 미완료)이면 **온보딩 화면(§5)**, `ACTIVE`이면 홈. 온보딩 토큰으로는 `GET /users/me`(ROLE_USER)가 `403`이라 상태를 따로 조회할 수 없으므로, 재개 지점은 이 응답의 `status`로 판단한다.

- **인증**: 불필요.
- Path/Query 파라미터: 없음.

#### Request Body

provider별로 **자격 필드 하나**를 채우고(Google은 `idToken`, Apple은 `authorizationCode` — 둘 다 단일 엔드포인트·동일 응답, [ADR-0031](../../adr/0031-apple-sign-in-authorization-code-flow.md) A안), **`email`·`name`은 provider 공통으로 앱이 네이티브 SDK에서 받아 함께 보내는 선택 필드**다 — **최초 로그인(신규 가입)에서만 캡처·영구 저장**하고 재로그인 요청 값은 무시한다(Apple은 최초 1회만 제공 — 애플 심사 대응, #192).

```json
// Google
{
  "provider": "GOOGLE",
  "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6...",
  "email": "minh@example.com",
  "name": "Minh Nguyen"
}
```

```json
// Apple
{
  "provider": "APPLE",
  "authorizationCode": "c1a2b3...",
  "email": "minh@example.com",
  "name": "Minh Nguyen"
}
```

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `provider` | string(enum) | 필수 | `APPLE` \| `GOOGLE` 중 하나(누락은 `INVALID_INPUT`, 허용 외 값은 역직렬화 실패로 `MALFORMED_REQUEST`) |
| `idToken` | string | provider별 | **Google 필수**. Google 발급 OIDC ID 토큰. Apple은 사용하지 않음 |
| `authorizationCode` | string | provider별 | **Apple 필수**. `ASAuthorizationAppleIDCredential.authorizationCode`(UTF-8 디코드한 문자열, 1회용·약 5분). Google은 사용하지 않음 |
| `email` | string | 선택(최초 로그인 필수) | 이메일 형식. 앱이 네이티브 SDK에서 받은 이메일. **최초 로그인(신규 가입)에서만 필요** — 이때 토큰의 `email` 클레임과 일치해야 하고(불일치 `AUTH_EMAIL_MISMATCH` 422, 토큰·요청 모두 email이 없으면 `AUTH_EMAIL_REQUIRED` 422) provider 진본으로 확정해 `User.email`에 영구 저장한다. 재로그인 요청 값은 무시(저장값 사용, 덮어쓰지 않음) |
| `name` | string | 선택 | 앱이 네이티브 SDK에서 받은 표시 이름(성·이름을 합친 단일 값). **검증하지 않고 요청 값을 신뢰**한다. Apple은 이름을 최초 로그인 1회만 반환 → **최초 로그인(신규 가입)에서만 캡처해 `User.name`에 영구 저장**하고(없으면 `null` → `PATCH /users/me`로 수정), 재로그인 요청 값은 무시(저장값 유지) |

> 필수 여부가 provider에 따라 달라(`idToken`↔`authorizationCode`) Bean Validation 대신 **application 계층에서 검증**한다 — 해당 provider의 자격 필드가 비어 있으면 `400 AUTH_MISSING_CREDENTIAL`. Apple `authorizationCode`는 1회용이므로 서버가 즉시 교환한다(재사용 시 `401 AUTH_INVALID_SOCIAL_TOKEN`).

#### 성공 Response — 기존 회원(ACTIVE) (200 OK)

```json
{
  "success": true,
  "data": {
    "onboardingRequired": false,
    "status": "ACTIVE",
    "email": "minh@example.com",
    "name": "Minh Nguyen",
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
    "email": "minh@example.com",
    "name": "Minh Nguyen",
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
| `email` | string | 사용자 이메일(provider 진본). **모든 분기(신규 PENDING·기존 ACTIVE·재로그인)에서 반환** — 온보딩 화면 프리필용. 값은 `User.email` |
| `name` | string \| null | 사용자 이름(단일 `name`). **모든 분기에서 반환** — 온보딩 화면 프리필용. 값은 `User.name`(아직 없으면 `null`) |
| `refreshToken` | string \| null | `ACTIVE` 로그인에서만 발급, 미완료(`PENDING`/`TERMS_AGREED`)는 `null` |

> `expiresIn`은 access 토큰 만료까지의 초(seconds). 미완료 회원에게 주는 access 토큰은 온보딩 흐름(약관 동의·임대인 연락처(SMS) 인증·온보딩) API만 통과시킨다(클레임 `onboardingCompleted=false`, refresh 미발급 — **세입자 이메일 인증(§3·§4)은 #192에서 온보딩 흐름에서 제외돼 정식(ACTIVE) 토큰 전용**이다). 온보딩 전용 임시 토큰 만료 1800초(30분), 정식 access 3600초(1시간) — [ADR-0011](../../adr/0011-token-lifetime-and-secret-policy.md)에서 확정.

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `provider` 누락(null) (Bean Validation: `@NotNull`) |
| 400 | `MALFORMED_REQUEST` | JSON 파싱 불가/타입 불일치. **`provider`가 허용 외 enum 문자열(`APPLE`/`GOOGLE` 외)이면 역직렬화 단계에서 거부되어 이 코드로 처리**된다 |
| 400 | `AUTH_MISSING_CREDENTIAL` | provider의 자격 필드 누락/빈값(Google `idToken` 또는 Apple `authorizationCode` 미전송) — application 계층 검증 |
| 401 | `AUTH_INVALID_SOCIAL_TOKEN` | Google `idToken`의 서명/`aud`/`iss`/`exp` 검증 실패, 또는 Apple 교환 실패(`invalid_grant`/`invalid_client` — 만료·재사용 코드, 잘못된 client_secret)와 교환으로 받은 `id_token` 검증 실패. **provider JWKS 조회 실패 등 OIDC 연동 오류도 현재 구현은 이 코드로 통합 처리**한다(아래 노트) |
| 422 | `AUTH_EMAIL_REQUIRED` | **최초 로그인(신규 가입) 시** 토큰의 `email` 클레임·요청 `email` 어느 쪽에도 이메일이 없음(provider 진본 이메일을 확정할 수 없음). 재로그인은 email 없이도 통과(저장값 사용) |
| 422 | `AUTH_EMAIL_MISMATCH` | **최초 로그인 시** 요청 `email`이 토큰의 `email` 클레임과 불일치(요청 값 위조 방어 — email은 provider 진본으로 확정) |
| 502 | `UPSTREAM_ERROR` | Apple `/auth/token` 인가코드 교환의 일시 장애(타임아웃·5xx·I/O). 자격 문제(401)가 아니므로 그대로 재시도할 수 있다(아래 노트) |

> **연동 실패 처리(현행)**: `OidcTokenVerifierImpl`은 JWKS 조회 실패·provider 응답 오류를 포함한 모든 OIDC 검증 실패를 `401 AUTH_INVALID_SOCIAL_TOKEN`으로 변환한다. Apple `/auth/token` 교환 호출의 인증 실패(`invalid_grant`/`invalid_client`)도 `401`로 통합하고, Apple 측 일시 장애·타임아웃 등 I/O·5xx는 `502 UPSTREAM_ERROR`로 분리한다([ADR-0031](../../adr/0031-apple-sign-in-authorization-code-flow.md)). Google 경로는 종전대로 `502`/`503`을 내지 않는다(시퀀스 [US-1-1](../../architecture/sequence-diagrams/01-auth-onboarding/us-1-1-social-login.md)·REST Docs 스니펫과 정합). 외부 연동 견고화(타임아웃·재시도·서킷브레이커) 확대는 [error-response-guide](../error-response-guide.md) §3 참고.

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

**온보딩을 완료한(ACTIVE) 사용자**가 이메일 주소로 인증번호를 발송받는다(#192에서 온보딩 단계 전용→정식(ACTIVE) 전용으로 반전 — 온보딩 토큰으로는 호출할 수 없고 정식 access 토큰이 필요하다). 온보딩 스코프(`PENDING`/`TERMS_AGREED`) 토큰으로 호출하면 `403 AUTH_ONBOARDING_REQUIRED`다. 같은 사용자에 미검증 인증 시도가 남아 있으면 새 인증번호로 대체한다. 인증번호는 서버에 **해시로만 보관**하고 일정 시간(예: 5분 — 확인 필요) 후 만료한다. 재발송은 레이트리밋으로 보호한다.

메일은 아웃바운드 포트 `VerificationEmailSender`(인프라 어댑터: SMTP)로 **동기 발송**하며, **발송에 성공한 뒤에만** 인증번호 챌린지를 저장한다. provider 장애·타임아웃 등 발송 실패 시 챌린지를 만들지 않고 `502 UPSTREAM_ERROR`로 응답해 클라이언트가 재시도하도록 한다(메일 템플릿·다국어, 동기/비동기 정책은 확인 필요).

- **인증**: 필수 — **정식 access 토큰(`ACTIVE`, `ROLE_USER`)**. 온보딩 스코프(`PENDING`/`TERMS_AGREED`, `ROLE_ONBOARDING`) 토큰으로 호출하면 `403 AUTH_ONBOARDING_REQUIRED`(#192).
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
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 정식 토큰 누락/위조 / 만료 |
| 403 | `AUTH_ONBOARDING_REQUIRED` | 온보딩 스코프(`PENDING`/`TERMS_AGREED`) 토큰으로 호출(정식(ACTIVE) 토큰 필요 — 온보딩 완료 후 호출하는 API, #192 반전) |
| 429 | `TOO_MANY_REQUESTS` | 재발송 레이트리밋 초과(확인 필요: 임계값) |
| 502 | `UPSTREAM_ERROR` | 메일 발송 실패(provider 장애·타임아웃). 챌린지 미저장, 클라이언트 재시도 유도(공통 코드 — [error-response-guide](../error-response-guide.md) §3) |

---

### 4. POST `/api/v1/auth/email/verify` — 이메일 인증번호 확인

발송된 인증번호를 검증한다. 성공하면 인증 챌린지를 **검증 완료(VERIFIED)** 로 표시한다. **접근은 온보딩을 완료한(ACTIVE) 사용자 전용**이다(#192에서 온보딩 단계 전용→정식(ACTIVE) 전용으로 반전). **다만 이번 범위(#192)에서는 verify 성공이 `User.email`을 바꾸지 않는다** — 접근만 ACTIVE로 제한하고, 실제 이메일 변경 반영은 후속 이슈다. 검증 시도는 횟수 상한으로 보호한다.

> **챌린지 부재(미발송·만료·이미 검증)**: 해당 사용자의 인증 챌린지(`email-verify:code:{userId}`)가 없으면 — 인증번호를 한 번도 요청하지 않았거나, TTL 만료, 이미 검증 완료로 소멸, 발송 실패(`502`)로 미저장 — 올릴 `attempts` 레코드 자체가 없으므로 **즉시 `422 AUTH_EMAIL_VERIFICATION_FAILED`** 로 거절하고 인증번호 (재)요청(§3)을 유도한다. `attempts`는 **챌린지가 존재하는데 코드가 불일치**할 때만 증가하며, 상한 초과 시 `429 TOO_MANY_REQUESTS`다.

- **인증**: 필수 — **정식 access 토큰(`ACTIVE`, `ROLE_USER`)**. 온보딩 스코프(`PENDING`/`TERMS_AGREED`, `ROLE_ONBOARDING`) 토큰은 `403 AUTH_ONBOARDING_REQUIRED`(#192).
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
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 정식 토큰 누락/위조 / 만료 |
| 403 | `AUTH_ONBOARDING_REQUIRED` | 온보딩 스코프(`PENDING`/`TERMS_AGREED`) 토큰으로 호출(정식(ACTIVE) 토큰 필요, #192 반전) |
| 422 | `AUTH_EMAIL_VERIFICATION_FAILED` | 코드 불일치, 또는 챌린지 부재(미발송·만료·이미 검증) — 부재 시 `attempts` 증가 없이 즉시 거절 |
| 429 | `TOO_MANY_REQUESTS` | 챌린지 존재 + 코드 불일치 누적으로 검증 시도 상한 초과(확인 필요: 임계값) |

---

### 4-1. POST `/api/v1/auth/phone/verification-code` — 연락처 인증번호 발송(임대인 전용)

**임대인 온보딩(US-1-10)** 또는 **정식 회원의 프로필 연락처 변경(US-1-5)** 시 입력한 연락처(휴대폰)로 SMS 인증번호를 발송한다(세입자 이메일 인증 §3과 대칭 — 임대인 트랙의 본인 확인, [ADR-0034](../../adr/0034-landlord-phone-sms-verification.md)). **약관 동의(§2, `TERMS_AGREED`) 이상**이면 진행한다 — 온보딩(`TERMS_AGREED`)·프로필 변경(`ACTIVE`) 두 컨텍스트 모두 허용하고, 약관 미동의(`PENDING`)면 `422 AUTH_TERMS_AGREEMENT_REQUIRED`로 거절하고 약관 동의(§2)를 먼저 유도한다. 같은 사용자에 미검증 인증 시도가 남아 있으면 새 인증번호로 대체한다. **인증번호 정책은 이메일 인증(§3·§4)과 동일하다** — 인증번호 6자리, 서버에 **해시로만 보관**하고 코드 TTL 5분 후 만료, 검증 마커(VERIFIED) TTL 30분(온보딩 토큰 만료), 검증 시도 상한 5회, 재발송 간격 60초로 보호한다.

SMS는 아웃바운드 포트 `VerificationSmsSender`(인프라 어댑터: SMS API — 구체 provider는 [ADR-0034](../../adr/0034-landlord-phone-sms-verification.md))로 **동기 발송**하며, **발송에 성공한 뒤에만** 인증번호 챌린지를 저장한다. provider 장애·타임아웃 등 발송 실패 시 챌린지를 만들지 않고 `502 UPSTREAM_ERROR`로 응답해 클라이언트가 재시도하도록 한다(인증번호 생성·해시·검증은 서버가 보유해 이메일 인증과 대칭 — 어댑터는 발송만 담당. 동기/비동기 정책·문자 템플릿은 확인 필요).

- **인증**: 필수 — **(온보딩 단계, US-1-10)** 소셜 로그인에서 받은 온보딩 토큰(`onboardingCompleted=false`), **또는 (프로필 연락처 변경, US-1-5) 정식 토큰(`ACTIVE`, `ROLE_USER`)**. `/auth/phone/**`는 두 티어 모두 허용한다(보안 경로 확장 — [ADR-0034](../../adr/0034-landlord-phone-sms-verification.md) §6·§8).
- Path/Query 파라미터: 없음.

#### Request Body

```json
{
  "phoneNumber": "010-1234-5678"
}
```

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `phoneNumber` | string | 필수 | 전화번호 형식. 빈 문자열 불가 |

#### 성공 Response — 200 OK

```json
{
  "success": true,
  "data": {
    "phoneNumber": "010-****-5678",
    "expiresIn": 300
  },
  "error": null
}
```

> `expiresIn`은 인증번호 만료까지의 초(seconds). `phoneNumber`는 마스킹해 반환한다. 인증번호 원문은 응답·로그에 노출하지 않는다.

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `phoneNumber` 누락/빈값/형식 위반 |
| 400 | `MALFORMED_REQUEST` | JSON 파싱 불가/타입 불일치 |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 온보딩/정식 토큰 누락/위조 / 만료 |
| 422 | `AUTH_TERMS_AGREEMENT_REQUIRED` | 약관 미동의(`PENDING`) 상태의 요청(약관 동의 §2 선행 필요) |
| 429 | `TOO_MANY_REQUESTS` | 재발송 레이트리밋 초과(이메일 인증과 동일 — 재발송 간격 60초) |
| 502 | `UPSTREAM_ERROR` | SMS 발송 실패(provider 장애·타임아웃). 챌린지 미저장, 클라이언트 재시도 유도(공통 코드 — [error-response-guide](../error-response-guide.md) §3) |

---

### 4-2. POST `/api/v1/auth/phone/verify` — 연락처 인증번호 확인(임대인 전용)

발송된 인증번호를 검증한다. 성공하면 해당 사용자의 연락처를 **검증 완료(VERIFIED)** 로 표시하고, 이후 임대인 온보딩 제출(§5-2) 시 같은 번호를 통과시킨다. 검증 시도는 횟수 상한으로 보호한다(이메일 인증번호 확인 §4와 대칭).

> **챌린지 부재(미발송·만료·이미 검증)**: 해당 사용자의 인증 챌린지(`phone-verify:code:{userId}`)가 없으면 — 인증번호를 한 번도 요청하지 않았거나, TTL 만료, 이미 검증 완료로 소멸, 발송 실패(`502`)로 미저장 — 올릴 `attempts` 레코드 자체가 없으므로 **즉시 `422 AUTH_PHONE_VERIFICATION_FAILED`** 로 거절하고 인증번호 (재)요청(§4-1)을 유도한다. `attempts`는 **챌린지가 존재하는데 코드가 불일치**할 때만 증가하며, 상한 초과 시 `429 TOO_MANY_REQUESTS`다.

- **인증**: 필수 — **(온보딩 단계, US-1-10)** 온보딩 토큰(`onboardingCompleted=false`), **또는 (프로필 연락처 변경, US-1-5) 정식 토큰(`ACTIVE`, `ROLE_USER`)**. `/auth/phone/**`는 두 티어 모두 허용한다([ADR-0034](../../adr/0034-landlord-phone-sms-verification.md) §6·§8).
- Path/Query 파라미터: 없음.

#### Request Body

```json
{
  "phoneNumber": "010-1234-5678",
  "code": "482915"
}
```

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `phoneNumber` | string | 필수 | 인증번호를 발송한 연락처와 일치해야 함 |
| `code` | string | 필수 | 발송된 인증번호. 빈 문자열 불가 |

#### 성공 Response — 200 OK

```json
{
  "success": true,
  "data": {
    "phoneNumber": "010-****-5678",
    "verified": true
  },
  "error": null
}
```

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `phoneNumber`/`code` 누락/빈값 |
| 400 | `MALFORMED_REQUEST` | JSON 파싱 불가/타입 불일치 |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 온보딩 토큰 누락/위조 / 만료 |
| 422 | `AUTH_PHONE_VERIFICATION_FAILED` | 코드 불일치, 또는 챌린지 부재(미발송·만료·이미 검증) — 부재 시 `attempts` 증가 없이 즉시 거절 |
| 429 | `TOO_MANY_REQUESTS` | 챌린지 존재 + 코드 불일치 누적으로 검증 시도 상한 초과(이메일 인증과 동일 — 검증 시도 5회) |

---

### 5. POST `/api/v1/auth/onboarding` — 온보딩 제출(가입 완료·세입자)

`TERMS_AGREED` 세입자가 필수 프로필을 제출해 가입을 완료한다. **약관 동의(§2)가 선행**되어야 한다 — 약관 미동의(`PENDING`)면 `422 AUTH_TERMS_AGREEMENT_REQUIRED`. **온보딩은 이름(`name`)과 이메일(`email`)을 받지 않는다** — 소셜 로그인 시점에 이미 `User`에 채워졌다(§1, #192). 성공 시 `ACTIVE`로 전이하고, 닉네임을 자동 배정하며 정식 access/refresh 토큰을 발급한다. 사용자 단위로 멱등 처리해 동시 요청은 한 건만 성공한다.

> 약관 동의·`termsVersion`은 §2(약관 동의)에서 이미 기록되므로 이 요청 본문에는 약관 필드를 담지 않는다. `nickname`은 서버가 형용사 풀·사물 풀의 active 단어에서 골라 `형용사 + 사물`로 조합하고 전역 유니크를 보장(충돌 시 재조합 재시도, 상한 초과 시 fallback 예: 숫자 접미사)해 자동 배정하므로 요청 본문에 담지 않는다(사용자 입력·수정 불가). **`name`·`email`은 온보딩 요청에 포함하지 않는다** — 소셜 로그인 시점에 `User`에 채워졌고(§1), 온보딩 이후 `name`은 `PATCH /users/me`(§9)로만 수정한다(`email` 수정은 #192 범위 밖의 후속 이슈로, 당분간 소셜 로그인 값으로 고정). 응답의 `countryName`·`countryFlag`는 서버가 `country`(코드)로 `countries`에서 resolve한 값이다(저장은 `country` 코드만). `countryFlag`는 **국기 이미지 URL**(flagcdn.com SVG)이다. `lang`은 **선택** 필드로, 보내면 그 값을 그대로 저장하고 보내지 않으면 저장하지 않으며(NULL) 표시 시 `en`으로 폴백한다 — `lang`을 모르는 기존 클라이언트는 `en`으로 보인다([ADR-0029](../../adr/0029-diagnosis-i18n-strategy.md) 개정(#141)). `occupation`도 **선택** 필드다(#187에서 필수→선택 완화 — 매물 추천·탐색에서 직업 정보를 활용하지 않는다) — 보내면 enum 검증 후 저장하고, 보내지 않으면 저장하지 않는다(NULL). **필수→선택 완화는 하위호환**이다: `occupation`을 보내던 기존 클라이언트의 요청은 그대로 유효하고, 값을 보낸 경우의 enum 검증도 종전과 동일하다.

- **인증**: 필수 — 소셜 로그인 단계에서 받은 온보딩 토큰(`onboardingCompleted=false`). 상태는 `TERMS_AGREED`여야 한다.
- Path/Query 파라미터: 없음.

#### Request Body

```json
{
  "gender": "MALE",
  "birthDate": "1998-04-12",
  "country": "VN",
  "lang": "en",
  "occupation": "UNDERGRADUATE_STUDENT",
  "visaType": "STUDENTS_TRAINEES"
}
```

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `gender` | string(enum) | 필수 | `MALE` \| `FEMALE` |
| `birthDate` | string(date) | 필수 | `YYYY-MM-DD`, 과거 날짜만 허용(미래 불가) |
| `country` | string | 필수 | 국적 ISO 3166-1 alpha-2 코드(예: `VN`). `countries`에 존재해야 함(없으면 `INVALID_INPUT`) |
| `lang` | string | 선택 | 표시 언어 ISO 639-1 소문자 코드. 지원 목록 `en` \| `ko` \| `ja` 중 하나여야 함(목록 밖 값은 `INVALID_INPUT`). **미전송이면 저장하지 않고(NULL) 표시 시 `en`으로 폴백**한다(`lang`을 보내지 않는 앱은 `en`으로 보인다) |
| `occupation` | string(enum) | 선택 | `UNDERGRADUATE_STUDENT` \| `GRADUATE_STUDENT` \| `EXCHANGE_STUDENT` \| `LANGUAGE_TEACHING` \| `MANUFACTURING_PRODUCTION` \| `BUSINESS_TRADE` \| `ETC` 중 하나여야 함(목록 밖 값은 `INVALID_INPUT` — 값을 보낸 경우의 enum 검증은 종전과 동일, 빈 문자열 `""`도 목록 밖 값이라 `INVALID_INPUT`). **미전송 또는 `null` 명시 전송이면(동일 취급) 저장하지 않고(NULL) 응답에서 생략**한다(#187) |
| `visaType` | string(enum) | 필수 | `SHORT_TERM_VISIT` \| `STUDENTS_TRAINEES` \| `NON_PROFESSIONAL_WORKERS` \| `WORKING_HOLIDAY_WORK_AND_VISIT` \| `OVERSEAS_KOREANS` \| `FAMILY_MARRIAGE_MIGRANTS` \| `PERMANENT_RESIDENTS` \| `PROFESSIONALS` \| `DIPLOMATIC_OFFICIAL_AND_OTHERS` \| `ETC` |

> 약관 동의(`termsOfServiceAgreed`·`privacyPolicyAgreed`·`marketingAgreed`)는 이 요청에 포함하지 않는다 — 앞선 `POST /auth/terms`(§2)에서 처리·기록된다.

#### 성공 Response — 200 OK

```json
{
  "success": true,
  "data": {
    "user": {
      "id": 1024,
      "name": "Minh Nguyen",
      "nickname": "BraveOtter",
      "gender": "MALE",
      "birthDate": "1998-04-12",
      "country": "VN",
      "countryName": "Vietnam",
      "countryFlag": "https://flagcdn.com/vn.svg",
      "lang": "en",
      "occupation": "UNDERGRADUATE_STUDENT",
      "email": "minh@example.com",
      "visaType": "STUDENTS_TRAINEES",
      "userType": "TENANT",
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

> 응답의 `occupation`은 **미설정(NULL)이면 필드 자체가 생략**된다(응답 뷰가 null 필드를 직렬화하지 않음 — 프로필 조회 §8도 동일). 따라서 REST Docs의 응답 필드 `occupation`은 **optional**로 선언한다(`lang`과 동일 — #187).

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | 필드 누락/형식·enum·날짜 위반(`gender`/`visaType` 불일치, **`occupation`은 선택이라 누락은 에러가 아니고 값을 보낸 경우 enum 목록 밖일 때만 해당**(#187), `birthDate` 형식·미래, `country` 빈값·형식, **`lang`이 지원 목록(`en`/`ko`/`ja`) 밖의 코드** 등) |
| 400 | `MALFORMED_REQUEST` | JSON 파싱 불가/타입 불일치 |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 누락/위조 / 만료 |
| 409 | `AUTH_ONBOARDING_ALREADY_COMPLETED` | 이미 `ACTIVE`인 사용자의 온보딩 재요청(동시 요청 포함) |
| 422 | `AUTH_TERMS_AGREEMENT_REQUIRED` | 약관 미동의(`PENDING`) 상태에서 온보딩 제출(약관 동의 §2 선행 필요) |

---

### 5-1. POST `/api/v1/auth/business/verify` — 사업자등록번호 검증(임대인 전용)

온보딩을 마친(`ACTIVE`) **임대인 전용**으로, 입력한 사업자등록번호를 외부 사업자등록정보 검증 API(국세청 사업자등록정보 기반, 구체 provider는 [ADR-0033](../../adr/0033-business-registry-verification.md))로 진위·영업 상태까지 확인하는 **무상태(stateless) 검증 API**다. 온보딩(§5-2)과 분리되어 있으며, 정식 access 토큰(`ROLE_USER`)을 가진 임대인이 나중에(매물 등록 시점) 호출한다. 형식(숫자 10자리) 위반은 외부 호출 전에 `400 INVALID_INPUT`으로 거른다. **정상(계속) 사업자면** `verified:true`를 응답 body로 돌려주고, 미등록·휴업·폐업 번호는 `422 AUTH_BUSINESS_NUMBER_VERIFICATION_FAILED`로 거절한다.

**무상태**: 검증 결과를 서버에 저장하지 않는다 — Redis 마커·`user.businessRegistrationNumberHash` 컬럼 어느 쪽에도 쓰지 않으며, 결과는 응답(HTTP body)에만 담긴다. 온보딩 제출에서 이 결과를 대조하는 게이트도 없다. 검증은 아웃바운드 포트 `BusinessRegistryVerifier`(인프라 어댑터: 사업자등록정보 검증 API — 국세청 사업자등록정보 진위·상태 기반)로 **동기 호출**한다. 검증 API 장애·타임아웃·5xx 등 연동 실패는 `502 UPSTREAM_ERROR`로 응답해 클라이언트가 재시도하도록 한다(공통 코드 — [error-response-guide](../error-response-guide.md) §3). 사업자등록번호 원문은 응답·로그에 노출하지 않고 마스킹한다.

- **인증**: 필수 — **정식 access 토큰(`ACTIVE`, `ROLE_USER`)**. 온보딩 토큰(`PENDING`/`TERMS_AGREED`, `ROLE_ONBOARDING`)으로 호출하면 `403 AUTH_ONBOARDING_REQUIRED`, 임대인이 아닌(`userType=TENANT`) ACTIVE 사용자면 `403 FORBIDDEN`이다.
- Path/Query 파라미터: 없음.

#### Request Body

```json
{
  "businessRegistrationNumber": "1234567890"
}
```

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `businessRegistrationNumber` | string | 필수 | 숫자 10자리 또는 하이픈 형식(예 `123-45-67890`) — 어댑터가 하이픈을 제거해 조회·대조. 빈 문자열·형식 위반 불가(`INVALID_INPUT` — 외부 호출 전 거름) |

#### 성공 Response — 200 OK

```json
{
  "success": true,
  "data": {
    "businessRegistrationNumber": "****567890",
    "verified": true
  },
  "error": null
}
```

> `businessRegistrationNumber`는 마스킹해 반환한다(예: `****567890` — 마스킹 형식 확인 필요). **검증 결과는 서버에 저장하지 않는다**(무상태) — Redis 마커·`user.businessRegistrationNumberHash` 어느 쪽에도 쓰지 않으며 응답 body(`verified:true`)로만 회신한다. 검증 서비스가 회신한 상호·대표자 등 표시용 정보의 응답 노출 여부는 확인 필요.

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `businessRegistrationNumber` 누락/빈값/형식(숫자 10자리) 위반 — 외부 호출 전 거름 |
| 400 | `MALFORMED_REQUEST` | JSON 파싱 불가/타입 불일치 |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 정식 토큰 누락/위조 / 만료 |
| 403 | `AUTH_ONBOARDING_REQUIRED` | 온보딩 토큰(`PENDING`/`TERMS_AGREED`, `ROLE_ONBOARDING`)으로 호출(정식 토큰 필요 — 온보딩 완료 후 호출하는 API) |
| 403 | `FORBIDDEN` | 임대인이 아닌(`userType=TENANT`) ACTIVE 사용자의 요청(임대인 전용) |
| 422 | `AUTH_BUSINESS_NUMBER_VERIFICATION_FAILED` | 검증 서비스 조회 결과 미등록이거나 휴업·폐업 상태(진위·상태 검증 실패) |
| 429 | `TOO_MANY_REQUESTS` | 검증 시도 레이트리밋 초과(확인 필요: 시도 상한·간격 임계값) |
| 502 | `UPSTREAM_ERROR` | 사업자등록정보 검증 API 장애·타임아웃·5xx. 클라이언트 재시도 유도(공통 코드 — [error-response-guide](../error-response-guide.md) §3) |

---

### 5-2. POST `/api/v1/auth/landlord/onboarding` — 임대인 온보딩 제출(임대인 전용·가입 완료)

`TERMS_AGREED` 사용자가 임대인 필수 프로필을 제출해 가입을 완료한다(세입자 온보딩 §5와 분리된 **임대인 전용 엔드포인트**). **약관 동의(§2)·연락처 인증(§4-1·§4-2)이 선행**되어야 한다 — **임대인 온보딩은 약관 동의 + 연락처(SMS) 인증만으로 완료**되며, 사업자등록번호는 수집·검증하지 않는다(온보딩 후 매물 등록 시점에 별도 검증 API(§5-1)로 검증). 성공 시 `ACTIVE`로 전이하고 **`userType`을 `LANDLORD`로 확정**하며, 닉네임을 자동 배정하고 정식 access/refresh 토큰을 발급한다(상태 전이 액션이므로 `200`). 사용자 단위로 멱등 처리해 동시 요청은 한 건만 성공한다. 임대인은 성별·직업·비자정보를 온보딩에서 수집하지 않으며(**이름 `name`과 이메일 `email`은 세입자와 동일하게 소셜 로그인 시 provider 값으로 이미 확정돼 있어 온보딩에서 재입력·재수집하지 않는다** — #192; 생년월일 `birthDate`은 세입자와 동일하게 필수 수집한다 — [#131](https://github.com/swyp-app-5th-team1/Kohere-backend/issues/131)). 이름은 단일 `name`이다(#192에서 세입자의 `firstName`/`lastName`도 단일 `name`으로 통합돼 두 역할의 이름 모델·수집 시점이 완전히 통일됐다 — 이후 수정은 `PATCH /users/me` §9). **국적 `country`와 표시 언어 `lang`은 클라이언트가 보내지 않고 서버가 `country="KR"`·`lang="ko"`로 고정 부여**한다 — 임대인은 한국인 사업자를 전제하므로 국적을 묻지 않고, 한국어로만 서비스를 본다(변경 불가). 이는 [ADR-0034](../../adr/0034-landlord-phone-sms-verification.md)의 "임대인 국적 미수집" 결정을 개정한 것으로, 요청 본문은 `{ phoneNumber, birthDate }` 두 필드이고 응답에는 소셜 로그인 시 확정된 `name`·`email`과 서버 고정 국적·국기·언어가 더해진다.

> **검증 게이트 우선순위**: 약관 미동의(`PENDING`) → `422 AUTH_TERMS_AGREEMENT_REQUIRED`(이미 `ACTIVE`면 `409 AUTH_ONBOARDING_ALREADY_COMPLETED`) → 제출 `phoneNumber` 미검증·불일치 → `422 AUTH_PHONE_NOT_VERIFIED` 순으로 판정한다(약관 → 연락처, 사업자번호 게이트 없음). 약관 동의·`termsVersion`은 §2에서 이미 기록되므로 이 요청 본문에 약관 필드를 담지 않는다. `phoneNumber`는 §4-1·§4-2로 검증 완료된 값과 일치해야 한다. `nickname`은 서버가 자동 배정하므로 요청 본문에 담지 않는다(사용자 입력·수정 불가).

- **인증**: 필수 — 소셜 로그인 단계에서 받은 온보딩 토큰(`onboardingCompleted=false`). 상태는 `TERMS_AGREED`여야 한다.
- Path/Query 파라미터: 없음.

#### Request Body

```json
{
  "phoneNumber": "010-1234-5678",
  "birthDate": "1998-04-12"
}
```

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `phoneNumber` | string | 필수 | 전화번호 형식. **§4-1·§4-2로 사전 검증된 값과 일치**해야 함(미검증·불일치 `AUTH_PHONE_NOT_VERIFIED` 422) |
| `birthDate` | string(date) | 필수 | `YYYY-MM-DD`, 과거 날짜만 허용(미래 불가) — 세입자 온보딩(§5)과 동일 규칙 |

#### 성공 Response — 200 OK

```json
{
  "success": true,
  "data": {
    "user": {
      "id": 2048,
      "name": "Kim Minsu",
      "nickname": "BraveOtter",
      "birthDate": "1998-04-12",
      "phoneNumber": "010-****-5678",
      "country": "KR",
      "countryName": "South Korea",
      "countryFlag": "https://flagcdn.com/kr.svg",
      "lang": "ko",
      "email": "minsu@example.com",
      "userType": "LANDLORD",
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

> 임대인 응답은 세입자와 달리 `gender`·`occupation`·`visaType`을 포함하지 않는다. **`email`은 세입자와 동일하게 소셜 로그인 시 provider 값으로 확정돼 임대인도 보유하므로 응답에 포함한다**([ADR-0034](../../adr/0034-landlord-phone-sms-verification.md)의 "임대인 이메일 미수집" 결정을 개정(#192) — 이메일은 인증 대상 아닌 미검증 연락처). **`birthDate`는 임대인도 온보딩에서 수집하므로 응답에 포함한다.** **`country`·`countryName`·`countryFlag`·`lang`은 서버가 고정 부여한 값(`KR`·`ko`)이라 임대인 응답에도 포함한다**(요청 본문에는 없다 — [ADR-0034](../../adr/0034-landlord-phone-sms-verification.md) 개정(#141)). `phoneNumber`는 마스킹해 반환한다(예: `010-****-5678` — 프로필 조회 §8은 본인이라 평문). `marketingAgreed`는 포함한다(약관 동의 시 확정). 사업자등록번호는 온보딩에서 수집하지 않으므로 응답에도 포함하지 않는다(온보딩 후 별도 검증 §5-1). 임대인 프로필 조회·수정은 `GET`(§8)·`PATCH`(§9) `/users/me`에서 `userType`에 따라 분기해 다룬다.

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `phoneNumber` 누락·빈값·형식(전화번호) 위반, `birthDate` 누락·형식·미래 날짜 위반(`errors[]`로 위반 필드 반환) |
| 400 | `MALFORMED_REQUEST` | JSON 파싱 불가/타입 불일치 |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 누락/위조 / 만료 |
| 409 | `AUTH_ONBOARDING_ALREADY_COMPLETED` | 이미 `ACTIVE`인 사용자의 온보딩 재요청(동시 요청 포함 — 한 요청만 성공) |
| 422 | `AUTH_TERMS_AGREEMENT_REQUIRED` | 약관 미동의(`PENDING`) 상태에서 온보딩 제출(약관 동의 §2 선행 — 우선 판정) |
| 422 | `AUTH_PHONE_NOT_VERIFIED` | 제출 `phoneNumber`가 미검증이거나 검증한 번호와 불일치(연락처 인증 §4-1·§4-2 선행) |

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

인증된 본인의 프로필을 조회한다. **응답 필드는 `userType`에 따라 갈린다** — 세입자(`TENANT`)는 이름(단일 `name`)·국적·직업·비자정보 등 외국인 프로필을, 임대인(`LANDLORD`)은 단일 `name`·연락처 중심 프로필을 받는다(#192에서 세입자·임대인 모두 단일 `name`으로 통일).

- **인증**: 필수(ACTIVE 사용자). PENDING 토큰 접근은 `403 AUTH_ONBOARDING_REQUIRED`.
- Path/Query 파라미터: 없음.

#### 성공 Response — 세입자(TENANT) (200 OK)

```json
{
  "success": true,
  "data": {
    "id": 1024,
    "userType": "TENANT",
    "name": "Minh Nguyen",
    "nickname": "BraveOtter",
    "gender": "MALE",
    "birthDate": "1998-04-12",
    "country": "VN",
    "countryName": "Vietnam",
    "countryFlag": "https://flagcdn.com/vn.svg",
    "lang": "en",
    "occupation": "UNDERGRADUATE_STUDENT",
    "email": "minh@example.com",
    "visaType": "STUDENTS_TRAINEES",
    "status": "ACTIVE",
    "termsOfServiceAgreed": true,
    "privacyPolicyAgreed": true,
    "marketingAgreed": false,
    "createdAt": "2026-06-15T08:30:00Z"
  },
  "error": null
}
```

#### 성공 Response — 임대인(LANDLORD) (200 OK)

```json
{
  "success": true,
  "data": {
    "id": 2048,
    "userType": "LANDLORD",
    "name": "Kim Minsu",
    "nickname": "BraveOtter",
    "birthDate": "1998-04-12",
    "phoneNumber": "010-1234-5678",
    "country": "KR",
    "countryName": "South Korea",
    "countryFlag": "https://flagcdn.com/kr.svg",
    "lang": "ko",
    "email": "minsu@example.com",
    "status": "ACTIVE",
    "termsOfServiceAgreed": true,
    "privacyPolicyAgreed": true,
    "marketingAgreed": false,
    "createdAt": "2026-06-15T08:30:00Z"
  },
  "error": null
}
```

> 본인 프로필이므로 `phoneNumber`는 평문으로 반환한다(로그·타 사용자 노출 시에만 마스킹). **세입자·임대인 모두 이름은 단일 `name`으로 반환한다**(#192 — 세입자의 `firstName`/`lastName`은 단일 `name`으로 통합). 세입자 응답의 `occupation`은 온보딩에서 **선택**(#187)이라 **미설정이면 필드가 생략**된다(온보딩 응답 §5와 동일 — REST Docs 응답 필드는 optional로 선언).
> 임대인 응답은 세입자 전용 필드(`gender`·`occupation`·`visaType`)를 포함하지 않는다. **`email`은 세입자와 동일하게 소셜 로그인 시 provider 값으로 확정돼 임대인도 보유하므로 응답에 포함한다**([ADR-0034](../../adr/0034-landlord-phone-sms-verification.md)의 "임대인 이메일 미수집" 결정을 개정(#192) — 이메일은 인증 대상 아닌 미검증 연락처). **`birthDate`는 임대인도 온보딩에서 수집하므로 응답에 포함한다.** **`country`·`countryName`·`countryFlag`·`lang`은 임대인 응답에도 포함한다** — 온보딩에서 서버가 `KR`·`ko`로 고정 부여하기 때문이며(클라이언트가 보내지 않는다), 이는 [ADR-0034](../../adr/0034-landlord-phone-sms-verification.md)의 "임대인 국적 미수집" 결정을 개정한 것이다. **`businessRegistrationNumber`는 온보딩에서 수집하지 않으므로(온보딩 후 별도 검증 §5-1, 결과 미저장) 응답에 포함하지 않는다.**

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 누락/위조 / 만료 |
| 403 | `AUTH_ONBOARDING_REQUIRED` | 온보딩 미완료(PENDING·TERMS_AGREED) 토큰으로 접근 |
| 404 | `USER_NOT_FOUND` | 사용자가 `WITHDRAWN`이거나 삭제되어 없음 |

---

### 9. PATCH `/api/v1/users/me` — 내 프로필 부분 수정

본인 프로필을 부분 수정한다. 전송한 필드만 변경하고, 미전송 필드는 유지한다(미전송 ≠ 값 비움 — 현재 수정 대상 필드는 비움 불가). **수정 가능 필드는 `userType`에 따라 갈린다** — 세입자(`TENANT`)는 이름(단일 `name`)·국적·표시 언어(`lang`)·직업·비자정보·마케팅 동의를, 임대인(`LANDLORD`)은 `name`·`phoneNumber`·`marketingAgreed`만 수정한다(**임대인은 `lang`을 바꿀 수 없다** — `ko` 고정, [ADR-0034](../../adr/0034-landlord-phone-sms-verification.md) 개정(#141)).

- **인증**: 필수(ACTIVE 사용자). PENDING 토큰 접근은 `403 AUTH_ONBOARDING_REQUIRED`.
- Path/Query 파라미터: 없음.

#### Request Body — 세입자(TENANT) (모든 필드 선택)

```json
{
  "country": "KR",
  "lang": "ko",
  "occupation": "BUSINESS_TRADE",
  "visaType": "SHORT_TERM_VISIT",
  "marketingAgreed": true
}
```

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `name` | string | 선택 | 성·이름을 합친 단일 이름. 빈 문자열 불가 |
| `gender` | string(enum) | 선택 | `MALE` \| `FEMALE` |
| `birthDate` | string(date) | 선택 | `YYYY-MM-DD`, 과거 날짜만 |
| `country` | string | 선택 | 국적 ISO 코드(예: `KR`). `countries`에 존재해야 함 |
| `lang` | string | 선택 | 표시 언어 ISO 639-1 소문자 코드. 빈 문자열 불가. 지원 목록 `en` \| `ko` \| `ja` 중 하나여야 함(목록 밖 값은 `INVALID_INPUT`) |
| `occupation` | string(enum) | 선택 | 직업 enum(위 목록과 동일) |
| `visaType` | string(enum) | 선택 | 비자정보 enum(위 목록과 동일) |
| `marketingAgreed` | boolean | 선택 | 마케팅 수신 동의 |

#### Request Body — 임대인(LANDLORD) (모든 필드 선택)

```json
{
  "name": "Kim Minsu",
  "phoneNumber": "010-1234-5678",
  "marketingAgreed": true
}
```

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `name` | string | 선택 | 성·이름을 합친 단일 이름. 빈 문자열 불가. 저장은 단일 `name`(§핵심 개념 표 참조) |
| `phoneNumber` | string | 선택 | 전화번호 형식. 빈 문자열 불가. **변경 시 SMS 재인증(§4-1·§4-2) 필요** — 새 번호가 VERIFIED일 때만 반영(미인증·불일치 `AUTH_PHONE_NOT_VERIFIED` 422) |
| `marketingAgreed` | boolean | 선택 | 마케팅 수신 동의 |

> **세입자 전용 — `country`·`lang` 독립**: `country`와 `lang`은 서로 독립이다 — `country`만 바꿔도 `lang`은 그대로 유지되고(국적을 바꿔도 표시 언어는 따라 바뀌지 않는다), `lang`만 보내면 `country`는 그대로 둔다([ADR-0029](../../adr/0029-diagnosis-i18n-strategy.md) 개정(#141)).
> 필수 약관 동의(`termsOfServiceAgreed`/`privacyPolicyAgreed`)는 이 엔드포인트로 철회할 수 없다(탈퇴 경로로만 처리). (확인 필요: 동의 철회 정책)
> `nickname`은 시스템 배정값이라 수정 대상이 아니다(세입자·임대인 공통 불변). **세입자·임대인 모두** `email` 변경은 **#192 범위 밖(후속 이슈)** 이라 이 엔드포인트로 수정하지 않는다 — 당분간 소셜 로그인 provider 값으로 고정한다(임대인도 소셜 로그인 시 provider email을 보유하며, 수정은 세입자와 동일하게 후속 이슈다). 정식(ACTIVE) 사용자의 이메일 인증 API(§3·§4)는 접근만 ACTIVE로 열어 두었고, 실제 이메일 변경 반영은 후속 이슈다.
> **임대인 전용**: `userType`은 온보딩으로 확정된 뒤 불변이다. `birthDate`는 온보딩에서 수집·확정하며 이 경로의 수정 대상이 아니다(임대인 조회 전용 — [#131](https://github.com/swyp-app-5th-team1/Kohere-backend/issues/131)). `businessRegistrationNumber`는 온보딩·프로필에서 수집·저장하지 않으므로 이 경로의 수정 대상이 아니다(필요 시 별도 검증 API §5-1로 무상태 검증). **`phoneNumber` 변경은 SMS 재인증(§4-1·§4-2)이 필요하다** — 새 번호를 재인증(VERIFIED)한 뒤에만 반영하며, 미인증·불일치는 `422 AUTH_PHONE_NOT_VERIFIED`다(온보딩 시 연락처 인증과 동일한 발송·확인을 정식 토큰 컨텍스트에서 재사용 — [ADR-0034](../../adr/0034-landlord-phone-sms-verification.md)). **클라이언트 계약**: 앱은 연락처 변경 시 **PATCH 이전에 새 번호 인증(§4-1·§4-2)을 먼저 수행**한다(정상 흐름). `422 AUTH_PHONE_NOT_VERIFIED`는 happy path가 아니라 **미인증·마커 TTL 만료·불일치 제출에 대한 서버 가드**다.

#### 성공 Response — 200 OK

수정된 프로필 전체를 `GET /users/me`와 동일 스키마의 공통 래퍼로 반환한다.

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | **`gender`/`visaType`/`occupation`이 허용 목록 밖**, `birthDate`가 `YYYY-MM-DD` 형식이 아니거나 미래 날짜, `country` 미존재(`countries`에 없음), **`lang`이 지원 목록(`en`/`ko`/`ja`) 밖의 코드·빈 문자열** 등 값 검증 위반. 위반 필드는 `errors[]`로 반환한다 |
| 400 | `MALFORMED_REQUEST` | 요청 본문을 JSON으로 해석할 수 없는 경우뿐이다. **enum 후보(`gender`/`visaType`/`occupation`)와 `birthDate`는 요청 DTO가 String으로 받아 서버가 파싱하므로 값 위반도 `INVALID_INPUT`이다** — 온보딩(§5)과 같은 코드다(#151에서 통일) |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 누락/위조 / 만료 |
| 403 | `AUTH_ONBOARDING_REQUIRED` | 온보딩 미완료(PENDING·TERMS_AGREED) 토큰으로 접근 |
| 404 | `USER_NOT_FOUND` | 사용자가 `WITHDRAWN`이거나 삭제되어 없음 |
| 422 | `AUTH_PHONE_NOT_VERIFIED` | (임대인) 새 `phoneNumber`로 변경 시 그 번호가 SMS 재인증(§4-1·§4-2)되지 않았거나 검증한 번호와 불일치 |

---

### 10. DELETE `/api/v1/users/me` — 회원 탈퇴

본인 계정을 탈퇴 처리한다. 사용자 상태를 `WITHDRAWN`으로 전이하고 모든 refresh 토큰을 무효화한다. PENDING(온보딩 미완료) 사용자도 탈퇴할 수 있다(온보딩 중단·정리 목적). **Apple 연동 계정은 저장된 `apple_refresh_token`으로 Apple `/auth/revoke`를 호출해 앱↔Apple ID 연동까지 폐기**한다(App Store 5.1.1(v), [ADR-0031](../../adr/0031-apple-sign-in-authorization-code-flow.md)).

- **인증**: 필수.
- Path/Query 파라미터: 없음.
- Request Body: 없음.

#### 성공 Response — 204 No Content

본문 없음. 개인정보(세입자: 이름·생년월일·국적·표시 언어·직업·이메일·비자·닉네임 / 임대인: 이름·생년월일·연락처·국적·표시 언어·이메일·닉네임, 사업자번호 해시가 저장돼 있으면 함께)는 탈퇴 시 즉시 익명화, social_accounts 매핑 삭제([ADR-0014](../../adr/0014-withdrawal-pii-anonymization.md)). Apple 연동은 매핑 삭제 전에 `/auth/revoke`로 폐기하며, **best-effort**(이미 폐기·Apple 장애여도 탈퇴는 완료)다([ADR-0031](../../adr/0031-apple-sign-in-authorization-code-flow.md)).

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 누락/위조 / 만료 |
| 409 | `USER_ALREADY_WITHDRAWN` | 이미 `WITHDRAWN`된 사용자의 탈퇴 재요청 |
| 404 | `USER_NOT_FOUND` | 사용자가 삭제되어 없음 |

---

### 11. GET `/api/v1/users/me/blocks` — 내 차단 목록

본인이 차단한 사용자 목록을 조회한다. **차단 해제(§12)를 위한 조회 경로**로, 차단한 시각(`blockedAt`) 내림차순 **오프셋 페이지네이션**(api-design-guide §4-1)이다. 차단은 `user_blocks(blocker_id, blocked_user_id)` 한 행으로 표현하며 `user` 모듈이 소유한다.

> **왜 사용자 단위 차단인가**: 차단 대상은 **예약이 아니라 사용자**다. 한 임대인은 **매물(`Listing.landlordId`)을 여러 개** 소유하고, 한 매물은 **방 상품(`Listing.roomOffers`)을 여러 개** 갖는다. 그래서 예약(방) 단위로 차단하면 상대는 **자기 다른 방 상품에 신청**하는 것만으로 새 예약 = 새 채팅방을 만들어 차단을 우회한다. 차단은 본질적으로 **사람**에 대한 것이라, 대상이 예약이면 상대가 방을 하나 더 가진 순간 무력해진다. 사용자 단위여야 상대의 모든 매물·방으로 효력이 미친다.
> — 보조 근거(중복 방지 반영): `bookings`에는 중복 방지 유니크 제약(`uq_bookings_tenant_room_offer` on `(tenant_id, room_offer_id)`)이 있어 **같은 방 상품 재신청은 `409 BOOKING_ALREADY_EXISTS`로 막힌다**. 그래도 임대인은 **매물·방 상품을 여러 개** 가지므로 상대는 **다른 방으로는 여전히 우회**할 수 있다 — 그래서 여전히 사용자 단위여야 한다. 이 보조 근거는 위 구조적 근거를 뒤집지 않는다(중복 제한 여부와 무관하게 사용자 단위 결론은 성립).
> **왜 해제가 예약이 아니라 여기 있는가**: 차단하면 그 상대와의 예약이 내 목록에서 전부 사라져(아래 의미론) **`bookingId`를 다시 얻을 수 없다**. `bookingId`를 경로에 요구하는 해제 API는 성립하지 않으므로, 해제 경로는 예약과 무관한 `/users/me/blocks`여야 한다. 목록(§11)이 해제(§12)의 유일한 대상 공급원이다.
> **왜 `is_active` 컬럼이 없는가**: **행의 존재가 곧 차단**이고 해제는 행 삭제다. 상태 플래그를 두면 "행은 있는데 차단이 아닌" 상태가 생겨 목록·필터 술어가 두 갈래로 갈린다.

**차단 의미론**(생성은 §04, 효과는 두 방향이 다르다 — 반드시 구분한다):

| 효과 | 방향 | 동작 |
| --- | --- | --- |
| 예약 목록·상세 숨김 | **단방향(차단자 기준)** | 내가 A를 차단하면 **A와의 모든 예약**이 내 목록·상세에서 사라진다(상세는 `404 BOOKING_NOT_FOUND`). **A의 목록은 그대로**라 A에게는 예약이 계속 보인다 |
| 신규 예약 신청 | **양방향** | 어느 한쪽이라도 차단 관계면 `POST /api/v1/listings/{listingId}/bookings`가 `403 FORBIDDEN`이다. 단방향으로 두면 상대가 신청은 성공(`201`)하는데 내 목록엔 영영 보이지 않는 **블랙홀 예약**이 생긴다 |

- **인증**: 필수. `ACTIVE` 사용자 전용(세입자·임대인 공통, 역할 `403` 없음).
- Path 파라미터: 없음.

> **차단 생성 경로는 여기에 없다** — 생성은 예약 문맥 전용 `POST /api/v1/bookings/{bookingId}/block`([04-booking-inquiry-chat](04-booking-inquiry-chat.md))뿐이고, **`userId`로 차단을 만드는 엔드포인트는 두지 않는다**. 차단 상대는 클라이언트가 보내지 않고 **서버가 예약에서 도출**(`요청자 == tenantId ? landlordId : tenantId`)하기 때문이다. 앱에는 임의의 사용자를 지목해 차단하는 화면 자체가 없다(차단은 예약으로 맺어진 상대에게만 성립).

#### Query 파라미터

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `page` | int | 선택 | 0 | 0-base 페이지 번호 |
| `size` | int | 선택 | 20 | 페이지 크기(최대 100). 범위 초과는 `INVALID_INPUT`(400) |

> 정렬은 `blockedAt,desc` 고정(쿼리로 변경 불가). 상대 표시명(`name`)은 `user` 모듈 내부 조회로 채운다 — 노출은 확정이고 **마스킹 수준만 (확인 필요)**(아래 필드 표).

#### 성공 Response — 200 OK

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "userId": 2048,
        "name": "Kim Minsu",
        "blockedAt": "2026-06-15T08:30:00Z"
      }
    ],
    "page": {
      "number": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false
    }
  },
  "error": null
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `userId` | number | 차단한 상대의 사용자 ID. §12의 경로 변수로 그대로 쓴다 |
| `name` | string | 차단한 상대의 표시명(세입자·임대인 모두 단일 `name`). 해제 UI(§12)가 대상을 식별해야 하므로 **노출한다** — 다만 타 사용자 정보(PII)라 **마스킹 수준은 (확인 필요)**(원문 그대로 vs 부분 마스킹) |
| `blockedAt` | string(date-time) | 차단 시각(ISO-8601 UTC) |

> 차단이 하나도 없으면 `content: []` + `page.totalElements: 0` + `page.hasNext: false`(에러 아님). 차단한 상대가 탈퇴(`WITHDRAWN`)해도 행은 남으므로 목록에 나타난다 — 표시명 익명화는 탈퇴 시 익명화 정책([ADR-0014](../../adr/0014-withdrawal-pii-anonymization.md))을 따른다.

> **SecurityConfig 매처 주의**: 현행 `/api/v1/users/me` 매처(`SecurityConfig.java:61`)는 `**`가 아닌 **정확 경로**라 `/api/v1/users/me/blocks`·`/api/v1/users/me/blocks/*`를 **덮지 않는다**. 두 경로용 매처를 `hasRole("USER")`로 **명시 추가**해야 하며, 빠뜨리면 `anyRequest().authenticated()`로 떨어져 **온보딩 토큰(`ROLE_ONBOARDING`)이 그대로 통과**한다(`403 AUTH_ONBOARDING_REQUIRED`가 나가지 않는다). 같은 이유로 `DELETE /api/v1/users/me` 매처(`SecurityConfig.java:58`, 온보딩 토큰 허용 — 탈퇴)도 `/me/blocks/*`에는 적용되지 않는다.

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `page`/`size` 범위 위반(음수 `page`, `size` 1 미만·100 초과). 보정하지 않고 거절한다 |
| 400 | `MALFORMED_REQUEST` | `page`/`size`가 정수가 아님(쿼리 파라미터 타입 불일치) |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 누락/위조 / 만료 |
| 403 | `AUTH_ONBOARDING_REQUIRED` | 온보딩 미완료(PENDING·TERMS_AGREED) 토큰으로 접근 |

---

### 12. DELETE `/api/v1/users/me/blocks/{userId}` — 차단 해제

`{userId}`에 대한 내 차단을 해제한다. `user_blocks`에서 `(blocker_id=요청자, blocked_user_id={userId})` **행을 삭제**한다(`is_active` 플래그를 내리는 게 아니다 — §11 근거). 해제 즉시 그 상대와의 예약이 내 목록·상세에 다시 나타난다 — 단 내가 [04 §4](04-booking-inquiry-chat.md)로 **직접 삭제한 예약은 `*_deleted_at`이 남아 계속 숨겨진다**(차단과 삭제는 독립된 숨김 사유다). 신규 예약 신청은 **역방향 차단이 없을 때만** 다시 가능해진다 — 가드는 양방향이라 상대가 나를 차단한 행이 남아 있으면 여전히 `403 FORBIDDEN`이다(차단은 방향별로 별개 행이다 — §11 의미론).

- **인증**: 필수. `ACTIVE` 사용자 전용(세입자·임대인 공통).

#### Path 파라미터

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `userId` | number | 필수 | 해제할 상대의 사용자 ID. §11 목록의 `userId`를 그대로 쓴다 |

- Request Body: 없음.

#### 성공 Response — 204 No Content

본문 없음. **멱등** — 차단한 적이 없거나 이미 해제한 `userId`로 호출해도 `404`가 아니라 `204`다(삭제할 행이 없으면 아무것도 하지 않는다). "차단 아님"이라는 목표 상태가 이미 성립하므로 재시도·중복 탭이 실패로 보이지 않게 한다.

> 존재하지 않는 `userId`·탈퇴한 사용자에게도 `204`다 — 차단 여부는 **내 `user_blocks` 행의 유무**로만 판정하며, 상대 사용자의 실재 여부를 확인하지 않는다(확인하면 임의 `userId`를 넣어 계정 존재를 탐지할 수 있다). 같은 이유로 `USER_NOT_FOUND`(404)를 내지 않는다.

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `MALFORMED_REQUEST` | `userId`가 숫자가 아님(경로 변수 타입 불일치) |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 누락/위조 / 만료 |
| 403 | `AUTH_ONBOARDING_REQUIRED` | 온보딩 미완료(PENDING·TERMS_AGREED) 토큰으로 접근(§11의 SecurityConfig 매처 주의 참조) |

---

## 도메인 에러 코드

> 공통 코드(`INVALID_INPUT`, `MALFORMED_REQUEST`, `UNAUTHENTICATED`, `TOKEN_EXPIRED`, `FORBIDDEN`, `RESOURCE_NOT_FOUND` 등)는 [error-response-guide](../error-response-guide.md) §3·§4를 따르며 여기서 재정의하지 않는다. provider/idToken 등 입력 형식 위반은 별도 도메인 코드 없이 공통 코드로 처리한다 — Bean Validation 위반(누락·빈값)은 `INVALID_INPUT`, 역직렬화 실패(허용 외 enum 문자열 등)는 `MALFORMED_REQUEST`. 아래는 auth/user 도메인 고유 코드만 정의한다. prefix는 `AUTH` / `USER`.

| code | status | 의미 |
| --- | --- | --- |
| `AUTH_MISSING_CREDENTIAL` | 400 | provider의 자격 필드 누락(Google `idToken` 또는 Apple `authorizationCode` 미전송) |
| `AUTH_INVALID_SOCIAL_TOKEN` | 401 | Google `idToken` 검증 실패(서명/`aud`/`iss`/`exp`), 또는 Apple `authorizationCode` 교환 실패·교환 `id_token` 검증 실패(위조·만료·앱 불일치·재사용 코드) |
| `AUTH_EMAIL_REQUIRED` | 422 | 소셜 로그인(`POST /auth/social-login`) 시 토큰의 `email` 클레임·요청 `email` 어느 쪽에도 이메일이 없음(provider 진본 이메일 확정 불가) |
| `AUTH_EMAIL_MISMATCH` | 422 | 소셜 로그인 요청 `email`이 토큰의 `email` 클레임과 불일치(email은 provider 진본으로 확정) |
| `AUTH_EMAIL_VERIFICATION_FAILED` | 422 | 이메일 인증번호 불일치 또는 만료(미발송·만료·오입력) — 세입자(정식(ACTIVE) 사용자 이메일 인증 §3·§4) |
| `AUTH_PHONE_VERIFICATION_FAILED` | 422 | 연락처(SMS) 인증번호 불일치 또는 만료(미발송·만료·오입력) — 임대인 |
| `AUTH_PHONE_NOT_VERIFIED` | 422 | 임대인 온보딩 제출 또는 프로필 연락처 변경 시 `phoneNumber`가 미검증이거나 검증한 번호와 불일치 |
| `AUTH_REQUIRED_AGREEMENT_MISSING` | 422 | 필수 약관(이용약관/개인정보처리방침) 미동의(약관 동의 `POST /auth/terms`) |
| `AUTH_TERMS_AGREEMENT_REQUIRED` | 422 | 약관 미동의(`PENDING`) 상태로 온보딩 제출 또는 연락처 인증(약관 동의 선행 필요) |
| `AUTH_BUSINESS_NUMBER_VERIFICATION_FAILED` | 422 | 사업자번호 검증(`POST /auth/business/verify`) 시 검증 서비스 조회 결과 미등록·휴업·폐업(진위·상태 검증 실패) |
| `AUTH_ONBOARDING_REQUIRED` | 403 | 온보딩 미완료(`PENDING`/`TERMS_AGREED`) 상태로 보호 API 접근 |
| `AUTH_ONBOARDING_ALREADY_COMPLETED` | 409 | 이미 온보딩 완료(ACTIVE)된 사용자가 온보딩 재요청 |
| `AUTH_INVALID_REFRESH_TOKEN` | 401 | refresh 토큰 만료/위조/무효화/재사용 탐지 |
| `USER_NOT_FOUND` | 404 | 대상 사용자가 없거나 탈퇴되어 조회 불가 |
| `USER_ALREADY_WITHDRAWN` | 409 | 이미 탈퇴(WITHDRAWN)된 사용자에 대한 탈퇴 재요청 |
