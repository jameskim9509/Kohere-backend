package com.kohere.auth.application;

import com.kohere.auth.domain.EmailNotVerifiedException;
import com.kohere.auth.domain.EmailRateLimitException;
import com.kohere.auth.domain.EmailVerification;
import com.kohere.auth.domain.EmailVerificationCodeHasher;
import com.kohere.auth.domain.EmailVerificationFailedException;
import com.kohere.auth.domain.EmailVerificationRepository;
import com.kohere.auth.domain.VerificationEmailSender;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 온보딩 중 이메일 인증 유스케이스. 인증번호 발송(동기·발송 성공 시에만 챌린지 확정)·검증(해시 대조·시도 상한)·온보딩 제출 시 검증 완료 확인을 담당한다. Redis
 * 저장·해시·메일 발송은 도메인 포트로 협력한다(시퀀스 US-1-6).
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final EmailVerificationRepository repository;
  private final EmailVerificationCodeHasher codeHasher;
  private final VerificationEmailSender emailSender;
  private final EmailVerificationProperties properties;

  /**
   * 인증번호 발송. 재발송 간격 미달이면 429. 인증번호를 생성·해시하고 <b>동기 발송에 성공한 뒤에만</b> 챌린지를 저장한다(발송 실패는 502, 챌린지 미저장).
   *
   * @return 인증번호 만료까지의 초(expiresIn)
   */
  public long sendCode(long userId, String email) {
    Instant now = Instant.now();
    Optional<EmailVerification> existing = repository.findChallenge(userId);
    if (existing.isPresent()
        && existing
            .get()
            .getIssuedAt()
            .plusSeconds(properties.getResendIntervalSeconds())
            .isAfter(now)) {
      throw new EmailRateLimitException();
    }
    String code = generateNumericCode(properties.getCodeLength());
    emailSender.send(email, code); // 발송 실패 시 EmailDispatchException(502) — 챌린지 미저장
    repository.saveChallenge(
        EmailVerification.issue(
            userId, email, codeHasher.hash(code), now, properties.getCodeTtlSeconds()));
    return properties.getCodeTtlSeconds();
  }

  /**
   * 인증번호 검증. 챌린지 부재(미발송·만료·이미 검증)면 즉시 422(attempts 무관). 챌린지 존재 + 불일치면 attempts 증가 후 상한 초과 시 429·아니면
   * 422. 일치하면 검증 완료 마커를 저장하고 코드 챌린지를 제거한다.
   */
  public void verify(long userId, String email, String code) {
    EmailVerification challenge =
        repository.findChallenge(userId).orElseThrow(EmailVerificationFailedException::new);
    if (challenge.isExpired(Instant.now())) {
      repository.deleteChallenge(userId);
      throw new EmailVerificationFailedException();
    }
    if (challenge.getAttempts() >= properties.getMaxAttempts()) {
      throw new EmailRateLimitException();
    }
    if (!challenge.matches(codeHasher.hash(code), email)) {
      EmailVerification bumped = challenge.incrementAttempt();
      repository.saveChallenge(bumped);
      if (bumped.getAttempts() >= properties.getMaxAttempts()) {
        throw new EmailRateLimitException();
      }
      throw new EmailVerificationFailedException();
    }
    repository.markVerified(userId, email, properties.getVerifiedTtlSeconds());
    repository.deleteChallenge(userId);
  }

  /** 온보딩 제출 선행 검사 — 제출 email이 검증 완료 마커와 일치해야 한다. */
  public void assertVerified(long userId, String email) {
    String verified =
        repository.findVerifiedEmail(userId).orElseThrow(EmailNotVerifiedException::new);
    if (!verified.equals(email)) {
      throw new EmailNotVerifiedException();
    }
  }

  private static String generateNumericCode(int length) {
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(SECURE_RANDOM.nextInt(10));
    }
    return sb.toString();
  }
}
