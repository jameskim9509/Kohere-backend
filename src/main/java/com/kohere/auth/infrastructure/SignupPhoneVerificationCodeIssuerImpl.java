package com.kohere.auth.infrastructure;

import com.kohere.auth.application.PhoneVerificationProperties;
import com.kohere.auth.domain.SignupPhoneVerificationCodeIssuer;
import com.kohere.auth.domain.VerificationSmsSender;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 가입용 연락처 인증번호 발급 구현 — {@link SecureRandom}으로 정책 자릿수({@code app.phone.code-length})만큼 생성하고 온보딩용과
 * <b>같은</b> SMS 포트({@link VerificationSmsSender})로 동기 발송한다. 발송 실패({@code SmsDispatchException},
 * 502)는 그대로 전파되어 호출자가 챌린지를 저장하지 않는다(send-then-store).
 *
 * <p>{@link PhoneVerificationCodeIssuerImpl}과 생성 로직이 같은데도 위임하지 않는 이유는 {@link
 * SignupPhoneVerificationCodeIssuer}에 적었다 — 그 포트는 {@code userId}를 요구하고, local·dev에서는 그 자리에 앱스토어 심사용
 * 고정 인증번호 우회({@link FixedCodePhoneVerificationCodeIssuer})가 {@code @Primary}로 들어와 있다. <b>그 우회는 웹 가입
 * 경로에 적용되지 않으며 적용해서도 안 된다</b>({@code userId} + Google 소셜 계정 기준이라 가입 전 단계에는 판정 근거가 없다). 그래서 웹 가입은
 * 프로파일과 무관하게 <b>항상 실제 발급·발송</b>을 하고, 로컬 수동 테스트는 {@link LoggingVerificationSmsSender}가 인증번호를 콘솔에 찍어
 * 성립시킨다.
 *
 * <p>공유해야 할 것은 실제로 공유한다 — 자릿수 정책은 {@link PhoneVerificationProperties}, 발송은 {@link
 * VerificationSmsSender}(SOLAPI 또는 로깅 폴백)로 온보딩용과 같은 빈을 쓴다. 중복은 난수 자릿수 루프뿐이다.
 */
@Component
@RequiredArgsConstructor
class SignupPhoneVerificationCodeIssuerImpl implements SignupPhoneVerificationCodeIssuer {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final VerificationSmsSender smsSender;
  private final PhoneVerificationProperties properties;

  @Override
  public String issue(String phoneNumber) {
    String code = generateNumericCode(properties.getCodeLength());
    smsSender.send(phoneNumber, code); // 발송 실패 시 SmsDispatchException(502) — 챌린지 미저장
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
