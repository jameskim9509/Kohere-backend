package com.kohere.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.kohere.auth.application.EmailVerificationProperties;
import com.kohere.auth.application.MailSenderProperties;
import com.kohere.auth.domain.VerificationEmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 발송 채널 토글({@code app.mail.enabled}) 배선 테스트.
 *
 * <p><b>왜 이 테스트가 필요한가</b> — 이 토글이 무효가 되는 회귀는 <b>아무것도 깨뜨리지 않는다</b>. {@link
 * SmtpVerificationEmailSender}에 {@code @Component}가 다시 붙으면 컴포넌트 스캔이 그 빈을 항상 등록해 {@link
 * ConditionalOnMissingBean} 폴백이 영원히 걸리지 않는데, 기능은 그대로 동작하므로 빌드·통합 테스트가 전부 초록이다. 그 상태의 증상은 "로컬에서 여전히
 * 메일 서버를 요구한다"뿐이라 한참 뒤에야 드러난다. <b>빈 타입을 직접 단정하는 것이 유일한 방어</b>다.
 *
 * <p>Spring 컨텍스트를 통째로 띄우지 않고 {@link ApplicationContextRunner}로 이 구성만 돌린다 — Testcontainers·DB 없이 수
 * ms에 끝나고, 판정 대상이 조건부 빈 등록 하나뿐이라 그 이상은 필요 없다.
 */
class MailSenderConfigTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of())
          .withUserConfiguration(StubMailInfrastructure.class, MailSenderConfig.class);

  @Test
  @DisplayName("app.mail.enabled=true 면 SMTP 어댑터가 등록된다")
  void enabled_registersSmtpSender() {
    runner
        .withPropertyValues("app.mail.enabled=true")
        .run(
            context ->
                assertThat(context.getBean(VerificationEmailSender.class))
                    .isInstanceOf(SmtpVerificationEmailSender.class));
  }

  @Test
  @DisplayName("app.mail.enabled=false 면 로깅 폴백이 등록된다")
  void disabled_registersLoggingFallback() {
    runner
        .withPropertyValues("app.mail.enabled=false")
        .run(
            context ->
                assertThat(context.getBean(VerificationEmailSender.class))
                    .isInstanceOf(LoggingVerificationEmailSender.class));
  }

  @Test
  @DisplayName("값이 아예 없으면 로깅 폴백이다 — 미설정이 실발송으로 흐르지 않는다")
  void absent_registersLoggingFallback() {
    runner.run(
        context ->
            assertThat(context.getBean(VerificationEmailSender.class))
                .isInstanceOf(LoggingVerificationEmailSender.class));
  }

  /** SMTP 어댑터가 요구하는 협력자만 채운다 — 실제 메일 연결은 만들지 않는다. */
  @Configuration(proxyBeanMethods = false)
  static class StubMailInfrastructure {

    @Bean
    JavaMailSender javaMailSender() {
      return Mockito.mock(JavaMailSender.class);
    }

    @Bean
    EmailVerificationProperties emailVerificationProperties() {
      return new EmailVerificationProperties();
    }

    @Bean
    MailSenderProperties mailSenderProperties() {
      return new MailSenderProperties();
    }
  }
}
