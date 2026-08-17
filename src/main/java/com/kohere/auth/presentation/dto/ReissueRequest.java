package com.kohere.auth.presentation.dto;

/**
 * 토큰 재발급 요청 DTO. 재발급할 refresh 토큰을 담는다(헤더 access 토큰 없이 이 값으로 처리한다).
 *
 * <p><b>본문 자체가 선택이고 {@code refreshToken}에도 제약이 없다</b>(ADR-0048 §3) — 서버는 refresh를 <b>쿠키({@code
 * refreshToken}) 우선 · 본문 fallback</b>으로 읽으므로 브라우저는 이 DTO를 아예 보내지 않는다. {@code @NotBlank}를 남겨 두면 쿠키에
 * 멀쩡한 토큰이 있는데 본문의 빈 문자열 때문에 요청이 거절되어, <b>한 채널이 다른 채널을 막는다</b>.
 *
 * <p>그래서 "값이 없다"는 판정은 두 채널을 합친 뒤 {@code AuthService}가 한 곳에서 하고, 결과는 Bean Validation과 같은 모양(400
 * {@code INVALID_INPUT} + {@code errors[].field=refreshToken})으로 나간다.
 *
 * <p>docs/api/specs/01-auth-onboarding.md §6 (POST /api/v1/auth/reissue).
 */
public record ReissueRequest(String refreshToken) {}
