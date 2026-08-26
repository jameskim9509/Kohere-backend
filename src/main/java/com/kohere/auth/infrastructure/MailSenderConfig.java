package com.kohere.auth.infrastructure;

import com.kohere.auth.application.EmailVerificationProperties;
import com.kohere.auth.application.MailSenderProperties;
import com.kohere.auth.domain.VerificationEmailSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 인증번호 메일 발송 포트({@link VerificationEmailSender}) 빈 구성. {@code app.mail.enabled=true}면 SMTP 실
 * 어댑터({@link SmtpVerificationEmailSender})를, 아니면 로깅 폴백({@link LoggingVerificationEmailSender})을
 * 등록한다(@ConditionalOnMissingBean — 실 어댑터가 먼저 선언되어 활성 시 폴백을 건너뛴다). {@link SolapiClientConfig}(SMS)와
 * 같은 구조다.
 *
 * <p><b>{@link SmtpVerificationEmailSender}에서 {@code @Component}를 뗀 것이 이 배선의 전제다.</b> 컴포넌트 스캔으로
 * SMTP 빈이 항상 등록되면 {@code @ConditionalOnMissingBean} 폴백이 영원히 걸리지 않아 <b>토글이 조용히 무효</b>가 되고, 그 상태로도
 * 빌드·테스트는 전부 초록이다({@link SolapiVerificationSmsSender}가 {@code @Component}가 아닌 것도 같은 이유다).
 *
 * <p>이 토글은 포트 전체에 걸리므로 <b>정식 사용자 이메일 인증과 가입용 이메일 인증이 같은 채널을 쓴다</b> — SMS 토글 하나가 온보딩·가입·이메일 찾기 세 채널을
 * 함께 덮는 것과 같은 모양이다. 반대로 비밀번호 재설정 링크({@code PasswordResetLinkEmailSender})는 <b>별개 포트라 항상 SMTP</b>다:
 * 일회용 토큰 원문이 로그에 남으면 그 한 번으로 계정이 넘어가므로 인증번호와 위험 등급이 다르다.
 */
@Configuration
public class MailSenderConfig {

  @Bean
  @ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
  VerificationEmailSender smtpVerificationEmailSender(
      JavaMailSender mailSender,
      EmailVerificationProperties properties,
      MailSenderProperties mailSenderProperties) {
    return new SmtpVerificationEmailSender(mailSender, properties, mailSenderProperties);
  }

  @Bean
  @ConditionalOnMissingBean(VerificationEmailSender.class)
  VerificationEmailSender loggingVerificationEmailSender() {
    return new LoggingVerificationEmailSender();
  }
}
