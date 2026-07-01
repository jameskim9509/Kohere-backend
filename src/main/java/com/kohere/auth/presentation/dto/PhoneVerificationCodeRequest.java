package com.kohere.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 연락처 인증번호 발송 요청 DTO(POST /api/v1/auth/phone/verification-code, 임대인 전용). {@code phoneNumber}는 인증번호를
 * 받을 휴대폰 번호다. docs/api/specs/01-auth-onboarding.md §4-1.
 */
public record PhoneVerificationCodeRequest(@NotBlank String phoneNumber) {}
