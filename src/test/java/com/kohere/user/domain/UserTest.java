package com.kohere.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * {@link User} 도메인 불변식 — 상태 전이(PENDING→ACTIVE→WITHDRAWN)·부분 수정·탈퇴 PII 익명화(domain-model §2,
 * ADR-0014).
 */
class UserTest {

  private static final Instant NOW = Instant.parse("2026-06-17T00:00:00Z");

  @Test
  void createPending_startsInPending() {
    User user = User.createPending(NOW);

    assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
    assertThat(user.getCreatedAt()).isEqualTo(NOW);
  }

  @Test
  void completeOnboarding_transitionsPendingToActive() {
    User pending = User.createPending(NOW);

    User active =
        pending.completeOnboarding(
            "Gil",
            "Hong",
            Gender.MALE,
            LocalDate.of(1990, 1, 1),
            "+82",
            "1012345678",
            VisaType.VISA_WORK,
            true,
            "v1.0",
            NOW);

    assertThat(active.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(active.getFirstName()).isEqualTo("Gil");
    assertThat(active.isTermsOfServiceAgreed()).isTrue();
    assertThat(active.isPrivacyPolicyAgreed()).isTrue();
    assertThat(active.isMarketingAgreed()).isTrue();
    assertThat(active.getTermsVersion()).isEqualTo("v1.0");
  }

  @Test
  void completeOnboarding_whenAlreadyActive_throws() {
    User active = activeUser();

    assertThatThrownBy(
            () ->
                active.completeOnboarding(
                    "A",
                    "B",
                    Gender.FEMALE,
                    LocalDate.of(1995, 5, 5),
                    "+82",
                    "1099998888",
                    VisaType.VISA_STUDENT,
                    false,
                    "v1.0",
                    NOW))
        .isInstanceOf(OnboardingAlreadyCompletedException.class);
  }

  @Test
  void updateProfile_changesOnlyProvidedFields() {
    User active = activeUser();

    User updated = active.updateProfile("Updated", null, null, null, null, null, null, null, NOW);

    assertThat(updated.getFirstName()).isEqualTo("Updated");
    assertThat(updated.getLastName()).isEqualTo(active.getLastName());
    assertThat(updated.getVisaType()).isEqualTo(active.getVisaType());
  }

  @Test
  void withdraw_anonymizesPiiAndRecordsTimestamp() {
    User active = activeUser();

    User withdrawn = active.withdraw(NOW);

    assertThat(withdrawn.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
    assertThat(withdrawn.getWithdrawnAt()).isEqualTo(NOW);
    assertThat(withdrawn.getFirstName()).isNull();
    assertThat(withdrawn.getLastName()).isNull();
    assertThat(withdrawn.getPhoneNumber()).isNull();
    assertThat(withdrawn.getVisaType()).isNull();
    assertThat(withdrawn.getBirthDate()).isNull();
  }

  @Test
  void withdraw_whenAlreadyWithdrawn_throws() {
    User withdrawn = activeUser().withdraw(NOW);

    assertThatThrownBy(() -> withdrawn.withdraw(NOW))
        .isInstanceOf(UserAlreadyWithdrawnException.class);
  }

  private static User activeUser() {
    return User.createPending(NOW)
        .completeOnboarding(
            "Gil",
            "Hong",
            Gender.MALE,
            LocalDate.of(1990, 1, 1),
            "+82",
            "1012345678",
            VisaType.VISA_WORK,
            true,
            "v1.0",
            NOW);
  }
}
