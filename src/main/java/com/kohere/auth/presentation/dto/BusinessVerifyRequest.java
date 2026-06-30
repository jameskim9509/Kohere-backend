package com.kohere.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 사업자등록번호 검증 요청 DTO(POST /api/v1/auth/business/verify, 임대인 전용). 숫자 10자리. 형식 위반은 외부 호출 전에 {@code
 * INVALID_INPUT}으로 거른다. docs/api/specs/01-auth-onboarding.md §5-1.
 */
public record BusinessVerifyRequest(
    @NotBlank @Pattern(regexp = "^\\d{10}$", message = "사업자등록번호는 숫자 10자리여야 합니다.")
        String businessRegistrationNumber) {}
