package com.kohere.auth.application.dto;

/**
 * 이메일 인증번호 확인(POST /auth/email/verify) 결과. {@code email}은 마스킹, {@code verified}는 성공 시 true.
 * docs/api/specs/01-auth-onboarding.md §4.
 */
public record EmailVerifyResponse(String email, boolean verified) {}
