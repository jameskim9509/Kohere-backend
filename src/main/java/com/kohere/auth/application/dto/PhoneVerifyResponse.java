package com.kohere.auth.application.dto;

/**
 * 연락처 인증번호 확인(POST /auth/phone/verify) 결과. {@code phoneNumber}는 마스킹, {@code verified}는 성공 시 true.
 * docs/api/specs/01-auth-onboarding.md §4-2.
 */
public record PhoneVerifyResponse(String phoneNumber, boolean verified) {}
