package com.kohere.auth.presentation.dto;

import com.kohere.auth.domain.Provider;
import jakarta.validation.constraints.NotNull;

/**
 * 소셜 로그인 요청 DTO. {@code provider} 누락(null)은 Bean Validation으로 {@code INVALID_INPUT}, 허용 외 enum
 * 문자열이면 역직렬화 단계에서 거부되어 {@code MALFORMED_REQUEST}로 처리한다.
 *
 * <p>자격 필드는 provider 조건부다 — Google은 {@code idToken}, Apple은 {@code authorizationCode}(1회용·약 5분,
 * ADR-0031). provider별 필수 여부는 Bean Validation이 아니라 application 계층에서 판정하며, 누락 시 {@code
 * AUTH_MISSING_CREDENTIAL}(400)이다.
 *
 * <p>docs/api/specs/01-auth-onboarding.md §1 (POST /api/v1/auth/social-login).
 */
public record SocialLoginRequest(
    @NotNull Provider provider, String idToken, String authorizationCode) {}
