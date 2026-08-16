# ADR-0047. 웹 로컬 자격증명을 별도 테이블로 분리하고 휴대폰 번호로 계정을 공유한다

| 항목 | 값 |
|---|---|
| 번호 | ADR-0047 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-08-16 |
| 기준 코드 | `feature/229-web-landlord-auth` @ `86654fb`. 본 ADR의 파일·경로 참조는 전부 이 시점 기준이며, 재검증 없이 인용하지 않는다 |
| 관련 문서 | [ADR-0001](./0001-bounded-context-module-decomposition.md), [ADR-0003](./0003-jwt-auth-after-oauth-login.md), [ADR-0005](./0005-polyglot-persistence.md), [ADR-0008](./0008-mysql-migration-flyway.md), [ADR-0014](./0014-withdrawal-pii-anonymization.md), [ADR-0030](./0030-error-message-i18n-resource-bundle.md), [ADR-0034](./0034-landlord-phone-sms-verification.md), [ADR-0048](./0048-web-refresh-token-httponly-cookie.md), [US-1-11·US-1-12·US-1-13·US-1-15](../requirements/user-stories.md), [01-auth-onboarding](../api/specs/01-auth-onboarding.md), [error-response-guide §4](../api/error-response-guide.md), [database-design §4](../database/database-design.md), [code-style §3](../convention/code-style.md) |

## Status

Proposed

## Context

임대인이 매물을 등록하는 **웹**(앱과 별개 클라이언트)에 로그인·회원가입을 붙인다. 소셜이 아니라 **이메일 + 비밀번호**다. 지금까지 인증은 소셜 OIDC 하나뿐이었으므로([ADR-0003](./0003-jwt-auth-after-oauth-login.md)) 서버에 비밀번호 자격증명이 존재한 적이 없다.

**진짜 제약은 인증 방식이 아니라 소유권 사슬이다.** 임대인 데이터는 `users.id` 하나에 전부 매달려 있다.

```
매물 등록  listing.landlordId = principal.userId()          (ListingV2Controller.register)
예약 생성  booking.landlordId = offer.landlordId()          (BookingService — 비정규화 복사)
예약 조회  getUserType(userId)=="LANDLORD"
             → bookingRepository.findVisibleByLandlordId(userId, …)   (BookingService)
```

즉 **웹 가입이 새 `users` 행을 만드는 순간 `landlordId`가 갈라지고, 앱은 웹에서 등록한 매물의 예약을 영원히 보지 못한다.** 채팅도 같은 축을 쓸 예정이라 같은 결과가 된다. 이 기능의 존재 이유가 "웹에서 등록한 매물의 신청을 앱에서 본다"이므로, 계정이 갈라지면 기능 자체가 성립하지 않는다.

두 클라이언트의 계정을 같다고 판정할 재료는 하나뿐이다. `users.phone_number`는 **임대인 온보딩의 SMS 인증값으로만 채워지고 세입자는 NULL**이며([V8](../../src/main/resources/db/migration/V8__users_landlord_fields.sql)), 그 번호는 이미 소유가 증명된 값이다([ADR-0034](./0034-landlord-phone-sms-verification.md)). 반대로 소셜 로그인 시점에 서버가 아는 것은 provider·`sub`·`email`·`name`뿐이라 **그 시점엔 번호를 모른다** — `AuthService`가 `createPendingUser(name, email)`로 `users` 행을 먼저 만든다.

기존 SMS 인증은 그대로 재사용할 수 없다. 챌린지 키가 `phone-verify:code:{userId}`라 **계정이 없는 단계에서 쓸 수 없고**, 경로도 인증 필요 티어다([ADR-0034](./0034-landlord-phone-sms-verification.md) §8).

현재 `users.phone_number`에는 인덱스도 UNIQUE도 없다(V8). [ADR-0034](./0034-landlord-phone-sms-verification.md)가 "연락처 유니크 제약(동일 번호 다계정 허용 여부)"을 미결로 남겨 둔 그 자리다.

따라서 **① 웹 자격증명을 어디에 둘지 ② 두 클라이언트의 계정을 무엇으로 같다고 볼지 ③ 그 판정을 언제 할지**를 결정해야 한다.

## Decision

**웹 로컬 자격증명을 `local_accounts` 테이블로 신설해 `social_accounts`와 대칭으로 한 `users` 행에 매단다. 계정 동일성은 SMS 인증을 통과한 휴대폰 번호 단독으로 판정하고, `users.phone_number`에 UNIQUE를 걸어 그 판정을 DB가 지키게 한다.**

### 1. `users`에 비밀번호 컬럼을 붙이지 않는다 — 자격증명은 `auth`, 프로필은 `user`

`users`는 `user` 모듈이 소유하는 엔티티다. 거기에 `password_hash`·`failed_login_attempts`·`locked_at`을 붙이면 **`auth`가 `user`의 테이블을 쓰는 구조**가 되어 모듈 경계가 뚫린다([ADR-0001](./0001-bounded-context-module-decomposition.md)). 자격증명 검증은 명백히 `auth`의 일이고 `user`는 그 값을 읽을 이유가 없다.

그리고 그 자리는 이미 대칭이 잡혀 있다 — 소셜 자격증명은 `users`가 아니라 `social_accounts`에 있다. 로컬 자격증명도 같은 모양으로 둔다.

```
users (id=42, name, phone_number, user_type=LANDLORD, status=ACTIVE)   ← 사람 = 소유권 축
  ├── social_accounts (provider, provider_user_id, ...)                ← 앱 로그인
  └── local_accounts  (email, password_hash, ...)                      ← 웹 로그인   신규
```

부수 효과로 **세입자 다수의 `users` 행에 영원히 NULL인 자격증명 컬럼 넷이 생기지 않는다.**

`local_accounts`(V22)는 `uq_local_accounts_email`(로그인 ID 유일성)과 `uq_local_accounts_user_id`(**한 계정에 웹 자격증명 하나**)를 갖는다. 후자가 "이미 웹 계정이 있는데 또 붙는" 상태를 DB 레벨에서 막는다. **FK는 걸지 않는다** — `social_accounts`가 V1에서 의도적으로 생략한 선례를 따른다. 비밀번호는 **BCrypt**로만 보관하고(`password_hash VARCHAR(100)` — BCrypt 60자 + 여유), 원문은 저장·로그 어디에도 남기지 않는다. 비밀번호 정책은 **영문자·숫자·ASCII 특수문자 각 1자 이상 · 길이 8~10 · 공백 불허**이며 요청 DTO의 `@Pattern`으로 걸어 기존 `INVALID_INPUT` 흐름에 태운다.

### 2. 한 `users` 행을 공유한다 — 웹 가입은 "계정 생성"이 아니라 "자격증명 추가"다

§Context의 사슬이 전부 `landlordId` 하나를 본다. 그래서 **웹 계정과 앱 계정이 같은 `users` 행을 쓰면 예약·채팅 연동은 추가 코드 없이 성립한다.** 옮길 데이터가 없기 때문이다.

- 번호로 기존 `users`를 찾으면 → 그 `user_id`에 `local_accounts` 행만 INSERT한다. **`users`는 건드리지 않는다**(`linked=true`).
- 못 찾으면 → 새 `users`를 만들되 **앱과 같은 상태 체인**(`PENDING → TERMS_AGREED → ACTIVE`)을 한 트랜잭션에서 연속 전이시킨다(`linked=false`). 기존 도메인 메서드(`createPendingUser`·`agreeToTerms`·`completeLandlordOnboarding`)를 순서대로 부를 뿐 `user` 모듈에 새 생성 메서드를 만들지 않는다 — `@Transactional(REQUIRED)` 전파로 원자성이 성립한다.
- 찾았는데 그 계정에 이미 `local_accounts`가 있으면 → `409 AUTH_WEB_ACCOUNT_ALREADY_EXISTS`다. 붙일 자리가 이미 찼고, 남은 동작은 기존 자격증명 덮어쓰기뿐인데 그건 가입이 아니라 **자격증명 교체**다.

웹에 `PENDING`·`TERMS_AGREED` 같은 부분 완료 상태를 남기지 않는 이유는 단순하다 — **웹에는 온보딩 재개 화면이 없어서 로그인해도 갈 곳이 없는 죽은 계정이 된다.** 그럼에도 상태 체인 자체는 그대로 태운다. 앱 계정과 데이터 모양이 같아야 연동이 성립하기 때문이다.

신규 엔드포인트는 넷이고 전부 `POST`·전부 permitAll이다.

| Path | 역할 | 스토리 |
|---|---|---|
| `/api/v1/auth/phone/signup/verification-code` | 가입용 인증번호 발송(번호 키) | US-1-13 |
| `/api/v1/auth/phone/signup/verify` | 가입용 인증번호 확인 | US-1-13 |
| `/api/v1/auth/signup` | 웹 회원가입 + 기존 계정 연동 | US-1-11 |
| `/api/v1/auth/login` | 웹 로그인 | US-1-12 |

### 3. 매칭 키는 휴대폰 번호 단독이고, SMS 소유 증명이 반드시 앞선다

**휴대폰 번호는 비밀이 아니다.** 번호를 아는 것만으로 기존 계정에 자격증명을 붙일 수 있으면, 남의 번호를 아는 사람이 비밀번호를 심어 그 계정의 매물·예약·신청자 PII를 통째로 가져간다.

> **번호는 조회 키이지 인증 수단이 아니다. 소유 증명은 전적으로 SMS 인증이 담당한다.** 인증 마커가 없으면 `422 AUTH_PHONE_NOT_VERIFIED`이고 **계정 생성도 연동도 하지 않는다.**

**이름은 매칭 조건에 넣지 않는다.** SMS가 이미 소유를 증명하므로 이름을 더해도 막히는 공격이 늘지 않는 반면, 실패는 크게 는다 — 앱 이름은 소셜 SDK 표기(`Kim Imdae`)이고 웹 이름은 직접 입력(`김임대`)이라 불일치가 자연스럽다. 불일치하면 **계정이 조용히 갈라지고 사용자는 "앱에서 내 매물의 예약이 안 보인다"만 겪는다.** 원인을 알 수 없는 실패를 만드는 조건은 걸지 않는다.

가입 전 단계라 기존 인증을 못 쓰므로 **번호 키 챌린지**를 신설한다(`signup-phone:code:{정규화번호}` / `signup-phone:verified:{정규화번호}`). 인증번호 정책(6자리·TTL 5분·마커 30분·시도 5회·재발송 60초)과 `VerificationSmsSender` 포트는 [ADR-0034](./0034-landlord-phone-sms-verification.md)를 그대로 재사용한다. **permitAll SMS 발송은 문자 폭탄·발송비 남용 표면**이므로 번호 5회/시간 + IP 20회/시간의 이중 레이트리밋을 건다(초과 시 `429 TOO_MANY_REQUESTS`). 가입 이력 유무와 무관하게 같은 응답을 내려 계정 존재 여부를 노출하지 않는다.

번호는 **입력 경로에서만 정규화**한다(숫자만 남김). 기존 데이터는 백필하지 않는다(§Consequences).

### 4. 앱 방향의 연동 지점은 로그인이 아니라 임대인 온보딩이다 — 그리고 연결이 아니라 병합이다

연동은 어느 쪽으로 먼저 가입하든 성립해야 하는데, **판정 가능 시점이 두 방향에서 다르다.** 서버가 번호를 언제 아는지가 다르기 때문이다.

| 방향 | 판정 시점 | 그때 번호를 아는가 | 기존 행 상태 | 동작 |
|---|---|---|---|---|
| 웹 → 앱 | 가입 제출(US-1-11) | O — SMS 인증을 이미 통과 | 아직 안 만듦 | **연결**(`local_accounts` INSERT) |
| 앱 → 웹 | **소셜 로그인 시점엔 불가** | X — 소셜은 `name`·`email`만 준다 | PENDING 행이 이미 있음 | **병합**(온보딩 제출에서) |

소셜 로그인은 신원 확인 직후 `createPendingUser`로 행을 만든다. 그 시점에 번호가 없으므로 **로그인에서는 판정할 방법이 없다.** 번호를 처음 아는 지점은 임대인 온보딩의 SMS 인증이고, 그때는 이미 임시 행이 있으므로 병합이 된다.

병합이 안전한 이유는 **그 임시 계정이 방금 만들어져 매물·예약이 하나도 없기 때문**이다. 실제로 옮기는 것은 `social_accounts` 한 행뿐이다.

```
verified = 정규화된 인증 번호
target = SELECT id FROM users
          WHERE phone_number = :verified AND id <> :currentUserId
            AND status='ACTIVE' AND user_type='LANDLORD'
          FOR UPDATE
├─ 없음 → 기존 동작 그대로 (US-1-9 무변경)
└─ 있음 → UPDATE social_accounts SET user_id = :targetId WHERE user_id = :currentUserId
          DELETE FROM users WHERE id = :currentUserId
          issueFullTokens(:targetId)      ← currentUserId가 아니다
```

`status='ACTIVE' AND user_type='LANDLORD'`는 지금은 중복이다(번호가 채워진 계정은 사실상 `ACTIVE` 임대인뿐이다). **그래도 명시한다** — 나중에 누군가 다른 경로에서 `PENDING` 계정에 번호를 채워도 병합이 오작동하지 않게 하기 위해서다. 암묵적 불변식에 기대지 않는다. 영향 행 수는 단언하지 않는다(UPDATE는 N행이어도 안전하고, 대상 쪽에 `social_accounts`가 여러 행인 것은 한 사람이 Google·Apple로 들어온 정상 상태다).

임시 계정 행은 **하드 삭제**한다 — 미완료 계정(`PENDING`·`TERMS_AGREED`)을 `DELETE`로 정리한 [V21](../../src/main/resources/db/migration/V21__delete_incomplete_accounts.sql)의 선례를 따른다. 탈퇴의 상태 전이·익명화([ADR-0014](./0014-withdrawal-pii-anonymization.md))를 쓰지 않는 이유는, 이 행이 사람의 계정이 아니라 **몇 분 전에 만들어진 빈 껍데기**라 보존할 이력이 없기 때문이다. 다만 **그 계정의 진단 기록은 지우지 않는다** — 현재는 탈퇴조차 진단을 지우지 않으므로(`UserWithdrawnEvent` 구독자는 `auth` 하나뿐), 병합이 탈퇴보다 공격적으로 지우는 비대칭을 만들지 않는다.

### 5. `users.phone_number`에 UNIQUE — check-then-act를 막는 유일한 수단

같은 번호로 **웹 가입 제출**과 **앱 임대인 온보딩 제출**이 거의 동시에 도착하면, 양쪽이 "그 번호를 가진 `users`가 있는가"를 상대의 커밋 전에 조회해 **둘 다 없음으로 판정**한다 → 웹은 새 계정을 만들고 앱은 병합 없이 자기 계정을 `ACTIVE`로 전이시킨다 → **같은 번호의 `ACTIVE` 계정이 둘** 생기고, 이후 병합 트리거가 없다.

애플리케이션 레벨 조회로는 막을 수 없다. **V23에서 `uq_users_phone_number`를 건다** — 두 번째 트랜잭션이 DB 제약으로 실패하므로 계정이 갈라지지 않고, 실패한 쪽은 재시도하면 상대가 만든 계정을 발견해 정상 연동·병합된다.

MySQL의 UNIQUE는 **NULL 중복을 허용**하므로 세입자(정의상 NULL)와 탈퇴자(익명화로 NULL — [ADR-0014](./0014-withdrawal-pii-anonymization.md))는 영향받지 않는다. 이 제약이 [ADR-0034](./0034-landlord-phone-sms-verification.md)의 미결 항목("연락처 유니크 제약 — 현재 미적용")을 닫는다.

### 6. 정본은 `users`, 자격증명 테이블은 스냅샷이다

웹 폼은 연동 여부와 무관하게 항상 전체 필드를 받는다(화면이 하나이고 분기가 없다). 그 `name`·`birth_date`를 버리지 않고 `local_accounts`에 함께 저장한다 — 새 패턴이 아니라 **기존 구조의 대칭 적용**이다. `social_accounts`가 이미 `email`(V1)과 `name`([V20](../../src/main/resources/db/migration/V20__social_accounts_name.sql) — *"provider가 준 표시 이름 스냅샷을 보관한다(User.name과 별개)"*)을 들고 있다.

| 테이블 | 역할 | 값의 성격 |
|---|---|---|
| `users` | 사람 · 소유권 축(`landlordId`) | **정본** |
| `social_accounts` | 앱 자격증명 + provider가 준 `email`·`name` | 스냅샷 |
| `local_accounts` | 웹 자격증명 + 웹 폼이 준 `name`·`birth_date` | 스냅샷 |

> **표시 규칙: 모든 응답은 `users`의 값을 쓴다. `local_accounts`의 사본은 어떤 응답에도 싣지 않는다.**

규칙을 못박는 이유는, "웹 요청이면 local·앱이면 users"로 갈리는 순간 같은 사람이 웹에선 `김임대`·앱에선 `Kim Imdae`로 보이고 프로필 수정이 어느 테이블을 바꿔야 하는지 모호해지기 때문이다. 따름정리는 둘이다 — **① 프로필 수정(`PATCH /users/me`)은 `users`만 바꾼다**(스냅샷은 갱신 대상이 아니다), **② 연동 시 `users`를 덮어쓰지 않는다.** 기존 값은 온보딩을 마친 확정 값이고 폼 값은 방금 입력한 미검증 값이다.

`users.email`도 같은 원칙이다. **연동 시에는 건드리지 않고**(소셜 진본 유지) 웹 이메일은 `local_accounts.email`에만 둔다. **신규 가입일 때만** 폼 이메일을 `users.email`에도 기록한다. 그래서 이메일 중복 검사는 **`local_accounts.email`에만** 건다 — `users.email`까지 유일성을 걸면 "본인이 본인 소셜 이메일로 웹 가입하다 409를 맞는" 가장 흔한 정상 경로가 막힌다. 소셜 로그인은 `(provider, provider_user_id)`로 판정하므로 `users.email`은 로그인에 쓰이지 않는다. **`users.email`에는 UNIQUE를 추가하지 않는다.**

### 7. 에러 매핑

| 코드 | status | 의미 |
|---|---|---|
| `AUTH_INVALID_CREDENTIALS` | 401 | 웹 로그인 실패. **이메일 없음과 비밀번호 불일치를 같은 응답으로** 낸다(존재 여부 비노출) |
| `AUTH_ACCOUNT_LOCKED` | **423** | 비밀번호 5회 연속 실패로 잠긴 계정. **비밀번호가 맞아도 잠금이 우선**이다 |
| `AUTH_EMAIL_ALREADY_REGISTERED` | 409 | 그 이메일을 이미 남이 웹 로그인 ID로 쓰고 있다 |
| `AUTH_WEB_ACCOUNT_ALREADY_EXISTS` | 409 | 번호로 찾은 계정에 이미 웹 자격증명이 있다. 로그인 화면으로 보낸다 |

`AUTH_WEB_ACCOUNT_ALREADY_EXISTS` 응답에는 **마스킹 이메일을 싣지 않는다.** 공통 스키마(code/message)만 낸다 — SMS 인증을 통과해야 닿는 지점이라 무차별 열거는 불가능하지만, 번호 소유자에게 남의 이메일 일부를 노출할 이유도 없다.

실패 횟수·잠금 여부는 **`local_accounts`의 컬럼**(`failed_login_attempts`·`locked_at`)에 둔다. Redis TTL로 두면 만료와 함께 잠금이 저절로 풀려 "해제 기능 없음"이라는 정책이 깨진다. 네 코드 모두 `messages.properties`(영어)·`messages_ko.properties`(한국어) **양쪽**에 키를 넣는다([ADR-0030](./0030-error-message-i18n-resource-bundle.md)) — 기존에 `AUTH_EMAIL_REQUIRED`·`AUTH_EMAIL_MISMATCH`가 두 파일 모두에서 누락돼 조용히 한국어를 내보내고 있다.

## Alternatives

| 대안 | 장점 | 단점 | 채택 안 한 이유 |
|---|---|---|---|
| **A. `local_accounts` 분리 + 번호로 `users` 공유(채택)** | 소유권 사슬이 그대로 성립(옮길 데이터 0), `social_accounts`와 대칭, 앱 흐름 무변경 | 테이블 하나 증가, 계정 동일성이 번호 하나에 걸린다 | — (채택) |
| B. `users`에 `password_hash` 등 컬럼 추가 | 테이블이 늘지 않고 조인이 없다 | `auth`가 `user` 테이블을 쓰게 되어 모듈 경계가 뚫리고, 세입자 행에 영원히 NULL인 컬럼 넷이 남는다 | 자격증명(`auth`)과 프로필(`user`)의 소유가 갈리는 것이 이 코드베이스의 기존 경계다 |
| C. `/auth/social-login`에 `provider=LOCAL`을 끼워 넣는다 | 엔드포인트가 늘지 않는다 | `SocialLoginRequest`가 이미 provider별 조건부 자격 필드로 복잡한데 `password`까지 섞으면 검증 분기가 3중이 되고, OIDC 검증이 없는 경로가 OIDC 엔드포인트 안에 숨는다 | 로그인 수단이 다르면 계약도 달라야 한다 — `LOCAL`은 `idToken`도 `authorizationCode`도 없다 |
| D. 웹 전용 계정 스키마(별도 `users`) | 웹 도메인이 앱과 완전히 독립 | `landlordId`가 갈라져 **앱이 웹 등록 매물에 영구히 눈이 먼다** — 예약·채팅을 보이게 하려면 두 id를 잇는 매핑 테이블과 모든 조회의 이중 조회가 필요하다 | 이 기능의 목적을 정면으로 부순다(§Context) |
| E. 이름 + 번호로 매칭 | 조건이 하나 더 있어 안전해 보인다 | SMS가 이미 소유를 증명하므로 보안 기여가 0인데, 소셜 표기와 직접 입력의 불일치로 **계정이 조용히 갈라진다** | 막는 공격은 없고 만드는 실패는 많다(§3) |
| F. 이메일로 매칭 | 소셜 이메일과 웹 이메일이 같은 경우가 많다 | 소셜 이메일은 provider가 준 값이고 웹 이메일은 미검증 입력이다 — 남의 소셜 이메일을 적으면 그 계정에 붙는다 | 소유 증명이 없는 값을 매칭 키로 쓸 수 없다 |

## Consequences

- **긍정**
  - **웹에서 등록한 매물의 예약이 앱에서 그대로 보인다** — `landlordId`가 하나라 추가 코드가 없다. 매물·예약 데이터를 어느 방향에서도 옮기지 않는다.
  - 앱 흐름과 데이터 모양이 바뀌지 않는다(상태 체인·도메인 메서드 재사용). 세입자 흐름은 전혀 영향받지 않는다.
  - 자격증명 테이블이 `social_accounts`·`local_accounts` 둘로 대칭이 되어, 세 번째 채널이 생겨도 같은 모양으로 붙는다.
  - `users.phone_number` UNIQUE가 [ADR-0034](./0034-landlord-phone-sms-verification.md)의 미결을 닫고, 동시 가입으로 계정이 갈라지는 경로를 DB 레벨에서 막는다.
- **부정/트레이드오프** — 아래는 전부 **알고 수용한 한계**이며 운영·후속에서 다뤄야 한다.
  - **번호 정규화 백필이 없다.** 정규화는 입력 경로에만 적용하고 기존 행은 손대지 않는다. 하이픈으로 저장된 기존 임대인 번호는 **매칭에서 누락될 수 있다** — 그 임대인은 웹 가입 시 연동되지 않고 별개 계정이 생긴다.
  - **잠금 해제 경로가 없다.** 시간 경과 자동 해제도 없다. 운영자가 DB에서 `locked_at`을 비우는 것이 유일한 방법이므로 **대응 창구를 운영에서 먼저 정해야 한다.**
  - **의도적 계정 잠금(DoS)이 가능하다.** 남의 이메일로 5회 틀리면 그 계정을 잠글 수 있다. 잠금 정책의 고전적 부작용이며 IP 레이트리밋으로 완화하되 완전히 막을 수는 없다.
  - **세입자는 임대인이 될 수 없다.** `user_type`은 온보딩 확정 후 불변이고 세입자는 `phone_number`가 NULL이라 **구조적으로 매칭 후보에서 빠진다.** 앱에서 세입자로 가입한 사람이 웹에서 임대인 가입을 하면 별개 계정이 생기며, **서버는 두 계정이 동일인인지 알 방법이 없어 막을 수도 안내할 수도 없다.** 매칭 로직에 역할 분기를 추가하지 않는다.
  - **양쪽 모두 완주한 계정은 자동 병합하지 않는다.** 앱·웹 양쪽에 같은 번호의 `ACTIVE` 계정이 각각 있으면(위 백필 누락 등으로) 온보딩을 다시 타지 않으므로 트리거가 없다. **운영 수동 처리 대상**이며 코드·화면을 만들지 않는다.
  - **병합해도 임시 계정의 진단 기록은 남는다.** 사라진 `users` 행을 가리키는 진단 문서가 남을 수 있다 — 조회 주체가 없어 실질 영향은 없지만 고아 문서다.
  - **앱스토어 심사용 고정코드 우회는 웹에 적용되지 않는다.** `FixedVerificationPolicy`가 `userId` + Google 소셜 계정 기반이라 비로그인 번호 키 경로에 걸 자리가 없다. 앱 심사용 기능이라 웹과 무관하다.
  - **비밀번호 상한 10자는 이례적으로 낮다.** 비밀번호 관리자가 만드는 16자 이상을 거부하고 패스프레이즈를 못 쓴다. BCrypt는 72바이트까지 받으므로 기술적 제약이 아니라 정책 선택이며, 되돌리려면 정규식 상한 숫자 하나만 바꾸면 된다.
  - **S3 pending 키가 id를 품는다.** 업로드 임시 키가 `uploads/{landlordId}/{uuid}.{ext}`라(`ListingImageKeys.pending`) 병합으로 id가 바뀌면 진행 중이던 pending 업로드는 고아가 된다. 병합은 가입 직후에만 일어나 실무상 무해하다.
- **후속 작업**
  - 신규 4경로는 `SecurityConfig`의 permitAll 매처와 **`PublicPaths.ALL` 두 곳**에 등록한다. 한쪽만 넣으면 만료 토큰을 든 브라우저가 로그인에서 `401 TOKEN_EXPIRED`를 맞는다(#181이 고친 버그와 같은 모양).
  - V23 적용 전 `SELECT phone_number, COUNT(*) FROM users WHERE phone_number IS NOT NULL GROUP BY phone_number HAVING COUNT(*) > 1`로 중복을 점검한다 — 있으면 제약 추가가 실패한다.
  - 잠긴 계정의 대응 창구와 `locked_at` 해제 절차를 운영에서 정한다(코드 변경 없음).
  - 양쪽 완주 계정의 수동 병합 런북을 만든다.
  - `chat`은 아직 미영속(`ChatRoomRepositoryImpl`이 `UnsupportedOperationException`)이라 병합에서 다룰 것이 없다. **영속이 붙을 때 병합 대상에 채팅방이 들어가는지 다시 본다.**
  - 기존 하이픈 번호의 백필 여부는 실제 누락 사례가 나온 뒤 판단한다.

## Validation

- **양방향 통합 테스트가 이 기능의 존재 이유다** — 둘 다 통과해야 한다.
  - **앱 먼저**: 앱 임대인 온보딩 완료 → 웹 가입(연동) → 웹에서 매물 등록 → 앱에서 `GET /api/v1/bookings`(임대인 분기)에 그 매물의 신청이 보인다.
  - **웹 먼저**: 웹 가입 → 웹에서 매물 등록 → 앱 소셜 로그인(임시 계정) → 임대인 온보딩(병합) → 같은 조회가 된다.
- 연동 가입은 **`users` 행을 늘리지 않고** `users.name`·`birth_date`·`email`도 바꾸지 않는다. 폼 값은 `local_accounts`에만 들어가고 응답의 `name`·`email`은 `users`에서 나간다.
- 같은 번호에 웹 자격증명이 이미 있으면 `409 AUTH_WEB_ACCOUNT_ALREADY_EXISTS`, 이메일만 겹치면 `409 AUTH_EMAIL_ALREADY_REGISTERED`로 **서로 다른 코드**가 나간다.
- SMS 인증 마커 없이 가입을 제출하면 `422 AUTH_PHONE_NOT_VERIFIED`이고 **`users`·`local_accounts` 어디에도 행이 생기지 않는다.**
- 온보딩 미완료 앱 계정(번호 NULL)과 세입자 계정은 매칭되지 않아 `linked=false`로 새 계정이 생긴다.
- 존재하지 않는 이메일과 비밀번호 불일치가 **같은 `401 AUTH_INVALID_CREDENTIALS`** 를 낸다. 5회 실패 후에는 올바른 비밀번호도 `423 AUTH_ACCOUNT_LOCKED`다.
- 같은 번호로 웹 가입과 앱 온보딩을 동시에 제출하면 한쪽만 성공하고, 실패한 쪽을 재시도하면 연동·병합으로 수렴한다.
- 병합 후 앱 소셜 로그인이 항상 대상 id로 귀결되고 임시 행이 남지 않는다.
- **재검토 시점**: 하이픈 번호 누락이 실제 문의로 올라오거나, 양쪽 완주 계정의 수동 처리가 반복되면 백필·계정 연결 화면을 다시 검토한다. `chat`이 영속되면 병합 범위를 다시 본다.
