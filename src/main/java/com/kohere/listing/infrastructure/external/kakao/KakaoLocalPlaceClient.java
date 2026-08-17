package com.kohere.listing.infrastructure.external.kakao;

import com.kohere.listing.domain.nearby.Coordinate;
import com.kohere.listing.domain.nearby.NearbyPlace;
import com.kohere.listing.domain.nearby.NearbyPlaceSearchClient;
import com.kohere.listing.domain.nearby.NearbyPlaceSearchUpstreamException;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

/**
 * 카카오 로컬 API를 {@link NearbyPlaceSearchClient} 포트에 연결하는 HTTP 어댑터다(ADR-0044).
 *
 * <p>요청 파라미터는 카카오 계약과 Kohere UX에 맞춰 서버가 고정한다 — {@code category_group_code=SW8}(지하철역), 좌표 검색은 반경
 * 2km다. 프론트에는 제공자 필드명을 노출하지 않고 WGS84 십진수 좌표({@code x}→{@code lng}, {@code y}→{@code lat})로 변환해
 * 전달한다. 외부 HTTP 오류, 키 누락, 본문·좌표 계약 위반은 모두 {@link NearbyPlaceSearchUpstreamException}으로 통일한다.
 */
@Component
public class KakaoLocalPlaceClient implements NearbyPlaceSearchClient {

  private static final String KEYWORD_SEARCH_PATH = "/v2/local/search/keyword.json";
  private static final String CATEGORY_SEARCH_PATH = "/v2/local/search/category.json";
  private static final String AUTHORIZATION_PREFIX = "KakaoAK ";

  /** 지하철역 카테고리 그룹 코드. */
  private static final String SUBWAY_STATION_CODE = "SW8";

  private static final int KEYWORD_SEARCH_SIZE = 10;
  private static final int CATEGORY_SEARCH_SIZE = 15;
  private static final int FIRST_PAGE = 1;
  private static final int NEARBY_RADIUS_METERS = 2_000;
  private static final String SORT_DISTANCE = "distance";
  private static final String SORT_ACCURACY = "accuracy";

  private final KakaoLocalProperties properties;
  private final RestClient restClient;

  /**
   * 인증정보 설정과 카카오 로컬 전용 HTTP 클라이언트를 명시적으로 주입받는다.
   *
   * @param properties URL·REST 키·타임아웃 설정
   * @param restClient 카카오 로컬 전용 HTTP 클라이언트
   */
  public KakaoLocalPlaceClient(
      KakaoLocalProperties properties, @Qualifier("kakaoLocalRestClient") RestClient restClient) {
    this.properties = properties;
    this.restClient = restClient;
  }

  /**
   * 역 이름으로 지하철역 후보를 찾는다.
   *
   * <p>좌표를 주면 거리순으로 정렬하고 {@code distance}를 받는다 — 전국에 같은 이름이 있는 역(예 {@code 시청역})을 가려내기 위해서다. 좌표가 없으면
   * 정확도순이며 거리 정보가 없다. {@code radius}는 보내지 않는다 — 이름 검색은 전국을 대상으로 하되 정렬만 좌표를 따른다.
   */
  @Override
  public List<NearbyPlace> searchStationsByKeyword(String keyword, Coordinate origin) {
    List<KakaoLocalSearchResponse.Document> documents =
        get(
            uriBuilder -> {
              uriBuilder
                  .path(KEYWORD_SEARCH_PATH)
                  .queryParam("query", keyword)
                  .queryParam("category_group_code", SUBWAY_STATION_CODE)
                  .queryParam("size", KEYWORD_SEARCH_SIZE)
                  .queryParam("page", FIRST_PAGE)
                  .queryParam("sort", origin == null ? SORT_ACCURACY : SORT_DISTANCE);
              appendOrigin(uriBuilder, origin);
            });
    return toPlaces(documents, SUBWAY_STATION_CODE);
  }

  /** 좌표 주변 반경 2km의 지하철역을 가까운 순으로 찾는다. */
  @Override
  public List<NearbyPlace> searchNearbyStations(Coordinate origin) {
    return toPlaces(categorySearch(SUBWAY_STATION_CODE, origin), SUBWAY_STATION_CODE);
  }

  private List<KakaoLocalSearchResponse.Document> categorySearch(
      String categoryGroupCode, Coordinate origin) {
    return get(
        uriBuilder -> {
          uriBuilder
              .path(CATEGORY_SEARCH_PATH)
              .queryParam("category_group_code", categoryGroupCode)
              .queryParam("radius", NEARBY_RADIUS_METERS)
              .queryParam("size", CATEGORY_SEARCH_SIZE)
              .queryParam("page", FIRST_PAGE)
              .queryParam("sort", SORT_DISTANCE);
          appendOrigin(uriBuilder, origin);
        });
  }

  /**
   * 중심 좌표를 카카오 파라미터로 싣는다.
   *
   * <p><b>{@code x}가 경도, {@code y}가 위도다</b> — 순서가 뒤집히기 가장 쉬운 지점이라 한 곳에서만 만든다.
   */
  private static void appendOrigin(UriBuilder uriBuilder, Coordinate origin) {
    if (origin == null) {
      return;
    }
    uriBuilder.queryParam("x", origin.lng()).queryParam("y", origin.lat());
  }

  /**
   * 카카오 로컬을 호출하고 {@code documents}를 꺼낸다.
   *
   * <p>정상적으로 결과가 없는 {@code documents=[]}는 빈 목록으로 반환하지만, 본문 또는 {@code documents} 자체가 누락된 응답은 정상 빈
   * 결과와 구분해 상류 계약 위반으로 처리한다. 카카오가 내려주는 에러 코드({@code -401}·{@code -5}·{@code -10} 등)는 구분하지 않는다 —
   * 프론트가 할 수 있는 대응이 "잠시 후 재시도" 하나로 같다.
   */
  private List<KakaoLocalSearchResponse.Document> get(Consumer<UriBuilder> uriCustomizer) {
    requireConfiguredCredentials();

    KakaoLocalSearchResponse response;
    try {
      response =
          restClient
              .get()
              .uri(
                  uriBuilder -> {
                    uriCustomizer.accept(uriBuilder);
                    return uriBuilder.build();
                  })
              .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION_PREFIX + properties.getRestApiKey())
              .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
              .retrieve()
              .body(KakaoLocalSearchResponse.class);
    } catch (RestClientException e) {
      throw new NearbyPlaceSearchUpstreamException(e);
    }

    if (response == null || response.documents() == null) {
      throw malformedResponse("Kakao local response missing documents");
    }
    return response.documents();
  }

  /** 로컬에서 키 없이 다른 기능을 실행하는 것은 허용하되, 실제 검색 요청을 무인증으로 카카오에 보내지는 않는다. */
  private void requireConfiguredCredentials() {
    if (!properties.isConfigured()) {
      throw malformedResponse("Kakao local REST API key is not configured");
    }
  }

  /** 기대한 카테고리 그룹과 이름이 성립하는 문서만 남겨 도메인 값으로 옮긴다. */
  private static List<NearbyPlace> toPlaces(
      List<KakaoLocalSearchResponse.Document> documents, String expectedGroupCode) {
    try {
      return documents.stream()
          .filter(document -> isExpectedGroup(document, expectedGroupCode))
          .map(KakaoLocalPlaceClient::toPlace)
          .toList();
    } catch (IllegalArgumentException e) {
      throw new NearbyPlaceSearchUpstreamException(e);
    }
  }

  /**
   * 요청한 카테고리 그룹의 문서이고 표시할 이름이 있는지 확인한다.
   *
   * <p>카카오가 그룹 코드를 비워 보내는 경우가 있어 <b>값이 있을 때만</b> 대조한다 — 비어 있다고 버리면 정상 후보까지 사라진다.
   */
  private static boolean isExpectedGroup(
      KakaoLocalSearchResponse.Document document, String expectedGroupCode) {
    if (document == null || !StringUtils.hasText(document.placeName())) {
      return false;
    }
    return !StringUtils.hasText(document.categoryGroupCode())
        || expectedGroupCode.equals(document.categoryGroupCode());
  }

  /**
   * 카카오 원본 항목의 좌표를 검증한 뒤 프론트 친화적인 WGS84 값으로 변환한다.
   *
   * <p>주소는 보조 표시용이라 비어 있어도 후보를 버리지 않고 빈 문자열로 채운다(주소 검색과 반대다 — 등록에 실리는 것은 이름이지 주소가 아니다). 반면 좌표가 어긋난
   * 후보는 지도에 찍을 수 없으므로 내보내지 않는다.
   */
  private static NearbyPlace toPlace(KakaoLocalSearchResponse.Document document) {
    if (!StringUtils.hasText(document.x()) || !StringUtils.hasText(document.y())) {
      throw new IllegalArgumentException("Kakao local document missing coordinates");
    }

    double lng = parseCoordinate(document.x());
    double lat = parseCoordinate(document.y());
    if (!isLatitude(lat) || !isLongitude(lng)) {
      throw new IllegalArgumentException("Kakao local document has invalid WGS84 coordinates");
    }

    return new NearbyPlace(
        document.placeName().trim(),
        Objects.requireNonNullElse(document.roadAddressName(), ""),
        Objects.requireNonNullElse(document.addressName(), ""),
        lat,
        lng,
        parseDistance(document.distance()));
  }

  /** 카카오가 문자열로 주는 십진수 좌표를 숫자로 되돌린다. 배율 변환은 없다(네이버 지역 검색과 다른 점). */
  private static double parseCoordinate(String value) {
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Kakao local coordinate is not a number: " + value, e);
    }
  }

  /** {@code distance}는 x·y를 보낸 요청에만 온다. 없거나 숫자가 아니면 거리 정보 없음으로 둔다 — 후보 자체는 살린다. */
  private static Integer parseDistance(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return Integer.valueOf(value.trim());
    } catch (NumberFormatException e) {
      return null;
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
  private static NearbyPlaceSearchUpstreamException malformedResponse(String message) {
    return new NearbyPlaceSearchUpstreamException(new IllegalStateException(message));
  }
}
