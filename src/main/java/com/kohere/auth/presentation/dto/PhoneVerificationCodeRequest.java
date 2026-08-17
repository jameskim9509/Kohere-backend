package com.kohere.auth.presentation.dto;

import com.kohere.common.request.PhoneNumbers;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 연락처 인증번호 발송 요청 DTO(POST /api/v1/auth/phone/verification-code, 임대인 전용). {@code phoneNumber}는 인증번호를
 * 받을 휴대폰 번호로, 하이픈은 있어도 없어도 된다 — 응용 계층이 숫자만 남겨 정규화한 값으로 챌린지를 저장한다({@link PhoneNumbers}, #229). 형식
 * 위반은 SMS 발송 전에 {@code INVALID_INPUT}으로 거른다. docs/api/specs/01-auth-onboarding.md §4-1.
 */
public record PhoneVerificationCodeRequest(
    @NotBlank @Pattern(regexp = PhoneNumbers.PATTERN) String phoneNumber) {}
