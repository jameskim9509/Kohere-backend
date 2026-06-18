package com.kohere.auth.presentation.dto;

import com.kohere.auth.domain.Provider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 소셜 로그인 요청 DTO. {@code provider} 누락(null)·{@code idToken} 빈값은 Bean Validation으로 {@code
 * INVALID_INPUT}, {@code provider}가 허용 외 enum 문자열이면 역직렬화 단계에서 거부되어 {@code MALFORMED_REQUEST}로 처리한다.
 *
 * <p>docs/api/specs/01-auth-onboarding.md §1 (POST /api/v1/auth/social-login).
 */
public record SocialLoginRequest(@NotNull Provider provider, @NotBlank String idToken) {}
