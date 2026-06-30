# US-1-5 — 내 프로필 조회·수정하기

> 모듈: 소셜 로그인 · 온보딩 · [유저 스토리](../../../requirements/user-stories.md) · [API 스펙](../../../api/specs/01-auth-onboarding.md)

```mermaid
sequenceDiagram
    actor U as 사용자
    participant C as 앱(클라이언트)
    participant SEC as 공통 보안 필터
    participant USER as user 모듈
    participant DB as MySQL
    participant RDS as Redis

    U->>C: 내 정보 화면 진입
    C->>SEC: GET /api/v1/users/me<br/>Authorization: Bearer accessToken
    Note over SEC: JWT 검증 (서명·만료·클레임)<br/>매 요청 stateless·status 무조회
    alt 온보딩 스코프 토큰(ROLE_ONBOARDING)
        Note over SEC: 보호경로 인가 거부<br/>AccessDeniedHandler (모듈 도달 전)
        SEC-->>C: 403 AUTH_ONBOARDING_REQUIRED
        C-->>U: 온보딩 완료 안내
    else 정식 인증 토큰(ROLE_USER)
        SEC->>USER: 인증된 요청 전달 (userId)
        Note over USER: 본인 프로필 조회 (민감정보 마스킹 정책)
        USER->>DB: 프로필 조회
        DB-->>USER: 프로필 데이터
        Note over USER: 응답 필드는 userType에 따라 분기
        Note over USER: ResponseBodyAdvice가 공통 래퍼 자동 래핑<br/>(컨트롤러는 DTO만)
        alt userType=TENANT (세입자)
            Note over USER: country(코드)로 countries 조회 → countryName·countryFlag resolve<br/>countryFlag=국기 이미지 URL(flagcdn.com)
            USER-->>C: 200 OK<br/>{ success, data: { id, firstName, lastName, nickname, gender, birthDate,<br/>country, countryName, countryFlag, occupation, email, visaType, status,<br/>termsOfServiceAgreed, privacyPolicyAgreed, marketingAgreed }, error }
        else userType=LANDLORD (임대인)
            Note over USER: name=FullName.firstName(전체 이름, lastName 미사용)<br/>세입자 전용 필드(gender/country/countryName/countryFlag/<br/>occupation/visaType/birthDate)·email 미포함(임대인 이메일 미수집)<br/>businessRegistrationNumber 미포함(원문 비저장)
            USER-->>C: 200 OK<br/>{ success, data: { id, userType=LANDLORD, name, nickname,<br/>phoneNumber, status, termsOfServiceAgreed,<br/>privacyPolicyAgreed, marketingAgreed, createdAt }, error }
        end
        C-->>U: 프로필 표시
    end

    U->>C: 비자정보·국적·직업 등 일부 수정
    C->>SEC: PATCH /api/v1/users/me<br/>Authorization: Bearer accessToken<br/>{ country, occupation, visaType, marketingAgreed }
    Note over SEC: JWT 검증 (서명·만료·클레임)<br/>매 요청 stateless·status 무조회
    alt 온보딩 스코프 토큰(ROLE_ONBOARDING)
        Note over SEC: 보호경로 인가 거부<br/>AccessDeniedHandler (모듈 도달 전)
        SEC-->>C: 403 AUTH_ONBOARDING_REQUIRED
        C-->>U: 온보딩 완료 안내
    else 정식 인증 토큰(ROLE_USER)
        SEC->>USER: 인증된 요청 전달 (userId)
        Note over USER: 전송 필드만 변경 (미전송 필드 유지)
        Note over USER: 수정 가능 필드는 userType에 따라 분기<br/>TENANT: country/occupation/visaType/marketingAgreed 등<br/>LANDLORD: name(→FullName.firstName)·marketingAgreed 자유, phoneNumber는 SMS 재인증 필요<br/>공통 불변: userType·nickname (TENANT email은 재인증 필요·LANDLORD email 미보유)<br/>LANDLORD businessRegistrationNumber 불변(외부 사업자등록정보 재검증)
        Note over U,USER: (정상 흐름) 앱은 연락처 변경 시 PATCH 이전에 새 번호 인증(US-1-10)을 선행<br/>아래 422는 미인증·TTL 만료·불일치 제출에 대한 서버 가드
        alt 임대인 · phoneNumber 변경 포함
            USER->>RDS: phone-verify:verified:{userId} 조회<br/>(제출 phoneNumber 대조)
            RDS-->>USER: VERIFIED 연락처(있음/없음)
            alt 미인증·불일치 (마커 없음/다름)
                USER-->>C: 422 AUTH_PHONE_NOT_VERIFIED
                C-->>U: 새 번호 SMS 재인증 안내
                Note over U,C: /auth/phone/verification-code·/auth/phone/verify로<br/>새 번호 재인증(정식 토큰, US-1-10) 후 재PATCH
            else VERIFIED 일치 — 변경 반영
                USER->>DB: phoneNumber 포함 부분 수정
                DB-->>USER: 수정 완료
                USER-->>C: 200 OK<br/>{ success, data: { 수정된 프로필 전체 }, error }
                C-->>U: 변경 내용 반영
            end
        else 그 외 필드만 (name·marketingAgreed·세입자 필드)
            USER->>DB: 프로필 부분 수정
            DB-->>USER: 수정 완료
            Note over USER: ResponseBodyAdvice가 공통 래퍼 자동 래핑<br/>(컨트롤러는 DTO만)
            USER-->>C: 200 OK<br/>{ success, data: { 수정된 프로필 전체 }, error }
            C-->>U: 변경 내용 반영
        end
    end
```

## 흐름 요약

- 정식 인증 토큰(ROLE_USER) 사용자가 `user 모듈`의 `GET /api/v1/users/me`로 본인 프로필(이름·`nickname`·성별·생년월일·`country`(코드) + 서버 resolve `countryName`·`countryFlag`·`occupation`·`email`·`visaType`·약관 동의 상태)을 MySQL에서 **프로필 조회**해 `200 OK`로 반환하며, 공통 보안 필터(SEC)가 컨트롤러 앞단에서 JWT를 검증(매 요청 stateless·status 무조회)한 뒤 모듈로 전달한다. 본인 프로필이라 `email`은 평문, 성공 응답은 ResponseBodyAdvice가 공통 래퍼(`{ success, data, error }`)로 자동 래핑하며 컨트롤러는 DTO만 반환한다.
- 응답 필드는 `userType`에 따라 갈린다 — **세입자(TENANT)** 는 위의 성별·국적·직업·비자 등 세입자 전용 필드를 포함하고, **임대인(LANDLORD)** 은 `userType=LANDLORD`·`name`(=`FullName.firstName`, `lastName` 미사용)·`phoneNumber`·`status`·약관 동의·`createdAt`만 포함한다(세입자 전용 필드 및 `email`(임대인 미수집)·`businessRegistrationNumber`는 미포함, 사업자 검증은 원문 비저장).
- 온보딩 스코프 토큰(ROLE_ONBOARDING)으로 접근하면 SEC가 보호경로 인가 단계에서(모듈 도달 전 `AccessDeniedHandler`) `403 AUTH_ONBOARDING_REQUIRED`를 반환한다.
- `user 모듈`의 `PATCH /api/v1/users/me`에 변경 필드만 담아 보내면 미전송 필드는 유지한 채 MySQL에서 **프로필을 부분 수정**한 뒤 `200 OK`로 수정된 프로필을 반환한다. 조회와 동일하게 온보딩 스코프 토큰은 SEC가 `403 AUTH_ONBOARDING_REQUIRED`로 차단한다.
- 수정 가능 필드도 `userType`에 따라 갈린다 — **세입자**는 국적·직업·비자 등(+`marketingAgreed`)을, **임대인**은 `name`(→`FullName.firstName`)·`marketingAgreed`를 자유 수정하고 `phoneNumber`는 **SMS 재인증(US-1-10)** 을 거쳐 변경한다. **정상 흐름은 앱이 PATCH 이전에 새 번호 인증(`/auth/phone/**`, 정식 토큰)을 먼저 수행**하는 것이다 — `PATCH`에 새 `phoneNumber`가 담기면 `user`가 Redis `phone-verify:verified:{userId}` 마커와 대조해 일치(VERIFIED)할 때만 반영하고, 마커가 없거나(미인증·TTL 만료) 다르면 `422 AUTH_PHONE_NOT_VERIFIED`로 거절한다. 즉 `422`는 happy path가 아니라 **미인증·만료·불일치 제출을 막는 서버 가드**이며, 제대로 동작하는 앱은 인증을 선행해 이를 보지 않는다. 세입자 `email`은 재인증이 필요해 이 경로로 수정하지 않으며(임대인은 `email` 미보유) `userType`·`nickname`은 공통 불변이고, 임대인의 `businessRegistrationNumber`는 변경 시 외부 사업자등록정보 재검증이 필요하므로 이 경로로 수정하지 않는다(불변).

