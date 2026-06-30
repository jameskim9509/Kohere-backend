package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.BusinessRegistryVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 사업자등록번호 검증 포트({@link BusinessRegistryVerifier}) 빈 구성. {@code app.bizno.enabled=true}면 비즈노 실 어댑터를,
 * 아니면 스텁 폴백을 등록한다(@ConditionalOnMissingBean). 크리덴셜·API 계약 확정 후 활성화한다(ADR-0033).
 */
@Configuration
public class BiznoClientConfig {

  @Bean
  @ConditionalOnProperty(prefix = "app.bizno", name = "enabled", havingValue = "true")
  BusinessRegistryVerifier biznoBusinessRegistryVerifier(BiznoProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout((int) properties.getConnectTimeoutMillis());
    requestFactory.setReadTimeout((int) properties.getReadTimeoutMillis());
    RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
    return new BiznoBusinessRegistryVerifier(properties, restClient);
  }

  @Bean
  @ConditionalOnMissingBean(BusinessRegistryVerifier.class)
  BusinessRegistryVerifier stubBusinessRegistryVerifier() {
    return new StubBusinessRegistryVerifier();
  }
}
