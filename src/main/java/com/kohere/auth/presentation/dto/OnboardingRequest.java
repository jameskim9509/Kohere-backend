package com.kohere.auth.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import java.time.LocalDate;

/**
 * 온보딩 제출 요청 DTO. 필수 프로필을 담는다. 약관 동의(POST /auth/terms)와 이메일 인증(POST /auth/email/*)이 선행되어야 한다.
 *
 * <p>docs/api/specs/01-auth-onboarding.md §5 (POST /api/v1/auth/onboarding). {@code gender}·{@code
 * occupation}·{@code visaType}는 user 도메인 enum이라 String으로 받고 enum 매핑·검증은 user 모듈이 수행한다(user enum
 * import 금지). {@code country}는 ISO 3166-1 alpha-2 코드, {@code email}은 사전 인증된 값과 일치해야 한다. 닉네임은 서버가
 * 생성하므로 입력에 없다.
 */
public record OnboardingRequest(
    @NotBlank String firstName,
    @NotBlank String lastName,
    @NotBlank String gender,
    @NotNull @Past LocalDate birthDate,
    @NotBlank String country,
    @NotBlank String occupation,
    @NotBlank @Email String email,
    @NotBlank String visaType) {}
