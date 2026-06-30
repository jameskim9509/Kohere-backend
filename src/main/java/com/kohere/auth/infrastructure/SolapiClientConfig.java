package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.VerificationSmsSender;
import com.solapi.sdk.SolapiClient;
import com.solapi.sdk.message.service.DefaultMessageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 연락처 SMS 발송 포트({@link VerificationSmsSender}) 빈 구성. {@code app.solapi.enabled=true}면 SOLAPI 실
 * 어댑터({@link DefaultMessageService} + {@link SolapiVerificationSmsSender})를, 아니면 로깅 폴백을
 * 등록한다(@ConditionalOnMissingBean — 실 어댑터가 먼저 선언되어 활성 시 폴백을 건너뛴다). 크리덴셜·발신번호 확정 후 활성화한다(ADR-0034).
 */
@Configuration
public class SolapiClientConfig {

  @Bean
  @ConditionalOnProperty(prefix = "app.solapi", name = "enabled", havingValue = "true")
  VerificationSmsSender solapiVerificationSmsSender(SolapiProperties properties) {
    DefaultMessageService messageService =
        SolapiClient.INSTANCE.createInstance(properties.getApiKey(), properties.getApiSecret());
    return new SolapiVerificationSmsSender(messageService, properties.getFrom());
  }

  @Bean
  @ConditionalOnMissingBean(VerificationSmsSender.class)
  VerificationSmsSender loggingVerificationSmsSender() {
    return new LoggingVerificationSmsSender();
  }
}
