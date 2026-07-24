package com.kohere.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * {@link User} 도메인 불변식 — 상태 전이(PENDING→TERMS_AGREED→ACTIVE→WITHDRAWN)·약관 동의·온보딩 선행조건·부분 수정·탈퇴 PII
 * 익명화(domain-model §2, ADR-0014). 이름은 세입자·임대인 공통 단일 {@code name}으로, 소셜 로그인 시점({@code
 * createPending})에 채우고 온보딩에서는 건드리지 않는다(#192).
 */
class UserTest {

  private static final Instant NOW = Instant.parse("2026-06-17T00:00:00Z");

  @Test
  void createPending_startsInPendingWithNameAndEmail() {
    User user = User.createPending("Gil Hong", "gil@example.com", NOW);

    assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
    assertThat(user.getName()).isEqualTo("Gil Hong");
    assertThat(user.getEmail()).isEqualTo("gil@example.com");
    assertThat(user.getCreatedAt()).isEqualTo(NOW);
  }

  @Test
  void agreeToTerms_transitionsPendingToTermsAgreed() {
    User agreed =
        User.createPending("Gil Hong", "gil@example.com", NOW).agreeToTerms(true, "v1.0", NOW);

    assertThat(agreed.getStatus()).isEqualTo(UserStatus.TERMS_AGREED);
    assertThat(agreed.isTermsOfServiceAgreed()).isTrue();
    assertThat(agreed.isPrivacyPolicyAgreed()).isTrue();
    assertThat(agreed.isMarketingAgreed()).isTrue();
    assertThat(agreed.getTermsVersion()).isEqualTo("v1.0");
    assertThat(agreed.getAgreedAt()).isEqualTo(NOW);
  }

  @Test
  void agreeToTerms_whenAlreadyTermsAgreed_isIdempotent() {
    User agreed =
        User.createPending("Gil Hong", "gil@example.com", NOW).agreeToTerms(true, "v1.0", NOW);

    User again = agreed.agreeToTerms(false, "v1.0", NOW);

    assertThat(again.getStatus()).isEqualTo(UserStatus.TERMS_AGREED);
    assertThat(again).isSameAs(agreed);
  }

  @Test
  void completeOnboarding_transitionsTermsAgreedToActive_keepsNameAndEmail() {
    User termsAgreed =
        User.createPending("Gil Hong", "gil@example.com", NOW).agreeToTerms(true, "v1.0", NOW);

    User active =
        termsAgreed.completeOnboarding(
            "BraveOtter",
            Gender.MALE,
            LocalDate.of(1990, 1, 1),
            "KR",
            Occupation.UNDERGRADUATE_STUDENT,
            VisaType.SHORT_TERM_VISIT,
            Language.EN,
            NOW);

    assertThat(active.getStatus()).isEqualTo(UserStatus.ACTIVE);
    // 이름·이메일은 소셜 로그인 시점 값을 그대로 유지(온보딩에서 미수집 — #192)
    assertThat(active.getName()).isEqualTo("Gil Hong");
    assertThat(active.getEmail()).isEqualTo("gil@example.com");
    assertThat(active.getNickname()).isEqualTo("BraveOtter");
    assertThat(active.getCountry()).isEqualTo("KR");
    assertThat(active.getOccupation()).isEqualTo(Occupation.UNDERGRADUATE_STUDENT);
    // 동의는 약관 동의 단계에서 이미 확정됨
    assertThat(active.isTermsOfServiceAgreed()).isTrue();
  }

  @Test
  void completeOnboarding_whenPending_throwsTermsAgreementRequired() {
    User pending = User.createPending("Gil Hong", "gil@example.com", NOW);

    assertThatThrownBy(
            () ->
                pending.completeOnboarding(
                    "BraveOtter",
                    Gender.MALE,
                    LocalDate.of(1990, 1, 1),
                    "KR",
                    Occupation.UNDERGRADUATE_STUDENT,
                    VisaType.SHORT_TERM_VISIT,
                    Language.EN,
                    NOW))
        .isInstanceOf(TermsAgreementRequiredException.class);
  }

  @Test
  void completeOnboarding_whenAlreadyActive_throws() {
    User active = activeUser();

    assertThatThrownBy(
            () ->
                active.completeOnboarding(
                    "CalmFox",
                    Gender.FEMALE,
                    LocalDate.of(1995, 5, 5),
                    "VN",
                    Occupation.ETC,
                    VisaType.STUDENTS_TRAINEES,
                    Language.EN,
                    NOW))
        .isInstanceOf(OnboardingAlreadyCompletedException.class);
  }

  @Test
  void updateProfile_changesOnlyProvidedFields() {
    User active = activeUser();

    User updated = active.updateProfile("Updated", null, null, null, null, null, null, null, NOW);

    assertThat(updated.getName()).isEqualTo("Updated");
    assertThat(updated.getCountry()).isEqualTo(active.getCountry());
    assertThat(updated.getVisaType()).isEqualTo(active.getVisaType());
  }

  @Test
  void withdraw_anonymizesPiiAndRecordsTimestamp() {
    User active = activeUser();

    User withdrawn = active.withdraw(NOW);

    assertThat(withdrawn.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
    assertThat(withdrawn.getWithdrawnAt()).isEqualTo(NOW);
    assertThat(withdrawn.getName()).isNull();
    assertThat(withdrawn.getNickname()).isNull();
    assertThat(withdrawn.getCountry()).isNull();
    assertThat(withdrawn.getOccupation()).isNull();
    assertThat(withdrawn.getEmail()).isNull();
    assertThat(withdrawn.getVisaType()).isNull();
    assertThat(withdrawn.getBirthDate()).isNull();
  }

  @Test
  void withdraw_whenAlreadyWithdrawn_throws() {
    User withdrawn = activeUser().withdraw(NOW);

    assertThatThrownBy(() -> withdrawn.withdraw(NOW))
        .isInstanceOf(UserAlreadyWithdrawnException.class);
  }

  @Test
  void completeLandlordOnboarding_transitionsTermsAgreedToActiveAsLandlord() {
    User termsAgreed =
        User.createPending("Kim Imdae", "kim@example.com", NOW).agreeToTerms(true, "v1.0", NOW);

    User active =
        termsAgreed.completeLandlordOnboarding(
            "01012345678", LocalDate.of(1988, 5, 20), "CalmFox", NOW);

    assertThat(active.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(active.getUserType()).isEqualTo(UserType.LANDLORD);
    // 이름·이메일은 소셜 로그인 시점 값 유지(세입자와 통일 — #192)
    assertThat(active.getName()).isEqualTo("Kim Imdae");
    assertThat(active.getEmail()).isEqualTo("kim@example.com");
    assertThat(active.getPhoneNumber()).isEqualTo("01012345678");
    // 사업자번호 해시는 온보딩에서 확정하지 않는다(온보딩 후 매물 등록 시점에 채움, ADR-0033)
    assertThat(active.getBusinessRegistrationNumberHash()).isNull();
    assertThat(active.getNickname()).isEqualTo("CalmFox");
    // 임대인은 성별·직업·비자를 수집하지 않는다(생년월일은 세입자와 동일하게 수집 — #131)
    assertThat(active.getGender()).isNull();
    assertThat(active.getOccupation()).isNull();
    assertThat(active.getVisaType()).isNull();
    assertThat(active.getBirthDate()).isEqualTo(LocalDate.of(1988, 5, 20));
    // 국적·표시 언어는 서버가 KR·ko로 고정 부여한다(ADR-0034 개정, #141)
    assertThat(active.getCountry()).isEqualTo("KR");
    assertThat(active.getLang()).isEqualTo(Language.KO);
  }

  @Test
  void completeLandlordOnboarding_whenPending_throwsTermsAgreementRequired() {
    User pending = User.createPending("Kim Imdae", "kim@example.com", NOW);

    assertThatThrownBy(
            () ->
                pending.completeLandlordOnboarding(
                    "01012345678", LocalDate.of(1988, 5, 20), "CalmFox", NOW))
        .isInstanceOf(TermsAgreementRequiredException.class);
  }

  @Test
  void completeLandlordOnboarding_whenAlreadyActive_throws() {
    User active = activeUser();

    assertThatThrownBy(
            () ->
                active.completeLandlordOnboarding(
                    "01012345678", LocalDate.of(1988, 5, 20), "CalmFox", NOW))
        .isInstanceOf(OnboardingAlreadyCompletedException.class);
  }

  @Test
  void withdraw_landlord_anonymizesPhoneAndBusinessHash() {
    User landlord = activeLandlord();

    User withdrawn = landlord.withdraw(NOW);

    assertThat(withdrawn.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
    assertThat(withdrawn.getPhoneNumber()).isNull();
    assertThat(withdrawn.getBusinessRegistrationNumberHash()).isNull();
  }

  @Test
  void updateLandlordProfile_changesOnlyProvidedFields() {
    User landlord = activeLandlord();

    User updated = landlord.updateLandlordProfile("New Name", null, null, NOW);

    // 단일 name 갱신, 미전송 연락처·마케팅은 유지
    assertThat(updated.getName()).isEqualTo("New Name");
    assertThat(updated.getPhoneNumber()).isEqualTo(landlord.getPhoneNumber());
    assertThat(updated.isMarketingAgreed()).isEqualTo(landlord.isMarketingAgreed());
    assertThat(updated.getUserType()).isEqualTo(UserType.LANDLORD);
  }

  @Test
  void updateLandlordProfile_updatesPhoneAndMarketing() {
    User landlord = activeLandlord();

    User updated = landlord.updateLandlordProfile(null, "01099998888", true, NOW);

    assertThat(updated.getPhoneNumber()).isEqualTo("01099998888");
    assertThat(updated.isMarketingAgreed()).isTrue();
    // name 미전송이면 기존 이름 유지
    assertThat(updated.getName()).isEqualTo(landlord.getName());
  }

  private static User activeLandlord() {
    return User.createPending("Kim Imdae", "kim@example.com", NOW)
        .agreeToTerms(true, "v1.0", NOW)
        .completeLandlordOnboarding("01012345678", LocalDate.of(1988, 5, 20), "CalmFox", NOW);
  }

  private static User activeUser() {
    return User.createPending("Gil Hong", "gil@example.com", NOW)
        .agreeToTerms(true, "v1.0", NOW)
        .completeOnboarding(
            "BraveOtter",
            Gender.MALE,
            LocalDate.of(1990, 1, 1),
            "KR",
            Occupation.UNDERGRADUATE_STUDENT,
            VisaType.SHORT_TERM_VISIT,
            Language.EN,
            NOW);
  }
}
