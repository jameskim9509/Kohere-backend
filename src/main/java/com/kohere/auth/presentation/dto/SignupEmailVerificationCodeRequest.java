package com.kohere.auth.presentation.dto;

import com.kohere.common.request.Emails;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 가입용 이메일 인증번호 발송 요청 DTO(POST /api/v1/auth/email/signup/verification-code, 임대인 웹·비로그인). {@code
 * email}은 인증번호를 받을 주소이자 가입 후 <b>웹 로그인 ID</b>가 될 값으로, 대소문자·앞뒤 공백은 응용 계층이 정규화한다.
 *
 * <p>길이 상한은 저장 컬럼({@code local_accounts.email} VARCHAR(255), V22)과 맞춘다 — 상한이 없으면 300자짜리 값이 인증을 통과한
 * 뒤 가입 INSERT에서 {@code DataIntegrityViolationException}으로 죽어 <b>400이어야 할 입력 오류가 그때 가서 500</b>이 된다.
 *
 * <p>형식 위반은 {@code INVALID_INPUT}으로 <b>메일 발송·레이트리밋 카운트 이전에</b> 거른다 — 걸러 내지 않으면 형식이 깨진 값이 그대로 Redis
 * 키가 된다. docs/api/specs/01-auth-onboarding.md §1-11.
 */
public record SignupEmailVerificationCodeRequest(
    @NotBlank @Size(max = 255) @Pattern(regexp = Emails.PATTERN) String email) {}
