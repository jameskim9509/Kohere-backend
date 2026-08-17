package com.kohere.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 소셜 로그인 요청 DTO. {@code provider}는 누락·빈값과 허용 외 값 모두 {@code INVALID_INPUT}이다 — enum 타입으로 선언하면 허용 외
 * 값이 역직렬화 단계에서 거부돼 {@code MALFORMED_REQUEST}가 되고 어느 필드인지 남지 않아, {@code String}으로 받아 응용 계층이
 * 파싱한다(#151).
 *
 * <p>자격 필드는 provider 조건부다 — Google은 {@code idToken}, Apple은 {@code authorizationCode}(1회용·약 5분,
 * ADR-0031). provider별 필수 여부는 Bean Validation이 아니라 application 계층에서 판정하며, 누락 시 {@code
 * AUTH_MISSING_CREDENTIAL}(400)이다.
 *
 * <p>{@code email}·{@code name}은 앱이 네이티브 SDK(Apple {@code ASAuthorization}/Google account)에서 받아 함께
 * 보내는 **선택** 필드다(그래서 검증 애너테이션이 없다) — 최초 로그인(신규 가입)에서만 캡처해 {@code User}에 영구 저장하고 재로그인 요청 값은 무시한다.
 * Apple은 이름·이메일을 최초 인증 1회만 준다(#192). 최초 로그인 시 {@code email}은 토큰의 {@code email} 클레임과 교차 검증하고(불일치 422
 * AUTH_EMAIL_MISMATCH, 양쪽 모두 없으면 422 AUTH_EMAIL_REQUIRED), {@code name}은 검증하지 않고 요청 값을 신뢰한다.
 *
 * <p>docs/api/specs/01-auth-onboarding.md §1 (POST /api/v1/auth/social-login).
 */
public record SocialLoginRequest(
    @NotBlank String provider,
    String idToken,
    String authorizationCode,
    String email,
    String name) {}
