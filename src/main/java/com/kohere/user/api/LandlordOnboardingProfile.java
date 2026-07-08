package com.kohere.user.api;

import java.time.LocalDate;

/**
 * 임대인 온보딩 프로필 입력(모듈 간 전달용). 단일 {@code name}(성·이름 합친 전체 이름)·연락처·생년월일({@code birthDate})을 받는다. {@code
 * phoneNumber}는 auth가 SMS 인증 완료를 선행 확인한 값이다. 사업자등록번호는 온보딩에서 수집하지 않는다 — 온보딩 후 별도 검증 API로
 * 검증한다(ADR-0033). 닉네임은 서버가 생성하므로 입력에 없다. 임대인은 성별·국적·직업·비자정보·이메일을 수집하지 않는다(생년월일은 세입자와 동일하게 수집 —
 * ADR-0034, #131).
 */
public record LandlordOnboardingProfile(String name, String phoneNumber, LocalDate birthDate) {}
