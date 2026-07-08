package com.kohere.user.api;

/**
 * 임대인이 받은 신청(예약)을 상세 조회할 때 노출하는 신청자(세입자) 프로필 뷰(모듈 간 전달용). booking이 조회 시점에 실시간 조인한다(ADR-0002 공개 API
 * 협력).
 *
 * <p>{@code name}은 {@code firstName + " " + lastName}, {@code gender}는 user 소유 {@code Gender} enum
 * 문자열(UPPER_SNAKE), {@code country}는 ISO 3166-1 alpha-2 코드, {@code countryName}은 서버가 {@code
 * countries} 참조로 resolve한 표시명이다. 임대인에게 **마스킹 없이 평문**으로 노출한다(제품 결정). 탈퇴 회원은 PII 익명화(ADR-0014)로
 * {@code name}이 빈 문자열, 나머지가 {@code null}일 수 있다. docs/api/specs/04-booking-inquiry-chat.md §3.
 */
public record ApplicantProfileView(
    long userId, String name, String gender, String country, String countryName, String email) {}
