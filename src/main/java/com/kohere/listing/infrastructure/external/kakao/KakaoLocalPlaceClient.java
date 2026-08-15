package com.kohere.listing.infrastructure.external.kakao;

import com.kohere.listing.domain.nearby.Coordinate;
import com.kohere.listing.domain.nearby.NearbyPlace;
import com.kohere.listing.domain.nearby.NearbyPlaceSearchClient;
import com.kohere.listing.domain.nearby.NearbyPlaceSearchUpstreamException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * <p>요청 파라미터는 카카오 계약과 Kohere UX에 맞춰 서버가 고정한다 — 역은 {@code category_group_code=SW8}, 대학은 {@code SC4}에
 * 반경 2km다. 프론트에는 제공자 필드명을 노출하지 않고 WGS84 십진수 좌표({@code x}→{@code lng}, {@code y}→{@code lat})로 변환해
 * 전달한다. 외부 HTTP 오류, 키 누락, 본문·좌표 계약 위반은 모두 {@link NearbyPlaceSearchUpstreamException}으로 통일한다.
 *
 * <p><b>대학은 카테고리 코드만으로 걸러지지 않는다.</b> 카카오에 대학 전용 코드가 없어 {@code SC4}(학교)가 초·중·고를 함께 담는다. 그래서 {@code
 * category_name} 계층 문자열을 파싱해 대학만 남긴다(§{@link #isUniversity}).
 */
@Component
public class KakaoLocalPlaceClient implements NearbyPlaceSearchClient {

  private static final String KEYWORD_SEARCH_PATH = "/v2/local/search/keyword.json";
  private static final String CATEGORY_SEARCH_PATH = "/v2/local/search/category.json";
  private static final String AUTHORIZATION_PREFIX = "KakaoAK ";

  /** 지하철역 카테고리 그룹 코드. */
  private static final String SUBWAY_STATION_CODE = "SW8";

  /** 학교 카테고리 그룹 코드 — 초·중·고가 함께 들어 있어 {@code category_name}으로 한 번 더 거른다. */
  private static final String SCHOOL_CODE = "SC4";

  /** {@code category_name}의 계층 구분자. */
  private static final String CATEGORY_DEPTH_DELIMITER = ">";

  /** 대학 판별과 이름 정규화의 기준이 되는 토막이다. */
  private static final String UNIVERSITY_SEGMENT = "대학교";

  private static final int KEYWORD_SEARCH_SIZE = 10;
  private static final int CATEGORY_SEARCH_SIZE = 15;
  private static final int FIRST_PAGE = 1;
  private static final int NEARBY_RADIUS_METERS = 2_000;
  private static final String SORT_DISTANCE = "distance";
  private static final String SORT_ACCURACY = "accuracy";

  private final KakaoLocalProperties properties;
  private final RestClient searchRestClient;
  private final RestClient registerRestClient;

  /**
   * 인증정보 설정과 용도별 HTTP 클라이언트를 명시적으로 주입받는다.
   *
   * @param properties URL·REST 키·타임아웃 설정
   * @param searchRestClient 역 검색용 클라이언트
   * @param registerRestClient 등록 중 대학 파생용 클라이언트(read 타임아웃이 더 짧다)
   */
  public KakaoLocalPlaceClient(
      KakaoLocalProperties properties,
      @Qualifier("kakaoLocalRestClient") RestClient searchRestClient,
      @Qualifier("kakaoLocalRegisterRestClient") RestClient registerRestClient) {
    this.properties = properties;
    this.searchRestClient = searchRestClient;
    this.registerRestClient = registerRestClient;
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
            searchRestClient,
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
    return toPlaces(
        categorySearch(searchRestClient, SUBWAY_STATION_CODE, origin), SUBWAY_STATION_CODE);
  }

  /**
   * 좌표 주변 반경 2km의 대학을 가까운 순으로 찾는다.
   *
   * <p>{@code SC4}에서 {@code category_name}으로 대학만 남긴 뒤, 캠퍼스·건물이 별도 문서로 오는 것을 <b>본명으로 정규화해 하나로
   * 합친다</b>. 응답이 이미 거리순이므로 각 대학의 첫 문서가 곧 매물에서 가장 가까운 지점이다.
   */
  @Override
  public List<NearbyPlace> searchNearbyUniversities(Coordinate origin) {
    List<KakaoLocalSearchResponse.Document> documents =
        categorySearch(registerRestClient, SCHOOL_CODE, origin);

    Map<String, NearbyPlace> byName = new LinkedHashMap<>();
    try {
      for (KakaoLocalSearchResponse.Document document : documents) {
        if (!isExpectedGroup(document, SCHOOL_CODE) || !isUniversity(document.categoryName())) {
          continue;
        }
        NearbyPlace place = toPlace(document);
        byName.putIfAbsent(toUniversityName(place.name()), place);
      }
    } catch (IllegalArgumentException e) {
      throw new NearbyPlaceSearchUpstreamException(e);
    }
    return byName.entrySet().stream()
        .map(entry -> withName(entry.getValue(), entry.getKey()))
        .toList();
  }

  private List<KakaoLocalSearchResponse.Document> categorySearch(
      RestClient restClient, String categoryGroupCode, Coordinate origin) {
    return get(
        restClient,
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
  private List<KakaoLocalSearchResponse.Document> get(
      RestClient restClient, Consumer<UriBuilder> uriCustomizer) {
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
   * {@code "교육,학문 > 학교 > 대학교"}를 깊이별로 쪼개 어느 한 단계가 대학교인지 본다.
   *
   * <p><b>{@code equals}가 아니라 {@code contains}인 이유</b>: 카카오가 하위 단계({@code … > 대학교 > 사립대학교})를 추가해도
   * 계속 걸린다. {@code 고등학교}·{@code 중학교}·{@code 초등학교}는 {@code 대학교}를 부분 문자열로 포함하지 않아 오탐이 없다.
   *
   * <p><b>전체 문자열이 아니라 세그먼트 단위인 이유</b>: 상위 분류에 우연히 {@code 대학교}가 섞인 경우를 걸러 낸다.
   *
   * <p>{@code … > 대학원}·{@code … > 전문대학}은 이 규칙에서 빠진다 — 조건을 {@code 대학}으로 넓히면 2년제와 함께 대학원도 들어오기 때문이다.
   * 파생 정확도는 관리자 승인 심사가 보정한다(ADR-0044 §6).
   */
  private static boolean isUniversity(String categoryName) {
    if (!StringUtils.hasText(categoryName)) {
      return false;
    }
    return Arrays.stream(categoryName.split(CATEGORY_DEPTH_DELIMITER))
        .map(String::trim)
        .anyMatch(segment -> segment.contains(UNIVERSITY_SEGMENT));
  }

  /**
   * 캠퍼스·건물 이름을 대학 본명으로 줄인다 — {@code 연세대학교 신촌캠퍼스 제1공학관} → {@code 연세대학교}.
   *
   * <p>{@code 대학교}를 포함하지 않는 이름({@code 한국과학기술원})은 그대로 둔다.
   */
  private static String toUniversityName(String placeName) {
    int end = placeName.indexOf(UNIVERSITY_SEGMENT);
    return end < 0
        ? placeName.trim()
        : placeName.substring(0, end + UNIVERSITY_SEGMENT.length()).trim();
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

  /** 이름만 바꾼 사본을 만든다(대학 본명 정규화 결과를 싣는다). */
  private static NearbyPlace withName(NearbyPlace place, String name) {
    return new NearbyPlace(
        name,
        place.roadAddress(),
        place.jibunAddress(),
        place.lat(),
        place.lng(),
        place.distanceMeters());
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
