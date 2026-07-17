package com.kohere.user;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kohere.TestcontainersConfiguration;
import com.kohere.common.security.JwtTokenService;
import com.kohere.listing.api.BookingListingQueryService;
import com.kohere.listing.api.RoomOfferBookingView;
import com.kohere.user.api.UserAccountService;
import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * 내 차단 목록·해제 API 문서화 + 통합 테스트(/api/v1/users/me/blocks). docs/api/specs/01-auth-onboarding.md
 * §11·§12.
 *
 * <p>차단 생성은 예약 문맥(POST /api/v1/bookings/{id}/block)에서만 가능하므로, 예약 생성→차단으로 상태를 만든 뒤 목록·해제를 검증한다. 실제
 * 리포지토리에 기록되므로 {@link Transactional}로 각 메서드 종료 시 롤백해 격리한다.
 */
@SpringBootTest
@ExtendWith(RestDocumentationExtension.class)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class UserBlockDocsTest {

  private static final long TENANT_ID = 1L;
  private static final long LANDLORD_ID = 42L;
  private static final String LISTING_ID = "6858e2000000000000000001";
  private static final String ROOM_OFFER_ID = "6858e2000000000000000abc";
  private static final Pattern BOOKING_ID = Pattern.compile("\"bookingId\"\\s*:\\s*(\\d+)");

  @Autowired private WebApplicationContext context;
  @Autowired private JwtTokenService jwtTokenService;

  @MockitoBean private UserAccountService userAccountService;
  @MockitoBean private BookingListingQueryService listingQueryService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp(RestDocumentationContextProvider restDocumentation) {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .apply(documentationConfiguration(restDocumentation))
            .build();
  }

  private String token(long userId) {
    return "Bearer " + jwtTokenService.issueAccessToken(userId);
  }

  private RoomOfferBookingView offerView() {
    return new RoomOfferBookingView(
        LISTING_ID,
        ROOM_OFFER_ID,
        "강남역 도보 5분 원룸",
        "https://cdn.kohere.com/listings/" + LISTING_ID + "/thumb.jpg",
        "서울특별시 강남구 테헤란로 1",
        "101호 원룸",
        5_000_000,
        500_000,
        LocalDate.of(2026, 1, 1),
        LANDLORD_ID);
  }

  /** 세입자가 예약을 만들고 그 상대(임대인)를 차단한다 — 차단 상대 식별자 = LANDLORD_ID. */
  private void blockLandlordViaBooking() throws Exception {
    given(userAccountService.getUserType(TENANT_ID)).willReturn("TENANT");
    given(listingQueryService.findPublishedRoomOffer(anyString(), anyString()))
        .willReturn(Optional.of(offerView()));
    String body =
        mockMvc
            .perform(
                post("/api/v1/listings/{listingId}/bookings", LISTING_ID)
                    .header(HttpHeaders.AUTHORIZATION, token(TENANT_ID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"roomOfferId\":\""
                            + ROOM_OFFER_ID
                            + "\",\"moveInDate\":\"2030-01-01\",\"contractPeriod\":6}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Matcher m = BOOKING_ID.matcher(body);
    if (!m.find()) {
      throw new IllegalStateException("bookingId not found: " + body);
    }
    long bookingId = Long.parseLong(m.group(1));
    mockMvc
        .perform(
            post("/api/v1/bookings/{bookingId}/block", bookingId)
                .header(HttpHeaders.AUTHORIZATION, token(TENANT_ID)))
        .andExpect(status().isNoContent());
  }

  @Test
  void getMyBlocks_success() throws Exception {
    blockLandlordViaBooking();

    mockMvc
        .perform(
            get("/api/v1/users/me/blocks")
                .header(HttpHeaders.AUTHORIZATION, token(TENANT_ID))
                .param("page", "0")
                .param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].userId").value(LANDLORD_ID))
        .andDo(
            document(
                "user-blocks-list",
                queryParameters(
                    parameterWithName("page").optional().description("0-base 페이지 번호(기본 0)"),
                    parameterWithName("size").optional().description("페이지 크기(기본 20, 최대 100)")),
                responseFields(
                    fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                    fieldWithPath("data.content[].userId")
                        .type(JsonFieldType.NUMBER)
                        .description("차단한 상대 식별자"),
                    fieldWithPath("data.content[].name")
                        .type(JsonFieldType.STRING)
                        .description("차단한 상대 표시명(마스킹 수준 확인 필요)"),
                    fieldWithPath("data.content[].blockedAt")
                        .type(JsonFieldType.STRING)
                        .description("차단 시각(UTC)"),
                    fieldWithPath("data.page.number")
                        .type(JsonFieldType.NUMBER)
                        .description("현재 페이지 번호"),
                    fieldWithPath("data.page.size")
                        .type(JsonFieldType.NUMBER)
                        .description("페이지 크기"),
                    fieldWithPath("data.page.totalElements")
                        .type(JsonFieldType.NUMBER)
                        .description("전체 건수"),
                    fieldWithPath("data.page.totalPages")
                        .type(JsonFieldType.NUMBER)
                        .description("전체 페이지 수"),
                    fieldWithPath("data.page.hasNext")
                        .type(JsonFieldType.BOOLEAN)
                        .description("다음 페이지 존재 여부"),
                    fieldWithPath("error")
                        .type(JsonFieldType.NULL)
                        .optional()
                        .description("성공 응답의 error는 항상 null"))));
  }

  @Test
  void unblock_success() throws Exception {
    blockLandlordViaBooking();

    mockMvc
        .perform(
            delete("/api/v1/users/me/blocks/{userId}", LANDLORD_ID)
                .header(HttpHeaders.AUTHORIZATION, token(TENANT_ID)))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "user-blocks-unblock",
                pathParameters(parameterWithName("userId").description("차단을 해제할 상대 식별자"))));

    mockMvc
        .perform(get("/api/v1/users/me/blocks").header(HttpHeaders.AUTHORIZATION, token(TENANT_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isEmpty());
  }

  @Test
  void unblock_idempotent() throws Exception {
    // 차단하지 않은 상대를 해제해도 204(멱등)
    mockMvc
        .perform(
            delete("/api/v1/users/me/blocks/{userId}", LANDLORD_ID)
                .header(HttpHeaders.AUTHORIZATION, token(TENANT_ID)))
        .andExpect(status().isNoContent());
  }
}
