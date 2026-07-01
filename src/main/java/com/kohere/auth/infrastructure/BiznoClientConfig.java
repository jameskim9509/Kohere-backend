package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.BusinessRegistryVerifier;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
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
    RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
    applyBiznoMessageConverters(builder);
    return new BiznoBusinessRegistryVerifier(properties, builder.build());
  }

  /**
   * 비즈노 fapi는 JSON 본문을 {@code application/json}이 아닌 Content-Type(예 {@code text/html})으로 반환할 수 있어,
   * 기본 Jackson 컨버터(=application/json 전용)로는 {@code body(BiznoApiResponse.class)}가 파싱에 실패한다. 선언된
   * Content-Type과 무관하게 JSON으로 역직렬화하도록 Jackson 컨버터가 모든 미디어 타입({@link MediaType#ALL})을 처리하게 교체한다.
   * Jackson이 바이트 스트림에서 인코딩(UTF-8)을 자체 감지하므로 한글 상태값({@code bstt})도 안전하다. 어댑터 실제 파싱을 검증하는 테스트와 이 설정을
   * 공유한다.
   */
  static void applyBiznoMessageConverters(RestClient.Builder builder) {
    MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
    jsonConverter.setSupportedMediaTypes(List.of(MediaType.ALL));
    builder.messageConverters(
        converters -> {
          converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
          converters.add(jsonConverter);
        });
  }

  @Bean
  @ConditionalOnMissingBean(BusinessRegistryVerifier.class)
  BusinessRegistryVerifier stubBusinessRegistryVerifier() {
    return new StubBusinessRegistryVerifier();
  }
}
