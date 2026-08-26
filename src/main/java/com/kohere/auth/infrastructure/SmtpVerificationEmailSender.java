package com.kohere.auth.infrastructure;

import com.kohere.auth.application.EmailVerificationProperties;
import com.kohere.auth.application.MailSenderProperties;
import com.kohere.auth.domain.EmailDispatchException;
import com.kohere.auth.domain.VerificationEmailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 인증번호 메일 발송 어댑터(SMTP). {@link JavaMailSender}로 동기 발송하고, provider 장애·타임아웃 등 실패는 {@link
 * EmailDispatchException}(502)으로 변환한다(send-then-store 정책상 발송 성공 시에만 챌린지 확정). 메일 템플릿·다국어는 확인 필요(문서
 * §6).
 *
 * <p><b>{@code @Component}가 없는 것은 의도다</b> — 이 빈은 {@code app.mail.enabled=true}일 때만 {@link
 * MailSenderConfig}가 등록하고, 아니면 {@link LoggingVerificationEmailSender}가 대신 등록된다. 컴포넌트 스캔으로 항상 잡히면
 * {@code @ConditionalOnMissingBean} 폴백이 영원히 걸리지 않아 <b>토글이 조용히 무효</b>가 되며, 그 상태로도 빌드·테스트는 전부
 * 초록이다({@code SolapiVerificationSmsSender}가 {@code @Component}가 아닌 것과 같은 이유).
 *
 * <p>정식 사용자 이메일 인증과 가입용 이메일 인증이 <b>이 한 어댑터를 공유</b>한다 — 발신 주소·템플릿이 갈리면 사용자가 둘 중 하나를 사칭으로 의심한다.
 */
@RequiredArgsConstructor
public class SmtpVerificationEmailSender implements VerificationEmailSender {

  private final JavaMailSender mailSender;
  private final EmailVerificationProperties properties;
  private final MailSenderProperties mailSenderProperties;

  @Override
  public void send(String to, String code) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(mailSenderProperties.getFrom());
    message.setTo(to);
    message.setSubject("[Kohere] 이메일 인증번호");
    message.setText(
        "인증번호: " + code + "\n유효시간: " + (properties.getCodeTtlSeconds() / 60) + "분 이내에 입력해 주세요.");
    try {
      mailSender.send(message);
    } catch (MailException e) {
      throw new EmailDispatchException(e);
    }
  }
}
