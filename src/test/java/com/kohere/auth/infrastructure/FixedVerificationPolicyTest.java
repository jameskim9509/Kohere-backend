package com.kohere.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.kohere.auth.application.EmailVerificationProperties;
import com.kohere.auth.application.PhoneVerificationProperties;
import com.kohere.auth.domain.Provider;
import com.kohere.auth.domain.SocialAccount;
import com.kohere.auth.domain.SocialAccountRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link FixedVerificationPolicy} 단위 테스트 — <b>역할별 채널 제한</b>(임차인 계정은 이메일만·임대인 계정은 SMS만)과 그룹 허용목록 대조,
 * 설정 정합성 검사. 역할 밖 채널·미등록 값·비심사 계정이 각각 어떻게 갈리는지 고정한다(이슈 #180).
 */
@ExtendWith(MockitoExtension.class)
class FixedVerificationPolicyTest {

  private static final long TENANT_USER_ID = 11L;
  private static final long LANDLORD_USER_ID = 22L;
  private static final long OUTSIDER_USER_ID = 33L;

  private static final String TENANT_GOOGLE = "review-tenant@gmail.com";
  private static final String TENANT_EMAIL = "demo-tenant@kohere.io";
  private static final String LANDLORD_GOOGLE = "review-landlord@gmail.com";
  private static final String LANDLORD_PHONE = "01000000000";

  @Mock private SocialAccountRepository socialAccountRepository;

  private FixedVerificationProperties properties;
  private final EmailVerificationProperties emailProperties = new EmailVerificationProperties();
  private final PhoneVerificationProperties phoneProperties = new PhoneVerificationProperties();
  private FixedVerificationPolicy policy;

  @BeforeEach
  void setUp() {
    properties = new FixedVerificationProperties();
    properties.setEnabled(true);
    properties.setCode("000000");
    properties.getTenant().setGoogleEmails(List.of(TENANT_GOOGLE));
    properties.getTenant().setEmails(List.of(TENANT_EMAIL));
    properties.getLandlord().setGoogleEmails(List.of(LANDLORD_GOOGLE));
    properties.getLandlord().setPhoneNumbers(List.of(LANDLORD_PHONE));
    policy =
        new FixedVerificationPolicy(
            socialAccountRepository, properties, emailProperties, phoneProperties);
  }

  // --- 역할 밖 채널 (이 설계의 핵심) ---

  @Test
  void appliesToPhone_tenantAccount_isFalseRegardlessOfValue() {
    linkGoogleAccount(TENANT_USER_ID, TENANT_GOOGLE);

    // 임차인 심사 계정은 SMS 인증 대상이 아니다 — 등록된 임대인 번호를 제출해도 적용되지 않는다
    assertThat(policy.appliesToPhone(TENANT_USER_ID, LANDLORD_PHONE)).isFalse();
    // 그러나 심사 계정이므로 실발송으로 흘러가지 않고 거절된다
    assertThat(policy.isReviewAccount(TENANT_USER_ID)).isTrue();
  }

  @Test
  void appliesToEmail_landlordAccount_isFalseRegardlessOfValue() {
    linkGoogleAccount(LANDLORD_USER_ID, LANDLORD_GOOGLE);

    assertThat(policy.appliesToEmail(LANDLORD_USER_ID, TENANT_EMAIL)).isFalse();
    assertThat(policy.isReviewAccount(LANDLORD_USER_ID)).isTrue();
  }

  // --- 정상 경로 ---

  @Test
  void appliesToEmail_tenantAccountWithRegisteredEmail_isTrue() {
    linkGoogleAccount(TENANT_USER_ID, TENANT_GOOGLE);

    assertThat(policy.appliesToEmail(TENANT_USER_ID, TENANT_EMAIL)).isTrue();
  }

  @Test
  void appliesToPhone_landlordAccountWithRegisteredPhone_isTrue() {
    linkGoogleAccount(LANDLORD_USER_ID, LANDLORD_GOOGLE);

    assertThat(policy.appliesToPhone(LANDLORD_USER_ID, LANDLORD_PHONE)).isTrue();
  }

  @Test
  void appliesToEmail_anyTenantAccountMayUseAnyTenantEmail() {
    // 그룹 안에서는 계정과 값을 묶지 않는다 — 심사자가 어느 계정으로 로그인했든 노트의 값이 통해야 한다
    properties.getTenant().setGoogleEmails(List.of(TENANT_GOOGLE, "review-tenant2@gmail.com"));
    properties.getTenant().setEmails(List.of(TENANT_EMAIL, "demo-tenant2@kohere.io"));
    linkGoogleAccount(TENANT_USER_ID, "review-tenant2@gmail.com");

    assertThat(policy.appliesToEmail(TENANT_USER_ID, TENANT_EMAIL)).isTrue();
  }

  @Test
  void appliesToEmail_normalizesCaseAndWhitespace() {
    linkGoogleAccount(TENANT_USER_ID, "  Review-Tenant@Gmail.com ");

    assertThat(policy.appliesToEmail(TENANT_USER_ID, "  DEMO-Tenant@Kohere.io ")).isTrue();
  }

  @Test
  void appliesToPhone_ignoresHyphenFormatting() {
    linkGoogleAccount(LANDLORD_USER_ID, LANDLORD_GOOGLE);

    assertThat(policy.appliesToPhone(LANDLORD_USER_ID, "010-0000-0000")).isTrue();
  }

  // --- 미등록 값 ---

  @Test
  void appliesToEmail_tenantAccountButUnregisteredEmail_isFalse() {
    linkGoogleAccount(TENANT_USER_ID, TENANT_GOOGLE);

    assertThat(policy.appliesToEmail(TENANT_USER_ID, "someone-else@kohere.io")).isFalse();
  }

  @Test
  void appliesToPhone_landlordAccountButUnregisteredPhone_isFalse() {
    linkGoogleAccount(LANDLORD_USER_ID, LANDLORD_GOOGLE);

    assertThat(policy.appliesToPhone(LANDLORD_USER_ID, "01099998888")).isFalse();
  }

  // --- 비심사 계정 ---

  @Test
  void appliesToEmail_registeredEmailButNotReviewAccount_isFalse() {
    linkGoogleAccount(OUTSIDER_USER_ID, "stranger@gmail.com");

    // 데모 이메일을 알더라도 심사 계정이 아니면 고정 인증번호를 받지 못한다
    assertThat(policy.appliesToEmail(OUTSIDER_USER_ID, TENANT_EMAIL)).isFalse();
    // 그리고 거절 대상도 아니다 — 실제 발송 경로로 가야 한다
    assertThat(policy.isReviewAccount(OUTSIDER_USER_ID)).isFalse();
  }

  @Test
  void isReviewAccount_noSocialAccount_isFalse() {
    when(socialAccountRepository.findAllByUserId(OUTSIDER_USER_ID)).thenReturn(List.of());

    assertThat(policy.isReviewAccount(OUTSIDER_USER_ID)).isFalse();
  }

  @Test
  void isReviewAccount_nonGoogleProvider_isFalse() {
    when(socialAccountRepository.findAllByUserId(TENANT_USER_ID))
        .thenReturn(List.of(socialAccount(TENANT_USER_ID, Provider.APPLE, TENANT_GOOGLE)));

    assertThat(policy.isReviewAccount(TENANT_USER_ID)).isFalse();
  }

  @Test
  void isReviewAccount_coversBothGroups() {
    linkGoogleAccount(LANDLORD_USER_ID, LANDLORD_GOOGLE);

    assertThat(policy.isReviewAccount(LANDLORD_USER_ID)).isTrue();
  }

  // --- 설정 정합성 ---

  @Test
  void validateConfiguration_validSetup_passes() {
    policy.validateConfiguration();

    assertThat(policy.code()).isEqualTo("000000");
  }

  @Test
  void validateConfiguration_onlyTenantGroupConfigured_passes() {
    // 한쪽 트랙만 쓰는 운용은 허용한다
    properties.getLandlord().setGoogleEmails(List.of());
    properties.getLandlord().setPhoneNumbers(List.of());

    policy.validateConfiguration();

    assertThat(policy.code()).isEqualTo("000000");
  }

  @Test
  void validateConfiguration_halfConfiguredTenantGroup_failsFast() {
    properties.getTenant().setEmails(List.of()); // 계정만 있고 대상이 없다

    assertThatThrownBy(() -> policy.validateConfiguration())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("tenant");
  }

  @Test
  void validateConfiguration_halfConfiguredLandlordGroup_failsFast() {
    properties.getLandlord().setGoogleEmails(List.of()); // 대상만 있고 계정이 없다

    assertThatThrownBy(() -> policy.validateConfiguration())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("landlord");
  }

  @Test
  void validateConfiguration_bothGroupsEmpty_failsFast() {
    properties.setTenant(new FixedVerificationProperties.Tenant());
    properties.setLandlord(new FixedVerificationProperties.Landlord());

    assertThatThrownBy(() -> policy.validateConfiguration())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("심사 계정이 없다");
  }

  @Test
  void validateConfiguration_accountInBothRoles_failsFast() {
    properties.getLandlord().setGoogleEmails(List.of(TENANT_GOOGLE));

    // 같은 계정이 두 역할이면 어느 채널을 허용할지 결정할 수 없다
    assertThatThrownBy(() -> policy.validateConfiguration())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("역할이 모호");
  }

  @Test
  void validateConfiguration_blankEnvValuesAreIgnored_notTreatedAsConfigured() {
    // 환경변수 미주입 시 빈 문자열이 들어온다 — 채워진 것으로 오인하면 안 된다
    properties.getTenant().setGoogleEmails(List.of("", "  "));
    properties.getTenant().setEmails(List.of(""));
    properties.getLandlord().setGoogleEmails(List.of(""));
    properties.getLandlord().setPhoneNumbers(List.of(""));

    assertThatThrownBy(() -> policy.validateConfiguration())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("심사 계정이 없다");
  }

  @Test
  void validateConfiguration_blankCode_failsFast() {
    properties.setCode("  ");

    assertThatThrownBy(() -> policy.validateConfiguration())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("code");
  }

  @Test
  void validateConfiguration_nonNumericCode_failsFast() {
    properties.setCode("00A000");

    assertThatThrownBy(() -> policy.validateConfiguration())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("code");
  }

  @Test
  void validateConfiguration_codeLengthMismatch_failsFast() {
    properties.setCode("0000"); // 정책은 6자리인데 4자리 주입

    assertThatThrownBy(() -> policy.validateConfiguration())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("code-length");
  }

  private void linkGoogleAccount(long userId, String googleEmail) {
    when(socialAccountRepository.findAllByUserId(userId))
        .thenReturn(List.of(socialAccount(userId, Provider.GOOGLE, googleEmail)));
  }

  private static SocialAccount socialAccount(long userId, Provider provider, String email) {
    return SocialAccount.builder()
        .provider(provider)
        .providerUserId("provider-user-" + userId)
        .email(email)
        .userId(userId)
        .build();
  }
}
