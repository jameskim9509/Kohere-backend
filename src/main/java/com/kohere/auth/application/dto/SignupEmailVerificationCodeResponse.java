package com.kohere.auth.application.dto;

/**
 * 가입용 이메일 인증번호 발송(POST /auth/email/signup/verification-code) 결과. {@code email}은 마스킹해 반환하고, {@code
 * expiresIn}은 인증번호 만료까지의 초다. 인증번호 원문은 응답·로그에 노출하지 않는다.
 *
 * <p><b>가입 이력이 있는 주소는 여기까지 오지 않는다</b> — 이미 웹 로그인 ID로 쓰이는 이메일이면 발송 전에 409 {@code
 * AUTH_EMAIL_ALREADY_REGISTERED}로 끊기므로 이 응답은 언제나 "발송했다"는 뜻이다. 번호 채널(가입용 SMS)이 계정 존재를 감추는 것과 갈리는
 * 지점이며, 그 대가로 가입 여부 열거를 수용한다. docs/api/specs/01-auth-onboarding.md §1-11.
 */
public record SignupEmailVerificationCodeResponse(String email, long expiresIn) {}
