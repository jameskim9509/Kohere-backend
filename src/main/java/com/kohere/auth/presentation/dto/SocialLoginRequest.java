package com.kohere.auth.presentation.dto;

import com.kohere.auth.domain.Provider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 소셜 로그인 요청 DTO. provider enum 불일치 등 입력 형식 위반은 공통 {@code INVALID_INPUT}으로 처리한다.
 *
 * <p>docs/api/specs/01-auth-onboarding.md §1 (POST /api/v1/auth/social-login).
 */
public record SocialLoginRequest(@NotNull Provider provider, @NotBlank String idToken) {}
