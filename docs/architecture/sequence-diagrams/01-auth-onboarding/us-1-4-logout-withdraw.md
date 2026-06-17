# US-1-4 — 로그아웃·회원 탈퇴로 세션과 계정 정리하기

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

    alt 로그아웃
        U->>C: 로그아웃 선택
        C->>SEC: POST /api/v1/auth/logout<br/>Authorization: Bearer accessToken<br/>{ refreshToken }
        Note over SEC: JWT 검증 (서명·만료·클레임)
        SEC->>AUTH: 인증된 요청 전달 (userId)
        Note over AUTH: 전달된 refreshToken 무효화<br/>(이미 무효화면 멱등 처리)
        AUTH->>RDS: refreshToken 무효화
        RDS-->>AUTH: 무효화 완료
        AUTH-->>C: 204 No Content
        C-->>U: 세션 종료, 로그인 화면
    else 회원 탈퇴
        U->>C: 회원 탈퇴 선택
        C->>SEC: DELETE /api/v1/users/me<br/>Authorization: Bearer accessToken
        Note over SEC: JWT 검증 (서명·만료·클레임)<br/>온보딩 스코프(PENDING)도 탈퇴 허용
        SEC->>USER: 인증된 요청 전달 (userId)
        alt 이미 WITHDRAWN
            USER-->>C: 409 USER_ALREADY_WITHDRAWN
            C-->>U: 이미 탈퇴된 계정 안내
        else 정상 탈퇴
            Note over USER: status=WITHDRAWN 전이<br/>withdrawn_at(UTC) 기록<br/>식별 PII(이름·전화·비자·생년월일) 즉시 익명화(복구불가)
            USER->>SQL: 사용자 WITHDRAWN 갱신 + PII 익명화
            SQL-->>USER: 갱신 완료 (행 보존)
            USER-)AUTH: UserWithdrawnEvent (userId)
            USER-->>C: 204 No Content
            C-->>U: 계정 정리 완료
            Note over AUTH: 이벤트 구독 처리
            AUTH->>SQL: social_accounts 매핑 삭제<br/>(provider, provider_user_id)
            SQL-->>AUTH: 삭제 완료
            AUTH->>RDS: 해당 user refresh 일괄 무효화<br/>(status=REVOKED)
            RDS-->>AUTH: 무효화 완료
        end
    end
```

## 흐름 요약

- 로그아웃은 access 토큰으로 `auth 모듈`의 `POST /api/v1/auth/logout`에 `refreshToken`을 담아 호출하면 Redis에서 해당 **refreshToken을 무효화**하고 `204 No Content`를 반환한다(이미 무효화면 멱등).
- 회원 탈퇴는 `user 모듈`의 `DELETE /api/v1/users/me` 호출 시 MySQL에서 **상태를 WITHDRAWN으로 전이**하고 `withdrawn_at`(UTC)을 기록하며 식별 PII(이름·전화·비자·생년월일)를 **즉시 익명화(복구불가)**한다. 행은 보존하고 `204 No Content`를 반환한 뒤 `UserWithdrawnEvent`를 발행한다(비동기).
- `auth 모듈`은 `UserWithdrawnEvent`를 구독해 MySQL의 **`social_accounts` 매핑(provider, provider_user_id)을 삭제**하고 Redis에서 **해당 user의 refresh 토큰을 일괄 무효화(status=REVOKED)**한다.
- 이미 WITHDRAWN 상태이면 `409 USER_ALREADY_WITHDRAWN`을 반환한다.
- 두 동작 모두 인증 필수이며, 공통 보안 필터(SEC)가 컨트롤러 앞단에서 JWT를 검증한 뒤 모듈로 전달한다. 온보딩 스코프(PENDING) 사용자도 탈퇴는 허용된다.
