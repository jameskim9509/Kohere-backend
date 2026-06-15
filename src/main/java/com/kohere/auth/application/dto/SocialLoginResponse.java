package com.kohere.auth.application.dto;

/**
 * 소셜 로그인 결과. 기존 회원/신규 회원 분기를 {@code onboardingRequired}로 표현한다. 신규 회원은 온보딩 전용 access 토큰만 받고 {@code
 * refreshToken}은 {@code null}이다.
 *
 * <p>docs/api/specs/01-auth-onboarding.md §1 (social-login 응답).
 */
public record SocialLoginResponse(
    boolean onboardingRequired,
    String tokenType,
    String accessToken,
    String refreshToken,
    long expiresIn) {}
