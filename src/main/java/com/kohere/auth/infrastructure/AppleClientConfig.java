package com.kohere.auth.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Apple 연동 전용 {@link RestClient} 빈. connect/read 타임아웃을 짧게 둬(ADR-0031 #5) 로그인·탈퇴 흐름이 Apple 지연에 묶이지
 * 않게 한다. 어댑터({@link AppleAuthClientImpl})에 주입해 테스트에서 교체 가능하게 분리한다.
 */
@Configuration
public class AppleClientConfig {

  @Bean
  RestClient appleRestClient(AppleProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout((int) properties.getConnectTimeoutMillis());
    requestFactory.setReadTimeout((int) properties.getReadTimeoutMillis());
    return RestClient.builder().requestFactory(requestFactory).build();
  }
}
