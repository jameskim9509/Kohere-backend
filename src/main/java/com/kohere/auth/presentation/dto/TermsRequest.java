package com.kohere.auth.presentation.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 약관 동의 요청 DTO(POST /api/v1/auth/terms). 필수 약관(이용약관·개인정보처리방침) {@code false}는 응용 계층에서 {@code
 * AUTH_REQUIRED_AGREEMENT_MISSING}(422)으로 처리한다. docs/api/specs/01-auth-onboarding.md §2.
 */
public record TermsRequest(
    @NotNull Boolean termsOfServiceAgreed,
    @NotNull Boolean privacyPolicyAgreed,
    Boolean marketingAgreed) {}
