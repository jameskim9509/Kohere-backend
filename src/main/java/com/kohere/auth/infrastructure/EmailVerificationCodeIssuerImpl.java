package com.kohere.auth.infrastructure;

import com.kohere.auth.application.EmailVerificationProperties;
import com.kohere.auth.domain.EmailVerificationCodeIssuer;
import com.kohere.auth.domain.VerificationEmailSender;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 기본 인증번호 발급 구현 — {@link SecureRandom}으로 정책 자릿수만큼 생성하고 SMTP 포트로 동기 발송한다. 발송 실패({@code
 * EmailDispatchException}, 502)는 그대로 전파되어 호출자가 챌린지를 저장하지 않는다(send-then-store).
 *
 * <p>발송 어댑터({@link SmtpVerificationEmailSender})와 같은 계층에 두어 {@code
 * FixedCodeEmailVerificationCodeIssuer}가 concrete 타입으로 주입받아 위임할 수 있게 한다({@link
 * TestLoginOidcTokenVerifier} → {@link OidcTokenVerifierImpl}과 동일 구조). 우회 빈이 없는 환경(prod 등)에서는 이 구현이
 * 유일한 발급기다.
 */
@Component
@RequiredArgsConstructor
class EmailVerificationCodeIssuerImpl implements EmailVerificationCodeIssuer {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final VerificationEmailSender emailSender;
  private final EmailVerificationProperties properties;

  @Override
  public String issue(long userId, String email) {
    String code = generateNumericCode(properties.getCodeLength());
    emailSender.send(email, code); // 발송 실패 시 EmailDispatchException(502) — 챌린지 미저장
    return code;
  }

  private static String generateNumericCode(int length) {
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(SECURE_RANDOM.nextInt(10));
    }
    return sb.toString();
  }
}
