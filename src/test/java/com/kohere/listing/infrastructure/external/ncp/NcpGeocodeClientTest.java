package com.kohere.listing.infrastructure.external.ncp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.kohere.listing.domain.address.AddressSearchResult;
import com.kohere.listing.domain.address.AddressSearchUpstreamException;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * {@link NcpGeocodeClient}의 HTTP 계약과 응답 변환을 실제 네트워크 없이 검증한다.
 *
 * <p>{@link MockRestServiceServer}로 요청 URL·고정 파라미터·인증 헤더를 확인하고, NCP 원본 좌표({@code x}·{@code y} 문자열)
 * 변환 및 외부 오류의 502 예외 매핑을 고정한다. 성공 응답은 NCP 공식 문서의 예시를 그대로 쓴다. 테스트에는 운영 인증정보 대신 명시적인 더미 값만 사용한다.
 */
class NcpGeocodeClientTest {

  private static final String BASE_URL = "https://maps.apigw.ntruss.com";
  private static final String CLIENT_ID = "test-ncp-key-id";
  private static final String CLIENT_SECRET = "test-ncp-key";

  private MockRestServiceServer server;
  private NcpGeocodeClient client;

  /** 실제 어댑터와 같은 base URL을 쓰되 요청을 Mock 서버가 가로채도록 RestClient를 조립한다. */
  @BeforeEach
  void setUp() {
    NcpGeocodeProperties properties = configuredProperties();
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    server = MockRestServiceServer.bindTo(builder).build();
    client = new NcpGeocodeClient(properties, builder.build());
  }

  /** NCP 문서의 고정 파라미터와 인증 헤더를 보내고, 문자열 좌표를 WGS84 십진수로 변환한다. */
  @Test
  void search_정상_응답을_주소_후보로_변환한다() {
    server
        .expect(requestTo(Matchers.containsString("/map-geocode/v2/geocode")))
        .andExpect(method(HttpMethod.GET))
        // MockRestRequestMatchers는 URI의 raw query를 비교하므로 UTF-8 percent-encoding 결과를 단정한다.
        .andExpect(
            queryParam("query", "%EB%B6%84%EB%8B%B9%EA%B5%AC%20%EB%B6%88%EC%A0%95%EB%A1%9C%206"))
        .andExpect(queryParam("count", "5"))
        .andExpect(queryParam("page", "1"))
        .andExpect(queryParam("language", "kor"))
        .andExpect(header("x-ncp-apigw-api-key-id", CLIENT_ID))
        .andExpect(header("x-ncp-apigw-api-key", CLIENT_SECRET))
        .andRespond(withSuccess(successBody(), MediaType.APPLICATION_JSON));

    List<AddressSearchResult> results = client.search("분당구 불정로 6");

    assertThat(results).hasSize(1);
    AddressSearchResult first = results.getFirst();
    // 건물명이 붙은 도로명 주소를 서버가 다듬지 않는다 — 사용자가 고른 표준 주소 그대로다.
    assertThat(first.roadAddress()).isEqualTo("경기도 성남시 분당구 불정로 6 NAVER그린팩토리");
    assertThat(first.jibunAddress()).isEqualTo("경기도 성남시 분당구 정자동 178-1 NAVER그린팩토리");
    assertThat(first.englishAddress())
        .isEqualTo("6, Buljeong-ro, Bundang-gu, Seongnam-si, Gyeonggi-do, Republic of Korea");
    // x가 경도, y가 위도다. 네이버 지역 검색과 달리 배율 변환이 없다.
    assertThat(first.lng()).isEqualTo(127.1054328);
    assertThat(first.lat()).isEqualTo(37.3595963);
    server.verify();
  }

  /** NCP가 정상 200과 빈 addresses를 반환하면 장애가 아닌 빈 검색 결과로 유지한다. */
  @Test
  void search_정상_빈_응답이면_빈_목록을_반환한다() {
    server
        .expect(requestTo(Matchers.containsString("/map-geocode/v2/geocode")))
        .andRespond(
            withSuccess(
                "{\"status\":\"OK\",\"meta\":{\"totalCount\":0},\"addresses\":[],"
                    + "\"errorMessage\":\"\"}",
                MediaType.APPLICATION_JSON));

    assertThat(client.search("없는주소")).isEmpty();
    server.verify();
  }

  /** 도로명이 없는 후보는 등록에 쓸 수 없어 응답에서 제외한다. */
  @Test
  void search_도로명이_없는_후보는_제외한다() {
    server
        .expect(requestTo(Matchers.containsString("/map-geocode/v2/geocode")))
        .andRespond(
            withSuccess(
                "{\"status\":\"OK\",\"addresses\":["
                    + "{\"roadAddress\":\"\",\"jibunAddress\":\"서울특별시 종로구 1-1\","
                    + "\"englishAddress\":\"\",\"x\":\"126.9\",\"y\":\"37.5\"},"
                    + "{\"roadAddress\":\"서울특별시 종로구 세종대로 1\",\"jibunAddress\":\"\","
                    + "\"englishAddress\":\"\",\"x\":\"126.9\",\"y\":\"37.5\"}]}",
                MediaType.APPLICATION_JSON));

    List<AddressSearchResult> results = client.search("세종대로 1");

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().roadAddress()).isEqualTo("서울특별시 종로구 세종대로 1");
    server.verify();
  }

  /** 방어적으로 NCP가 5개를 초과해 내려주더라도 공개 계약의 최대 5개를 넘기지 않는다. */
  @Test
  void search_응답이_5개를_초과하면_앞의_5개만_반환한다() {
    server
        .expect(requestTo(Matchers.containsString("/map-geocode/v2/geocode")))
        .andRespond(withSuccess(sixAddressesBody(), MediaType.APPLICATION_JSON));

    assertThat(client.search("서울")).hasSize(5);
    server.verify();
  }

  /** NCP 4xx(INVALID_REQUEST)는 서버가 이미 검증을 끝낸 뒤라 사용자 오류가 아니므로 502로 변환한다. */
  @Test
  void search_NCP_4xx이면_상류_예외를_던진다() {
    server
        .expect(requestTo(Matchers.containsString("/map-geocode/v2/geocode")))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST));

    assertThatThrownBy(() -> client.search("신촌로 12"))
        .isInstanceOf(AddressSearchUpstreamException.class);
    server.verify();
  }

  /** NCP 5xx도 재시도 가능한 외부 장애로 분류해 공통 502 응답 경로로 전달한다. */
  @Test
  void search_NCP_5xx이면_상류_예외를_던진다() {
    server
        .expect(requestTo(Matchers.containsString("/map-geocode/v2/geocode")))
        .andRespond(withServerError());

    assertThatThrownBy(() -> client.search("신촌로 12"))
        .isInstanceOf(AddressSearchUpstreamException.class);
    server.verify();
  }

  /** 본문에 addresses가 없으면 정상 빈 배열과 구분해 NCP 응답 계약 위반으로 처리한다. */
  @Test
  void search_addresses가_누락되면_상류_예외를_던진다() {
    server
        .expect(requestTo(Matchers.containsString("/map-geocode/v2/geocode")))
        .andRespond(withSuccess("{\"status\":\"OK\"}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.search("신촌로 12"))
        .isInstanceOf(AddressSearchUpstreamException.class);
    server.verify();
  }

  /** HTTP 200이어도 status가 OK가 아니면 결과를 믿을 수 없으므로 상류 오류로 처리한다. */
  @Test
  void search_status가_OK가_아니면_상류_예외를_던진다() {
    server
        .expect(requestTo(Matchers.containsString("/map-geocode/v2/geocode")))
        .andRespond(
            withSuccess(
                "{\"status\":\"INVALID_REQUEST\",\"addresses\":[],"
                    + "\"errorMessage\":\"query is invalid\"}",
                MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.search("신촌로 12"))
        .isInstanceOf(AddressSearchUpstreamException.class);
    server.verify();
  }

  /** 숫자로 해석할 수 없는 좌표는 그대로 매물에 저장될 값이라 잘못된 위치를 노출하지 않고 상류 계약 위반으로 처리한다. */
  @Test
  void search_좌표가_잘못되면_상류_예외를_던진다() {
    server
        .expect(requestTo(Matchers.containsString("/map-geocode/v2/geocode")))
        .andRespond(
            withSuccess(
                "{\"status\":\"OK\",\"addresses\":[{\"roadAddress\":\"서울특별시 종로구 세종대로 1\","
                    + "\"jibunAddress\":\"\",\"englishAddress\":\"\","
                    + "\"x\":\"not-a-number\",\"y\":\"37.5\"}]}",
                MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.search("세종대로 1"))
        .isInstanceOf(AddressSearchUpstreamException.class);
    server.verify();
  }

  /** WGS84 범위를 벗어난 좌표도 지도에 찍을 수 없으므로 후보를 내보내지 않는다. */
  @Test
  void search_좌표가_WGS84_범위를_벗어나면_상류_예외를_던진다() {
    server
        .expect(requestTo(Matchers.containsString("/map-geocode/v2/geocode")))
        .andRespond(
            withSuccess(
                "{\"status\":\"OK\",\"addresses\":[{\"roadAddress\":\"서울특별시 종로구 세종대로 1\","
                    + "\"jibunAddress\":\"\",\"englishAddress\":\"\","
                    + "\"x\":\"126.9\",\"y\":\"137.5\"}]}",
                MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.search("세종대로 1"))
        .isInstanceOf(AddressSearchUpstreamException.class);
    server.verify();
  }

  /** 로컬 인증정보가 비어 있으면 무인증 외부 요청을 보내지 않고 즉시 상류 설정 오류로 처리한다. */
  @Test
  void search_인증정보가_없으면_HTTP를_호출하지_않고_상류_예외를_던진다() {
    NcpGeocodeProperties properties = new NcpGeocodeProperties();
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    MockRestServiceServer isolatedServer = MockRestServiceServer.bindTo(builder).build();
    NcpGeocodeClient unconfiguredClient = new NcpGeocodeClient(properties, builder.build());

    assertThatThrownBy(() -> unconfiguredClient.search("신촌로 12"))
        .isInstanceOf(AddressSearchUpstreamException.class);
    isolatedServer.verify();
  }

  /** 모든 테스트가 동일한 더미 인증정보와 base URL을 사용하도록 설정 객체를 만든다. */
  private static NcpGeocodeProperties configuredProperties() {
    NcpGeocodeProperties properties = new NcpGeocodeProperties();
    properties.setBaseUrl(BASE_URL);
    properties.setClientId(CLIENT_ID);
    properties.setClientSecret(CLIENT_SECRET);
    return properties;
  }

  /** NCP 공식 문서의 geocode 응답 예시를 그대로 옮긴 픽스처다(추정이 아니라 문서 값이다). */
  private static String successBody() {
    return """
        {
          "status": "OK",
          "meta": { "totalCount": 1, "page": 1, "count": 1 },
          "addresses": [
            {
              "roadAddress": "경기도 성남시 분당구 불정로 6 NAVER그린팩토리",
              "jibunAddress": "경기도 성남시 분당구 정자동 178-1 NAVER그린팩토리",
              "englishAddress": "6, Buljeong-ro, Bundang-gu, Seongnam-si, Gyeonggi-do, Republic of Korea",
              "addressElements": [
                { "types": ["SIDO"], "longName": "경기도", "shortName": "경기도", "code": "" },
                { "types": ["POSTAL_CODE"], "longName": "13561", "shortName": "13561", "code": "" }
              ],
              "x": "127.1054328",
              "y": "37.3595963",
              "distance": 0.0
            }
          ],
          "errorMessage": ""
        }
        """;
  }

  /** 제공자 이상 상황에서 최대 5개 방어 제한이 유지되는지 확인할 6건 응답을 만든다. */
  private static String sixAddressesBody() {
    String address =
        "{\"roadAddress\":\"서울특별시 종로구 세종대로 1\",\"jibunAddress\":\"서울특별시 종로구 1-1\","
            + "\"englishAddress\":\"1, Sejong-daero\",\"x\":\"126.9\",\"y\":\"37.5\"}";
    return "{\"status\":\"OK\",\"addresses\":["
        + String.join(",", List.of(address, address, address, address, address, address))
        + "]}";
  }
}
