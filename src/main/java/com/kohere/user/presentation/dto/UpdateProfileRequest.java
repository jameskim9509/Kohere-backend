package com.kohere.user.presentation.dto;

import com.kohere.user.domain.Gender;
import com.kohere.user.domain.VisaType;
import jakarta.validation.constraints.Past;
import java.time.LocalDate;

/**
 * 내 프로필 부분 수정 요청 DTO. 모든 필드가 선택이며, 전송한 필드만 변경하고 미전송 필드는 유지한다(미전송 ≠ 값 비움).
 *
 * <p>docs/api/specs/01-auth-onboarding.md §6 PATCH /users/me 요청 스키마. enum 불일치·날짜 범위 위반 등 입력 형식 위반은
 * 공통 {@code INVALID_INPUT}으로 처리한다.
 */
public record UpdateProfileRequest(
    String firstName,
    String lastName,
    Gender gender,
    @Past LocalDate birthDate,
    String countryCode,
    String phoneNumber,
    VisaType visaType,
    Boolean marketingAgreed) {}
