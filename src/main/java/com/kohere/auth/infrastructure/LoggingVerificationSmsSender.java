package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.VerificationSmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 크리덴셜 없는 환경(로컬/dev/test)용 {@link VerificationSmsSender} 폴백. 실 발송 대신 인증번호를 로그로만 남겨 외부 의존 없이 전체 가입
 * 흐름이 동작하게 한다. {@code app.twilio.enabled=true}면 {@link TwilioVerificationSmsSender}가 대신
 * 등록된다(ADR-0034). 절대 운영에서 쓰지 않는다(로그에 인증번호 노출).
 */
public class LoggingVerificationSmsSender implements VerificationSmsSender {

  private static final Logger log = LoggerFactory.getLogger(LoggingVerificationSmsSender.class);

  @Override
  public void send(String toPhoneNumber, String code) {
    log.warn(
        "[DEV SMS] Twilio 비활성 — 연락처 {} 인증번호 [{}] (실발송 안 함). 실연동은 app.twilio.enabled=true.",
        mask(toPhoneNumber),
        code);
  }

  private static String mask(String phone) {
    if (phone == null || phone.length() < 4) {
      return "***";
    }
    return "***" + phone.substring(phone.length() - 4);
  }
}
