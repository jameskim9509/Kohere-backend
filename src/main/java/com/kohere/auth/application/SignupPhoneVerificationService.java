package com.kohere.auth.application;

import com.kohere.auth.application.dto.SignupPhoneVerificationCodeResponse;
import com.kohere.auth.application.dto.SignupPhoneVerifyResponse;
import com.kohere.auth.domain.PhoneRateLimitException;
import com.kohere.auth.domain.PhoneVerificationCodeHasher;
import com.kohere.auth.domain.PhoneVerificationFailedException;
import com.kohere.auth.domain.SignupPhoneVerification;
import com.kohere.auth.domain.SignupPhoneVerificationCodeIssuer;
import com.kohere.auth.domain.SignupPhoneVerificationRepository;
import com.kohere.auth.domain.SignupSmsRateLimiter;
import com.kohere.common.request.PhoneNumbers;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 임대인 웹 회원가입 전 연락처(휴대폰) SMS 인증 유스케이스(US-1-13 · 스펙 §1-1·§1-2). 발송(동기·발송 성공 시에만 챌린지 확정)과 확인(해시 대조·시도
 * 상한)을 담당하며, 확인에 성공하면 <b>가입 제출(US-1-11)이 대조할 검증 마커</b>를 남긴다.
 *
 * <p><b>온보딩용 {@link PhoneVerificationService}와의 차이는 딱 둘이다</b> — (1) 계정이 없어 챌린지 키가 {@code userId}가
 * 아니라 정규화한 번호이고, (2) permitAll 경로라 번호·IP 이중 레이트리밋({@link SignupSmsRateLimiter})이 붙는다. 인증번호
 * 자릿수·TTL·시도 상한·재발송 간격({@link PhoneVerificationProperties})과 해시({@link
 * PhoneVerificationCodeHasher})·SMS 발송 포트는 그대로 공유한다(ADR-0034의 정책을 웹 가입 경로로 확장).
 *
 * <p><b>확인 실패를 한 코드로 묶는다</b> — 챌린지 부재·불일치·만료·시도 상한 초과가 모두 422 {@link
 * PhoneVerificationFailedException} 이다. 앱 트랙은 시도 초과를 429로 구분하지만, 비로그인 경로에서는 그 구분 자체가 "이 번호에 챌린지가 살아
 * 있다 / 시도가 몇 번 남았다"를 알려주는 신호가 된다.
 *
 * <p><b>번호는 이 경계에서 한 번만 정규화한다</b>({@link PhoneNumbers#normalize}) — 챌린지 키, 검증 마커 키, 레이트리밋 카운터 키가 전부
 * 같은 표준형이라야 한다. 한 곳이라도 원문을 쓰면 {@code 010-1234-5678}로 발송하고 {@code 01012345678}로 확인하는 순간 조용히
 * 실패한다(#229 D10).
 *
 * <p>Redis만 만지고 DB·모듈 간 호출이 없어 트랜잭션 경계를 두지 않는다(전이가 있는 가입 제출은 US-1-11이 한 트랜잭션으로 감싼다).
 */
@Service
@RequiredArgsConstructor
public class SignupPhoneVerificationService {

  private final SignupPhoneVerificationRepository repository;
  private final SignupPhoneVerificationCodeIssuer codeIssuer;
  private final PhoneVerificationCodeHasher codeHasher;
  private final SignupSmsRateLimiter rateLimiter;
  private final PhoneVerificationProperties properties;

  /**
   * 인증번호 발송. <b>재발송 쿨다운 → 번호·IP 시간당 한도 → 발급·발송 → 챌린지 저장</b> 순으로 진행한다.
   *
   * <p>쿨다운을 한도보다 먼저 보는 것은 의도다 — 60초 안의 재요청(버튼 두 번 누르기)은 어차피 거절되므로 그것으로 시간당 한도까지 깎지 않는다. 둘 다 429라
   * 클라이언트가 보는 응답은 같다.
   *
   * <p>발급 포트가 인증번호 생성과 SMS 발송을 함께 맡고, <b>정상 반환한 뒤에만</b> 해시해 챌린지를 저장한다 — 발송 실패는 502가 전파되고 Redis에는
   * 아무것도 남지 않는다(챌린지를 먼저 쓰면 SMS를 못 받은 사용자가 만료를 기다려야 하고 재발송 쿨다운만 소모한다).
   *
   * @param clientIp 호출자 IP(프레젠테이션 계층이 X-Forwarded-For·remote address에서 추출해 넘긴다)
   */
  public SignupPhoneVerificationCodeResponse sendCode(String phoneNumber, String clientIp) {
    String normalized = PhoneNumbers.normalize(phoneNumber);
    Instant now = Instant.now();
    Optional<SignupPhoneVerification> existing = repository.findChallenge(normalized);
    if (existing.isPresent()
        && existing
            .get()
            .getIssuedAt()
            .plusSeconds(properties.getResendIntervalSeconds())
            .isAfter(now)) {
      throw new PhoneRateLimitException();
    }
    rateLimiter.recordAttempt(normalized, clientIp);
    String code = codeIssuer.issue(normalized); // 발송 실패 시 502 전파 — 챌린지 미저장
    repository.saveChallenge(
        SignupPhoneVerification.issue(
            normalized, codeHasher.hash(code), now, properties.getCodeTtlSeconds()));
    return new SignupPhoneVerificationCodeResponse(
        maskPhone(normalized), properties.getCodeTtlSeconds());
  }

  /**
   * 인증번호 확인. 챌린지 부재(미발송·만료·이미 검증·발송 실패로 미저장)면 올릴 {@code attempts} 레코드가 없어 즉시 422다. 불일치면 {@code
   * attempts}를 올려 저장한 뒤 422이고, 이미 상한에 도달한 챌린지는 해시를 대조하지 않고 422로 끊는다.
   *
   * <p>일치하면 검증 마커(TTL 30분)를 남기고 코드 챌린지를 삭제한다 — 같은 인증번호를 다시 제출해도 챌린지가 없어 실패한다(1회용). 마커는 가입 제출이 소비한다.
   */
  public SignupPhoneVerifyResponse verify(String phoneNumber, String code) {
    String normalized = PhoneNumbers.normalize(phoneNumber);
    SignupPhoneVerification challenge =
        repository.findChallenge(normalized).orElseThrow(PhoneVerificationFailedException::new);
    if (challenge.isExpired(Instant.now())) {
      repository.deleteChallenge(normalized);
      throw new PhoneVerificationFailedException();
    }
    if (challenge.getAttempts() >= properties.getMaxAttempts()) {
      throw new PhoneVerificationFailedException(); // 앱 트랙(§4-2)의 429와 달리 422로 통일한다
    }
    if (!challenge.matches(codeHasher.hash(code))) {
      repository.saveChallenge(challenge.incrementAttempt());
      throw new PhoneVerificationFailedException();
    }
    repository.markVerified(normalized, properties.getVerifiedTtlSeconds());
    repository.deleteChallenge(normalized);
    return new SignupPhoneVerifyResponse(maskPhone(normalized), true);
  }

  /**
   * 응답·로그용 연락처 마스킹(예: {@code 01012345678} → {@code 010-****-5678}, error-response-guide §6). 앱 트랙의
   * 같은 규칙을 {@code AuthService}가 별도로 들고 있는데, 공용 헬퍼로 끌어올리는 정리는 이 변경의 범위 밖으로 둔다 — 응답 계약이 걸린 순수 함수라 중복
   * 비용이 낮고, 두 트랙의 응답 표기가 어긋나면 안 된다는 요구는 문서(§6)가 이미 고정하고 있다.
   */
  private static String maskPhone(String phone) {
    if (phone == null) {
      return null;
    }
    String digits = phone.replaceAll("\\D", "");
    if (digits.length() < 4) {
      return "***";
    }
    String prefix = digits.substring(0, Math.min(3, digits.length() - 4));
    String suffix = digits.substring(digits.length() - 4);
    return prefix + "-****-" + suffix;
  }
}
