package com.kohere.auth.domain;

/**
 * 사업자등록번호 진위·상태(정상 영업) 검증 아웃바운드 포트. 인프라 어댑터(예: 비즈노 API — 국세청 사업자등록정보 기반)가 구현한다. 동기 검증 — 정상(계속 사업자,
 * 진위 확인)이면 {@code true}, 미등록·휴업·폐업·진위 실패면 {@code false}를 반환한다. provider 장애·타임아웃은 {@link
 * BusinessVerificationUpstreamException}(502)을 던진다(ADR-0033).
 */
public interface BusinessRegistryVerifier {

  boolean verify(String businessRegistrationNumber);
}
