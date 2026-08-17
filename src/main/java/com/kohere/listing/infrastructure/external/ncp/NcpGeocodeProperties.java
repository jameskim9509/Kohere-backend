package com.kohere.listing.infrastructure.external.ncp;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * NCP Maps Geocoding API의 인증정보와 네트워크 정책을 바인딩한다(ADR-0042).
 *
 * <p>Client ID와 Secret은 환경변수에서만 주입하며 코드·로그·응답에 노출하지 않는다. <b>네이버 지역 검색({@code app.naver.search})과 다른
 * 콘솔에서 발급한 값</b>이라 서로 대체할 수 없다.
 *
 * <p>base URL을 상수가 아니라 설정으로 두는 이유는 NCP 문서가 호스트를 두 가지로 적고 있기 때문이다 — 기본값은 Maps 개요(신규 Application 등록)
 * 기준이며, 발급 키가 구 도메인({@code naveropenapi.apigw.ntruss.com})만 받으면 설정만 바꿔 대응한다. 경로와 헤더는 양쪽이 같다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.naver.geocode")
public class NcpGeocodeProperties {

  /** NCP API Gateway의 origin. */
  private String baseUrl = "https://maps.apigw.ntruss.com";

  /** NCP 콘솔에서 발급한 Maps Client ID. */
  private String clientId;

  /** NCP 콘솔에서 발급한 Maps Client Secret. */
  private String clientSecret;

  /** 외부 연결 수립을 기다릴 최대 시간(ms). */
  private long connectTimeoutMillis = 3000;

  /** 연결 후 응답 본문을 기다릴 최대 시간(ms). */
  private long readTimeoutMillis = 5000;

  /**
   * 두 인증값이 모두 실제 호출에 사용할 수 있는지 확인한다.
   *
   * <p>키가 없어도 애플리케이션의 다른 기능은 개발·기동할 수 있게 두고, 주소 검색 호출 시 이 값이 {@code false}이면 외부 연동 실패로 처리한다(장소
   * 검색·SMS·사업자번호 검증과 같은 정책).
   *
   * @return Client ID와 Client Secret이 모두 공백이 아닐 때 {@code true}
   */
  public boolean isConfigured() {
    return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
  }
}
