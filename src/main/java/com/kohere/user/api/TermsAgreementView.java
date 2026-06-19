package com.kohere.user.api;

import java.time.Instant;

/**
 * 약관 동의(PENDING→TERMS_AGREED) 결과(모듈 간 전달용). status는 원시 문자열로 노출한다.
 * docs/api/specs/01-auth-onboarding.md §2(POST /auth/terms 응답)·시퀀스 US-1-7과 정합.
 */
public record TermsAgreementView(
    String status,
    boolean termsOfServiceAgreed,
    boolean privacyPolicyAgreed,
    boolean marketingAgreed,
    Instant agreedAt) {}
