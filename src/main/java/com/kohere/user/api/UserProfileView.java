package com.kohere.user.api;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 온보딩 완료 직후 반환하는 회원 프로필(모듈 간 전달용). gender·occupation·visaType·status는 user 소유 enum이라 원시 문자열로 노출해 내부
 * 타입을 공유하지 않는다(domain-model §1·§2).
 *
 * <p>{@code country}는 ISO 코드, {@code countryName}·{@code countryFlag}는 서버가 {@code countries} 참조로
 * resolve한 표시값이며 {@code countryFlag}는 국기 이미지 URL(flagcdn.com SVG)이다(저장은 코드만).
 * docs/api/specs/01-auth-onboarding.md §5(onboarding 응답의 {@code user})·시퀀스 US-1-2와 정합.
 */
public record UserProfileView(
    long id,
    String firstName,
    String lastName,
    String nickname,
    String gender,
    LocalDate birthDate,
    String country,
    String countryName,
    String countryFlag,
    String occupation,
    String email,
    String visaType,
    String status,
    boolean marketingAgreed,
    Instant createdAt) {}
