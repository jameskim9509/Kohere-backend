package com.kohere.auth.application;

import com.kohere.auth.domain.EmailRateLimitException;
import com.kohere.auth.domain.EmailVerification;
import com.kohere.auth.domain.EmailVerificationCodeHasher;
import com.kohere.auth.domain.EmailVerificationCodeIssuer;
import com.kohere.auth.domain.EmailVerificationFailedException;
import com.kohere.auth.domain.EmailVerificationRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 이메일 인증 유스케이스(정식(ACTIVE) 사용자 전용 — #192). 인증번호 발송(동기·발송 성공 시에만 챌린지 확정)·검증(해시 대조·시도 상한·성공 시 마커 저장)을
 * 담당한다. 검증 성공 마커는 저장하지만 이번 범위에서 소비처(온보딩 대조)는 없다(실제 이메일 변경 반영은 후속 이슈). Redis 저장·해시·메일 발송은 도메인 포트로
 * 협력한다(시퀀스 US-1-6).
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

  private final EmailVerificationRepository repository;
  private final EmailVerificationCodeHasher codeHasher;
  private final EmailVerificationCodeIssuer codeIssuer;
  private final EmailVerificationProperties properties;

  /**
   * 인증번호 발송. 재발송 간격 미달이면 429. 인증번호 생성·발송은 발급 포트가 함께 맡고, <b>발급에 성공한 뒤에만</b> 해시해 챌린지를 저장한다(발송 실패는
   * 502, 챌린지 미저장).
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
    String code = codeIssuer.issue(userId, email); // 발송 실패 시 502 전파 — 챌린지 미저장
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
}
