package com.kohere.auth.presentation.dto;

import com.kohere.common.request.PhoneNumbers;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 임대인 온보딩 제출 요청 DTO(POST /api/v1/auth/landlord/onboarding, 임대인 전용). 연락처·생년월일({@code birthDate})을
 * 담는다. 이름은 소셜 로그인 시점에 이미 확정됐으므로 온보딩에서 수집하지 않는다(#192 — 세입자와 통일). 약관 동의(§2)·연락처 인증(§4-1·§4-2)이 선행되어야
 * 하며, {@code phoneNumber}는 사전 SMS 인증값과 일치해야 한다 — 정규화한 값끼리 대조하므로 하이픈 표기 차이는 흡수되고, {@code
 * users.phone_number}에도 정규화된 값이 저장된다({@link PhoneNumbers}, #229). {@code birthDate}는 필수·과거 날짜만
 * 허용한다(세입자 온보딩과 동일 규칙 — #131). 사업자등록번호·이메일은 온보딩에서 수집하지 않는다(사업자번호는 온보딩 후 별도 검증 API §5-1로 검증). 닉네임은
 * 서버가 생성하므로 입력에 없다. docs/api/specs/01-auth-onboarding.md §5-2 · ADR-0034.
 */
public record LandlordOnboardingRequest(
    @NotBlank @Pattern(regexp = PhoneNumbers.PATTERN) String phoneNumber,
    @NotBlank String birthDate) {}
