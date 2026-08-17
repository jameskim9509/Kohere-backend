package com.kohere.auth.presentation.dto;

import com.kohere.common.request.PhoneNumbers;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 가입용 연락처 인증번호 확인 요청 DTO(POST /api/v1/auth/phone/signup/verify, 임대인 웹·비로그인). {@code phoneNumber}는
 * 인증번호를 발송한 번호와 같아야 한다 — 정규화한 값이 곧 챌린지 키라 발송 때와 하이픈 표기가 달라도 같은 챌린지를 가리킨다({@link PhoneNumbers}).
 *
 * <p>{@code code}에는 길이 제약을 두지 않는다 — 자릿수는 서버 정책({@code app.phone.code-length})이고 검증은 해시 대조라, 자릿수를 요청
 * 검증으로 알려 주면 무차별 대입의 탐색 공간만 좁혀 준다. docs/api/specs/01-auth-onboarding.md §1-2.
 */
public record SignupPhoneVerifyRequest(
    @NotBlank @Pattern(regexp = PhoneNumbers.PATTERN) String phoneNumber, @NotBlank String code) {}
