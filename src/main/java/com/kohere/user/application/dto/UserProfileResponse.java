package com.kohere.user.application.dto;

import com.kohere.user.domain.Gender;
import com.kohere.user.domain.UserStatus;
import com.kohere.user.domain.VisaType;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 내 프로필 응답 DTO. 응용 계층이 도메인을 표현 계층으로 전달할 때 쓰는 결과 타입이다(표현 계층은 이를 공통 래퍼로 감싼다).
 *
 * <p>docs/api/specs/01-auth-onboarding.md §5 GET /users/me 응답 스키마.
 */
public record UserProfileResponse(
    Long id,
    String firstName,
    String lastName,
    Gender gender,
    LocalDate birthDate,
    String countryCode,
    String phoneNumber,
    VisaType visaType,
    UserStatus status,
    boolean termsOfServiceAgreed,
    boolean privacyPolicyAgreed,
    boolean marketingAgreed,
    Instant createdAt) {}
