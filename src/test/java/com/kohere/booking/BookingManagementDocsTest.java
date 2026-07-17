package com.kohere.booking;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.kohere.TestcontainersConfiguration;
import com.kohere.common.security.JwtTokenService;
import com.kohere.listing.api.BookingListingQueryService;
import com.kohere.listing.api.RoomOfferBookingView;
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
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * 예약 내역 관리(삭제·차단·신고·신고 사유) API 문서화 + 통합 테스트. docs/api/specs/04-booking-inquiry-chat.md §4~§7.
 *
 * <p>실제 MySQL(Testcontainers, JPA)에 저장하고 교차 모듈 협력({@code listing :: api}·{@code user :: api}의
 * {@code getUserType}·{@code getLanguage} 등)은 {@link MockitoBean}으로 대체한다. 차단(user_blocks)·삭제는 실제
 * 리포지토리에 기록되므로 테스트 격리를 위해 {@link Transactional}로 각 메서드 종료 시 롤백한다.
 */
@SpringBootTest
@ExtendWith(RestDocumentationExtension.class)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class BookingManagementDocsTest {

  private static final long TENANT_ID = 1L;
  private static final long LANDLORD_ID = 42L;
  private static final long OUTSIDER_ID = 99L;
  private static final String LISTING_ID = "6858e2000000000000000001";
  private static final String ROOM_OFFER_ID = "6858e2000000000000000abc";
  private static final Pattern BOOKING_ID = Pattern.compile("\"bookingId\"\\s*:\\s*(\\d+)");

  @Autowired private WebApplicationContext context;
  @Autowired private JwtTokenService jwtTokenService;

  @MockitoBean private com.kohere.user.api.UserAccountService userAccountService;
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

  /** 세입자로 예약 1건 생성하고 bookingId를 반환한다(차단 없는 상태). */
  private long createBooking() throws Exception {
    given(userAccountService.getUserType(TENANT_ID)).willReturn("TENANT");
    given(listingQueryService.findPublishedRoomOffer(LISTING_ID, ROOM_OFFER_ID))
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
    return Long.parseLong(m.group(1));
  }

  // ── §4 예약 내역 삭제 ─────────────────────────────────────────
  @Test
  void deleteBooking_success() throws Exception {
    long bookingId = createBooking();

    mockMvc
        .perform(
            delete("/api/v1/bookings/{bookingId}", bookingId)
                .header(HttpHeaders.AUTHORIZATION, token(TENANT_ID)))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "booking-delete",
                pathParameters(
                    parameterWithName("bookingId").description("삭제할 예약 식별자(본인 참여 예약)"))));
  }

  @Test
  void deleteBooking_idempotent() throws Exception {
    long bookingId = createBooking();
    for (int i = 0; i < 2; i++) {
      mockMvc
          .perform(
              delete("/api/v1/bookings/{bookingId}", bookingId)
                  .header(HttpHeaders.AUTHORIZATION, token(TENANT_ID)))
          .andExpect(status().isNoContent());
    }
  }

  @Test
  void deleteBooking_notParticipant_notFound() throws Exception {
    long bookingId = createBooking();

    perform(
        delete("/api/v1/bookings/{bookingId}", bookingId)
            .header(HttpHeaders.AUTHORIZATION, token(OUTSIDER_ID)),
        status().isNotFound(),
        "BOOKING_NOT_FOUND",
        "booking-delete-not-found",
        "참여자가 아니거나 없는 예약 삭제는 404 BOOKING_NOT_FOUND(존재 비노출)");
  }

  @Test
  void deletedBooking_excludedFromList() throws Exception {
    long bookingId = createBooking();
    mockMvc
        .perform(
            delete("/api/v1/bookings/{bookingId}", bookingId)
                .header(HttpHeaders.AUTHORIZATION, token(TENANT_ID)))
        .andExpect(status().isNoContent());
    given(userAccountService.getUserType(TENANT_ID)).willReturn("TENANT");
    given(listingQueryService.findPublishedRoomOffer(anyString(), anyString()))
        .willReturn(Optional.of(offerView()));

    mockMvc
        .perform(get("/api/v1/bookings").header(HttpHeaders.AUTHORIZATION, token(TENANT_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[?(@.bookingId == " + bookingId + ")]").isEmpty());
  }

  // ── §5 예약 상대 차단 ─────────────────────────────────────────
  @Test
  void blockBooking_success() throws Exception {
    long bookingId = createBooking();

    mockMvc
        .perform(
            post("/api/v1/bookings/{bookingId}/block", bookingId)
                .header(HttpHeaders.AUTHORIZATION, token(TENANT_ID)))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "booking-block",
                pathParameters(
                    parameterWithName("bookingId").description("차단 대상 상대를 도출할 예약 식별자(본인 참여 예약)"))));
  }

  @Test
  void blockBooking_idempotent() throws Exception {
    long bookingId = createBooking();
    for (int i = 0; i < 2; i++) {
      mockMvc
          .perform(
              post("/api/v1/bookings/{bookingId}/block", bookingId)
                  .header(HttpHeaders.AUTHORIZATION, token(TENANT_ID)))
          .andExpect(status().isNoContent());
    }
  }

  @Test
  void createBooking_blockedCounterpart_forbidden() throws Exception {
    long bookingId = createBooking();
    mockMvc
        .perform(
            post("/api/v1/bookings/{bookingId}/block", bookingId)
                .header(HttpHeaders.AUTHORIZATION, token(TENANT_ID)))
        .andExpect(status().isNoContent());

    // 차단 후 같은 상대(매물 소유자) 매물에 신규 신청 → 403(양방향 가드, 블랙홀 예약 방지)
    given(userAccountService.getUserType(TENANT_ID)).willReturn("TENANT");
    given(listingQueryService.findPublishedRoomOffer(LISTING_ID, ROOM_OFFER_ID))
        .willReturn(Optional.of(offerView()));
    perform(
        post("/api/v1/listings/{listingId}/bookings", LISTING_ID)
            .header(HttpHeaders.AUTHORIZATION, token(TENANT_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"roomOfferId\":\""
                    + ROOM_OFFER_ID
                    + "\",\"moveInDate\":\"2030-01-01\",\"contractPeriod\":6}"),
        status().isForbidden(),
        "FORBIDDEN",
        "booking-create-blocked",
        "차단 관계(양방향)인 상대 매물에 신규 신청 시 403 FORBIDDEN");
  }

  // ── §6 예약 신고 ─────────────────────────────────────────────
  @Test
  void reportBooking_success() throws Exception {
    long bookingId = createBooking();

    mockMvc
        .perform(
            post("/api/v1/bookings/{bookingId}/report", bookingId)
                .header(HttpHeaders.AUTHORIZATION, token(TENANT_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"ABUSE\",\"detail\":\"욕설을 했습니다\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.bookingId").value(bookingId))
        .andExpect(jsonPath("$.data.reason").value("ABUSE"))
        .andDo(
            document(
                "booking-report",
                pathParameters(
                    parameterWithName("bookingId").description("신고 대상 예약 식별자(본인 참여 예약)")),
                requestFields(
                    optField("reason", JsonFieldType.STRING, "신고 사유(선택, BookingReportReason)"),
                    optField("detail", JsonFieldType.STRING, "신고 상세(선택, 최대 500자·응답 비노출)")),
                responseFields(
                    field("success", JsonFieldType.BOOLEAN, "성공 여부"),
                    field("data.reportId", JsonFieldType.NUMBER, "신고 식별자"),
                    field("data.bookingId", JsonFieldType.NUMBER, "신고 대상 예약 식별자"),
                    optField("data.reason", JsonFieldType.STRING, "신고 사유(없이 신고 시 null)"),
                    field("data.createdAt", JsonFieldType.STRING, "신고 접수 일시(UTC)"),
                    errorNull())));
  }

  @Test
  void reportBooking_withoutReason_created() throws Exception {
    long bookingId = createBooking();

    mockMvc
        .perform(
            post("/api/v1/bookings/{bookingId}/report", bookingId)
                .header(HttpHeaders.AUTHORIZATION, token(TENANT_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.reason").isEmpty());
  }

  @Test
  void reportBooking_multipleAllowed() throws Exception {
    long bookingId = createBooking();
    // 동일 신고자가 동일 예약을 여러 번 신고할 수 있다(다건 허용 · 도배 방지는 후속 레이트리밋).
    for (String reason : new String[] {"SPAM", "ABUSE"}) {
      mockMvc
          .perform(
              post("/api/v1/bookings/{bookingId}/report", bookingId)
                  .header(HttpHeaders.AUTHORIZATION, token(TENANT_ID))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"reason\":\"" + reason + "\"}"))
          .andExpect(status().isCreated());
    }
  }

  @Test
  void reportBooking_notParticipant_notFound() throws Exception {
    long bookingId = createBooking();

    perform(
        post("/api/v1/bookings/{bookingId}/report", bookingId)
            .header(HttpHeaders.AUTHORIZATION, token(OUTSIDER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"SPAM\"}"),
        status().isNotFound(),
        "BOOKING_NOT_FOUND",
        "booking-report-not-found",
        "참여자가 아니면 404 BOOKING_NOT_FOUND(존재 비노출)");
  }

  // ── §7 예약 신고 사유 목록(서버 번역) ─────────────────────────
  @Test
  void getReportReasons_translatedByUserLanguage() throws Exception {
    given(userAccountService.getLanguage(TENANT_ID)).willReturn("ko");

    mockMvc
        .perform(
            get("/api/v1/bookings/report-reasons")
                .header(HttpHeaders.AUTHORIZATION, token(TENANT_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.reasons[0].code").value("SPAM"))
        .andExpect(jsonPath("$.data.reasons[0].label").value("스팸/광고"))
        .andDo(
            document(
                "booking-report-reasons",
                responseFields(
                    field("success", JsonFieldType.BOOLEAN, "성공 여부"),
                    field("data.reasons[].code", JsonFieldType.STRING, "사유 코드(언어 무관 불변)"),
                    field("data.reasons[].label", JsonFieldType.STRING, "사유 표시명(사용자 표시 언어로 서버 번역)"),
                    errorNull())));
  }

  private void perform(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
      org.springframework.test.web.servlet.ResultMatcher expectedStatus,
      String expectedCode,
      String identifier,
      String summary)
      throws Exception {
    mockMvc
        .perform(request)
        .andExpect(expectedStatus)
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value(expectedCode))
        .andDo(
            document(
                identifier,
                resource(
                    ResourceSnippetParameters.builder()
                        .summary(summary)
                        .description("실패 응답 — 공통 래퍼(success=false·data=null·error).")
                        .responseFields(errorFields())
                        .build())));
  }

  private static FieldDescriptor field(String path, JsonFieldType type, String description) {
    return fieldWithPath(path).type(type).description(description);
  }

  private static FieldDescriptor optField(String path, JsonFieldType type, String description) {
    return fieldWithPath(path).type(type).optional().description(description);
  }

  private static FieldDescriptor errorNull() {
    return fieldWithPath("error")
        .type(JsonFieldType.NULL)
        .optional()
        .description("성공 응답의 error는 항상 null");
  }

  private static FieldDescriptor[] errorFields() {
    return new FieldDescriptor[] {
      fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부(false)"),
      fieldWithPath("data").type(JsonFieldType.NULL).optional().description("실패 시 null"),
      fieldWithPath("error.code").type(JsonFieldType.STRING).description("에러 코드"),
      fieldWithPath("error.message").type(JsonFieldType.STRING).description("에러 메시지"),
      fieldWithPath("error.errors").type(JsonFieldType.ARRAY).description("필드 오류 목록(없으면 빈 배열)"),
      fieldWithPath("error.errors[].field")
          .type(JsonFieldType.STRING)
          .optional()
          .description("위반 필드"),
      fieldWithPath("error.errors[].reason")
          .type(JsonFieldType.STRING)
          .optional()
          .description("위반 사유")
    };
  }
}
