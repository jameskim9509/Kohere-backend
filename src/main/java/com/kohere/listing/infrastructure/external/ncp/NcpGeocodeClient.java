package com.kohere.listing.infrastructure.external.ncp;

import com.kohere.listing.domain.address.AddressSearchClient;
import com.kohere.listing.domain.address.AddressSearchResult;
import com.kohere.listing.domain.address.AddressSearchUpstreamException;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * NCP Maps Geocoding API를 {@link AddressSearchClient} 포트에 연결하는 HTTP 어댑터다(ADR-0042).
 *
 * <p>요청 파라미터는 NCP 계약과 Kohere UX에 맞춰 {@code count=5}, {@code page=1}, {@code language=kor}으로 서버가
 * 고정한다. 프론트에는 제공자 필드명을 노출하지 않고 WGS84 십진수 좌표({@code x}→{@code lng}, {@code y}→{@code lat})로 변환해
 * 전달한다. 외부 HTTP 오류, 인증정보 누락, 본문·좌표 계약 위반은 모두 {@link AddressSearchUpstreamException}으로 통일한다.
 */
@Component
public class NcpGeocodeClient implements AddressSearchClient {

  private static final String GEOCODE_PATH = "/map-geocode/v2/geocode";
  private static final String API_KEY_ID_HEADER = "x-ncp-apigw-api-key-id";
  private static final String API_KEY_HEADER = "x-ncp-apigw-api-key";
  private static final int MAX_RESULTS = 5;
  private static final int FIRST_PAGE = 1;
  private static final String KOREAN_LANGUAGE = "kor";
  private static final String STATUS_OK = "OK";

  private final NcpGeocodeProperties properties;
  private final RestClient restClient;

  /**
   * 인증정보 설정과 지오코딩 전용 HTTP 클라이언트를 명시적으로 주입받는다.
   *
   * @param properties URL·인증정보·타임아웃 설정
   * @param restClient NCP Geocoding 전용 HTTP 클라이언트
   */
  public NcpGeocodeClient(
      NcpGeocodeProperties properties, @Qualifier("ncpGeocodeRestClient") RestClient restClient) {
    this.properties = properties;
    this.restClient = restClient;
  }

  /**
   * 지오코딩 결과를 최대 5개의 제공자 독립 주소 값으로 변환한다.
   *
   * <p>정상적으로 결과가 없는 {@code addresses=[]}는 빈 목록으로 반환하지만, 본문 또는 {@code addresses} 자체가 누락된 응답과 {@code
   * status}가 {@code OK}가 아닌 응답은 정상 빈 결과와 구분해 상류 계약 위반으로 처리한다.
   *
   * <p>도로명이 부여되지 않은 후보는 버린다 — 매물 등록이 받는 것은 도로명 주소이고, 고를 수 없는 후보를 폼에 띄울 이유가 없다.
   *
   * @param query 검증과 trim이 끝난 검색어
   * @return NCP 순서를 유지한 도로명 주소 후보 최대 5개
   */
  @Override
  public List<AddressSearchResult> search(String query) {
    requireConfiguredCredentials();

    NcpGeocodeResponse response;
    try {
      response =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path(GEOCODE_PATH)
                          .queryParam("query", query)
                          .queryParam("count", MAX_RESULTS)
                          .queryParam("page", FIRST_PAGE)
                          .queryParam("language", KOREAN_LANGUAGE)
                          .build())
              .header(API_KEY_ID_HEADER, properties.getClientId())
              .header(API_KEY_HEADER, properties.getClientSecret())
              .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
              .retrieve()
              .body(NcpGeocodeResponse.class);
    } catch (RestClientException e) {
      throw new AddressSearchUpstreamException(e);
    }

    if (response == null || response.addresses() == null) {
      throw malformedResponse("NCP geocode response missing addresses");
    }
    if (!STATUS_OK.equals(response.status())) {
      throw malformedResponse("NCP geocode response status is not OK: " + response.status());
    }

    try {
      return response.addresses().stream()
          .filter(NcpGeocodeClient::hasRoadAddress)
          .limit(MAX_RESULTS)
          .map(NcpGeocodeClient::toAddressSearchResult)
          .toList();
    } catch (IllegalArgumentException e) {
      throw new AddressSearchUpstreamException(e);
    }
  }

  /** 로컬에서 키 없이 다른 기능을 실행하는 것은 허용하되, 실제 주소 검색 요청을 무인증으로 NCP에 보내지는 않는다. */
  private void requireConfiguredCredentials() {
    if (!properties.isConfigured()) {
      throw malformedResponse("NCP geocode credentials are not configured");
    }
  }

  /** 도로명이 비어 있는 후보는 등록에 쓸 수 없어 응답에서 제외한다. */
  private static boolean hasRoadAddress(NcpGeocodeResponse.Address address) {
    return address != null && StringUtils.hasText(address.roadAddress());
  }

  /**
   * NCP 원본 항목의 좌표를 검증한 뒤 프론트 친화적인 WGS84 값으로 변환한다.
   *
   * <p>지번·영문 주소는 보조 표시용이라 비어 있어도 후보를 버리지 않고 빈 문자열로 채운다. 반면 좌표는 등록에 그대로 실려 저장되므로 하나라도 어긋나면 후보를 내보내지
   * 않는다.
   *
   * @param address NCP 원본 주소 항목
   * @return 제공자 필드명이 제거된 내부 주소 검색 결과
   */
  private static AddressSearchResult toAddressSearchResult(NcpGeocodeResponse.Address address) {
    if (!StringUtils.hasText(address.x()) || !StringUtils.hasText(address.y())) {
      throw new IllegalArgumentException("NCP geocode address missing coordinates");
    }

    double lng = parseCoordinate(address.x());
    double lat = parseCoordinate(address.y());
    if (!isLatitude(lat) || !isLongitude(lng)) {
      throw new IllegalArgumentException("NCP geocode address has invalid WGS84 coordinates");
    }

    return new AddressSearchResult(
        address.roadAddress(),
        Objects.requireNonNullElse(address.jibunAddress(), ""),
        Objects.requireNonNullElse(address.englishAddress(), ""),
        lat,
        lng);
  }

  /** NCP가 문자열로 주는 십진수 좌표를 숫자로 되돌린다. 배율 변환은 없다(네이버 지역 검색과 다른 점). */
  private static double parseCoordinate(String value) {
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("NCP geocode coordinate is not a number: " + value, e);
    }
  }

  /** 위도가 WGS84 유효 범위 안에 있는지 확인한다. */
  private static boolean isLatitude(double value) {
    return value >= -90.0 && value <= 90.0;
  }

  /** 경도가 WGS84 유효 범위 안에 있는지 확인한다. */
  private static boolean isLongitude(double value) {
    return value >= -180.0 && value <= 180.0;
  }

  /** 외부 응답·설정 계약 위반을 일관된 502 예외로 감싼다. */
  private static AddressSearchUpstreamException malformedResponse(String message) {
    return new AddressSearchUpstreamException(new IllegalStateException(message));
  }
}
