package com.kohere.auth.application.dto;

/**
 * 이메일 인증번호 발송(POST /auth/email/verification-code) 결과. {@code email}은 마스킹해 반환하고, {@code expiresIn}은
 * 인증번호 만료까지의 초다. 인증번호 원문은 응답·로그에 노출하지 않는다. docs/api/specs/01-auth-onboarding.md §3.
 */
public record EmailVerificationCodeResponse(String email, long expiresIn) {}
