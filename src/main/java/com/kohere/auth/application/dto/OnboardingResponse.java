package com.kohere.auth.application.dto;

import com.kohere.user.api.UserProfileView;

/**
 * 온보딩 완료 응답. 완성된 회원 프로필({@code user})과 정식 access/refresh 토큰을 함께 반환한다. {@code expiresIn}은 access 토큰
 * 만료까지의 초(seconds).
 *
 * <p>{@code linked}는 <b>이 요청이 기존 웹 임대인 계정과 병합됐는지</b>다(US-1-15). {@code true}면 {@code user}와 토큰이 모두
 * <b>요청 토큰의 계정이 아니라 합쳐진 웹 계정</b> 기준이며, 요청을 보낸 임시 계정은 이미 삭제됐다 — 클라이언트는 응답의 토큰으로 교체해야 하고 화면에 띄울
 * 이름·이메일도 방금 입력한 값이 아니라 살아남은 계정의 값이다. 반대 방향(웹 가입이 앱 계정에 자격증명만 붙이는 경우)의 {@link
 * SignupResponse#linked}와 <b>같은 이름을 쓰는 것은 의도다</b> — 두 방향 모두 "계정이 하나로 합쳐졌다"는 한 가지 사실을 알리므로 클라이언트가
 * 어휘를 두 벌 익힐 이유가 없다.
 *
 * <p><b>{@code user.id}만으로 병합을 추론하게 두지 않는 이유</b>: 그러려면 클라이언트가 자기가 보낸 토큰에 박힌 {@code userId}를 꺼내 응답과
 * 비교해야 하는데, 그 비교를 빠뜨려도 화면은 정상으로 보이고 <b>다음 API 호출에서야</b> 낡은 토큰으로 401을 맞는다. 병합은 서버가 아는 사실이므로 서버가 말한다.
 *
 * <p><b>세입자 온보딩({@code POST /auth/onboarding})에서는 언제나 {@code false}다 — 버그가 아니다.</b> 매칭 키가 SMS로 인증한
 * 휴대폰 번호 단독인데 세입자는 온보딩에서 번호를 수집하지 않아 {@code phone_number}가 NULL이다. 즉 세입자 트랙에는 <b>대조할 열쇠가 아예 없어</b>
 * 병합 분기에 닿을 수 없다(ADR-0047 §3 — 세입자→임대인 전환 미지원의 구조적 이유이기도 하다).
 *
 * <p>응답 필드가 늘어난 것은 하위 호환이라 버전은 {@code /api/v1} 그대로다.
 *
 * <p>docs/api/specs/01-auth-onboarding.md §5·§5-2 (onboarding 응답) · 시퀀스 US-1-2·US-1-15와 정합.
 */
public record OnboardingResponse(
    boolean linked,
    UserProfileView user,
    String tokenType,
    String accessToken,
    String refreshToken,
    long expiresIn) {}
