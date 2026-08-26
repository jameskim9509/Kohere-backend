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

import com.kohere.auth.domain.EmailAlreadyRegisteredException;
import com.kohere.auth.domain.EmailDispatchException;
import com.kohere.auth.domain.EmailNotVerifiedException;
import com.kohere.auth.domain.EmailRateLimitException;
import com.kohere.auth.domain.EmailVerificationCodeHasher;
import com.kohere.auth.domain.EmailVerificationFailedException;
import com.kohere.auth.domain.LocalAccountRepository;
import com.kohere.auth.domain.SignupEmailRateLimiter;
import com.kohere.auth.domain.SignupEmailVerification;
import com.kohere.auth.domain.SignupEmailVerificationCodeIssuer;
import com.kohere.auth.domain.SignupEmailVerificationRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 가입용 이메일 인증 유스케이스 단위 테스트(US-1-18). Redis·SMTP 없이 <b>판정 순서와 부수효과</b>만 고정한다 — 실제 HTTP·저장은 문서·통합 테스트가
 * 덮으므로 여기서는 그쪽이 재현하기 어려운 것(발송 실패 시 미저장, 중복이 한도보다 뒤인지, 시도 상한 경계)을 본다.
 *
 * <p>선례는 {@code PhoneVerificationServiceTest}이며, 이 파일이 추가로 못박는 계약이 하나 있다 — <b>중복 검사는 레이트리밋 뒤</b>다.
 * 순서를 뒤집어도 정상 흐름은 그대로라 다른 어떤 테스트도 깨지지 않지만, 그 순간 가입 여부 열거가 카운터를 하나도 쓰지 않는 공짜 질의가 된다.
 */
@ExtendWith(MockitoExtension.class)
class SignupEmailVerificationServiceTest {

  private static final String RAW_EMAIL = "  Kim@Work.COM ";
  private static final String NORMALIZED = "kim@work.com";
  private static final String CLIENT_IP = "203.0.113.10";
  private static final String CODE = "482913";
  private static final String CODE_HASH = "hash-482913";

  @Mock private SignupEmailVerificationRepository repository;
  @Mock private SignupEmailVerificationCodeIssuer codeIssuer;
  @Mock private EmailVerificationCodeHasher codeHasher;
  @Mock private SignupEmailRateLimiter rateLimiter;
  @Mock private LocalAccountRepository localAccountRepository;

  private SignupEmailVerificationService service;
  private EmailVerificationProperties properties;

  @BeforeEach
  void setUp() {
    properties = new EmailVerificationProperties();
    service =
        new SignupEmailVerificationService(
            repository, codeIssuer, codeHasher, rateLimiter, localAccountRepository, properties);
  }

  @Test
  @DisplayName("발송은 이메일을 정규화해 챌린지 키·한도 키·중복 조회에 같은 값을 쓴다")
  void sendCode_normalizesEmailOnce() {
    when(repository.findChallenge(NORMALIZED)).thenReturn(Optional.empty());
    when(localAccountRepository.existsByEmail(NORMALIZED)).thenReturn(false);
    when(codeIssuer.issue(NORMALIZED)).thenReturn(CODE);
    when(codeHasher.hash(CODE)).thenReturn(CODE_HASH);

    var response = service.sendCode(RAW_EMAIL, CLIENT_IP);

    // 한 곳이라도 원문을 쓰면 발송과 확인이 다른 키를 가리켜 조용히 422가 된다.
    verify(rateLimiter).recordAttempt(NORMALIZED, CLIENT_IP);
    ArgumentCaptor<SignupEmailVerification> saved =
        ArgumentCaptor.forClass(SignupEmailVerification.class);
    verify(repository).saveChallenge(saved.capture());
    assertThat(saved.getValue().getEmail()).isEqualTo(NORMALIZED);
    assertThat(saved.getValue().getCodeHash()).isEqualTo(CODE_HASH);
    assertThat(saved.getValue().getAttempts()).isZero();
    // 응답은 마스킹된 값이고 원문·인증번호는 나가지 않는다.
    assertThat(response.email()).isEqualTo("ki***@work.com");
    assertThat(response.expiresIn()).isEqualTo(properties.getCodeTtlSeconds());
  }

  @Test
  @DisplayName("중복 판정은 레이트리밋 뒤다 — 열거 관찰 한 번이 예산 한 칸을 쓴다")
  void sendCode_checksDuplicateAfterRateLimit() {
    when(repository.findChallenge(NORMALIZED)).thenReturn(Optional.empty());
    when(localAccountRepository.existsByEmail(NORMALIZED)).thenReturn(true);

    assertThatThrownBy(() -> service.sendCode(RAW_EMAIL, CLIENT_IP))
        .isInstanceOf(EmailAlreadyRegisteredException.class);

    // 순서를 뒤집으면 카운터를 올리지 않고 무한히 물어볼 수 있다 — 정상 흐름은 그대로라 이 단정만이 방어다.
    verify(rateLimiter).recordAttempt(NORMALIZED, CLIENT_IP);
    // 메일도 챌린지도 남기지 않는다.
    verify(codeIssuer, never()).issue(anyString());
    verify(repository, never()).saveChallenge(any());
  }

  @Test
  @DisplayName("재발송 쿨다운 미달이면 한도 카운터를 올리지 않고 429다")
  void sendCode_withinResendInterval_doesNotBurnRateLimitBudget() {
    Instant now = Instant.now();
    when(repository.findChallenge(NORMALIZED))
        .thenReturn(
            Optional.of(
                SignupEmailVerification.issue(
                    NORMALIZED, CODE_HASH, now, properties.getCodeTtlSeconds())));

    assertThatThrownBy(() -> service.sendCode(RAW_EMAIL, CLIENT_IP))
        .isInstanceOf(EmailRateLimitException.class);

    // 버튼 두 번 누르기로 시간당 한도까지 깎지 않는다는 것이 쿨다운을 먼저 보는 이유다.
    verify(rateLimiter, never()).recordAttempt(anyString(), anyString());
    verify(codeIssuer, never()).issue(anyString());
  }

  @Test
  @DisplayName("발송에 실패하면 챌린지를 저장하지 않는다(send-then-store)")
  void sendCode_dispatchFailure_doesNotStoreChallenge() {
    when(repository.findChallenge(NORMALIZED)).thenReturn(Optional.empty());
    when(localAccountRepository.existsByEmail(NORMALIZED)).thenReturn(false);
    doThrow(new EmailDispatchException(new RuntimeException("smtp down")))
        .when(codeIssuer)
        .issue(NORMALIZED);

    assertThatThrownBy(() -> service.sendCode(RAW_EMAIL, CLIENT_IP))
        .isInstanceOf(EmailDispatchException.class);

    // 먼저 저장하면 메일을 못 받은 사용자가 만료를 기다려야 하고 재발송 쿨다운만 소모한다.
    verify(repository, never()).saveChallenge(any());
  }

  @Test
  @DisplayName("확인 성공은 마커를 남기고 챌린지를 지운다 — 같은 인증번호를 두 번 쓸 수 없다")
  void verify_success_marksAndDeletesChallenge() {
    when(repository.findChallenge(NORMALIZED))
        .thenReturn(
            Optional.of(
                SignupEmailVerification.issue(
                    NORMALIZED, CODE_HASH, Instant.now(), properties.getCodeTtlSeconds())));
    when(codeHasher.hash(CODE)).thenReturn(CODE_HASH);

    var response = service.verify(RAW_EMAIL, CODE);

    assertThat(response.verified()).isTrue();
    assertThat(response.email()).isEqualTo("ki***@work.com");
    verify(repository).markVerified(NORMALIZED, properties.getVerifiedTtlSeconds());
    verify(repository).deleteChallenge(NORMALIZED);
  }

  @Test
  @DisplayName("시도 상한에 도달한 챌린지는 해시를 대조하지 않고 422다")
  void verify_atAttemptLimit_failsWithoutHashing() {
    SignupEmailVerification exhausted =
        SignupEmailVerification.issue(
            NORMALIZED, CODE_HASH, Instant.now(), properties.getCodeTtlSeconds());
    for (int i = 0; i < properties.getMaxAttempts(); i++) {
      exhausted = exhausted.incrementAttempt();
    }
    when(repository.findChallenge(NORMALIZED)).thenReturn(Optional.of(exhausted));

    // 429가 아니라 422다 — 비로그인 경로에서 코드가 갈리면 그 자체가 시도 잔량을 알려 주는 신호가 된다.
    assertThatThrownBy(() -> service.verify(RAW_EMAIL, CODE))
        .isInstanceOf(EmailVerificationFailedException.class);

    verify(codeHasher, never()).hash(anyString());
    verify(repository, never()).saveChallenge(any());
    verify(repository, never()).markVerified(anyString(), anyLong());
  }

  @Test
  @DisplayName("불일치는 시도를 누적해 저장한 뒤 422다")
  void verify_mismatch_incrementsAttempt() {
    when(repository.findChallenge(NORMALIZED))
        .thenReturn(
            Optional.of(
                SignupEmailVerification.issue(
                    NORMALIZED, CODE_HASH, Instant.now(), properties.getCodeTtlSeconds())));
    when(codeHasher.hash("000000")).thenReturn("other-hash");

    assertThatThrownBy(() -> service.verify(RAW_EMAIL, "000000"))
        .isInstanceOf(EmailVerificationFailedException.class);

    ArgumentCaptor<SignupEmailVerification> saved =
        ArgumentCaptor.forClass(SignupEmailVerification.class);
    verify(repository).saveChallenge(saved.capture());
    assertThat(saved.getValue().getAttempts()).isEqualTo(1);
  }

  @Test
  @DisplayName("가입 게이트는 정규화한 이메일로 마커를 찾고 없으면 422 AUTH_EMAIL_NOT_VERIFIED다")
  void assertVerified_withoutMarker_throws() {
    when(repository.isVerified(NORMALIZED)).thenReturn(false);

    assertThatThrownBy(() -> service.assertVerified(RAW_EMAIL))
        .isInstanceOf(EmailNotVerifiedException.class);

    verify(repository).isVerified(eq(NORMALIZED));
  }

  @Test
  @DisplayName("마커 소비도 같은 정규화 규칙을 쓴다 — 표기가 달라도 실제로 지워진다")
  void consumeVerification_normalizes() {
    service.consumeVerification(RAW_EMAIL);

    verify(repository).deleteVerified(NORMALIZED);
  }
}
