package com.kohere.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 사업자등록번호 검증 요청 DTO(POST /api/v1/auth/business/verify, 임대인 전용). 숫자 10자리 또는 하이픈 형식(예 {@code
 * 123-45-67890})을 허용한다 — 어댑터가 숫자만 정규화해 비즈노 응답 {@code bno}(하이픈 포함)와 대조하므로 두 형식 모두 동일하게 처리된다. 형식 위반은
 * 외부 호출 전에 {@code INVALID_INPUT}으로 거른다. docs/api/specs/01-auth-onboarding.md §5-1.
 */
public record BusinessVerifyRequest(
    @NotBlank
        @Pattern(
            regexp = "^(\\d{10}|\\d{3}-\\d{2}-\\d{5})$",
            message = "사업자등록번호는 숫자 10자리 또는 하이픈 형식(123-45-67890)이어야 합니다.")
        String businessRegistrationNumber) {}
