# US-1-6 — 온보딩 중 이메일 인증하기

> 모듈: 소셜 로그인 · 온보딩 · [유저 스토리](../../../requirements/user-stories.md) · [API 스펙](../../../api/specs/01-auth-onboarding.md)
>
> 온보딩(US-1-2) 제출 **전에** 수행하는 이메일 소유 인증이다. 인증번호는 `auth`가 해시로만 보관(Redis, TTL)하고 메일 발송은 인프라 어댑터에 위임한다.

```mermaid
sequenceDiagram
    actor U as 사용자
    participant C as 앱(클라이언트)
    participant SEC as 공통 보안 필터
    participant AUTH as auth 모듈
    participant USER as user 모듈
    participant RDS as Redis
    participant MAIL as 메일 발송(인프라 어댑터)

    Note over U,C: 온보딩 화면에서 이메일 입력
    U->>C: 이메일 입력 후 "인증번호 받기"
    C->>SEC: POST /api/v1/auth/email/verification-code<br/>Authorization: Bearer 온보딩토큰<br/>{ email }
    Note over SEC: JWT 검증 (서명·만료·클레임)<br/>온보딩 스코프(ROLE_ONBOARDING) 주입<br/>email 인증 경로 인가
    SEC->>AUTH: 인증된 요청 전달 (userId + 온보딩 스코프)
    AUTH->>USER: 계정 상태 조회(공개 API)
    USER-->>AUTH: status(PENDING|TERMS_AGREED|ACTIVE)
    alt 약관 미동의 (PENDING)
        AUTH-->>C: 422 AUTH_TERMS_AGREEMENT_REQUIRED
        C-->>U: 약관 동의 안내(US-1-7)
    else 이미 온보딩 완료 (ACTIVE)
        AUTH-->>C: 409 AUTH_ONBOARDING_ALREADY_COMPLETED
        C-->>U: 이미 가입 완료 안내
    else 약관 동의 완료 (TERMS_AGREED) — 인증번호 발송 진행
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
    C->>SEC: POST /api/v1/auth/email/verify<br/>Authorization: Bearer 온보딩토큰<br/>{ email, code }
    Note over SEC: JWT 검증 + 온보딩 스코프 인가
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
        AUTH->>RDS: email-verify:verified:{userId}=email 저장(TTL=온보딩토큰 만료)<br/>+ email-verify:code:{userId} 삭제
        RDS-->>AUTH: 저장 완료
        AUTH-->>C: 200 OK<br/>{ email: 마스킹, verified: true }
        C-->>U: 인증 완료 → 온보딩 계속(US-1-2)
    end
```

## 흐름 요약

- 온보딩 중인 사용자가 입력한 이메일로 인증번호를 받는다. 공통 보안 필터(SEC)가 **온보딩 토큰(`ROLE_ONBOARDING`)** 을 검증하고 이메일 인증 경로를 인가한 뒤 `auth 모듈`로 `userId`를 전달한다(온보딩 흐름이라 온보딩 토큰을 허용 — [API 스펙](../../../api/specs/01-auth-onboarding.md)).
- **선행 게이트**: 이메일 인증은 **약관 동의(US-1-7, `TERMS_AGREED`)가 선행**되어야 한다. `auth`가 `user 모듈` 공개 API로 계정 상태를 조회해 **약관 미동의(`PENDING`)면 `422 AUTH_TERMS_AGREEMENT_REQUIRED`**(약관 동의 안내가 먼저), 이미 완료(`ACTIVE`)면 `409 AUTH_ONBOARDING_ALREADY_COMPLETED`로 거절한다. `TERMS_AGREED`일 때만 아래 발송·확인을 진행한다.
- **인증번호 발송**(`POST /api/v1/auth/email/verification-code`): `auth`가 인증번호를 생성해 **아웃바운드 포트 `VerificationEmailSender`(인프라 어댑터: SMTP)로 동기 발송**하고, **발송에 성공한 뒤에만** 인증번호의 **단방향 해시**를 Redis `email-verify:code:{userId}`에 저장(TTL=만료, 예: 5분 — 확인 필요)한다(원문은 메일로만). provider 장애·타임아웃 등 **발송 실패 시 챌린지를 만들지 않고 `502 UPSTREAM_ERROR`** 로 응답해 재시도를 유도한다. 재발송 레이트리밋 초과는 `429 TOO_MANY_REQUESTS`. 응답의 `email`은 마스킹한다.
- **인증번호 확인**(`POST /api/v1/auth/email/verify`): 챌린지가 **없으면**(미발송·만료·이미 검증) 올릴 `attempts` 레코드가 없으므로 즉시 `422 AUTH_EMAIL_VERIFICATION_FAILED`로 거절하고 인증번호 재요청을 유도한다. 챌린지가 **있고** 입력 인증번호 해시가 `codeHash`와 **불일치**하면 `attempts`를 올려 상한 초과 시 `429 TOO_MANY_REQUESTS`, 아니면 `422`다. **일치**(미만료·시도 미초과)하면 `email-verify:verified:{userId}`에 검증 이메일을 기록(TTL=온보딩 토큰 만료)하고 코드 키를 제거한다.
- 이후 **온보딩 제출(US-1-2)** 에서 `auth`가 제출 `email`을 `email-verify:verified:{userId}`와 대조해 일치할 때만 `user` 온보딩 완료 명령을 진행한다(미인증·불일치 `422 AUTH_EMAIL_NOT_VERIFIED`). 인증 흔적은 TTL로 자동 소멸하며, 확정 이메일만 `users.email`로 영속한다([database-design](../../../database/database-design.md) §4-1·§4-2).
- 인증번호 원문은 저장·로그하지 않고(해시만), `email`은 응답·로그 마스킹한다([error-response-guide §6](../../../api/error-response-guide.md)).
