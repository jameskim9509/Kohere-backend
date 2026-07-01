package com.kohere.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 연락처 인증번호 확인 요청 DTO(POST /api/v1/auth/phone/verify, 임대인 전용). {@code phoneNumber}는 인증번호를 발송한 번호와
 * 일치해야 한다. docs/api/specs/01-auth-onboarding.md §4-2.
 */
public record PhoneVerifyRequest(@NotBlank String phoneNumber, @NotBlank String code) {}
