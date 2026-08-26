package com.kohere.auth.application.dto;

/**
 * 가입용 이메일 인증번호 확인(POST /auth/email/signup/verify) 결과. {@code email}은 마스킹, {@code verified}는 성공 시
 * true다(실패는 응답이 아니라 422 {@code AUTH_EMAIL_VERIFICATION_FAILED}로 나가므로 false가 실리는 경우는 없다).
 *
 * <p>이 응답은 <b>중복 여부를 다시 말하지 않는다</b> — 판정은 발송(§1-11)이 이미 했고, 발송~확인 사이에 남이 그 주소로 가입했더라도 그 사실은 가입
 * 제출(§1-3)의 중복 게이트가 잡는다. docs/api/specs/01-auth-onboarding.md §1-12.
 */
public record SignupEmailVerifyResponse(String email, boolean verified) {}
