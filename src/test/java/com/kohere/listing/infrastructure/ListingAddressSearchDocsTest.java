package com.kohere.listing.infrastructure;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.resourceDetails;
import static com.kohere.docs.ApiDocsErrors.errorSnippet;
import static com.kohere.docs.DocsTokens.bearer;
import static com.kohere.docs.DocsTokens.expiredAccessToken;
import static com.kohere.docs.ListingDocsFields.LISTING_ADDRESS_SEARCH_400;
import static com.kohere.docs.ListingDocsFields.LISTING_ADDRESS_SEARCH_401;
import static com.kohere.docs.ListingDocsFields.LISTING_ADDRESS_SEARCH_403;
import static com.kohere.docs.ListingDocsFields.LISTING_ADDRESS_SEARCH_502;
import static com.kohere.docs.ListingDocsFields.LISTING_ADDRESS_SEARCH_DESCRIPTION;
import static com.kohere.docs.ListingDocsFields.LISTING_ADDRESS_SEARCH_SUMMARY;
import static com.kohere.docs.ListingDocsFields.addressQueryParameters;
import static com.kohere.docs.ListingDocsFields.addressSearchResponseFields;
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
import com.kohere.listing.domain.address.AddressSearchClient;
import com.kohere.listing.domain.address.AddressSearchResult;
import com.kohere.listing.domain.address.AddressSearchUpstreamException;
import com.kohere.user.api.UserAccountService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 도로명 주소 검색({@code GET /api/v1/listings/addresses})의 REST Docs 스니펫 생성 테스트다(ADR-0042).
 *
 * <p>외부 지오코딩은 {@link AddressSearchClient} 포트를 목으로 대체해 네트워크 없이 돌린다 — HTTP 계약 자체는 {@code
 * NcpGeocodeClientTest}가 검증한다. 응답은 제공자가 준 후보를 그대로 옮길 뿐이라 코드 카탈로그를 보지 않는다.
 *
 * <p>문구·필드 기술자는 {@code ListingDocsFields}에 한 벌만 두고 여기서는 흐름만 만든다(ADR-0017).
 */
@SpringBootTest
@ExtendWith(RestDocumentationExtension.class)
@ActiveProfiles("test")
@Testcontainers
@Import(TestcontainersConfiguration.class)
class ListingAddressSearchDocsTest {

  /** 주소 검색 권한이 있는 임대인 계정이다. */
  private static final long LANDLORD_ID = 42L;

  /** 정식 회원이지만 임대인이 아니라 서비스가 403으로 거르는 계정이다. */
  private static final long TENANT_ID = 1L;

  private static final String LISTING_CATALOG_COLLECTION = "listingCatalog";

  // 다른 키로 서명한 위조 access 토큰. 401 예시도 구조상 JWT여야 restdocs-api-spec이 bearerAuth 보안 스킴을
  // 도출해 Swagger 자물쇠가 유지된다(ListingRegisterDocsTest와 동일 처리).
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
  @Autowired private MongoTemplate mongoTemplate;
  @Autowired private JwtTokenService jwtTokenService;
  @Autowired private JwtProperties jwtProperties;

  @MockitoBean private AddressSearchClient addressSearchClient;
  @MockitoBean private UserAccountService userAccountService;

  private MockMvc mockMvc;

  /** REST Docs용 MockMvc를 만들고 등록과 같은 판정을 쓰도록 코드 카탈로그를 시드한다. */
  @BeforeEach
  void setUp(RestDocumentationContextProvider restDocumentation) {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .apply(documentationConfiguration(restDocumentation))
            .build();
    mongoTemplate.getCollection(LISTING_CATALOG_COLLECTION).deleteMany(new Document());
    ListingTestSeeds.seedCatalog(mongoTemplate, LISTING_CATALOG_COLLECTION);
    given(userAccountService.getUserType(LANDLORD_ID)).willReturn("LANDLORD");
    given(userAccountService.getUserType(TENANT_ID)).willReturn("TENANT");
  }

  /** 임대인 검색 성공 — 등록에 그대로 실을 주소·좌표와 등록 가능 여부를 함께 단정한다. */
  @Test
  void 문서스니펫생성_주소검색_후보를_그대로_반환() throws Exception {
    given(addressSearchClient.search("신촌로 12")).willReturn(List.of(seodaemun(), bundang()));

    mockMvc
        .perform(
            get("/api/v1/listings/addresses")
                .param("keyword", "신촌로 12")
                .header(HttpHeaders.AUTHORIZATION, landlordToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].roadAddress").value("서울특별시 서대문구 신촌로 12"))
        .andExpect(jsonPath("$.data.items[0].jibunAddress").value("서울특별시 서대문구 창천동 1-1"))
        .andExpect(jsonPath("$.data.items[0].lat").value(37.5559918))
        .andExpect(jsonPath("$.data.items[0].lng").value(126.9368647))
        // 카탈로그가 모르는 지역도 거르지 않는다 — 등록이 받고 행정구역을 ETC로 저장한다.
        .andExpect(jsonPath("$.data.items[1].roadAddress").value("경기도 성남시 분당구 불정로 6 NAVER그린팩토리"))
        .andExpect(jsonPath("$.data.items[0].supported").doesNotExist())
        .andDo(
            document(
                "listing-addresses",
                resourceDetails()
                    .tag(ApiDocsTags.LISTINGS)
                    .summary(LISTING_ADDRESS_SEARCH_SUMMARY)
                    .description(LISTING_ADDRESS_SEARCH_DESCRIPTION),
                queryParameters(addressQueryParameters()),
                responseFields(addressSearchResponseFields())));
  }

  /** 일치하는 주소가 없으면 장애가 아니라 빈 목록이다. */
  @Test
  void 주소검색_결과가없으면_빈items를반환한다() throws Exception {
    given(addressSearchClient.search("없는주소")).willReturn(List.of());

    mockMvc
        .perform(
            get("/api/v1/listings/addresses")
                .param("keyword", "없는주소")
                .header(HttpHeaders.AUTHORIZATION, landlordToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isEmpty());
  }

  /** 스펙의 "발생 가능한 에러"를 실제로 트리거해 status·error.code와 실패 응답 스니펫을 함께 만든다. */
  @Test
  void 문서스니펫생성_스펙에적힌실패조건_status와errorcode가일치() throws Exception {
    // 공개 조회 매처(GET /api/v1/listings/*)보다 먼저 선언한 인증 매처가 살아 있어야 401이다.
    performError(
        get("/api/v1/listings/addresses").param("keyword", "신촌로 12"),
        401,
        "UNAUTHENTICATED",
        "listing-addresses-unauthenticated",
        LISTING_ADDRESS_SEARCH_401);

    performError(
        get("/api/v1/listings/addresses")
            .param("keyword", "신촌로 12")
            .header(HttpHeaders.AUTHORIZATION, bearer(FORGED_TOKEN)),
        401,
        "UNAUTHENTICATED",
        "listing-addresses-forged-token",
        LISTING_ADDRESS_SEARCH_401);

    performError(
        get("/api/v1/listings/addresses")
            .param("keyword", "신촌로 12")
            .header(HttpHeaders.AUTHORIZATION, bearer(expiredAccessToken(jwtProperties))),
        401,
        "TOKEN_EXPIRED",
        "listing-addresses-token-expired",
        LISTING_ADDRESS_SEARCH_401);

    performError(
        get("/api/v1/listings/addresses")
            .param("keyword", "신촌로 12")
            .header(
                HttpHeaders.AUTHORIZATION, bearer(jwtTokenService.issueOnboardingToken(TENANT_ID))),
        403,
        "AUTH_ONBOARDING_REQUIRED",
        "listing-addresses-onboarding-required",
        LISTING_ADDRESS_SEARCH_403);

    // 임대인 여부는 서비스가 본다 — 세입자 정식 토큰은 컨트롤러까지 도달한 뒤 거절된다.
    performError(
        get("/api/v1/listings/addresses")
            .param("keyword", "신촌로 12")
            .header(HttpHeaders.AUTHORIZATION, bearer(jwtTokenService.issueAccessToken(TENANT_ID))),
        403,
        "FORBIDDEN",
        "listing-addresses-forbidden",
        LISTING_ADDRESS_SEARCH_403);

    performError(
        get("/api/v1/listings/addresses")
            .param("keyword", "   ")
            .header(HttpHeaders.AUTHORIZATION, landlordToken()),
        400,
        "INVALID_INPUT",
        "listing-addresses-invalid-input",
        LISTING_ADDRESS_SEARCH_400);

    given(addressSearchClient.search("신촌로 12"))
        .willThrow(
            new AddressSearchUpstreamException(
                new IllegalStateException("NCP test upstream unavailable")));
    performError(
        get("/api/v1/listings/addresses")
            .param("keyword", "신촌로 12")
            .header(HttpHeaders.AUTHORIZATION, landlordToken()),
        502,
        "UPSTREAM_ERROR",
        "listing-addresses-upstream-error",
        LISTING_ADDRESS_SEARCH_502);
  }

  private void performError(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
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
                LISTING_ADDRESS_SEARCH_SUMMARY,
                LISTING_ADDRESS_SEARCH_DESCRIPTION,
                errorCodes));
  }

  /** 서울 서대문구 — 카탈로그가 시·도와 구·군을 모두 아는 등록 가능한 주소다. */
  private static AddressSearchResult seodaemun() {
    return new AddressSearchResult(
        "서울특별시 서대문구 신촌로 12",
        "서울특별시 서대문구 창천동 1-1",
        "12, Sinchon-ro, Seodaemun-gu, Seoul, Republic of Korea",
        37.5559918,
        126.9368647);
  }

  /** 경기도 성남시 분당구 — 카탈로그가 구·군을 모르는 지역이다. 등록되면 district가 ETC가 된다. */
  private static AddressSearchResult bundang() {
    return new AddressSearchResult(
        "경기도 성남시 분당구 불정로 6 NAVER그린팩토리",
        "경기도 성남시 분당구 정자동 178-1 NAVER그린팩토리",
        "6, Buljeong-ro, Bundang-gu, Seongnam-si, Gyeonggi-do, Republic of Korea",
        37.3595963,
        127.1054328);
  }

  private String landlordToken() {
    return bearer(jwtTokenService.issueAccessToken(LANDLORD_ID));
  }
}
