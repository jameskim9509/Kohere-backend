# US-1-2 — 필수 온보딩 정보·약관 동의 제출하기

> 모듈: 소셜 로그인 · 온보딩 · [유저 스토리](../../../requirements/user-stories.md) · [API 스펙](../../../api/specs/01-auth-onboarding.md)

```mermaid
sequenceDiagram
    actor U as 사용자
    participant C as 앱(클라이언트)
    participant SEC as 공통 보안 필터
    participant AUTH as auth 모듈
    participant USER as user 모듈
    participant SQL as MySQL
    participant RDS as Redis

    U->>C: 이름·성별·생년월일·연락처·비자정보 입력 및 약관 동의
    C->>SEC: POST /api/v1/auth/onboarding<br/>Authorization: Bearer 온보딩토큰<br/>{ firstName, lastName, gender, birthDate,<br/>countryCode, phoneNumber, visaType,<br/>termsOfServiceAgreed, privacyPolicyAgreed, marketingAgreed }
    Note over SEC: JWT 검증 (서명·만료·클레임)<br/>온보딩 스코프(ROLE_ONBOARDING) 주입<br/>onboarding 경로 인가
    SEC->>AUTH: 인증된 요청 전달 (userId + 온보딩 스코프)
    Note over AUTH: 필드 검증<br/>민감정보(전화·비자)는 응답·로그에서만 마스킹(저장은 원문)
    alt 필수 약관 동의 누락
        AUTH-->>C: 422 AUTH_REQUIRED_AGREEMENT_MISSING
        C-->>U: 약관 동의 안내
    else 정상 제출
        AUTH->>USER: 온보딩 완료 공개명령<br/>(프로필·동의 boolean 전달)
        Note over USER: 약관버전은 서버가 app.terms.version 기록<br/>(클라이언트 미전송)
        USER->>SQL: 회원 조회 (상태 확인)
        SQL-->>USER: 현재 상태
        alt 이미 ACTIVE
            USER-->>AUTH: 409 AUTH_ONBOARDING_ALREADY_COMPLETED
            AUTH-->>C: 409 AUTH_ONBOARDING_ALREADY_COMPLETED
            C-->>U: 이미 온보딩 완료 안내
        else PENDING (정상 전이)
            USER->>SQL: PENDING→ACTIVE 전이<br/>프로필·동의 확정·termsVersion=app.terms.version 기록
            SQL-->>USER: 갱신 완료
            USER-->>AUTH: 온보딩 완료 (user{ status: ACTIVE })
            Note over AUTH: 정식 accessToken+refreshToken 발급
            AUTH->>RDS: refreshToken 해시 저장
            RDS-->>AUTH: 저장 완료
            AUTH-->>C: 200 OK<br/>{ user{ status: ACTIVE, ... }, tokenType: Bearer,<br/>accessToken, refreshToken, expiresIn: 3600 }
            C-->>U: 가입 완료, 서비스 진입
        end
    end
```

## 흐름 요약

- PENDING 사용자가 온보딩 토큰으로 필수 프로필과 약관 동의(boolean)를 담아 `POST /api/v1/auth/onboarding`을 호출하며, 공통 보안 필터(SEC)가 컨트롤러 앞단에서 JWT를 검증하고 **온보딩 스코프(`ROLE_ONBOARDING`)를 주입·onboarding 경로 인가**를 마친 뒤 `userId + 온보딩 스코프`를 `auth 모듈`로 전달한다.
- `auth 모듈`이 요청을 수신해 필드를 검증하고, **온보딩 완료 공개명령으로 `user 모듈`을 호출**한다. 민감정보(전화·비자)는 **저장은 원문**이며 **응답·로그에서만 마스킹**한다.
- 필수 약관(`termsOfServiceAgreed`/`privacyPolicyAgreed`) 미동의면 `422 AUTH_REQUIRED_AGREEMENT_MISSING`으로 거절한다. 약관 버전은 **클라이언트가 보내지 않고**(동의 boolean만 전송), 온보딩 완료 시 **서버가 `app.terms.version`을 `termsVersion`에 기록**한다.
- 이미 `ACTIVE`인 사용자가 다시 제출하면 `409 AUTH_ONBOARDING_ALREADY_COMPLETED`로 거절한다.
- 정상(PENDING)이면 `user 모듈`이 **MySQL에서 상태를 PENDING→ACTIVE로 전이하며 프로필·동의·`termsVersion`을 확정**하고, `auth 모듈`이 정식 access/refresh 토큰을 발급하여 **Redis에 refreshToken 해시를 저장**한 뒤 `200 OK`(`expiresIn: 3600`)로 완성 프로필과 토큰을 반환한다.
