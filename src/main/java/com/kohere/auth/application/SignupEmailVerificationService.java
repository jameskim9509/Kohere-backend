package com.kohere.auth.application;

import com.kohere.auth.application.dto.SignupEmailVerificationCodeResponse;
import com.kohere.auth.application.dto.SignupEmailVerifyResponse;
import com.kohere.auth.domain.EmailAlreadyRegisteredException;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 임대인 웹 회원가입 전 이메일 인증 유스케이스(US-1-18 · 스펙 §1-11·§1-12). 발송(동기·발송 성공 시에만 챌린지 확정)과 확인(해시 대조·시도 상한)을
 * 담당하며, 확인에 성공하면 <b>가입 제출(US-1-11)이 대조할 검증 마커</b>를 남긴다. 그 마커의 게이트 검사({@link #assertVerified})와 성공 후
 * 소비({@link #consumeVerification})도 여기 둔다 — 마커를 남기는 곳과 읽는 곳이 갈리면 이메일 정규화 규칙이 두 벌이 된다.
 *
 * <p><b>정식 사용자용 {@link EmailVerificationService}와의 차이는 셋이다</b> — (1) 계정이 없어 챌린지 키가 {@code userId}가
 * 아니라 정규화한 이메일이고, (2) permitAll 경로라 이메일·IP 이중 레이트리밋({@link SignupEmailRateLimiter})이 붙으며, (3) 발송
 * 시점에 <b>로그인 ID 중복을 판정</b>한다. 인증번호 자릿수·TTL·시도 상한·재발송 간격({@link EmailVerificationProperties})과
 * 해시({@link EmailVerificationCodeHasher})·메일 발송 포트는 그대로 공유한다.
 *
 * <p><b>확인 실패를 한 코드로 묶는다</b> — 챌린지 부재·불일치·만료·시도 상한 초과가 모두 422 {@link
 * EmailVerificationFailedException} 이다. 정식 사용자 트랙은 시도 초과를 429로 구분하지만, 비로그인 경로에서는 그 구분 자체가 "이 주소에
 * 챌린지가 살아 있다 / 시도가 몇 번 남았다"를 알려주는 신호가 된다({@link SignupPhoneVerificationService}와 같은 판단).
 *
 * <p><b>이메일은 이 경계에서 한 번만 정규화한다</b>({@link Emails#normalize}) — 챌린지 키, 검증 마커 키, 레이트리밋 카운터 키, 중복 조회가
 * 전부 같은 표준형이라야 한다. 한 곳이라도 원문을 쓰면 {@code Kim@Work.com}으로 발송하고 {@code kim@work.com}으로 확인하는 순간 조용히
 * 실패한다.
 *
 * <p>발송 경로가 {@link LocalAccountRepository}를 읽지만 쓰기는 없고 모듈 간 호출도 없어 트랜잭션 경계를 두지 않는다(전이가 있는 가입 제출은
 * US-1-11이 한 트랜잭션으로 감싼다).
 */
@Service
@RequiredArgsConstructor
public class SignupEmailVerificationService {

  private final SignupEmailVerificationRepository repository;
  private final SignupEmailVerificationCodeIssuer codeIssuer;
  private final EmailVerificationCodeHasher codeHasher;
  private final SignupEmailRateLimiter rateLimiter;
  private final LocalAccountRepository localAccountRepository;
  private final EmailVerificationProperties properties;

  /**
   * 인증번호 발송. <b>재발송 쿨다운 → 이메일·IP 시간당 한도 → 로그인 ID 중복 → 발급·발송 → 챌린지 저장</b> 순으로 진행한다.
   *
   * <p>쿨다운을 한도보다 먼저 보는 것은 의도다 — 60초 안의 재요청(버튼 두 번 누르기)은 어차피 거절되므로 그것으로 시간당 한도까지 깎지 않는다. 둘 다 429라
   * 클라이언트가 보는 응답은 같다.
   *
   * <p><b>중복 판정이 한도보다 뒤인 것이 이 순서의 핵심이다.</b> 앞에 두면 카운터를 하나도 올리지 않고 임의의 주소를 무한히 물어볼 수 있어 가입 여부 열거가
   * 공짜가 되고, 그 판정은 <b>익명 호출자가 유발하는 DB 읽기</b>이기도 하다. 뒤에 두면 관찰 한 번이 예산 한 칸을 쓴다 — 다만 한도는 비용 상한이지 열거 방어가
   * 아니다(이메일 축은 한 주소당 한 번만 묻는 관찰에 무력하고, IP 축은 위조 가능하다).
   *
   * <p><b>이미 가입된 주소면 메일을 보내지 않는다</b>(409). 감추는 쪽이 열거에는 안전하지만, 그러면 <b>남의 메일함으로 인증번호가 실제로 날아간다</b> —
   * SMS와 달리 이메일 채널은 그 발송 자체가 피해이고, 사용자는 가입 제출까지 가서야 중복을 알게 된다. 열거 수용은 스펙 §개요 「알려진 제약」에 적혀 있다.
   *
   * <p>발급 포트가 인증번호 생성과 메일 발송을 함께 맡고, <b>정상 반환한 뒤에만</b> 해시해 챌린지를 저장한다 — 발송 실패는 502가 전파되고 Redis에는
   * 아무것도 남지 않는다(챌린지를 먼저 쓰면 메일을 못 받은 사용자가 만료를 기다려야 하고 재발송 쿨다운만 소모한다).
   *
   * @param clientIp 호출자 IP(프레젠테이션 계층이 X-Forwarded-For·remote address에서 추출해 넘긴다)
   */
  public SignupEmailVerificationCodeResponse sendCode(String email, String clientIp) {
    String normalized = Emails.normalize(email);
    Instant now = Instant.now();
    Optional<SignupEmailVerification> existing = repository.findChallenge(normalized);
    if (existing.isPresent()
        && existing
            .get()
            .getIssuedAt()
            .plusSeconds(properties.getResendIntervalSeconds())
            .isAfter(now)) {
      throw new EmailRateLimitException();
    }
    rateLimiter.recordAttempt(normalized, clientIp);
    if (localAccountRepository.existsByEmail(normalized)) {
      throw new EmailAlreadyRegisteredException(); // 메일 미발송 · 챌린지 미저장
    }
    String code = codeIssuer.issue(normalized); // 발송 실패 시 502 전파 — 챌린지 미저장
    repository.saveChallenge(
        SignupEmailVerification.issue(
            normalized, codeHasher.hash(code), now, properties.getCodeTtlSeconds()));
    return new SignupEmailVerificationCodeResponse(
        Masks.maskEmail(normalized), properties.getCodeTtlSeconds());
  }

  /**
   * 인증번호 확인. 챌린지 부재(미발송·만료·이미 검증·발송 실패로 미저장)면 올릴 {@code attempts} 레코드가 없어 즉시 422다. 불일치면 {@code
   * attempts}를 올려 저장한 뒤 422이고, 이미 상한에 도달한 챌린지는 해시를 대조하지 않고 422로 끊는다.
   *
   * <p>일치하면 검증 마커(TTL 30분)를 남기고 코드 챌린지를 삭제한다 — 같은 인증번호를 다시 제출해도 챌린지가 없어 실패한다(1회용). 마커는 가입 제출이 소비한다.
   *
   * <p><b>여기서는 중복을 다시 보지 않는다</b> — 판정은 발송이 이미 했고, 이 단계에서 또 갈라 봐야 새로 알려 주는 사실이 없다. 발송~확인 사이에 남이 그
   * 주소로 가입했더라도 그 사실은 가입 제출의 중복 게이트가 잡는다.
   */
  public SignupEmailVerifyResponse verify(String email, String code) {
    String normalized = Emails.normalize(email);
    SignupEmailVerification challenge =
        repository.findChallenge(normalized).orElseThrow(EmailVerificationFailedException::new);
    if (challenge.isExpired(Instant.now())) {
      repository.deleteChallenge(normalized);
      throw new EmailVerificationFailedException();
    }
    if (challenge.getAttempts() >= properties.getMaxAttempts()) {
      throw new EmailVerificationFailedException(); // 정식 사용자 트랙(§4)의 429와 달리 422로 통일한다
    }
    if (!challenge.matches(codeHasher.hash(code))) {
      repository.saveChallenge(challenge.incrementAttempt());
      throw new EmailVerificationFailedException();
    }
    repository.markVerified(normalized, properties.getVerifiedTtlSeconds());
    repository.deleteChallenge(normalized);
    return new SignupEmailVerifyResponse(Masks.maskEmail(normalized), true);
  }

  /**
   * 가입 제출(US-1-11) 선행 게이트 — 제출된 이메일의 검증 마커가 살아 있어야 한다. 없으면(미인증·만료·이미 소비) 422 {@link
   * EmailNotVerifiedException}이고 <b>계정 생성도 연동도 하지 않는다</b>.
   *
   * <p>연락처 게이트({@code SignupPhoneVerificationService#assertVerified})와 <b>서로 순서가 무관하다</b> — 둘 다
   * 부수효과 없는 조회이고 둘 다 422이며 어느 쪽도 계정 존재를 드러내지 않는다. 갈리는 것은 "둘 다 미인증"일 때 나가는 코드 하나뿐이라, 기존 계약을 유지하는 쪽으로
   * 연락처를 먼저 본다.
   *
   * <p>호출자가 이미 정규화한 값을 넘기지 않을 수 있으므로 여기서 한 번 접는다({@link Emails#normalize}는 멱등) — 마커를 남긴 {@link
   * #verify}와 <b>같은 경계에서 같은 규칙으로</b> 접어야 표기 차이로 조용히 어긋나지 않는다.
   */
  public void assertVerified(String email) {
    if (!repository.isVerified(Emails.normalize(email))) {
      throw new EmailNotVerifiedException();
    }
  }

  /**
   * 가입 성공 후 검증 마커 소비(삭제) — 마커 하나로 가입을 두 번 태우지 못하게 한다(1회용).
   *
   * <p><b>가입 트랜잭션이 커밋된 뒤에 부른다.</b> Redis 삭제는 MySQL 트랜잭션과 함께 롤백되지 않으므로, 앞에서 지우면 이후 단계가 실패했을 때 계정도 없고
   * 마커도 없어 사용자가 <b>인증 두 개를 처음부터 다시</b> 해야 한다(연락처 마커와 같은 이유·같은 시점이다).
   */
  public void consumeVerification(String email) {
    repository.deleteVerified(Emails.normalize(email));
  }
}
