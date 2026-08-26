package com.kohere.auth.presentation.dto;

import com.kohere.common.request.Emails;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 이메일 인증번호 확인 요청 DTO(POST /api/v1/auth/email/verify). {@code email}은 인증번호를 발송한 이메일과 일치해야 한다.
 * docs/api/specs/01-auth-onboarding.md §4.
 */
public record EmailVerifyRequest(
    @NotBlank @Size(max = 255) @Pattern(regexp = Emails.PATTERN) String email,
    @NotBlank String code) {}
