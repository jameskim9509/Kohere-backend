package com.kohere.listing.infrastructure;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.resourceDetails;
import static com.kohere.docs.ApiDocsErrors.errorSnippet;
import static com.kohere.docs.DocsTokens.bearer;
import static com.kohere.docs.DocsTokens.expiredAccessToken;
import static com.kohere.docs.ListingDocsFields.LISTING_NEARBY_STATION_DESCRIPTION;
import static com.kohere.docs.ListingDocsFields.LISTING_NEARBY_STATION_SUMMARY;
import static com.kohere.docs.ListingDocsFields.LISTING_STATION_SEARCH_400;
import static com.kohere.docs.ListingDocsFields.LISTING_STATION_SEARCH_401;
import static com.kohere.docs.ListingDocsFields.LISTING_STATION_SEARCH_403;
import static com.kohere.docs.ListingDocsFields.LISTING_STATION_SEARCH_502;
import static com.kohere.docs.ListingDocsFields.LISTING_STATION_SEARCH_DESCRIPTION;
import static com.kohere.docs.ListingDocsFields.LISTING_STATION_SEARCH_SUMMARY;
import static com.kohere.docs.ListingDocsFields.nearbyStationQueryParameters;
import static com.kohere.docs.ListingDocsFields.stationQueryParameters;
import static com.kohere.docs.ListingDocsFields.stationSearchResponseFields;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kohere.TestcontainersConfiguration;
import com.kohere.common.security.JwtProperties;
import com.kohere.common.security.JwtTokenService;
import com.kohere.docs.ApiDocsTags;
import com.kohere.listing.domain.nearby.NearbyPlace;
import com.kohere.listing.domain.nearby.NearbyPlaceSearchClient;
import com.kohere.listing.domain.nearby.NearbyPlaceSearchUpstreamException;
import com.kohere.user.api.UserAccountService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 인근 역 검색({@code GET /api/v1/listings/stations}·{@code /nearby})의 REST Docs 스니펫 생성 테스트다(ADR-0044).
 *
 * <p>외부 카카오 호출은 {@link NearbyPlaceSearchClient} 포트를 목으로 대체해 네트워크 없이 돌린다 — HTTP 계약 자체는 {@code
 * KakaoLocalPlaceClientTest}가 검증한다.
 *
 * <p><b>401 케이스가 SecurityConfig 매처 순서를 지켜 주는 회귀 방어선이다.</b> {@code /stations}는 한 세그먼트라 공개 조회
 * 매처({@code GET /api/v1/listings/*} {@code permitAll})에 잡히므로, 임대인 매처를 그 아래로 옮기면 이 테스트가 200을 받고 깨진다.
 *
 * <p>문구·필드 기술자는 {@code ListingDocsFields}에 한 벌만 두고 여기서는 흐름만 만든다(ADR-0017).
 */
@SpringBootTest
@ExtendWith(RestDocumentationExtension.class)
@ActiveProfiles("test")
@Testcontainers
@Import(TestcontainersConfiguration.class)
class ListingStationSearchDocsTest {

  /** 역 검색 권한이 있는 임대인 계정이다. */
  private static final long LANDLORD_ID = 42L;

  /** 정식 회원이지만 임대인이 아니라 서비스가 403으로 거르는 계정이다. */
  private static final long TENANT_ID = 1L;

  private static final String SINCHON_LAT = "37.5559918";
  private static final String SINCHON_LNG = "126.9368647";

  // 다른 키로 서명한 위조 access 토큰. 401 예시도 구조상 JWT여야 restdocs-api-spec이 bearerAuth 보안 스킴을
  // 도출해 Swagger 자물쇠가 유지된다(ListingAddressSearchDocsTest와 동일 처리).
  private static final String FORGED_TOKEN =
      Jwts.builder()
          .issuer("kohere")
          .subject("1")
          .claim("onboardingCompleted", true)
          .signWith(
              Keys.hmacShaKeyFor(
                  "forged-doc-only-wrong-secret-please-override-32bytes-min!!"
                      .getBytes(StandardCharsets.UTF_8)))
          .compact();

  @Container @ServiceConnection static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

  @Autowired private WebApplicationContext context;
  @Autowired private JwtTokenService jwtTokenService;
  @Autowired private JwtProperties jwtProperties;

  @MockitoBean private NearbyPlaceSearchClient nearbyPlaceSearchClient;
  @MockitoBean private UserAccountService userAccountService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp(RestDocumentationContextProvider restDocumentation) {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .apply(documentationConfiguration(restDocumentation))
            .build();
    given(userAccountService.getUserType(LANDLORD_ID)).willReturn("LANDLORD");
    given(userAccountService.getUserType(TENANT_ID)).willReturn("TENANT");
  }

  /** 이름 검색 성공 — 좌표를 함께 보내 거리와 도보 시간 제안까지 받는다. */
  @Test
  void 문서스니펫생성_역이름검색_거리와도보시간제안까지반환() throws Exception {
    given(nearbyPlaceSearchClient.searchStationsByKeyword(eq("신촌"), any()))
        .willReturn(List.of(sinchonLine2()));

    mockMvc
        .perform(
            get("/api/v1/listings/stations")
                .param("keyword", "신촌")
                .param("lat", SINCHON_LAT)
                .param("lng", SINCHON_LNG)
                .header(HttpHeaders.AUTHORIZATION, landlordToken()))
        .andExpect(status().isOk())
        // 환승역 노선 표기를 서버가 다듬지 않는다 — 이 값이 등록의 nearestTransit.name이 된다.
        .andExpect(jsonPath("$.data.items[0].name").value("신촌역 2호선"))
        .andExpect(jsonPath("$.data.items[0].roadAddress").value("서울 서대문구 신촌로 90"))
        .andExpect(jsonPath("$.data.items[0].lat").value(37.555134))
        .andExpect(jsonPath("$.data.items[0].lng").value(126.936893))
        .andExpect(jsonPath("$.data.items[0].distanceMeters").value(320))
        // ceil(320 / 80) = 4. 직선거리 기준이라 하한 제안이며 임대인이 고칠 수 있다.
        .andExpect(jsonPath("$.data.items[0].suggestedWalkMinutes").value(4))
        .andDo(
            document(
                "listing-stations",
                resourceDetails()
                    .tag(ApiDocsTags.LISTINGS)
                    .summary(LISTING_STATION_SEARCH_SUMMARY)
                    .description(LISTING_STATION_SEARCH_DESCRIPTION),
                queryParameters(stationQueryParameters()),
                responseFields(stationSearchResponseFields())));
  }

  /** 좌표를 빼면 정확도순이고 거리·도보 시간이 없다 — 지어내지 않는다. */
  @Test
  void 역이름검색_좌표가없으면_거리와도보시간이null이다() throws Exception {
    given(nearbyPlaceSearchClient.searchStationsByKeyword(eq("신촌"), isNull()))
        .willReturn(List.of(sinchonWithoutDistance()));

    mockMvc
        .perform(
            get("/api/v1/listings/stations")
                .param("keyword", "신촌")
                .header(HttpHeaders.AUTHORIZATION, landlordToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].distanceMeters").doesNotExist())
        .andExpect(jsonPath("$.data.items[0].suggestedWalkMinutes").doesNotExist());
  }

  /** 좌표 검색 성공 — 반경 2km 안의 역이 가까운 순으로 온다. */
  @Test
  void 문서스니펫생성_좌표로인근역목록() throws Exception {
    given(nearbyPlaceSearchClient.searchNearbyStations(any()))
        .willReturn(List.of(sinchonLine2(), ewhaStation()));

    mockMvc
        .perform(
            get("/api/v1/listings/stations/nearby")
                .param("lat", SINCHON_LAT)
                .param("lng", SINCHON_LNG)
                .header(HttpHeaders.AUTHORIZATION, landlordToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].name").value("신촌역 2호선"))
        .andExpect(jsonPath("$.data.items[1].name").value("이대역"))
        .andExpect(jsonPath("$.data.items[1].suggestedWalkMinutes").value(10))
        .andDo(
            document(
                "listing-stations-nearby",
                resourceDetails()
                    .tag(ApiDocsTags.LISTINGS)
                    .summary(LISTING_NEARBY_STATION_SUMMARY)
                    .description(LISTING_NEARBY_STATION_DESCRIPTION),
                queryParameters(nearbyStationQueryParameters()),
                responseFields(stationSearchResponseFields())));
  }

  /** 일치하는 역이 없으면 장애가 아니라 빈 목록이다. */
  @Test
  void 역검색_결과가없으면_빈items를반환한다() throws Exception {
    given(nearbyPlaceSearchClient.searchStationsByKeyword(eq("없는역"), isNull()))
        .willReturn(List.of());

    mockMvc
        .perform(
            get("/api/v1/listings/stations")
                .param("keyword", "없는역")
                .header(HttpHeaders.AUTHORIZATION, landlordToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isEmpty());
  }

  /** 스펙의 "발생 가능한 에러"를 실제로 트리거해 status·error.code와 실패 응답 스니펫을 함께 만든다. */
  @Test
  void 문서스니펫생성_스펙에적힌실패조건_status와errorcode가일치() throws Exception {
    // 공개 조회 매처(GET /api/v1/listings/*)보다 먼저 선언한 인증 매처가 살아 있어야 401이다.
    performError(
        get("/api/v1/listings/stations").param("keyword", "신촌"),
        401,
        "UNAUTHENTICATED",
        "listing-stations-unauthenticated",
        LISTING_STATION_SEARCH_401);

    performError(
        get("/api/v1/listings/stations")
            .param("keyword", "신촌")
            .header(HttpHeaders.AUTHORIZATION, bearer(FORGED_TOKEN)),
        401,
        "UNAUTHENTICATED",
        "listing-stations-forged-token",
        LISTING_STATION_SEARCH_401);

    performError(
        get("/api/v1/listings/stations")
            .param("keyword", "신촌")
            .header(HttpHeaders.AUTHORIZATION, bearer(expiredAccessToken(jwtProperties))),
        401,
        "TOKEN_EXPIRED",
        "listing-stations-token-expired",
        LISTING_STATION_SEARCH_401);

    performError(
        get("/api/v1/listings/stations")
            .param("keyword", "신촌")
            .header(
                HttpHeaders.AUTHORIZATION, bearer(jwtTokenService.issueOnboardingToken(TENANT_ID))),
        403,
        "AUTH_ONBOARDING_REQUIRED",
        "listing-stations-onboarding-required",
        LISTING_STATION_SEARCH_403);

    // 임대인 여부는 서비스가 본다 — 세입자 정식 토큰은 컨트롤러까지 도달한 뒤 거절된다.
    performError(
        get("/api/v1/listings/stations")
            .param("keyword", "신촌")
            .header(HttpHeaders.AUTHORIZATION, bearer(jwtTokenService.issueAccessToken(TENANT_ID))),
        403,
        "FORBIDDEN",
        "listing-stations-forbidden",
        LISTING_STATION_SEARCH_403);

    performError(
        get("/api/v1/listings/stations")
            .param("keyword", "   ")
            .header(HttpHeaders.AUTHORIZATION, landlordToken()),
        400,
        "INVALID_INPUT",
        "listing-stations-invalid-input",
        LISTING_STATION_SEARCH_400);

    // 좌표를 하나만 보내면 거리순인 줄 알고 정확도순 결과를 받게 되므로 입력 오류로 되돌린다.
    performError(
        get("/api/v1/listings/stations")
            .param("keyword", "신촌")
            .param("lat", SINCHON_LAT)
            .header(HttpHeaders.AUTHORIZATION, landlordToken()),
        400,
        "INVALID_INPUT",
        "listing-stations-invalid-coordinate-pair",
        LISTING_STATION_SEARCH_400);

    performError(
        get("/api/v1/listings/stations/nearby")
            .param("lat", "91.0")
            .param("lng", SINCHON_LNG)
            .header(HttpHeaders.AUTHORIZATION, landlordToken()),
        400,
        "INVALID_INPUT",
        "listing-stations-nearby-invalid-coordinate",
        LISTING_STATION_SEARCH_400);

    given(nearbyPlaceSearchClient.searchStationsByKeyword(eq("신촌"), isNull()))
        .willThrow(
            new NearbyPlaceSearchUpstreamException(
                new IllegalStateException("kakao test upstream unavailable")));
    performError(
        get("/api/v1/listings/stations")
            .param("keyword", "신촌")
            .header(HttpHeaders.AUTHORIZATION, landlordToken()),
        502,
        "UPSTREAM_ERROR",
        "listing-stations-upstream-error",
        LISTING_STATION_SEARCH_502);
  }

  private void performError(
      MockHttpServletRequestBuilder request,
      int expectedStatus,
      String expectedCode,
      String identifier,
      String... errorCodes)
      throws Exception {
    mockMvc
        .perform(request)
        .andExpect(status().is(expectedStatus))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value(expectedCode))
        .andDo(
            errorSnippet(
                identifier,
                ApiDocsTags.LISTINGS,
                LISTING_STATION_SEARCH_SUMMARY,
                LISTING_STATION_SEARCH_DESCRIPTION,
                errorCodes));
  }

  /** 신촌역 2호선 — 매물에서 320m라 도보 4분 제안이 붙는다. */
  private static NearbyPlace sinchonLine2() {
    return new NearbyPlace(
        "신촌역 2호선", "서울 서대문구 신촌로 90", "서울 서대문구 창천동 30-33", 37.555134, 126.936893, 320);
  }

  /** 이대역 — 780m라 도보 10분(ceil(780/80))이다. */
  private static NearbyPlace ewhaStation() {
    return new NearbyPlace(
        "이대역", "서울 서대문구 신촌로 205", "서울 서대문구 대현동 100-1", 37.556733, 126.946258, 780);
  }

  /** 좌표를 주지 않은 검색 결과 — 거리 정보가 없다. */
  private static NearbyPlace sinchonWithoutDistance() {
    return new NearbyPlace(
        "신촌역 2호선", "서울 서대문구 신촌로 90", "서울 서대문구 창천동 30-33", 37.555134, 126.936893, null);
  }

  private String landlordToken() {
    return bearer(jwtTokenService.issueAccessToken(LANDLORD_ID));
  }
}
