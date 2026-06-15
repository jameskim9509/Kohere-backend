package com.kohere.user.domain;

import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

/**
 * 회원 애그리거트 루트. 영속 기술(JPA)에 의존하지 않는 순수 도메인 모델이다. 영속 매핑은 infrastructure 계층의 어댑터에서
 * 처리한다(docs/convention/code-style.md §3-3).
 *
 * <p>스펙: docs/api/specs/01-auth-onboarding.md (회원/프로필). TODO: 상태 전이(PENDING→ACTIVE→WITHDRAWN)와 프로필
 * 부분 수정 불변식을 도메인 메서드로 채운다.
 */
@Getter
@Builder
public class User {

  private final Long id;
  private final String firstName;
  private final String lastName;
  private final Gender gender;
  private final LocalDate birthDate;
  private final String countryCode;
  private final String phoneNumber;
  private final VisaType visaType;
  private final UserStatus status;
  private final boolean termsOfServiceAgreed;
  private final boolean privacyPolicyAgreed;
  private final boolean marketingAgreed;
  private final Instant createdAt;
}
