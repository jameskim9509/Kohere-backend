package com.kohere.listing.infrastructure.external.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.kohere.listing.domain.nearby.Coordinate;
import com.kohere.listing.domain.nearby.NearbyPlace;
import com.kohere.listing.domain.nearby.NearbyPlaceSearchUpstreamException;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * {@link KakaoLocalPlaceClient}의 HTTP 계약과 응답 변환을 실제 네트워크 없이 검증한다(ADR-0044).
 *
 * <p>{@link MockRestServiceServer}로 요청 URL·고정 파라미터·인증 헤더를 확인하고, 카카오 원본 좌표({@code x}·{@code y} 문자열)
 * 변환과 외부 오류의 502 예외 매핑을 고정한다. 테스트에는 운영 인증정보 대신 명시적인 더미 값만 사용한다.
 *
 * <p><b>대학 판별 규칙을 여기서 못 박는다</b> — {@code category_name} 파싱·이름 정규화·중복 제거는 카카오 응답 모양에 붙어 있는 규칙이라 어댑터가
 * 소유하고, 그 경계가 흔들리면 초·중·고가 진단 추천의 조인 키로 새어 들어간다.
 */
class KakaoLocalPlaceClientTest {

  private static final String BASE_URL = "https://dapi.kakao.com";
  private static final String REST_API_KEY = "test-kakao-rest-api-key";

  /** 신촌 일대 — 좌표를 준 요청의 기준점이다. */
  private static final Coordinate SINCHON = new Coordinate(37.5559918, 126.9368647);

  private MockRestServiceServer server;
  private KakaoLocalPlaceClient client;

  /** 실제 어댑터와 같은 base URL을 쓰되 요청을 Mock 서버가 가로채도록 RestClient를 조립한다. */
  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    server = MockRestServiceServer.bindTo(builder).build();
    RestClient restClient = builder.build();
    // 검색용·등록용 클라이언트를 같은 목으로 묶어 두 경로의 요청을 한 서버에서 단정한다.
    client = new KakaoLocalPlaceClient(configuredProperties(), restClient, restClient);
  }

  // ── 역 검색(키워드) ────────────────────────────────────────────────────────

  /** 좌표를 주면 거리순 정렬과 x·y가 실리고, 문자열 좌표·거리가 숫자로 바뀐다. */
  @Test
  void 키워드역검색_좌표를주면_거리순정렬과거리를함께요청한다() {
    server
        .expect(requestTo(Matchers.containsString("/v2/local/search/keyword.json")))
        .andExpect(method(HttpMethod.GET))
        .andExpect(queryParam("category_group_code", "SW8"))
        .andExpect(queryParam("size", "10"))
        .andExpect(queryParam("page", "1"))
        .andExpect(queryParam("sort", "distance"))
        // x가 경도, y가 위도다 — 뒤집히기 가장 쉬운 지점이라 값으로 못 박는다.
        .andExpect(queryParam("x", "126.9368647"))
        .andExpect(queryParam("y", "37.5559918"))
        .andExpect(header("Authorization", "KakaoAK " + REST_API_KEY))
        .andRespond(withSuccess(stationBody(), MediaType.APPLICATION_JSON));

    List<NearbyPlace> places = client.searchStationsByKeyword("신촌", SINCHON);

    assertThat(places).hasSize(1);
    NearbyPlace first = places.getFirst();
    // 환승역 노선 표기를 서버가 다듬지 않는다 — 임대인이 고른 그대로 등록에 실린다.
    assertThat(first.name()).isEqualTo("신촌역 2호선");
    assertThat(first.roadAddress()).isEqualTo("서울 서대문구 신촌로 90");
    assertThat(first.jibunAddress()).isEqualTo("서울 서대문구 창천동 30-33");
    assertThat(first.lng()).isEqualTo(126.936893);
    assertThat(first.lat()).isEqualTo(37.555134);
    assertThat(first.distanceMeters()).isEqualTo(320);
    server.verify();
  }

  /** 좌표가 없으면 정확도순이고 x·y를 보내지 않으며, 거리 정보도 없다. */
  @Test
  void 키워드역검색_좌표가없으면_정확도순이고_거리가null이다() {
    server
        .expect(requestTo(Matchers.containsString("/v2/local/search/keyword.json")))
        .andExpect(queryParam("sort", "accuracy"))
        .andExpect(requestTo(Matchers.not(Matchers.containsString("x="))))
        .andRespond(withSuccess(stationBodyWithoutDistance(), MediaType.APPLICATION_JSON));

    List<NearbyPlace> places = client.searchStationsByKeyword("신촌", null);

    assertThat(places).hasSize(1);
    assertThat(places.getFirst().distanceMeters()).isNull();
    server.verify();
  }

  // ── 역 검색(좌표) ─────────────────────────────────────────────────────────

  /** 좌표 검색은 카테고리 API에 반경 2km를 고정해 보낸다. */
  @Test
  void 인근역검색_카테고리API에_반경2km를고정한다() {
    server
        .expect(requestTo(Matchers.containsString("/v2/local/search/category.json")))
        .andExpect(queryParam("category_group_code", "SW8"))
        .andExpect(queryParam("radius", "2000"))
        .andExpect(queryParam("size", "15"))
        .andExpect(queryParam("sort", "distance"))
        .andRespond(withSuccess(stationBody(), MediaType.APPLICATION_JSON));

    assertThat(client.searchNearbyStations(SINCHON)).hasSize(1);
    server.verify();
  }

  // ── 대학 파생 ─────────────────────────────────────────────────────────────

  /**
   * {@code SC4}에는 초·중·고가 함께 오므로 {@code category_name}으로 대학만 남긴다.
   *
   * <p>같은 대학의 캠퍼스·건물은 본명으로 합치고, 가장 가까운 문서의 좌표·거리를 남긴다.
   */
  @Test
  void 인근대학검색_대학만남기고_캠퍼스를본명으로합친다() {
    server
        .expect(requestTo(Matchers.containsString("/v2/local/search/category.json")))
        .andExpect(queryParam("category_group_code", "SC4"))
        .andExpect(queryParam("radius", "2000"))
        .andRespond(withSuccess(schoolBody(), MediaType.APPLICATION_JSON));

    List<NearbyPlace> places = client.searchNearbyUniversities(SINCHON);

    // 연세대 3건이 1건으로 합쳐지고, 이화여대가 뒤따르며, 초·중·고·대학원은 빠진다.
    assertThat(places).extracting(NearbyPlace::name).containsExactly("연세대학교", "이화여자대학교");
    // 합쳐진 뒤 남는 것은 가장 가까운 문서다(응답이 이미 거리순이다).
    assertThat(places.getFirst().distanceMeters()).isEqualTo(780);
    server.verify();
  }

  /** 반경 내 대학이 하나도 없으면 장애가 아니라 빈 목록이다. */
  @Test
  void 인근대학검색_초중고만있으면_빈목록이다() {
    server
        .expect(requestTo(Matchers.containsString("/v2/local/search/category.json")))
        .andRespond(withSuccess(schoolOnlyBody(), MediaType.APPLICATION_JSON));

    assertThat(client.searchNearbyUniversities(SINCHON)).isEmpty();
    server.verify();
  }

  // ── 정상 빈 결과와 장애의 구분 ────────────────────────────────────────────

  /** 카카오가 정상 200과 빈 documents를 반환하면 장애가 아닌 빈 검색 결과로 유지한다. */
  @Test
  void 검색결과가없으면_빈목록을반환한다() {
    server
        .expect(requestTo(Matchers.containsString("/v2/local/search/keyword.json")))
        .andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));

    assertThat(client.searchStationsByKeyword("없는역", null)).isEmpty();
    server.verify();
  }

  /** documents 자체가 없는 응답은 정상 빈 결과와 구분해 상류 계약 위반으로 처리한다. */
  @Test
  void documents가없는응답은_502예외다() {
    server
        .expect(requestTo(Matchers.containsString("/v2/local/search/keyword.json")))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.searchStationsByKeyword("신촌", null))
        .isInstanceOf(NearbyPlaceSearchUpstreamException.class);
  }

  /** 카카오 HTTP 오류는 502로 통일한다 — 에러 코드를 구분하지 않는다. */
  @Test
  void 외부HTTP오류는_502예외다() {
    server
        .expect(requestTo(Matchers.containsString("/v2/local/search/keyword.json")))
        .andRespond(withServerError());

    assertThatThrownBy(() -> client.searchStationsByKeyword("신촌", null))
        .isInstanceOf(NearbyPlaceSearchUpstreamException.class);
  }

  /** 좌표가 숫자가 아니면 지도에 찍을 수 없으므로 후보를 내보내지 않는다. */
  @Test
  void 좌표가숫자가아니면_502예외다() {
    server
        .expect(requestTo(Matchers.containsString("/v2/local/search/keyword.json")))
        .andRespond(
            withSuccess(
                "{\"documents\":[{\"place_name\":\"신촌역\",\"x\":\"없음\",\"y\":\"37.5\"}]}",
                MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.searchStationsByKeyword("신촌", null))
        .isInstanceOf(NearbyPlaceSearchUpstreamException.class);
  }

  /** REST 키가 없으면 카카오로 나가는 요청 자체가 없다. */
  @Test
  void 키가없으면_요청없이_502예외다() {
    KakaoLocalProperties empty = new KakaoLocalProperties();
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    MockRestServiceServer strict = MockRestServiceServer.bindTo(builder).build();
    RestClient restClient = builder.build();
    KakaoLocalPlaceClient unconfigured = new KakaoLocalPlaceClient(empty, restClient, restClient);

    assertThatThrownBy(() -> unconfigured.searchStationsByKeyword("신촌", null))
        .isInstanceOf(NearbyPlaceSearchUpstreamException.class);
    // 기대한 요청이 하나도 없으므로 verify가 곧 "호출하지 않았다"의 단정이다.
    strict.verify();
  }

  private static KakaoLocalProperties configuredProperties() {
    KakaoLocalProperties properties = new KakaoLocalProperties();
    properties.setBaseUrl(BASE_URL);
    properties.setRestApiKey(REST_API_KEY);
    return properties;
  }

  private static String stationBody() {
    return """
        {
          "documents": [
            {
              "place_name": "신촌역 2호선",
              "category_name": "교통,수송 > 지하철,전철 > 수도권2호선",
              "category_group_code": "SW8",
              "road_address_name": "서울 서대문구 신촌로 90",
              "address_name": "서울 서대문구 창천동 30-33",
              "x": "126.936893",
              "y": "37.555134",
              "distance": "320"
            }
          ]
        }
        """;
  }

  private static String stationBodyWithoutDistance() {
    return """
        {
          "documents": [
            {
              "place_name": "신촌역 2호선",
              "category_group_code": "SW8",
              "x": "126.936893",
              "y": "37.555134"
            }
          ]
        }
        """;
  }

  /** 학교 카테고리 원본 — 대학 캠퍼스·건물과 초·중·고·대학원이 섞여 온다. */
  private static String schoolBody() {
    return """
        {
          "documents": [
            {
              "place_name": "연세대학교",
              "category_name": "교육,학문 > 학교 > 대학교",
              "category_group_code": "SC4",
              "road_address_name": "서울 서대문구 연세로 50",
              "address_name": "서울 서대문구 신촌동 134",
              "x": "126.938572", "y": "37.565784", "distance": "780"
            },
            {
              "place_name": "창천초등학교",
              "category_name": "교육,학문 > 학교 > 초등학교",
              "category_group_code": "SC4",
              "x": "126.937", "y": "37.556", "distance": "820"
            },
            {
              "place_name": "연세대학교 신촌캠퍼스 제1공학관",
              "category_name": "교육,학문 > 학교 > 대학교",
              "category_group_code": "SC4",
              "x": "126.939", "y": "37.566", "distance": "860"
            },
            {
              "place_name": "이화여자대학교 대현캠퍼스",
              "category_name": "교육,학문 > 학교 > 대학교",
              "category_group_code": "SC4",
              "road_address_name": "서울 서대문구 이화여대길 52",
              "address_name": "서울 서대문구 대현동 11-1",
              "x": "126.946900", "y": "37.561800", "distance": "950"
            },
            {
              "place_name": "연세대학교 학생회관",
              "category_name": "교육,학문 > 학교 > 대학교",
              "category_group_code": "SC4",
              "x": "126.940", "y": "37.567", "distance": "980"
            },
            {
              "place_name": "고려대학교사범대학부속고등학교",
              "category_name": "교육,학문 > 학교 > 고등학교",
              "category_group_code": "SC4",
              "x": "126.941", "y": "37.568", "distance": "1100"
            },
            {
              "place_name": "연세대학교 대학원",
              "category_name": "교육,학문 > 학교 > 대학원",
              "category_group_code": "SC4",
              "x": "126.942", "y": "37.569", "distance": "1200"
            }
          ]
        }
        """;
  }

  private static String schoolOnlyBody() {
    return """
        {
          "documents": [
            {
              "place_name": "창천초등학교",
              "category_name": "교육,학문 > 학교 > 초등학교",
              "category_group_code": "SC4",
              "x": "126.937", "y": "37.556", "distance": "820"
            },
            {
              "place_name": "신촌중학교",
              "category_name": "교육,학문 > 학교 > 중학교",
              "category_group_code": "SC4",
              "x": "126.938", "y": "37.557", "distance": "900"
            }
          ]
        }
        """;
  }
}
