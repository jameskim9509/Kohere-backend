package com.kohere.auth.application.dto;

/**
 * 소셜 로그인 결과. 기존 회원/신규·미완료 회원 분기를 {@code onboardingRequired}로 표현하고, {@code
 * status}(PENDING/TERMS_AGREED/ACTIVE)로 클라이언트의 재개 지점을 분기한다 — PENDING→약관 동의, TERMS_AGREED→온보딩,
 * ACTIVE→홈. 미완료 회원은 온보딩 전용 access 토큰만 받고 {@code refreshToken}은 {@code null}이다.
 *
 * <p>{@code email}·{@code name}은 온보딩 화면 프리필용으로 모든 분기(신규 PENDING·재로그인·기존 ACTIVE)에서 반환한다 — 값은 {@code
 * User.email}·{@code User.name}이며 이름이 아직 없으면 {@code null}이다(#192).
 *
 * <p>docs/api/specs/01-auth-onboarding.md §1 (social-login 응답).
 */
public record SocialLoginResponse(
    boolean onboardingRequired,
    String status,
    String tokenType,
    String accessToken,
    String refreshToken,
    long expiresIn,
    String email,
    String name) {}
