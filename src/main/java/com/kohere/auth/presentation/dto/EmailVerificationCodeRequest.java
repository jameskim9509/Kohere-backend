package com.kohere.auth.presentation.dto;

import com.kohere.common.request.Emails;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 이메일 인증번호 발송 요청 DTO(POST /api/v1/auth/email/verification-code).
 * docs/api/specs/01-auth-onboarding.md §3.
 */
public record EmailVerificationCodeRequest(
    @NotBlank @Size(max = 255) @Pattern(regexp = Emails.PATTERN) String email) {}
