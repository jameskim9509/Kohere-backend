package com.kohere.user.api;

import java.time.LocalDate;

/**
 * 온보딩 프로필 입력(모듈 간 전달용). gender·visaType은 user 소유 enum이라 타입을 공유하지 않고 원시 문자열로 받는다(domain-model §1).
 * 필수 약관 동의 검증은 호출자(auth)가 선행하므로 여기서는 마케팅 동의만 변수로 받는다.
 */
public record OnboardingProfile(
    String firstName,
    String lastName,
    String gender,
    LocalDate birthDate,
    String countryCode,
    String phoneNumber,
    String visaType,
    boolean marketingAgreed) {}
