package com.kohere.auth.presentation.dto;

import com.kohere.common.request.Emails;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 가입용 이메일 인증번호 확인 요청 DTO(POST /api/v1/auth/email/signup/verify, 임대인 웹·비로그인). {@code email}은 인증번호를
 * 발송한 주소와 같아야 한다 — 정규화한 값이 곧 챌린지 키라 대소문자·앞뒤 공백이 달라도 같은 챌린지를 가리킨다.
 *
 * <p>{@code code}에는 길이 제약을 두지 않는다 — 자릿수는 서버 정책({@code app.email.code-length})이고 검증은 해시 대조라, 자릿수를 요청
 * 검증으로 알려 주면 무차별 대입의 탐색 공간만 좁혀 준다. docs/api/specs/01-auth-onboarding.md §1-12.
 */
public record SignupEmailVerifyRequest(
    @NotBlank @Size(max = 255) @Pattern(regexp = Emails.PATTERN) String email,
    @NotBlank String code) {}
