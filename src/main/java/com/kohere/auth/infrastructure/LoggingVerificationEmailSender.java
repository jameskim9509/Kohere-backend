package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.VerificationEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SMTP를 쓰지 않는 환경(로컬/테스트)용 {@link VerificationEmailSender} 폴백. 실 발송 대신 인증번호를 로그로만 남겨 <b>메일 서버 없이</b>
 * 전체 인증 흐름이 동작하게 한다. {@code app.mail.enabled=true}면 {@link SmtpVerificationEmailSender}가 대신
 * 등록된다({@link MailSenderConfig}). {@link LoggingVerificationSmsSender}의 이메일 채널 대응물이며 <b>절대 운영에서 쓰지
 * 않는다</b>(로그에 인증번호 노출).
 *
 * <p>이 폴백이 생기기 전에는 로컬에서 이메일 인증을 보려면 MailHog 컨테이너가 필수였고, 없으면 발송이 502로 실패했다. SMS는 처음부터 로그 폴백이 있어 콘솔만
 * 보면 됐는데 이메일만 달랐던 것이라, 두 채널의 로컬 경험을 맞춘다.
 *
 * <p>이메일은 마스킹해 남긴다 — 인증번호는 5분·1회용이지만 수신 주소는 그렇지 않다. 여러 주소를 동시에 테스트할 때 구분이 필요하므로 도메인과 로컬파트 앞 두 글자는
 * 남긴다({@code Masks}와 같은 규칙이되, 그 유틸이 {@code application} 패키지 package-private이라 여기서는 같은 모양으로 직접 만든다).
 */
public class LoggingVerificationEmailSender implements VerificationEmailSender {

  private static final Logger log = LoggerFactory.getLogger(LoggingVerificationEmailSender.class);

  @Override
  public void send(String to, String code) {
    log.warn(
        "[DEV MAIL] SMTP 비활성 — 이메일 {} 인증번호 [{}] (실발송 안 함). 실연동은 app.mail.enabled=true.",
        maskEmail(to),
        code);
  }

  private static String maskEmail(String email) {
    if (email == null) {
      return null;
    }
    int at = email.indexOf('@');
    if (at <= 0) {
      return "***";
    }
    String local = email.substring(0, at);
    String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
    return visible + "***" + email.substring(at);
  }
}
