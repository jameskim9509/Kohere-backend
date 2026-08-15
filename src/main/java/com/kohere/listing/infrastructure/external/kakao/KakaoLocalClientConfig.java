package com.kohere.listing.infrastructure.external.kakao;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 카카오 로컬 API 전용 HTTP 클라이언트의 네트워크 경계를 구성한다(ADR-0044).
 *
 * <p>다른 외부 연동과 타임아웃이나 인증 정책이 섞이지 않도록 전용 {@link RestClient} 빈을 둔다(네이버 지역 검색·NCP Geocoding과 같은 구조).
 *
 * <p><b>클라이언트를 둘로 나눈다.</b> 역 검색은 임대인이 화면에서 기다리는 호출이지만, 등록 중 인근 대학 파생은 폼을 다 채우고 제출을 누른 뒤의 마지막 단계다 —
 * 거기서 외부가 늘어지면 등록 응답이 그만큼 늦어진다. 둘의 read 타임아웃을 갈라 등록 지연 상한을 따로 잡는다.
 */
@Configuration
public class KakaoLocalClientConfig {

  /**
   * 역 검색이 쓰는 HTTP 클라이언트를 만든다.
   *
   * @param properties 환경별 카카오 로컬 설정
   * @return 역 검색 호출에 사용하는 {@link RestClient}
   */
  @Bean
  @Qualifier("kakaoLocalRestClient")
  RestClient kakaoLocalRestClient(KakaoLocalProperties properties) {
    return build(properties, properties.getReadTimeoutMillis());
  }

  /**
   * 등록 중 인근 대학 파생이 쓰는 HTTP 클라이언트를 만든다. read 타임아웃만 더 짧다.
   *
   * @param properties 환경별 카카오 로컬 설정
   * @return 등록 경로 전용 {@link RestClient}
   */
  @Bean
  @Qualifier("kakaoLocalRegisterRestClient")
  RestClient kakaoLocalRegisterRestClient(KakaoLocalProperties properties) {
    return build(properties, properties.getRegisterReadTimeoutMillis());
  }

  private static RestClient build(KakaoLocalProperties properties, long readTimeoutMillis) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout((int) properties.getConnectTimeoutMillis());
    requestFactory.setReadTimeout((int) readTimeoutMillis);
    return RestClient.builder()
        .baseUrl(properties.getBaseUrl())
        .requestFactory(requestFactory)
        .build();
  }
}
