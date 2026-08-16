package com.kohere.auth.application.dto;

/**
 * 가입용 연락처 인증번호 발송(POST /auth/phone/signup/verification-code) 결과. {@code phoneNumber}는 마스킹해 반환하고,
 * {@code expiresIn}은 인증번호 만료까지의 초다. 인증번호 원문은 응답·로그에 노출하지 않는다.
 *
 * <p>가입 이력이 있는 번호든 없는 번호든 <b>같은 모양·같은 값</b>이 나간다 — 계정 존재 여부를 응답으로 알 수 없게 하기 위해서다.
 * docs/api/specs/01-auth-onboarding.md §1-1.
 */
public record SignupPhoneVerificationCodeResponse(String phoneNumber, long expiresIn) {}
