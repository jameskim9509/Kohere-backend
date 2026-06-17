package com.kohere.user.api;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 온보딩 완료 직후 반환하는 회원 프로필(모듈 간 전달용). gender·visaType·status는 user 소유 enum이라 원시 문자열로 노출해 내부 타입을 공유하지
 * 않는다(domain-model §1·§2).
 *
 * <p>docs/api/specs/01-auth-onboarding.md §2 (onboarding 응답의 {@code user}) · 시퀀스 US-1-2와 정합. 약관 동의
 * 여부는 온보딩 시 항상 true이므로 응답에 싣지 않는다(필요 시 {@code GET /users/me}에서 조회).
 */
public record UserProfileView(
    long id,
    String firstName,
    String lastName,
    String gender,
    LocalDate birthDate,
    String countryCode,
    String phoneNumber,
    String visaType,
    String status,
    boolean marketingAgreed,
    Instant createdAt) {}
