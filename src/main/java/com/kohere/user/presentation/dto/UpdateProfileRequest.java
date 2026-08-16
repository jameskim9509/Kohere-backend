package com.kohere.user.presentation.dto;

import com.kohere.common.request.PhoneNumbers;
import jakarta.validation.constraints.Pattern;

/**
 * 내 프로필 부분 수정 요청 DTO. 모든 필드가 선택이며, 전송한 필드만 변경하고 미전송 필드는 유지한다(미전송 ≠ 값 비움). {@code email}·{@code
 * nickname}은 이 경로로 수정하지 않는다(이메일은 재인증, 닉네임은 시스템 배정).
 *
 * <p>{@code name}은 세입자·임대인 공통 단일 이름이다(#192). 그 밖의 수정 가능 필드는 {@code userType}에 따라 갈린다 — 세입자(TENANT)는
 * 성별·생년월일·국적·직업·비자정보·{@code marketingAgreed}를, 임대인(LANDLORD)은 {@code phoneNumber}·{@code
 * marketingAgreed}를 수정한다. 임대인의 {@code phoneNumber} 변경은 새 번호를 SMS로 재인증(§4-1·§4-2)한 뒤에만 반영되며,
 * 미인증·불일치는 422 {@code AUTH_PHONE_NOT_VERIFIED}다(ADR-0034).
 *
 * <p><b>{@code phoneNumber}는 선택이지만 형식은 강제한다</b> — 이 필드가 {@code users.phone_number}에 닿는 마지막 요청 경로다.
 * UNIQUE(V23)가 표기 차이로 뚫리지 않도록 응용 계층이 저장 전에 표준형으로 접는데({@link PhoneNumbers}), 접기 전에 형식을 걸러야 {@code
 * 1}·{@code abc} 같은 값이 접힌 뒤 정상 번호처럼 저장되는 일을 막을 수 있다. 온보딩·SMS 인증 DTO와 같은 {@link
 * PhoneNumbers#PATTERN}을 쓴다(#229). {@code @NotBlank}는 붙이지 않는다 — PATCH의 미전송({@code null})은 「변경 없음」이고
 * {@code @Pattern}은 {@code null}을 통과시키므로 선택 의미가 유지된다. 빈 문자열은 미전송이 아니라 형식 위반이라 {@code
 * INVALID_INPUT}이다.
 *
 * <p>docs/api/specs/01-auth-onboarding.md §9 PATCH /users/me 요청 스키마. {@code country}는 ISO 코드.
 *
 * <p><b>enum 후보는 String으로 받고 서버가 파싱한다</b> — {@code gender}·{@code occupation}·{@code visaType}을
 * enum 타입으로 선언하면 허용 외 문자열이 역직렬화 단계에서 거부돼 {@code MALFORMED_REQUEST}가 되고, 어느 필드가 문제인지 응답에 남지 않는다.
 * 온보딩(§5)은 모듈 경계 때문에 String으로 받아 같은 위반을 {@code INVALID_INPUT}으로 돌려주고 있어, 같은 실수에 두 엔드포인트가 다른 코드를 주는
 * 상태였다. 파싱을 응용 계층으로 내려 {@code INVALID_INPUT}으로 통일한다(#151).
 */
public record UpdateProfileRequest(
    String name,
    String gender,
    String birthDate,
    String country,
    String occupation,
    String visaType,
    String lang,
    @Pattern(regexp = PhoneNumbers.PATTERN) String phoneNumber,
    Boolean marketingAgreed) {}
