package com.kohere.user.presentation.dto;

import jakarta.validation.constraints.Past;
import java.time.LocalDate;

/**
 * 내 프로필 부분 수정 요청 DTO. 모든 필드가 선택이며, 전송한 필드만 변경하고 미전송 필드는 유지한다(미전송 ≠ 값 비움). {@code email}·{@code
 * nickname}은 이 경로로 수정하지 않는다(이메일은 재인증, 닉네임은 시스템 배정).
 *
 * <p>{@code name}은 세입자·임대인 공통 단일 이름이다(#192). 그 밖의 수정 가능 필드는 {@code userType}에 따라 갈린다 — 세입자(TENANT)는
 * 성별·생년월일·국적·직업·비자정보·{@code marketingAgreed}를, 임대인(LANDLORD)은 {@code phoneNumber}·{@code
 * marketingAgreed}를 수정한다. 임대인의 {@code phoneNumber} 변경은 새 번호를 SMS로 재인증(§4-1·§4-2)한 뒤에만 반영되며,
 * 미인증·불일치는 422 {@code AUTH_PHONE_NOT_VERIFIED}다(ADR-0034).
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
    @Past LocalDate birthDate,
    String country,
    String occupation,
    String visaType,
    String lang,
    String phoneNumber,
    Boolean marketingAgreed) {}
