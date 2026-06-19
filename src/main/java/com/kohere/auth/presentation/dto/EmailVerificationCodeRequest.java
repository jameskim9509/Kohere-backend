package com.kohere.auth.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 이메일 인증번호 발송 요청 DTO(POST /api/v1/auth/email/verification-code).
 * docs/api/specs/01-auth-onboarding.md §3.
 */
public record EmailVerificationCodeRequest(@NotBlank @Email String email) {}
