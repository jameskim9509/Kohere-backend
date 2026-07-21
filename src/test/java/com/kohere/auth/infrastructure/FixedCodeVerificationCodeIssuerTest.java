package com.kohere.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kohere.auth.domain.EmailDispatchException;
import com.kohere.auth.domain.EmailVerificationFailedException;
import com.kohere.auth.domain.PhoneVerificationFailedException;
import com.kohere.auth.domain.SmsDispatchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link FixedCodeEmailVerificationCodeIssuer}·{@link FixedCodePhoneVerificationCodeIssuer} 단위
 * 테스트(Mockito) — 실제 우회가 일어나는 지점이므로 <b>적중 시 고정 코드 반환 + 발송 위임 없음</b>, <b>미적중 시 실제 발급기로 위임</b> 두 갈래를
 * 모두 고정한다({@link TestLoginOidcTokenVerifierTest}와 동일한 구조).
 */
@ExtendWith(MockitoExtension.class)
class FixedCodeVerificationCodeIssuerTest {

  private static final long USER_ID = 42L;
  private static final String EMAIL = "demo-tenant@kohere.io";
  private static final String PHONE = "01000000000";
  private static final String FIXED_CODE = "000000";
  private static final String RANDOM_CODE = "731905";

  @Mock private EmailVerificationCodeIssuerImpl emailDelegate;
  @Mock private PhoneVerificationCodeIssuerImpl phoneDelegate;
  @Mock private FixedVerificationPolicy policy;

  private FixedCodeEmailVerificationCodeIssuer emailIssuer;
  private FixedCodePhoneVerificationCodeIssuer phoneIssuer;

  @BeforeEach
  void setUp() {
    emailIssuer = new FixedCodeEmailVerificationCodeIssuer(emailDelegate, policy);
    phoneIssuer = new FixedCodePhoneVerificationCodeIssuer(phoneDelegate, policy);
  }

  @Test
  void email_reviewAccount_returnsFixedCodeWithoutDispatching() {
    when(policy.appliesToEmail(USER_ID, EMAIL)).thenReturn(true);
    when(policy.code()).thenReturn(FIXED_CODE);

    String code = emailIssuer.issue(USER_ID, EMAIL);

    assertThat(code).isEqualTo(FIXED_CODE);
    // 실제 발급기를 거치지 않으므로 메일 발송도 일어나지 않는다
    verifyNoInteractions(emailDelegate);
  }

  @Test
  void email_ordinaryUser_delegatesToRealIssuer() {
    when(policy.appliesToEmail(USER_ID, EMAIL)).thenReturn(false);
    when(policy.isReviewAccount(USER_ID)).thenReturn(false);
    when(emailDelegate.issue(USER_ID, EMAIL)).thenReturn(RANDOM_CODE);

    String code = emailIssuer.issue(USER_ID, EMAIL);

    // 일반 사용자는 랜덤 인증번호 + 실제 발송 경로를 그대로 탄다
    assertThat(code).isEqualTo(RANDOM_CODE);
  }

  @Test
  void email_reviewAccountWithUnregisteredEmail_isRejectedWithoutDispatching() {
    when(policy.appliesToEmail(USER_ID, "typo@kohere.io")).thenReturn(false);
    when(policy.isReviewAccount(USER_ID)).thenReturn(true);

    // 심사자는 외부 메일을 받을 수 없다 — 실발송으로 흘려보내지 않고 거절한다
    assertThatThrownBy(() -> emailIssuer.issue(USER_ID, "typo@kohere.io"))
        .isInstanceOf(EmailVerificationFailedException.class);
    verifyNoInteractions(emailDelegate);
  }

  @Test
  void phone_reviewAccountWithUnregisteredPhone_isRejectedWithoutDispatching() {
    when(policy.appliesToPhone(USER_ID, "01099998888")).thenReturn(false);
    when(policy.isReviewAccount(USER_ID)).thenReturn(true);

    // SOLAPI를 호출하지 않으므로 SDK 충돌로 인한 500도 발생하지 않는다
    assertThatThrownBy(() -> phoneIssuer.issue(USER_ID, "01099998888"))
        .isInstanceOf(PhoneVerificationFailedException.class);
    verifyNoInteractions(phoneDelegate);
  }

  @Test
  void email_dispatchFailureFromDelegate_propagates() {
    when(policy.appliesToEmail(USER_ID, EMAIL)).thenReturn(false);
    when(emailDelegate.issue(USER_ID, EMAIL))
        .thenThrow(new EmailDispatchException(new RuntimeException("smtp")));

    // 502 계약이 래퍼를 통과해 그대로 전파돼야 챌린지가 저장되지 않는다
    assertThatThrownBy(() -> emailIssuer.issue(USER_ID, EMAIL))
        .isInstanceOf(EmailDispatchException.class);
  }

  @Test
  void phone_reviewAccount_returnsFixedCodeWithoutDispatching() {
    when(policy.appliesToPhone(USER_ID, PHONE)).thenReturn(true);
    when(policy.code()).thenReturn(FIXED_CODE);

    String code = phoneIssuer.issue(USER_ID, PHONE);

    assertThat(code).isEqualTo(FIXED_CODE);
    // SOLAPI 활성화 여부·장애와 무관하게 심사 흐름이 성립해야 한다
    verifyNoInteractions(phoneDelegate);
  }

  @Test
  void phone_ordinaryUser_delegatesToRealIssuer() {
    when(policy.appliesToPhone(USER_ID, PHONE)).thenReturn(false);
    when(phoneDelegate.issue(USER_ID, PHONE)).thenReturn(RANDOM_CODE);

    String code = phoneIssuer.issue(USER_ID, PHONE);

    assertThat(code).isEqualTo(RANDOM_CODE);
  }

  @Test
  void phone_dispatchFailureFromDelegate_propagates() {
    when(policy.appliesToPhone(USER_ID, PHONE)).thenReturn(false);
    when(phoneDelegate.issue(USER_ID, PHONE))
        .thenThrow(new SmsDispatchException(new RuntimeException("solapi")));

    assertThatThrownBy(() -> phoneIssuer.issue(USER_ID, PHONE))
        .isInstanceOf(SmsDispatchException.class);
  }

  @Test
  void email_policyMissesPhoneChannel_doesNotLeakFixedCode() {
    // 이메일 요청은 phone 판별을 쓰지 않는다 — 채널을 섞어 고정 코드가 새지 않는지 고정
    when(policy.appliesToEmail(anyLong(), anyString())).thenReturn(false);
    when(emailDelegate.issue(USER_ID, EMAIL)).thenReturn(RANDOM_CODE);

    assertThat(emailIssuer.issue(USER_ID, EMAIL)).isEqualTo(RANDOM_CODE);
  }
}
