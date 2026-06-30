package com.kohere.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kohere.auth.domain.PhoneNotVerifiedException;
import com.kohere.auth.domain.PhoneRateLimitException;
import com.kohere.auth.domain.PhoneVerification;
import com.kohere.auth.domain.PhoneVerificationCodeHasher;
import com.kohere.auth.domain.PhoneVerificationFailedException;
import com.kohere.auth.domain.PhoneVerificationRepository;
import com.kohere.auth.domain.SmsDispatchException;
import com.kohere.auth.domain.VerificationSmsSender;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link PhoneVerificationService} 단위 테스트(Mockito) — 인증번호 발송(재발송 간격·발송 실패 시 챌린지 미저장)·검증(챌린지
 * 부재/만료/불일치·시도 상한)·온보딩 선행 검사(검증 마커 대조). Redis 저장·해시·SMS 발송 포트를 모킹하고 정책값은 실제 기본값을 쓴다(세입자 이메일 인증과 대칭,
 * ADR-0034).
 */
@ExtendWith(MockitoExtension.class)
class PhoneVerificationServiceTest {

  private static final long USER_ID = 7L;
  private static final String PHONE = "01012345678";
  private static final String CODE = "482915";

  @Mock private PhoneVerificationRepository repository;
  @Mock private PhoneVerificationCodeHasher codeHasher;
  @Mock private VerificationSmsSender smsSender;
  // 정책값은 실제 기본값(스파이 아님 — when().thenReturn() 인자에서 getter를 호출해도 스터빙과 충돌하지 않도록).
  private final PhoneVerificationProperties properties = new PhoneVerificationProperties();

  private PhoneVerificationService service;

  @BeforeEach
  void setUp() {
    service = new PhoneVerificationService(repository, codeHasher, smsSender, properties);
  }

  @Test
  void sendCode_dispatchesSmsThenStoresChallenge_returnsExpiresIn() {
    when(repository.findChallenge(USER_ID)).thenReturn(Optional.empty());
    when(codeHasher.hash(anyString())).thenReturn("hashed-code");

    long expiresIn = service.sendCode(USER_ID, PHONE);

    assertThat(expiresIn).isEqualTo(properties.getCodeTtlSeconds());
    // 발송 성공 뒤에만 챌린지를 저장한다(attempts=0, 대상 번호·해시 보관)
    verify(smsSender).send(eq(PHONE), anyString());
    ArgumentCaptor<PhoneVerification> captor = ArgumentCaptor.forClass(PhoneVerification.class);
    verify(repository).saveChallenge(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
    assertThat(captor.getValue().getPhoneNumber()).isEqualTo(PHONE);
    assertThat(captor.getValue().getCodeHash()).isEqualTo("hashed-code");
    assertThat(captor.getValue().getAttempts()).isZero();
  }

  @Test
  void sendCode_withinResendInterval_throwsRateLimitAndDoesNotSend() {
    PhoneVerification recent =
        PhoneVerification.issue(
            USER_ID, PHONE, "hashed-code", Instant.now(), properties.getCodeTtlSeconds());
    when(repository.findChallenge(USER_ID)).thenReturn(Optional.of(recent));

    assertThatThrownBy(() -> service.sendCode(USER_ID, PHONE))
        .isInstanceOf(PhoneRateLimitException.class);

    verify(smsSender, never()).send(any(), any());
    verify(repository, never()).saveChallenge(any());
  }

  @Test
  void sendCode_dispatchFails_doesNotStoreChallenge() {
    when(repository.findChallenge(USER_ID)).thenReturn(Optional.empty());
    doThrow(new SmsDispatchException(new RuntimeException("twilio down")))
        .when(smsSender)
        .send(eq(PHONE), anyString());

    assertThatThrownBy(() -> service.sendCode(USER_ID, PHONE))
        .isInstanceOf(SmsDispatchException.class);

    // 발송 실패면 챌린지를 저장하지 않는다(다음 발송이 재발송 간격에 막히지 않도록)
    verify(repository, never()).saveChallenge(any());
  }

  @Test
  void verify_matchingCode_marksVerifiedAndDeletesChallenge() {
    when(repository.findChallenge(USER_ID)).thenReturn(Optional.of(challenge("hashed-code", 0)));
    when(codeHasher.hash(CODE)).thenReturn("hashed-code");

    service.verify(USER_ID, PHONE, CODE);

    verify(repository).markVerified(USER_ID, PHONE, properties.getVerifiedTtlSeconds());
    verify(repository).deleteChallenge(USER_ID);
  }

  @Test
  void verify_noChallenge_throwsFailedImmediately() {
    when(repository.findChallenge(USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.verify(USER_ID, PHONE, CODE))
        .isInstanceOf(PhoneVerificationFailedException.class);

    verify(repository, never()).markVerified(anyLong(), any(), anyLong());
  }

  @Test
  void verify_expiredChallenge_deletesAndThrowsFailed() {
    PhoneVerification expired =
        PhoneVerification.issue(
            USER_ID, PHONE, "hashed-code", Instant.now().minusSeconds(600), 300);
    when(repository.findChallenge(USER_ID)).thenReturn(Optional.of(expired));

    assertThatThrownBy(() -> service.verify(USER_ID, PHONE, CODE))
        .isInstanceOf(PhoneVerificationFailedException.class);

    verify(repository).deleteChallenge(USER_ID);
    verify(repository, never()).markVerified(anyLong(), any(), anyLong());
  }

  @Test
  void verify_mismatch_incrementsAttemptAndThrowsFailed() {
    when(repository.findChallenge(USER_ID)).thenReturn(Optional.of(challenge("hashed-code", 0)));
    when(codeHasher.hash(CODE)).thenReturn("wrong-hash");

    assertThatThrownBy(() -> service.verify(USER_ID, PHONE, CODE))
        .isInstanceOf(PhoneVerificationFailedException.class);

    ArgumentCaptor<PhoneVerification> captor = ArgumentCaptor.forClass(PhoneVerification.class);
    verify(repository).saveChallenge(captor.capture());
    assertThat(captor.getValue().getAttempts()).isEqualTo(1);
    verify(repository, never()).markVerified(anyLong(), any(), anyLong());
  }

  @Test
  void verify_mismatchReachingMaxAttempts_throwsRateLimit() {
    when(repository.findChallenge(USER_ID))
        .thenReturn(Optional.of(challenge("hashed-code", properties.getMaxAttempts() - 1)));
    when(codeHasher.hash(CODE)).thenReturn("wrong-hash");

    assertThatThrownBy(() -> service.verify(USER_ID, PHONE, CODE))
        .isInstanceOf(PhoneRateLimitException.class);

    // 상한에 도달한 시도도 누적 저장한다(이후 요청은 즉시 429로 차단)
    verify(repository).saveChallenge(any());
  }

  @Test
  void verify_attemptsAlreadyAtMax_throwsRateLimitWithoutCompare() {
    when(repository.findChallenge(USER_ID))
        .thenReturn(Optional.of(challenge("hashed-code", properties.getMaxAttempts())));

    assertThatThrownBy(() -> service.verify(USER_ID, PHONE, CODE))
        .isInstanceOf(PhoneRateLimitException.class);

    verify(repository, never()).saveChallenge(any());
    verify(repository, never()).markVerified(anyLong(), any(), anyLong());
  }

  @Test
  void assertVerified_matchingMarker_passes() {
    when(repository.findVerifiedPhone(USER_ID)).thenReturn(Optional.of(PHONE));

    service.assertVerified(USER_ID, PHONE);
  }

  @Test
  void assertVerified_noMarker_throwsNotVerified() {
    when(repository.findVerifiedPhone(USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.assertVerified(USER_ID, PHONE))
        .isInstanceOf(PhoneNotVerifiedException.class);
  }

  @Test
  void assertVerified_differentPhone_throwsNotVerified() {
    when(repository.findVerifiedPhone(USER_ID)).thenReturn(Optional.of("01099998888"));

    assertThatThrownBy(() -> service.assertVerified(USER_ID, PHONE))
        .isInstanceOf(PhoneNotVerifiedException.class);
  }

  private PhoneVerification challenge(String codeHash, int attempts) {
    return PhoneVerification.builder()
        .userId(USER_ID)
        .phoneNumber(PHONE)
        .codeHash(codeHash)
        .attempts(attempts)
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(properties.getCodeTtlSeconds()))
        .build();
  }
}
