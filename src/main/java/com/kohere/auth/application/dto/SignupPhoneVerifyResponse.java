package com.kohere.auth.application.dto;

/**
 * 가입용 연락처 인증번호 확인(POST /auth/phone/signup/verify) 결과. {@code phoneNumber}는 마스킹, {@code verified}는
 * 성공 시 true다(실패는 응답이 아니라 422 {@code AUTH_PHONE_VERIFICATION_FAILED}로 나가므로 false가 실리는 경우는 없다).
 *
 * <p>이 응답은 <b>연동 대상 계정의 유무를 알려주지 않는다</b> — 가입 폼은 연동 여부와 무관하게 항상 전체 필드를 받고, 판정은 가입 제출(US-1-11) 시점에만
 * 이뤄진다. docs/api/specs/01-auth-onboarding.md §1-2.
 */
public record SignupPhoneVerifyResponse(String phoneNumber, boolean verified) {}
