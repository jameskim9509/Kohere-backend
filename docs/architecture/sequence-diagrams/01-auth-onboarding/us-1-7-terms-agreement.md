# US-1-7 — 약관 동의 화면에서 약관 동의하기

> 모듈: 소셜 로그인 · 온보딩 · [유저 스토리](../../../requirements/user-stories.md) · [API 스펙](../../../api/specs/01-auth-onboarding.md)
>
> 소셜 로그인(US-1-1) 직후 `PENDING` 사용자가 거치는 **가입 첫 단계**다. 성공 시 `PENDING` → `TERMS_AGREED`로 전이하며, 이후 이메일 인증(US-1-6)·온보딩 제출(US-1-2)이 이어진다.

```mermaid
sequenceDiagram
    actor U as 사용자
    participant C as 앱(클라이언트)
    participant SEC as 공통 보안 필터
    participant AUTH as auth 모듈
    participant USER as user 모듈
    participant SQL as MySQL

    Note over U,C: 소셜 로그인 직후 약관 동의 화면
    U->>C: 이용약관·개인정보처리방침 동의(+선택 마케팅)
    C->>SEC: POST /api/v1/auth/terms<br/>Authorization: Bearer 온보딩토큰<br/>{ termsOfServiceAgreed, privacyPolicyAgreed, marketingAgreed }
    Note over SEC: JWT 검증 (서명·만료·클레임)<br/>온보딩 스코프(ROLE_ONBOARDING) 주입<br/>terms 경로 인가
    SEC->>AUTH: 인증된 요청 전달 (userId + 온보딩 스코프)
    AUTH->>USER: 약관 동의 공개명령<br/>(동의 boolean 전달)
    USER->>SQL: 회원 조회 (상태 확인)
    SQL-->>USER: 현재 상태
    alt 이미 ACTIVE
        USER-->>AUTH: 409 AUTH_ONBOARDING_ALREADY_COMPLETED
        AUTH-->>C: 409 AUTH_ONBOARDING_ALREADY_COMPLETED
        C-->>U: 이미 가입 완료 안내
    else 이미 TERMS_AGREED (중복 호출)
        Note over USER: 상태·동의 불변 — 멱등 처리(재동의 아님)
        USER-->>AUTH: 현재 동의 상태(변경 없음)
        AUTH-->>C: 200 OK (현재 status: TERMS_AGREED)
        C-->>U: 다음 단계로 진행
    else PENDING · 필수 약관 미동의
        USER-->>AUTH: 422 AUTH_REQUIRED_AGREEMENT_MISSING
        AUTH-->>C: 422 AUTH_REQUIRED_AGREEMENT_MISSING
        C-->>U: 약관 동의 안내
    else PENDING · 최초 동의
        Note over USER: termsVersion=app.terms.version 기록<br/>(클라이언트 미전송) · agreedAt 기록
        USER->>SQL: PENDING→TERMS_AGREED 전이<br/>consent(동의 3종·agreedAt·termsVersion) 확정
        SQL-->>USER: 갱신 완료
        USER-->>AUTH: 약관 동의 완료 (status: TERMS_AGREED)
        AUTH-->>C: 200 OK<br/>{ status: TERMS_AGREED, termsOfServiceAgreed,<br/>privacyPolicyAgreed, marketingAgreed, agreedAt }
        C-->>U: 이메일 인증·온보딩 화면으로 이동(US-1-6·US-1-2)
    end
```

## 흐름 요약

- 소셜 로그인 직후 `PENDING` 사용자가 약관 동의 화면에서 이용약관·개인정보처리방침(+선택 마케팅)에 동의해 `POST /api/v1/auth/terms`를 호출한다. 공통 보안 필터(SEC)가 **온보딩 토큰(`ROLE_ONBOARDING`)** 을 검증·인가한 뒤 `userId`를 `auth 모듈`로 전달한다(약관 동의는 온보딩 흐름이라 온보딩 토큰도 허용 — [API 스펙](../../../api/specs/01-auth-onboarding.md)).
- `auth 모듈`이 **약관 동의 공개명령으로 `user 모듈`을 호출**한다. 필수 약관(`termsOfServiceAgreed`/`privacyPolicyAgreed`) 미동의면 `422 AUTH_REQUIRED_AGREEMENT_MISSING`, 이미 `ACTIVE`면 `409 AUTH_ONBOARDING_ALREADY_COMPLETED`로 거절한다.
- `PENDING`의 **최초 동의**면 `user 모듈`이 MySQL에서 **상태를 `PENDING` → `TERMS_AGREED`로 전이하며 `consent`(동의 3종·`agreedAt`·`termsVersion`)를 확정**한다. `termsVersion`은 **클라이언트가 보내지 않고** 서버가 `app.terms.version`을 기록한다([ADR-0012](../../../adr/0012-terms-version-management.md)).
- 이미 `TERMS_AGREED`인 사용자가 (네트워크 재시도 등으로) 다시 호출하면 **상태·동의를 바꾸지 않고 멱등하게 `200`(현재 동의 상태)** 을 반환한다 — 의도적 재동의가 아니라 중복 요청 방어다. **동의 후 마케팅 수신 동의 변경은 `PATCH /users/me`(profile)** 로 처리하며 약관 재호출로 갱신하지 않는다. (약관 버전 변경에 따른 재동의 정책은 [ADR-0012](../../../adr/0012-terms-version-management.md) — 확인 필요)
- 토큰은 갱신하지 않는다(상태만 전이) — 같은 온보딩 토큰으로 이메일 인증(US-1-6)·온보딩 제출(US-1-2)을 이어서 진행한다. 정식 access/refresh 토큰은 온보딩 완료(`ACTIVE`) 시 발급된다.
