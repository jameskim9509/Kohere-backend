# US-1-6 — 이메일 인증하기 (가입 완료 후)

> 모듈: 소셜 로그인 · 온보딩 · [유저 스토리](../../../requirements/user-stories.md) · [API 스펙](../../../api/specs/01-auth-onboarding.md)
>
> 온보딩 완료(**ACTIVE**) 사용자 전용 이메일 소유 인증이다(#192로 온보딩 단계에서 분리). 정식 토큰(`ROLE_USER`)이 필요하며 **온보딩 토큰으로는 호출할 수 없다**. 인증번호는 `auth`가 해시로만 보관(Redis, TTL)하고 메일 발송은 인프라 어댑터에 위임한다. **이번 범위는 접근을 ACTIVE 전용으로 제한하는 것까지이며, 검증 성공이 `User.email`을 실제로 바꾸지는 않는다(실제 이메일 변경 반영은 후속 이슈).**

```mermaid
sequenceDiagram
    actor U as 사용자
    participant C as 앱(클라이언트)
    participant SEC as 공통 보안 필터
    participant AUTH as auth 모듈
    participant RDS as Redis
    participant MAIL as 메일 발송(인프라 어댑터)

    Note over U,C: 가입 완료(ACTIVE) 사용자가 내 정보에서 이메일 입력
    U->>C: 이메일 입력 후 "인증번호 받기"
    C->>SEC: POST /api/v1/auth/email/verification-code<br/>Authorization: Bearer accessToken(정식)<br/>{ email }
    Note over SEC: JWT 검증 (서명·만료·클레임)<br/>정식 스코프(ROLE_USER) 인가 — ACTIVE 전용
    alt 온보딩 스코프 토큰(ROLE_ONBOARDING)
        Note over SEC: 보호경로 인가 거부<br/>AccessDeniedHandler (모듈 도달 전)
        SEC-->>C: 403 AUTH_ONBOARDING_REQUIRED
        C-->>U: 온보딩 완료 안내(US-1-2)
    else 정식 인증 토큰(ROLE_USER)
        SEC->>AUTH: 인증된 요청 전달 (userId)
        Note over AUTH: 인증번호 생성 → 단방향 해시<br/>원문은 메일로만, 저장·로그는 해시
        alt 재발송 레이트리밋 초과
            AUTH-->>C: 429 TOO_MANY_REQUESTS
            C-->>U: 잠시 후 재시도 안내
        else 발송 시도
            AUTH->>MAIL: 인증번호 메일 동기 발송<br/>(VerificationEmailSender → SMTP)
            alt 발송 실패 (provider 장애·타임아웃)
                MAIL-->>AUTH: 발송 실패
                AUTH-->>C: 502 UPSTREAM_ERROR (챌린지 미저장)
                C-->>U: 잠시 후 재시도 안내
            else 발송 성공
                MAIL-->>AUTH: 발송 성공
                AUTH->>RDS: email-verify:code:{userId} 저장(발송 성공 후 확정)<br/>{ email, codeHash, attempts:0, status:PENDING }<br/>TTL=인증번호 만료(예: 5분)
                RDS-->>AUTH: 저장 완료
                AUTH-->>C: 200 OK<br/>{ email: 마스킹, expiresIn }
                C-->>U: 인증번호 입력 화면
            end
        end
    end

    Note over U,C: 메일에서 인증번호 확인 후 입력
    U->>C: 인증번호 입력
    C->>SEC: POST /api/v1/auth/email/verify<br/>Authorization: Bearer accessToken(정식)<br/>{ email, code }
    Note over SEC: JWT 검증 + 정식 스코프(ROLE_USER) 인가 — ACTIVE 전용
    alt 온보딩 스코프 토큰(ROLE_ONBOARDING)
        Note over SEC: 보호경로 인가 거부<br/>AccessDeniedHandler (모듈 도달 전)
        SEC-->>C: 403 AUTH_ONBOARDING_REQUIRED
        C-->>U: 온보딩 완료 안내(US-1-2)
    else 정식 인증 토큰(ROLE_USER)
        SEC->>AUTH: 인증된 요청 전달 (userId)
        AUTH->>RDS: email-verify:code:{userId} 조회
        RDS-->>AUTH: 챌린지(있음/없음)
        alt 챌린지 없음 (미발송·만료·이미 검증)
            AUTH-->>C: 422 AUTH_EMAIL_VERIFICATION_FAILED<br/>(attempts 레코드 없음 — 즉시 거절)
            C-->>U: 인증번호 재요청 안내(§3)
        else 챌린지 있음 · 인증번호 불일치
            AUTH->>RDS: attempts += 1 (시도 기록)
            alt 시도 상한 초과
                AUTH-->>C: 429 TOO_MANY_REQUESTS
                C-->>U: 재발송 후 재시도 안내
            else
                AUTH-->>C: 422 AUTH_EMAIL_VERIFICATION_FAILED
                C-->>U: 인증번호 오류 안내
            end
        else 챌린지 있음 · 인증번호 일치(미만료·시도 미초과)
            AUTH->>RDS: email-verify:verified:{userId}=email 저장(TTL=검증 유효기간)<br/>+ email-verify:code:{userId} 삭제
            RDS-->>AUTH: 저장 완료
            Note over AUTH: 이번 범위는 소유 인증 마커 기록까지 —<br/>User.email 실제 변경 반영은 후속 이슈(#192 범위 밖)
            AUTH-->>C: 200 OK<br/>{ email: 마스킹, verified: true }
            C-->>U: 인증 완료
        end
    end
```

## 흐름 요약

- **가입 완료(ACTIVE) 사용자 전용**이다(#192 — 종전 "온보딩 단계 전용, 온보딩 스코프 허용"에서 **ACTIVE 전용으로 반전**). 공통 보안 필터(SEC)가 **정식 토큰(`ROLE_USER`)** 을 검증·인가하며, **온보딩 토큰(`ROLE_ONBOARDING`)으로 접근하면 모듈 도달 전 `AccessDeniedHandler`에서 `403 AUTH_ONBOARDING_REQUIRED`** 로 차단한다(US-1-5와 동일한 tier3 보호경로). 이메일 인증은 온보딩을 마친 뒤에 수행하므로 SEC 단계에서 상태 게이트가 갈리고, `auth` 모듈은 별도 계정 상태 조회 없이 인증번호 발송·확인만 담당한다.
- **인증번호 발송**(`POST /api/v1/auth/email/verification-code`): `auth`가 인증번호를 생성해 **아웃바운드 포트 `VerificationEmailSender`(인프라 어댑터: SMTP)로 동기 발송**하고, **발송에 성공한 뒤에만** 인증번호의 **단방향 해시**를 Redis `email-verify:code:{userId}`에 저장(TTL=만료, 예: 5분 — 확인 필요)한다(원문은 메일로만). provider 장애·타임아웃 등 **발송 실패 시 챌린지를 만들지 않고 `502 UPSTREAM_ERROR`** 로 응답해 재시도를 유도한다. 재발송 레이트리밋 초과는 `429 TOO_MANY_REQUESTS`. 응답의 `email`은 마스킹한다.
- **인증번호 확인**(`POST /api/v1/auth/email/verify`): 챌린지가 **없으면**(미발송·만료·이미 검증) 올릴 `attempts` 레코드가 없으므로 즉시 `422 AUTH_EMAIL_VERIFICATION_FAILED`로 거절하고 인증번호 재요청을 유도한다. 챌린지가 **있고** 입력 인증번호 해시가 `codeHash`와 **불일치**하면 `attempts`를 올려 상한 초과 시 `429 TOO_MANY_REQUESTS`, 아니면 `422`다. **일치**(미만료·시도 미초과)하면 `email-verify:verified:{userId}`에 검증 이메일을 기록하고 코드 키를 제거한다.
- **이번 범위는 접근 제한(ACTIVE 전용)까지**다 — 검증 성공은 소유 인증 마커만 남기고 **`User.email`을 실제로 바꾸지 않는다**. 실제 이메일 변경 반영은 **후속 이슈(#192 범위 밖)**로 미룬다. 온보딩(US-1-2)은 더 이상 이 인증을 선행 게이트로 요구하지 않으며, `AUTH_EMAIL_NOT_VERIFIED`(온보딩 이메일 미인증)는 제거되었다(#192).
- 인증번호 원문은 저장·로그하지 않고(해시만), `email`은 응답·로그 마스킹한다([error-response-guide §6](../../../api/error-response-guide.md)).
