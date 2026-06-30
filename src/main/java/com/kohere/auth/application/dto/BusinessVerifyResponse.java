package com.kohere.auth.application.dto;

/**
 * 사업자등록번호 검증(POST /auth/business/verify) 결과. {@code businessRegistrationNumber}는 마스킹(예 {@code
 * ****567890})해 반환하고, {@code verified}는 정상 사업자면 true. 원문은 저장·로그하지 않는다.
 * docs/api/specs/01-auth-onboarding.md §5-1.
 */
public record BusinessVerifyResponse(String businessRegistrationNumber, boolean verified) {}
