package com.kohere.user.application.dto;

import com.kohere.user.domain.Gender;
import com.kohere.user.domain.Occupation;
import com.kohere.user.domain.UserStatus;
import com.kohere.user.domain.VisaType;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 내 프로필 응답 DTO. 응용 계층이 도메인을 표현 계층으로 전달할 때 쓰는 결과 타입이다(표현 계층은 이를 공통 래퍼로 감싼다).
 *
 * <p>{@code country}는 ISO 코드, {@code countryName}·{@code countryFlag}는 서버가 {@code countries} 참조로
 * resolve한 표시값이며 {@code countryFlag}는 국기 이미지 URL(flagcdn.com SVG)이다.
 * docs/api/specs/01-auth-onboarding.md §7 GET /users/me 응답 스키마.
 */
public record UserProfileResponse(
    Long id,
    String firstName,
    String lastName,
    String nickname,
    Gender gender,
    LocalDate birthDate,
    String country,
    String countryName,
    String countryFlag,
    Occupation occupation,
    String email,
    VisaType visaType,
    UserStatus status,
    boolean termsOfServiceAgreed,
    boolean privacyPolicyAgreed,
    boolean marketingAgreed,
    Instant createdAt) {}
