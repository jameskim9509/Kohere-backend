package com.kohere.auth.presentation.dto;

import com.kohere.common.request.PhoneNumbers;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 연락처 인증번호 확인 요청 DTO(POST /api/v1/auth/phone/verify, 임대인 전용). {@code phoneNumber}는 인증번호를 발송한 번호와
 * 일치해야 한다 — 정규화한 값끼리 대조하므로 발송 때와 하이픈 표기가 달라도 통과한다({@link PhoneNumbers}, #229).
 * docs/api/specs/01-auth-onboarding.md §4-2.
 */
public record PhoneVerifyRequest(
    @NotBlank @Pattern(regexp = PhoneNumbers.PATTERN) String phoneNumber, @NotBlank String code) {}
