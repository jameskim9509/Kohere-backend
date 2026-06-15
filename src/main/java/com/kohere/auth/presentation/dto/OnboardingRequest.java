package com.kohere.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import java.time.LocalDate;

/**
 * 온보딩 제출 요청 DTO. 필수 프로필과 약관 동의를 담는다.
 *
 * <p>docs/api/specs/01-auth-onboarding.md §2 (POST /api/v1/auth/onboarding).
 *
 * <p>TODO: {@code gender}({@code MALE}/{@code FEMALE})·{@code visaType}는 user 도메인 enum이므로 여기서는
 * String으로 받고 enum 매핑·검증은 user 모듈 연동 시 추가한다(user 모듈 enum import 금지).
 *
 * <p>TODO: 필수 약관(termsOfServiceAgreed/privacyPolicyAgreed) {@code false}는 응용 계층에서 {@code
 * AUTH_REQUIRED_AGREEMENT_MISSING}(422)으로 처리한다(@AssertTrue 적용 여부는 422 매핑 정책 확정 후 결정).
 */
public record OnboardingRequest(
    @NotBlank String firstName,
    @NotBlank String lastName,
    @NotBlank String gender,
    @NotNull @Past LocalDate birthDate,
    @NotBlank String countryCode,
    @NotBlank String phoneNumber,
    @NotBlank String visaType,
    @NotNull Boolean termsOfServiceAgreed,
    @NotNull Boolean privacyPolicyAgreed,
    Boolean marketingAgreed) {}
