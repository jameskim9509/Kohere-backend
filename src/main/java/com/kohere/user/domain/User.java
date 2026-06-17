package com.kohere.user.domain;

import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

/**
 * 회원 애그리거트 루트. 영속 기술(JPA)에 의존하지 않는 순수 도메인 모델이며, 상태 전이·프로필 변경은 불변식을 강제하는 도메인 메서드로만 수행한다(불변 객체 — 새
 * 인스턴스 반환). 영속 매핑은 infrastructure 어댑터가 처리한다(docs/convention/code-style.md §3-3).
 *
 * <p>상태 전이는 단방향 PENDING→ACTIVE→WITHDRAWN만 허용한다(domain-model §2, ADR-0014). 탈퇴 시 식별 PII를 즉시 익명화한다.
 */
@Getter
@Builder(toBuilder = true)
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
  private final String termsVersion;
  private final Instant agreedAt;
  private final Instant createdAt;
  private final Instant updatedAt;
  private final Instant withdrawnAt;

  /** 소셜 검증만 완료한 신규 회원(PENDING). 프로필·동의는 온보딩에서 채운다. */
  public static User createPending(Instant now) {
    return User.builder().status(UserStatus.PENDING).createdAt(now).updatedAt(now).build();
  }

  /**
   * 온보딩 완료(PENDING→ACTIVE). 프로필·동의·약관 버전·동의 시각(agreedAt)을 확정한다. 필수 약관(이용약관·개인정보)은 항상 동의 처리된다(미동의는
   * auth가 호출 전 차단).
   *
   * @throws OnboardingAlreadyCompletedException 이미 ACTIVE(또는 WITHDRAWN)인 경우
   */
  public User completeOnboarding(
      String firstName,
      String lastName,
      Gender gender,
      LocalDate birthDate,
      String countryCode,
      String phoneNumber,
      VisaType visaType,
      boolean marketingAgreed,
      String termsVersion,
      Instant now) {
    if (status != UserStatus.PENDING) {
      throw new OnboardingAlreadyCompletedException();
    }
    return toBuilder()
        .firstName(firstName)
        .lastName(lastName)
        .gender(gender)
        .birthDate(birthDate)
        .countryCode(countryCode)
        .phoneNumber(phoneNumber)
        .visaType(visaType)
        .termsOfServiceAgreed(true)
        .privacyPolicyAgreed(true)
        .marketingAgreed(marketingAgreed)
        .termsVersion(termsVersion)
        .agreedAt(now)
        .status(UserStatus.ACTIVE)
        .updatedAt(now)
        .build();
  }

  /** 프로필 부분 수정. 전송한(=null 아님) 필드만 변경하고 미전송 필드는 유지한다. */
  public User updateProfile(
      String firstName,
      String lastName,
      Gender gender,
      LocalDate birthDate,
      String countryCode,
      String phoneNumber,
      VisaType visaType,
      Boolean marketingAgreed,
      Instant now) {
    var builder = toBuilder();
    if (firstName != null) {
      builder.firstName(firstName);
    }
    if (lastName != null) {
      builder.lastName(lastName);
    }
    if (gender != null) {
      builder.gender(gender);
    }
    if (birthDate != null) {
      builder.birthDate(birthDate);
    }
    if (countryCode != null) {
      builder.countryCode(countryCode);
    }
    if (phoneNumber != null) {
      builder.phoneNumber(phoneNumber);
    }
    if (visaType != null) {
      builder.visaType(visaType);
    }
    if (marketingAgreed != null) {
      builder.marketingAgreed(marketingAgreed);
    }
    return builder.updatedAt(now).build();
  }

  /**
   * 회원 탈퇴(→WITHDRAWN). 식별 PII(이름·전화·비자·생년월일)를 즉시 익명화(제거)하고 탈퇴 시각을 기록한다(ADR-0014). 행 자체는 보존한다. 동의
   * 메타데이터(약관 동의 여부·termsVersion·agreedAt)는 식별 PII가 아니므로 동의 증빙을 위해 보존한다.
   *
   * @throws UserAlreadyWithdrawnException 이미 WITHDRAWN인 경우
   */
  public User withdraw(Instant now) {
    if (status == UserStatus.WITHDRAWN) {
      throw new UserAlreadyWithdrawnException();
    }
    return toBuilder()
        .firstName(null)
        .lastName(null)
        .gender(null)
        .birthDate(null)
        .countryCode(null)
        .phoneNumber(null)
        .visaType(null)
        .status(UserStatus.WITHDRAWN)
        .withdrawnAt(now)
        .updatedAt(now)
        .build();
  }
}
