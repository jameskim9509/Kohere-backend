package com.kohere.auth.application;

import com.kohere.auth.domain.PhoneNotVerifiedException;
import com.kohere.auth.domain.PhoneRateLimitException;
import com.kohere.auth.domain.PhoneVerification;
import com.kohere.auth.domain.PhoneVerificationCodeHasher;
import com.kohere.auth.domain.PhoneVerificationCodeIssuer;
import com.kohere.auth.domain.PhoneVerificationFailedException;
import com.kohere.auth.domain.PhoneVerificationRepository;
import com.kohere.common.request.PhoneNumbers;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 임대인 연락처(휴대폰) SMS 인증 유스케이스. 인증번호 발송(동기·발송 성공 시에만 챌린지 확정)·검증(해시 대조·시도 상한)·온보딩/프로필 변경 시 검증 완료 확인을
 * 담당한다. Redis 저장·해시·SMS 발송은 도메인 포트로 협력한다(세입자 이메일 인증 {@link EmailVerificationService}와 대칭, 시퀀스
 * US-1-10).
 *
 * <p><b>번호는 이 경계에서 한 번만 정규화한다</b>({@link PhoneNumbers#normalize}) — 챌린지에 담는 대상 번호, 검증 시 대조하는 번호, 검증
 * 완료 마커에 남기는 번호, 그리고 마커와 제출값을 비교하는 {@link #assertVerified}까지 전부 같은 표준형이라야 한다. 한 곳이라도 원문을 쓰면 발송은
 * {@code 010-1234-5678}로 하고 확인은 {@code 01012345678}로 하는 순간 조용히 실패한다(#229 D10).
 */
@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

  private final PhoneVerificationRepository repository;
  private final PhoneVerificationCodeHasher codeHasher;
  private final PhoneVerificationCodeIssuer codeIssuer;
  private final PhoneVerificationProperties properties;

  /**
   * 인증번호 발송. 재발송 간격 미달이면 429. 인증번호 생성·발송은 발급 포트가 함께 맡고, <b>발급에 성공한 뒤에만</b> 해시해 챌린지를 저장한다(발송 실패는
   * 502, 챌린지 미저장).
   *
   * @return 인증번호 만료까지의 초(expiresIn)
   */
  public long sendCode(long userId, String phoneNumber) {
    String normalized = PhoneNumbers.normalize(phoneNumber);
    Instant now = Instant.now();
    Optional<PhoneVerification> existing = repository.findChallenge(userId);
    if (existing.isPresent()
        && existing
            .get()
            .getIssuedAt()
            .plusSeconds(properties.getResendIntervalSeconds())
            .isAfter(now)) {
      throw new PhoneRateLimitException();
    }
    String code = codeIssuer.issue(userId, normalized); // 발송 실패 시 502 전파 — 챌린지 미저장
    repository.saveChallenge(
        PhoneVerification.issue(
            userId, normalized, codeHasher.hash(code), now, properties.getCodeTtlSeconds()));
    return properties.getCodeTtlSeconds();
  }

  /**
   * 인증번호 검증. 챌린지 부재(미발송·만료·이미 검증)면 즉시 422(attempts 무관). 챌린지 존재 + 불일치면 attempts 증가 후 상한 초과 시 429·아니면
   * 422. 일치하면 검증 완료 마커를 저장하고 코드 챌린지를 제거한다.
   */
  public void verify(long userId, String phoneNumber, String code) {
    String normalized = PhoneNumbers.normalize(phoneNumber);
    PhoneVerification challenge =
        repository.findChallenge(userId).orElseThrow(PhoneVerificationFailedException::new);
    if (challenge.isExpired(Instant.now())) {
      repository.deleteChallenge(userId);
      throw new PhoneVerificationFailedException();
    }
    if (challenge.getAttempts() >= properties.getMaxAttempts()) {
      throw new PhoneRateLimitException();
    }
    if (!challenge.matches(codeHasher.hash(code), normalized)) {
      PhoneVerification bumped = challenge.incrementAttempt();
      repository.saveChallenge(bumped);
      if (bumped.getAttempts() >= properties.getMaxAttempts()) {
        throw new PhoneRateLimitException();
      }
      throw new PhoneVerificationFailedException();
    }
    repository.markVerified(userId, normalized, properties.getVerifiedTtlSeconds());
    repository.deleteChallenge(userId);
  }

  /**
   * 임대인 온보딩 제출·프로필 연락처 변경 선행 검사 — 제출 phoneNumber가 검증 완료 마커와 일치해야 한다. 마커는 {@link #verify}가 정규화해
   * 저장했으므로 제출값도 같은 표준형으로 접어 대조한다(하이픈 표기 차이는 흡수, 다른 번호는 그대로 거절).
   */
  public void assertVerified(long userId, String phoneNumber) {
    String verified =
        repository.findVerifiedPhone(userId).orElseThrow(PhoneNotVerifiedException::new);
    if (!verified.equals(PhoneNumbers.normalize(phoneNumber))) {
      throw new PhoneNotVerifiedException();
    }
  }
}
