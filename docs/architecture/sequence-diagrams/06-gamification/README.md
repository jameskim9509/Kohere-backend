# 시퀀스 다이어그램 — 게이미피케이션 (퀴즈)

> 사용자 → 앱(클라이언트) → 백엔드(서버) 흐름. 관련: [유저 스토리](../../../requirements/user-stories.md) · [API 스펙](../../../api/specs/06-gamification.md)
>
> **게스트 접근**: `/api/v1/quizzes/**`는 `permitAll`로 열려 비회원도 호출할 수 있다(#181). 게스트는 `userId == null`(신원 부재)로 표현하며 세션 키를 요구하지 않는다 — 퀴즈는 영속에 userId 필드가 없어 신원 소비자가 없기 때문이다. 아래 세 다이어그램 모두 게스트 분기를 함께 그린다.
>
> **역할 게이트 없음**: 세입자 전용 게이트(`assertTenant` → `getUserType`)를 제거했다(#181) — 게스트에게 열린 마당에 로그인한 임대인만 막는 것은 실효가 없기 때문이다. **로그인한 임대인도 `200 OK`** 이며(종전 `403 FORBIDDEN`), `403 FORBIDDEN`(TenantOnly)은 이 도메인에서 사라진다. 회원 경로에 남는 `user` 모듈 호출은 표시 언어 조회(`getLanguage`)뿐이다(임대인은 온보딩 시 서버가 고정 부여한 `ko`, #141).

| 스토리 | 제목 | 다이어그램 |
| --- | --- | --- |
| US-6-1 | 랜덤 퀴즈 조회 | [us-6-1-random-quiz](us-6-1-random-quiz.md) |
| US-6-2 | 퀴즈 정답 제출 및 즉시 피드백 | [us-6-2-quiz-answer](us-6-2-quiz-answer.md) |
| US-6-3 | 사용자 표시 언어 기반 퀴즈 문항·해설 번역 제공 | [us-6-3-quiz-i18n](us-6-3-quiz-i18n.md) |
