package com.kohere.user.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kohere.user.domain.Gender;
import com.kohere.user.domain.Occupation;
import com.kohere.user.domain.UserStatus;
import com.kohere.user.domain.UserType;
import com.kohere.user.domain.VisaType;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 내 프로필 응답 DTO. 응용 계층이 도메인을 표현 계층으로 전달할 때 쓰는 결과 타입이다(표현 계층은 이를 공통 래퍼로 감싼다).
 *
 * <p>세입자·임대인 공용이며 {@code name}은 공통 단일 이름이다(#192). {@code userType}에 따라 갈리는 건 세입자 전용 필드(성별·국적·직업·
 * 비자정보)와 임대인 전용 {@code phoneNumber}(본인 조회이므로 평문)뿐이다. {@code null} 필드는 응답에서 생략한다({@link
 * JsonInclude.Include#NON_NULL}).
 *
 * <p>{@code country}는 ISO 코드, {@code countryName}·{@code countryFlag}는 서버가 {@code countries} 참조로
 * resolve한 표시값이며 {@code countryFlag}는 국기 이미지 URL(flagcdn.com SVG)이다.
 * docs/api/specs/01-auth-onboarding.md §8 GET /users/me 응답 스키마.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserProfileResponse(
    Long id,
    UserType userType,
    String name,
    String nickname,
    Gender gender,
    LocalDate birthDate,
    String country,
    String countryName,
    String countryFlag,
    String lang,
    Occupation occupation,
    String email,
    VisaType visaType,
    String phoneNumber,
    UserStatus status,
    boolean termsOfServiceAgreed,
    boolean privacyPolicyAgreed,
    boolean marketingAgreed,
    Instant createdAt) {}
