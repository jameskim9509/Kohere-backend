package com.kohere.auth.application.dto;

import java.time.Instant;

/**
 * 약관 동의(POST /auth/terms) 결과. 동의 후 상태(TERMS_AGREED)·동의 3종·동의 시각을 반환한다.
 * docs/api/specs/01-auth-onboarding.md §2.
 */
public record TermsResponse(
    String status,
    boolean termsOfServiceAgreed,
    boolean privacyPolicyAgreed,
    boolean marketingAgreed,
    Instant agreedAt) {}
