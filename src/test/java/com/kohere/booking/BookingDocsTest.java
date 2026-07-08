package com.kohere.booking;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.kohere.TestcontainersConfiguration;
import com.kohere.common.security.JwtTokenService;
import com.kohere.listing.api.BookingListingQueryService;
import com.kohere.listing.api.RoomOfferBookingView;
import com.kohere.user.api.ApplicantProfileView;
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
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 매물 예약(신청) API 문서화 + 통합 테스트. docs/api/specs/04-booking-inquiry-chat.md §1~§3. 예약 생성·내 예약 목록·단건 상세와
 * 주요 에러(403 비세입자·404 매물/예약 부재·422 입주일·400 입력)를 검증하고 restdocs-api-spec 스니펫(OpenAPI)을 생성한다.
 *
 * <p>예약 저장은 실제 MySQL(Testcontainers, JPA)에 하고, 교차 모듈 협력({@code listing :: api}·{@code user ::
 * api})은 {@link MockitoBean}으로 대체한다(listing::api는 스텁 미구현). 인증은 실제 access 토큰(ROLE_USER)을 발급해 사용한다.
 */
@SpringBootTest
@ExtendWith(RestDocumentationExtension.class)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class BookingDocsTest {

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

  private String tenantToken() {
    return "Bearer " + jwtTokenService.issueAccessToken(TENANT_ID);
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

  private String landlordToken() {
    return "Bearer " + jwtTokenService.issueAccessToken(LANDLORD_ID);
  }

  private static String createRequestJson(String roomOfferId, String moveInDate, Integer months) {
    return String.format(
        "{\"roomOfferId\":%s,\"moveInDate\":%s,\"contractPeriod\":%s}",
        roomOfferId == null ? "null" : "\"" + roomOfferId + "\"",
        moveInDate == null ? "null" : "\"" + moveInDate + "\"",
        months == null ? "null" : months);
  }

  private long createBooking() throws Exception {
    given(userAccountService.getUserType(TENANT_ID)).willReturn("TENANT");
    given(listingQueryService.findPublishedRoomOffer(LISTING_ID, ROOM_OFFER_ID))
        .willReturn(Optional.of(offerView()));
    String body =
        mockMvc
            .perform(
                post("/api/v1/listings/{listingId}/bookings", LISTING_ID)
                    .header(HttpHeaders.AUTHORIZATION, tenantToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequestJson(ROOM_OFFER_ID, "2030-01-01", 6)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Matcher m = BOOKING_ID.matcher(body);
    if (!m.find()) {
      throw new IllegalStateException("bookingId not found in response: " + body);
    }
    return Long.parseLong(m.group(1));
  }

  // ── §1 예약 생성 ─────────────────────────────────────────────
  @Test
  void createBooking_success() throws Exception {
    given(userAccountService.getUserType(TENANT_ID)).willReturn("TENANT");
    given(listingQueryService.findPublishedRoomOffer(LISTING_ID, ROOM_OFFER_ID))
        .willReturn(Optional.of(offerView()));

    mockMvc
        .perform(
            post("/api/v1/listings/{listingId}/bookings", LISTING_ID)
                .header(HttpHeaders.AUTHORIZATION, tenantToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson(ROOM_OFFER_ID, "2030-01-01", 6)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("REQUESTED"))
        .andExpect(jsonPath("$.data.roomOfferId").value(ROOM_OFFER_ID))
        .andExpect(jsonPath("$.data.contractPeriod").value(6))
        .andDo(
            document(
                "booking-create",
                pathParameters(
                    parameterWithName("listingId").description("예약 대상 매물 ID(ObjectId hex)")),
                requestFields(
                    field("roomOfferId", JsonFieldType.STRING, "예약 대상 방 상품 ID"),
                    field("moveInDate", JsonFieldType.STRING, "타겟 입주일(YYYY-MM-DD)"),
                    field("contractPeriod", JsonFieldType.NUMBER, "계약 개월수(양의 정수)")),
                responseFields(
                    field("success", JsonFieldType.BOOLEAN, "성공 여부"),
                    field("data.bookingId", JsonFieldType.NUMBER, "예약 식별자"),
                    field("data.status", JsonFieldType.STRING, "예약 상태(REQUESTED 고정)"),
                    field("data.listingId", JsonFieldType.STRING, "매물 ID"),
                    field("data.roomOfferId", JsonFieldType.STRING, "방 상품 ID"),
                    field("data.moveInDate", JsonFieldType.STRING, "타겟 입주일"),
                    field("data.contractPeriod", JsonFieldType.NUMBER, "계약 개월수"),
                    field("data.createdAt", JsonFieldType.STRING, "예약 일시(UTC)"),
                    errorNull())));
  }

  // ── §2 내 예약 목록 ───────────────────────────────────────────
  @Test
  void getMyBookings_success() throws Exception {
    createBooking();
    given(listingQueryService.findPublishedRoomOffer(anyString(), anyString()))
        .willReturn(Optional.of(offerView()));

    mockMvc
        .perform(
            get("/api/v1/bookings")
                .header(HttpHeaders.AUTHORIZATION, tenantToken())
                .param("page", "0")
                .param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isArray())
        .andDo(
            document(
                "booking-list",
                queryParameters(
                    parameterWithName("page").optional().description("0-base 페이지 번호(기본 0)"),
                    parameterWithName("size").optional().description("페이지 크기(기본 20, 최대 100)")),
                responseFields(
                    field("success", JsonFieldType.BOOLEAN, "성공 여부"),
                    field("data.content[].bookingId", JsonFieldType.NUMBER, "예약 식별자"),
                    field("data.content[].listingId", JsonFieldType.STRING, "매물 ID"),
                    optField(
                        "data.content[].title", JsonFieldType.STRING, "매물 제목(조회 시 조인, 삭제 시 null)"),
                    optField(
                        "data.content[].thumbnailUrl", JsonFieldType.STRING, "매물 썸네일(삭제 시 null)"),
                    field("data.content[].roomOfferId", JsonFieldType.STRING, "방 상품 ID"),
                    field("data.content[].moveInDate", JsonFieldType.STRING, "타겟 입주일"),
                    field("data.content[].contractPeriod", JsonFieldType.NUMBER, "계약 개월수"),
                    field("data.content[].status", JsonFieldType.STRING, "예약 상태"),
                    field("data.content[].createdAt", JsonFieldType.STRING, "예약 일시"),
                    field("data.page.number", JsonFieldType.NUMBER, "현재 페이지 번호"),
                    field("data.page.size", JsonFieldType.NUMBER, "페이지 크기"),
                    field("data.page.totalElements", JsonFieldType.NUMBER, "전체 건수"),
                    field("data.page.totalPages", JsonFieldType.NUMBER, "전체 페이지 수"),
                    field("data.page.hasNext", JsonFieldType.BOOLEAN, "다음 페이지 존재 여부"),
                    errorNull())));
  }

  // ── §3 예약 단건 상세 ─────────────────────────────────────────
  @Test
  void getBooking_success() throws Exception {
    long bookingId = createBooking();
    given(listingQueryService.findPublishedRoomOffer(LISTING_ID, ROOM_OFFER_ID))
        .willReturn(Optional.of(offerView()));
    given(userAccountService.getUserName(TENANT_ID)).willReturn("길동 홍");

    mockMvc
        .perform(
            get("/api/v1/bookings/{bookingId}", bookingId)
                .header(HttpHeaders.AUTHORIZATION, tenantToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.bookingId").value(bookingId))
        .andExpect(jsonPath("$.data.deposit").value(5_000_000))
        .andExpect(jsonPath("$.data.totalAmount").value(8_000_000)) // 5,000,000 + 500,000 × 6
        .andExpect(jsonPath("$.data.tenantName").value("길동 홍"))
        .andDo(
            document(
                "booking-detail",
                pathParameters(parameterWithName("bookingId").description("예약 식별자(본인 예약)")),
                responseFields(
                    field("success", JsonFieldType.BOOLEAN, "성공 여부"),
                    field("data.bookingId", JsonFieldType.NUMBER, "예약 식별자"),
                    field("data.status", JsonFieldType.STRING, "예약 상태"),
                    field("data.listingId", JsonFieldType.STRING, "매물 ID"),
                    field("data.roomOfferId", JsonFieldType.STRING, "방 상품 ID"),
                    optField("data.title", JsonFieldType.STRING, "매물 제목(조회 시 조인)"),
                    optField("data.thumbnailUrl", JsonFieldType.STRING, "매물 썸네일"),
                    optField("data.address", JsonFieldType.STRING, "매물 주소"),
                    optField("data.roomOfferName", JsonFieldType.STRING, "방 상품명"),
                    field("data.createdAt", JsonFieldType.STRING, "예약 일시(UTC)"),
                    field("data.moveInDate", JsonFieldType.STRING, "타겟 입주일"),
                    field("data.contractPeriod", JsonFieldType.NUMBER, "계약 개월수"),
                    field("data.tenantName", JsonFieldType.STRING, "예약자 성명"),
                    field("data.deposit", JsonFieldType.NUMBER, "보증금(KRW)"),
                    field(
                        "data.totalAmount",
                        JsonFieldType.NUMBER,
                        "총 금액 = 보증금 + 월세 × 계약 개월수(관리비 제외)"),
                    errorNull())));
  }

  // ── §2 임대인 분기 — 받은 신청 목록 ───────────────────────────
  @Test
  void getBookings_landlord_success() throws Exception {
    createBooking(); // 생성 시 landlord_id = LANDLORD_ID(offerView) 저장
    given(userAccountService.getUserType(LANDLORD_ID)).willReturn("LANDLORD");
    given(listingQueryService.findPublishedRoomOffer(anyString(), anyString()))
        .willReturn(Optional.of(offerView()));
    given(userAccountService.getUserName(TENANT_ID)).willReturn("길동 홍");

    mockMvc
        .perform(
            get("/api/v1/bookings")
                .header(HttpHeaders.AUTHORIZATION, landlordToken())
                .param("page", "0")
                .param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].applicantName").value("길동 홍"))
        .andExpect(jsonPath("$.data.content[0].roomOfferName").value("101호 원룸"))
        .andDo(
            document(
                "booking-list-landlord",
                queryParameters(
                    parameterWithName("page").optional().description("0-base 페이지 번호(기본 0)"),
                    parameterWithName("size").optional().description("페이지 크기(기본 20, 최대 100)")),
                responseFields(
                    field("success", JsonFieldType.BOOLEAN, "성공 여부"),
                    field("data.content[].bookingId", JsonFieldType.NUMBER, "예약 식별자"),
                    field("data.content[].listingId", JsonFieldType.STRING, "매물 ID"),
                    optField(
                        "data.content[].title", JsonFieldType.STRING, "매물 제목(조회 시 조인, 삭제 시 null)"),
                    optField(
                        "data.content[].thumbnailUrl", JsonFieldType.STRING, "매물 썸네일(삭제 시 null)"),
                    field("data.content[].roomOfferId", JsonFieldType.STRING, "방 상품 ID"),
                    optField(
                        "data.content[].roomOfferName", JsonFieldType.STRING, "방 상품명(조회 시 조인)"),
                    field("data.content[].applicantName", JsonFieldType.STRING, "신청자(세입자) 성명"),
                    field("data.content[].moveInDate", JsonFieldType.STRING, "타겟 입주일"),
                    field("data.content[].contractPeriod", JsonFieldType.NUMBER, "계약 개월수"),
                    field("data.content[].status", JsonFieldType.STRING, "예약 상태"),
                    field("data.content[].createdAt", JsonFieldType.STRING, "예약 일시"),
                    field("data.page.number", JsonFieldType.NUMBER, "현재 페이지 번호"),
                    field("data.page.size", JsonFieldType.NUMBER, "페이지 크기"),
                    field("data.page.totalElements", JsonFieldType.NUMBER, "전체 건수"),
                    field("data.page.totalPages", JsonFieldType.NUMBER, "전체 페이지 수"),
                    field("data.page.hasNext", JsonFieldType.BOOLEAN, "다음 페이지 존재 여부"),
                    errorNull())));
  }

  // ── §3 임대인 분기 — 받은 신청 단건 상세 ──────────────────────
  @Test
  void getBooking_landlord_success() throws Exception {
    long bookingId = createBooking();
    given(userAccountService.getUserType(LANDLORD_ID)).willReturn("LANDLORD");
    given(listingQueryService.findPublishedRoomOffer(LISTING_ID, ROOM_OFFER_ID))
        .willReturn(Optional.of(offerView()));
    given(userAccountService.getApplicantProfile(TENANT_ID))
        .willReturn(
            new ApplicantProfileView(
                TENANT_ID, "길동 홍", "MALE", "US", "United States", "hong@example.com"));

    mockMvc
        .perform(
            get("/api/v1/bookings/{bookingId}", bookingId)
                .header(HttpHeaders.AUTHORIZATION, landlordToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.bookingId").value(bookingId))
        .andExpect(jsonPath("$.data.applicantName").value("길동 홍"))
        .andExpect(jsonPath("$.data.applicantEmail").value("hong@example.com"))
        .andExpect(jsonPath("$.data.totalAmount").value(8_000_000)) // 5,000,000 + 500,000 × 6
        .andDo(
            document(
                "booking-detail-landlord",
                pathParameters(
                    parameterWithName("bookingId").description("예약 식별자(내 소유 매물에 신청된 예약)")),
                responseFields(
                    field("success", JsonFieldType.BOOLEAN, "성공 여부"),
                    field("data.bookingId", JsonFieldType.NUMBER, "예약 식별자"),
                    field("data.status", JsonFieldType.STRING, "예약 상태"),
                    field("data.listingId", JsonFieldType.STRING, "매물 ID"),
                    field("data.roomOfferId", JsonFieldType.STRING, "방 상품 ID"),
                    optField("data.title", JsonFieldType.STRING, "매물 제목(조회 시 조인)"),
                    optField("data.thumbnailUrl", JsonFieldType.STRING, "매물 썸네일"),
                    optField("data.address", JsonFieldType.STRING, "매물 주소"),
                    optField("data.roomOfferName", JsonFieldType.STRING, "방 상품명"),
                    field("data.createdAt", JsonFieldType.STRING, "예약 일시(UTC)"),
                    field("data.moveInDate", JsonFieldType.STRING, "타겟 입주일"),
                    field("data.contractPeriod", JsonFieldType.NUMBER, "계약 개월수"),
                    field("data.applicantId", JsonFieldType.NUMBER, "신청자(세입자) ID"),
                    field("data.applicantName", JsonFieldType.STRING, "신청자 성명"),
                    optField(
                        "data.applicantGender",
                        JsonFieldType.STRING,
                        "신청자 성별(UPPER_SNAKE, 마스킹 없음 · 탈퇴 시 null)"),
                    optField(
                        "data.applicantCountry", JsonFieldType.STRING, "신청자 국적 ISO 코드(탈퇴 시 null)"),
                    optField(
                        "data.applicantCountryName", JsonFieldType.STRING, "신청자 국적 표시명(탈퇴 시 null)"),
                    optField(
                        "data.applicantEmail", JsonFieldType.STRING, "신청자 이메일(마스킹 없음 · 탈퇴 시 null)"),
                    field("data.deposit", JsonFieldType.NUMBER, "보증금(KRW)"),
                    field(
                        "data.totalAmount",
                        JsonFieldType.NUMBER,
                        "총 금액 = 보증금 + 월세 × 계약 개월수(관리비 제외, 세입자 분기와 동일)"),
                    errorNull())));
  }

  // ── 에러 ─────────────────────────────────────────────────────
  @Test
  void createBooking_nonTenant_forbidden() throws Exception {
    given(userAccountService.getUserType(TENANT_ID)).willReturn("LANDLORD");

    perform(
        post("/api/v1/listings/{listingId}/bookings", LISTING_ID)
            .header(HttpHeaders.AUTHORIZATION, tenantToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(createRequestJson(ROOM_OFFER_ID, "2030-01-01", 6)),
        status().isForbidden(),
        "FORBIDDEN",
        "booking-create-forbidden",
        "예약은 세입자 전용 — 임대인 등 비세입자 요청은 403 FORBIDDEN");
  }

  @Test
  void createBooking_listingNotFound() throws Exception {
    given(userAccountService.getUserType(TENANT_ID)).willReturn("TENANT");
    given(listingQueryService.findPublishedRoomOffer(anyString(), anyString()))
        .willReturn(Optional.empty());

    perform(
        post("/api/v1/listings/{listingId}/bookings", LISTING_ID)
            .header(HttpHeaders.AUTHORIZATION, tenantToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(createRequestJson(ROOM_OFFER_ID, "2030-01-01", 6)),
        status().isNotFound(),
        "LISTING_NOT_FOUND",
        "booking-create-listing-not-found",
        "매물/방 상품이 없거나 비공개면 404 LISTING_NOT_FOUND");
  }

  @Test
  void createBooking_pastMoveInDate_unprocessable() throws Exception {
    given(userAccountService.getUserType(TENANT_ID)).willReturn("TENANT");
    given(listingQueryService.findPublishedRoomOffer(LISTING_ID, ROOM_OFFER_ID))
        .willReturn(Optional.of(offerView()));

    perform(
        post("/api/v1/listings/{listingId}/bookings", LISTING_ID)
            .header(HttpHeaders.AUTHORIZATION, tenantToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(createRequestJson(ROOM_OFFER_ID, "2020-01-01", 6)),
        status().isUnprocessableEntity(),
        "BOOKING_INVALID_MOVE_IN_DATE",
        "booking-create-invalid-move-in-date",
        "타겟 입주일이 과거이거나 입주 가능일 이전이면 422");
  }

  @Test
  void createBooking_missingRoomOfferId_invalidInput() throws Exception {
    given(userAccountService.getUserType(TENANT_ID)).willReturn("TENANT");

    perform(
        post("/api/v1/listings/{listingId}/bookings", LISTING_ID)
            .header(HttpHeaders.AUTHORIZATION, tenantToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(createRequestJson(null, "2030-01-01", 6)),
        status().isBadRequest(),
        "INVALID_INPUT",
        "booking-create-invalid-input",
        "필수값 누락·contractPeriod 비양수는 400 INVALID_INPUT");
  }

  @Test
  void getBooking_notFound() throws Exception {
    perform(
        get("/api/v1/bookings/{bookingId}", 999_999L)
            .header(HttpHeaders.AUTHORIZATION, tenantToken()),
        status().isNotFound(),
        "BOOKING_NOT_FOUND",
        "booking-detail-not-found",
        "예약이 없거나 본인 예약이 아니면 404 BOOKING_NOT_FOUND");
  }

  @Test
  void createBooking_unauthenticated() throws Exception {
    perform(
        post("/api/v1/listings/{listingId}/bookings", LISTING_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createRequestJson(ROOM_OFFER_ID, "2030-01-01", 6)),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "booking-create-unauthenticated",
        "토큰이 없으면 401 UNAUTHENTICATED");
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
