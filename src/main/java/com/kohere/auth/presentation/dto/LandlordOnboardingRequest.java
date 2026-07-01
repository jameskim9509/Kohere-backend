package com.kohere.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 임대인 온보딩 제출 요청 DTO(POST /api/v1/auth/landlord/onboarding, 임대인 전용). 단일 {@code name}(성·이름 합친 전체
 * 이름)·연락처를 담는다. 약관 동의(§2)·연락처 인증(§4-1·§4-2)이 선행되어야 하며, {@code phoneNumber}는 사전 SMS 인증값과 일치해야 한다.
 * 사업자등록번호는 온보딩에서 수집하지 않는다 — 온보딩 후 별도 검증 API(§5-1)로 검증한다. 닉네임은 서버가 생성하므로 입력에 없다(이메일 미수집).
 * docs/api/specs/01-auth-onboarding.md §5-2 · ADR-0034.
 */
public record LandlordOnboardingRequest(@NotBlank String name, @NotBlank String phoneNumber) {}
