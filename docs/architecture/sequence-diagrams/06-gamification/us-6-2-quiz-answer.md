# US-6-2 — 퀴즈 정답 제출 및 즉시 피드백

> 모듈: 게이미피케이션 (퀴즈) · [유저 스토리](../../../requirements/user-stories.md) · [API 스펙](../../../api/specs/06-gamification.md)

```mermaid
sequenceDiagram
    actor U as 사용자
    participant C as 앱(클라이언트)
    participant SEC as 공통 보안 필터
    participant GAME as gamification 모듈
    participant USER as user 모듈
    participant DB as MongoDB

    U->>C: 보기 선택(예: B) 후 제출
    C->>SEC: POST /api/v1/quizzes/{quizId}/answer<br/>Authorization: Bearer accessToken (게스트는 헤더 없음)<br/>{ selectedChoice: "B" }
    Note over SEC: Authorization 헤더가 있을 때만 JWT 검증 (서명·만료·클레임)<br/>인가는 permitAll — 인증 없이도 통과 (#181)

    alt access token 만료 (토큰을 보냈는데 만료)
        SEC-->>C: 401 TOKEN_EXPIRED
        C-->>U: 토큰 재발급 후 재시도
        Note over U,DB: ↑ 에러 응답 — 이후 단계는 수행하지 않음<br/>(게스트 강등 아님 — 재발급이 필요한 회원)
    else 토큰 없음 · 위조/형식 오류 → 게스트
        Note over SEC: SecurityContext 비움 → principal null<br/>(합성 userId를 발급하지 않는다 — 게스트는 신원 부재로 표현)
        SEC->>GAME: 게스트 요청 전달 (userId = null)
    else 유효 토큰 (ROLE_USER 또는 ROLE_ONBOARDING)
        SEC->>GAME: 인증된 요청 전달 (userId)
    end

    Note over GAME: 역할 게이트 없음 — 세입자·임대인을 구분하지 않는다(#181)<br/>getUserType을 호출하지 않으므로 403 FORBIDDEN 분기가 없다
    Note over GAME: selectedChoice(A~D) 검증 후<br/>저장된 correctChoice와 대조해 서버가 판정<br/>무상태(제출·포인트 없음, 반복 무부작용)

    alt quizId 대상 퀴즈 없음
        GAME-->>C: 404 QUIZ_NOT_FOUND
        C-->>U: 퀴즈 없음 안내
    else 대상 퀴즈 존재
        alt 회원 (userId != null)
            Note over GAME: 해설 번역을 위해 표시 언어 필요<br/>(JWT 클레임 비의존 — 항상 user 공개 query로 취득)
            GAME->>USER: user 공개 query 동기 호출 getLanguage(userId)<br/>(표시 언어 조회 — user가 users.lang 있으면 그 값, 없으면 en, ADR-0002 Decision 5)
            USER-->>GAME: 표시 언어 lang (임대인은 온보딩 때 서버가 고정 부여한 ko)
        else 게스트 (userId == null)
            Note over GAME,USER: getLanguage를 호출하지 않는다 — 표시 언어 lang=en 고정<br/>(users 행이 없어 호출하면 404 USER_NOT_FOUND)
        end
        GAME->>DB: quizId로 대상 퀴즈 도큐먼트 조회 (quizzes)
        DB-->>GAME: 퀴즈 도큐먼트(question·choices·correctChoice·explanation 언어-키 맵)
        Note over GAME: 채점은 correctChoice로, 해설은 explanation만 사용<br/>(question·choices는 채점 응답에 쓰지 않음)

        alt 도큐먼트에 그 언어 키 존재
            Note over GAME: explanation 언어-키 맵에서 그 언어 값 선택<br/>explanation=explanation[lang]
        else 미지원 언어
            Note over GAME: 언어-키 맵을 영어(en)로 폴백<br/>(에러 아님 — 기본 언어=영어)
        end

        alt 정답 (selectedChoice == correctChoice)
            Note over GAME: 채점만 수행 — DB 쓰기·제출 기록·포인트 없음<br/>(해설은 정답·오답 모두 반환)
            GAME-->>C: 200 OK<br/>{ quizId, selectedChoice, correct: true,<br/>explanation(번역) }
            C-->>U: 정답 안내 + 해설 표시
        else 오답 (selectedChoice != correctChoice)
            GAME-->>C: 200 OK<br/>{ quizId, selectedChoice, correct: false,<br/>correctChoice, explanation(번역) }
            C-->>U: 오답 안내 + 정답·해설 표시
        end
    end
```

## 흐름 요약

- `POST /api/v1/quizzes/{quizId}/answer`에 `selectedChoice`(A~D)를 보내면 gamification 모듈이 MongoDB `quizzes`에서 `quizId`로 퀴즈 도큐먼트를 읽어(랜덤 조회와 동일한 단건 도큐먼트 읽기) 저장된 `correctChoice`와 대조해 서버가 판정하고 `200 OK`로 즉시 피드백한다. 채점에는 `correctChoice`, 해설에는 `explanation`만 사용한다(`question`·`choices`는 채점 응답에 쓰지 않는다)(공통 보안 필터(SEC)가 컨트롤러 앞단에서 JWT를 검증한 뒤 모듈로 전달). **무상태 채점** — 제출 기록·포인트 적립·저장이 전혀 없고, 같은 요청을 반복해도 부작용이 없다(멱등·재생 가능). `201 Created`/`Location`이 아니다.
- 정답이면 `{ quizId, selectedChoice, correct: true, explanation(번역) }`을 응답한다. 오답이면 `{ quizId, selectedChoice, correct: false, correctChoice, explanation(번역) }`을 응답한다 — 해설(`explanation`)은 정답·오답 모두 사용자 표시 언어로 번역해 반환하고, `correctChoice`는 오답일 때만 포함한다.
- 해설 번역을 위해 회원 요청이면 gamification 모듈은 `user`의 공개 query(`getLanguage`)를 동기 호출해 표시 언어(`lang`)를 취득하고(ADR-0002 Decision 5), `quizzes` 도큐먼트의 `explanation` 언어-키 맵에서 그 언어 값을 고른다. 해당 언어 키가 없으면 **영어(`en`)로 폴백**한다(에러 아님). 선택지 키 `A~D`는 **언어 불변**이며 채점은 키 기준으로 한다.
- 오류 경계: `selectedChoice`가 A~D가 아니면 `400 INVALID_INPUT`, JSON 파싱 실패 등은 `400 MALFORMED_REQUEST`, `quizId`에 해당하는 퀴즈가 없으면 `404 QUIZ_NOT_FOUND`로 반환한다(하루 1회 제한·중복 제출 개념이 없으므로 `409`/`422`는 없다).
- `/api/v1/quizzes/**`는 **`permitAll`** 로 열려 비회원(게스트)도 채점을 요청할 수 있다(#181). 게스트 신원은 **`userId == null`(신원 부재)** 이며 합성 userId를 발급하지 않는다. 게스트 경로에서는 표시 언어 조회(`getLanguage`)를 **호출하지 않고**(호출하면 `users` 행이 없어 `404 USER_NOT_FOUND`) 해설을 `en`으로 내려준다. 채점은 무상태라 게스트여도 저장·기록이 없다.
- **이 엔드포인트에는 역할 게이트가 없다** — 세입자 전용 게이트(`assertTenant` → `getUserType`)를 제거했다(#181). `permitAll`로 게스트가 채점할 수 있는데 로그인한 임대인만 막는 것은 실효가 없기 때문이다(임대인이 로그아웃하면 그대로 호출된다). 따라서 **로그인한 임대인도 `200 OK`** 다(종전 `403 FORBIDDEN`에서 변경) — `403 FORBIDDEN`(TenantOnly)은 이 도메인에서 사라진다.
- 토큰 없음·위조는 게스트로 처리하지만 **만료된 access token은 `401 TOKEN_EXPIRED`** 를 유지한다(재발급이 필요한 회원이므로). 온보딩 미완료(`ROLE_ONBOARDING`) 토큰도 통과하므로 `403 AUTH_ONBOARDING_REQUIRED`는 이 엔드포인트에서 발생하지 않는다.
- 호출자별 결과는 다음과 같다(해설 `explanation`의 표시 언어).

| 호출자 | 결과 | 표시 언어 |
| --- | --- | --- |
| 비로그인 게스트 | 200 | `en` 고정(`getLanguage` 미호출) |
| 세입자(ACTIVE) | 200 | `users.lang` |
| 임대인 | 200 (종전 403에서 변경) | `users.lang` = `ko`(온보딩 시 서버 고정 부여, #141) |
| 온보딩 미완료(PENDING/TERMS_AGREED) | 200 | `users.lang` |
