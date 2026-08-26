package com.kohere.auth.infrastructure;

import com.kohere.auth.application.EmailVerificationProperties;
import com.kohere.auth.domain.SignupEmailVerificationCodeIssuer;
import com.kohere.auth.domain.VerificationEmailSender;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 가입용 이메일 인증번호 발급 구현 — {@link SecureRandom}으로 정책 자릿수({@code app.email.code-length})만큼 생성하고 정식 사용자용과
 * <b>같은</b> 메일 포트({@link VerificationEmailSender})로 동기 발송한다. 발송 실패({@code EmailDispatchException},
 * 502)는 그대로 전파되어 호출자가 챌린지를 저장하지 않는다(send-then-store).
 *
 * <p>{@link EmailVerificationCodeIssuerImpl}과 생성 로직이 같은데도 위임하지 않는 이유는 {@link
 * SignupEmailVerificationCodeIssuer}에 적었다 — 그 포트는 {@code userId}를 요구하고, local·dev에서는 그 자리에 앱스토어 심사용
 * 고정 인증번호 우회({@link FixedCodeEmailVerificationCodeIssuer})가 {@code @Primary}로 들어와 있다. <b>그 우회는 웹 가입
 * 경로에 적용되지 않으며 적용해서도 안 된다</b>({@code userId}로 소셜 계정을 되짚어 판정하므로 가입 전 단계에는 근거가 없다). 그래서 웹 가입은 프로파일과
 * 무관하게 <b>항상 실제 발급</b>을 하고, 로컬 수동 테스트는 {@link LoggingVerificationEmailSender}가 인증번호를 콘솔에 찍어 성립시킨다.
 *
 * <p>공유해야 할 것은 실제로 공유한다 — 자릿수 정책은 {@link EmailVerificationProperties}, 발송은 {@link
 * VerificationEmailSender}(SMTP 또는 로깅 폴백)로 정식 사용자용과 같은 빈을 쓴다. 중복은 난수 자릿수 루프뿐이다.
 */
@Component
@RequiredArgsConstructor
class SignupEmailVerificationCodeIssuerImpl implements SignupEmailVerificationCodeIssuer {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final VerificationEmailSender emailSender;
  private final EmailVerificationProperties properties;

  @Override
  public String issue(String email) {
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
