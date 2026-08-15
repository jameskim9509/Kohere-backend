package com.kohere.listing.infrastructure.external.kakao;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 카카오 로컬 API의 인증정보와 네트워크 정책을 바인딩한다(ADR-0044).
 *
 * <p>REST API 키는 환경변수에서만 주입하며 코드·로그·응답에 노출하지 않는다. <b>네이버 지역 검색({@code app.naver.search})·NCP
 * Geocoding({@code app.naver.geocode})과 다른 콘솔에서 발급한 값</b>이라 서로 대체할 수 없고, 저 둘과 달리 ID/Secret 쌍이 아니라
 * <b>키 하나</b>다.
 *
 * <p>read 타임아웃을 둘로 나눈다 — 역 검색은 임대인이 화면에서 기다리는 호출이지만, 등록 중 대학 파생은 폼을 다 채우고 제출을 누른 뒤의 마지막 단계라 외부 지연을
 * 더 짧게 잘라야 한다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.kakao.local")
public class KakaoLocalProperties {

  /** 카카오 로컬 API의 origin. */
  private String baseUrl = "https://dapi.kakao.com";

  /** 카카오 개발자 콘솔에서 발급한 REST API 키. */
  private String restApiKey;

  /** 외부 연결 수립을 기다릴 최대 시간(ms). */
  private long connectTimeoutMillis = 3000;

  /** 역 검색이 응답 본문을 기다릴 최대 시간(ms). */
  private long readTimeoutMillis = 5000;

  /** 등록 중 인근 대학 파생이 응답 본문을 기다릴 최대 시간(ms). */
  private long registerReadTimeoutMillis = 2000;

  /**
   * 실제 호출에 사용할 수 있는 키가 있는지 확인한다.
   *
   * <p>키가 없어도 애플리케이션의 다른 기능은 개발·기동할 수 있게 두고, 역 검색은 외부 연동 실패(502)로, 등록의 대학 파생은 빈 집합으로 처리한다(장소 검색·주소
   * 검색·SMS·사업자번호 검증과 같은 정책).
   *
   * @return REST API 키가 공백이 아닐 때 {@code true}
   */
  public boolean isConfigured() {
    return StringUtils.hasText(restApiKey);
  }
}
