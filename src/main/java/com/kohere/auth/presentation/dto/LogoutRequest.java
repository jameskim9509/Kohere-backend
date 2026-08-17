package com.kohere.auth.presentation.dto;

/**
 * 로그아웃 요청 DTO. 무효화할 refresh 토큰을 담는다.
 *
 * <p><b>본문도 {@code refreshToken}도 선택이다</b> — 읽는 규칙이 재발급과 같아 쿠키({@code refreshToken})가 있으면 본문을 보지
 * 않는다(ADR-0048 §3). 제약을 두지 않는 이유는 {@link ReissueRequest}와 같다.
 *
 * <p>docs/api/specs/01-auth-onboarding.md §7 (POST /api/v1/auth/logout).
 */
public record LogoutRequest(String refreshToken) {}
