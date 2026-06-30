package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.VerificationSmsSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 연락처 SMS 발송 포트({@link VerificationSmsSender}) 빈 구성. {@code app.twilio.enabled=true}면 Twilio 실
 * 어댑터를, 아니면 로깅 폴백을 등록한다(@ConditionalOnMissingBean — Twilio 빈이 먼저 선언되어 활성 시 폴백을 건너뛴다). 크리덴셜·계약 확정 후
 * 활성화한다(ADR-0034).
 */
@Configuration
public class TwilioClientConfig {

  @Bean
  @ConditionalOnProperty(prefix = "app.twilio", name = "enabled", havingValue = "true")
  VerificationSmsSender twilioVerificationSmsSender(TwilioProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout((int) properties.getConnectTimeoutMillis());
    requestFactory.setReadTimeout((int) properties.getReadTimeoutMillis());
    RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
    return new TwilioVerificationSmsSender(properties, restClient);
  }

  @Bean
  @ConditionalOnMissingBean(VerificationSmsSender.class)
  VerificationSmsSender loggingVerificationSmsSender() {
    return new LoggingVerificationSmsSender();
  }
}
