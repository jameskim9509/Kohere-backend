package com.kohere.booking;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.resourceDetails;
import static com.kohere.docs.ApiDocsFields.codeField;
import static com.kohere.docs.ApiDocsFields.errorNull;
import static com.kohere.docs.ApiDocsFields.field;
import static com.kohere.docs.ApiDocsFields.optCodeField;
import static com.kohere.docs.ApiDocsFields.optField;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kohere.TestcontainersConfiguration;
import com.kohere.common.security.JwtTokenService;
import com.kohere.docs.ApiDocsErrors;
import com.kohere.docs.ApiDocsTags;
import com.kohere.docs.BookingDocsFields;
import com.kohere.listing.api.BookingListingQueryService;
import com.kohere.listing.api.RoomOfferBookingView;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.restdocs.request.ParameterDescriptor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * 예약 내역 관리(삭제·차단·신고·신고 사유) API 문서화 + 통합 테스트. docs/api/specs/04-booking-inquiry-chat.md §4~§7.
 *
 * <p>실제 MySQL(Testcontainers, JPA)에 저장하고 교차 모듈 협력({@code listing :: api}·{@code user :: api}의
 * {@code getUserType}·{@code getLanguage} 등)은 {@link MockitoBean}으로 대체한다. 차단(user_blocks)·삭제는 실제
 * 리포지토리에 기록되므로 테스트 격리를 위해 {@link Transactional}로 각 메서드 종료 시 롤백한다.
 *
 * <p>차단 관계 403({@code booking-create-blocked})만은 예약 <b>생성</b> 오퍼레이션({@code POST
 * /api/v1/listings/{listingId}/bookings})에 병합되므로 문구·path 파라미터·에러 코드 배열을 {@link BookingDocsFields}에서
 * 가져온다 — 같은 {@code (path, method)}의 summary/description은 첫 non-blank 하나만 채택되기 때문이다(#151).
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

  // ── §4 예약 내역 삭제 — DELETE /api/v1/bookings/{bookingId} ────────────────
  private static final String DELETE_SUMMARY = "예약 내역 삭제";

  private static final String DELETE_DESCRIPTION =
      """
      내 목록·상세에서 예약을 숨긴다. 예약 행 자체는 지우지 않는다.

      인증: 필수. 정식(`ACTIVE`) 회원 중 해당 예약의 참여자(세입자 또는 임대인)만 호출할 수 있다. 세입자·임대인 공통이라 역할 403은 없다.

      - **취소가 아니다.** 요청자 쪽 소프트삭제 컬럼만 세팅하므로 **상대 목록에는 그대로 보이고** 상대에게 알림도 가지 않는다. 두 참여자의 삭제는 서로 완전히 독립이다.
      - 멱등이다 — 이미 삭제한 예약을 다시 삭제해도 204다(변이 경로는 삭제·차단 필터가 걸리지 않은 조회를 쓴다).
      - 차단(`POST .../block`)과 무관하다. 삭제해도 상대는 여전히 새 신청을 보낼 수 있다.
      - 삭제해도 신고(`POST .../report`)는 계속 가능하다(증거 보존).
      - 성공 응답은 본문이 없다(204). 공통 래퍼도 없다.
      - 참여자가 아니거나 없는 예약이면 403이 아니라 404 `BOOKING_NOT_FOUND`다(존재 비노출).

      에러 코드 — 400 `MALFORMED_REQUEST` / 401 `UNAUTHENTICATED`·`TOKEN_EXPIRED` / 403 `AUTH_ONBOARDING_REQUIRED` / 404 `BOOKING_NOT_FOUND`
      """;

  private static final String[] DELETE_404 = {"BOOKING_NOT_FOUND"};

  // ── §5 예약 상대 차단 — POST /api/v1/bookings/{bookingId}/block ────────────
  private static final String BLOCK_SUMMARY = "예약 상대 차단";

  private static final String BLOCK_DESCRIPTION =
      """
      해당 예약의 상대 참여자를 사용자 단위로 차단한다.

      인증: 필수. 정식(`ACTIVE`) 회원 중 해당 예약의 참여자만 호출할 수 있다. 세입자·임대인 공통이라 역할 403은 없다.

      - **차단 대상을 요청 본문으로 보내지 않는다.** 서버가 예약에서 상대를 도출한다 — 요청자가 세입자면 임대인, 임대인이면 세입자다(임의의 사용자를 차단하는 경로를 만들지 않기 위해서다).
      - 차단은 예약 단위가 아니라 **사용자 단위**다. 이후 요청자의 목록·상세에서 **그 상대와의 모든 예약**이 사라진다(이 예약 1건만이 아니다).
      - 숨김은 **단방향**이다 — 상대의 목록은 그대로다. 다만 신규 신청 가드는 **양방향**이라 상대가 새 예약을 신청하면 403이 돌아온다.
      - 멱등이다 — 이미 차단한 상대를 다시 차단해도 409가 아니라 204다.
      - 삭제(`DELETE /api/v1/bookings/{bookingId}`)와 독립이다. 차단만 했다면 해제 시 그 예약들이 다시 보이고, 삭제까지 했다면 해제해도 계속 숨겨진다.
      - 차단 목록 조회·해제는 예약과 무관해 `GET`·`DELETE /api/v1/users/me/blocks`(Users)에 있다.
      - 성공 응답은 본문이 없다(204). 참여자가 아니거나 없는 예약이면 404 `BOOKING_NOT_FOUND`다(존재 비노출).

      에러 코드 — 400 `MALFORMED_REQUEST` / 401 `UNAUTHENTICATED`·`TOKEN_EXPIRED` / 403 `AUTH_ONBOARDING_REQUIRED` / 404 `BOOKING_NOT_FOUND`
      """;

  // ── §6 예약 신고 접수 — POST /api/v1/bookings/{bookingId}/report ───────────
  private static final String REPORT_SUMMARY = "예약 신고 접수";

  private static final String REPORT_DESCRIPTION =
      """
      예약 1건에 대한 신고를 접수·저장한다. 범위는 접수까지이고 운영자 검토·제재는 포함하지 않는다.

      인증: 필수. 정식(`ACTIVE`) 회원 중 해당 예약의 참여자만 호출할 수 있다. 신고자는 access 토큰으로 식별하며 본문에 넣지 않는다. 세입자·임대인 공통이라 역할 403은 없다.

      - `reason`·`detail` 모두 **선택**이다. 본문 전체를 `{}`로 보내도 접수된다 — 사유를 고르기 전에 이탈해도 접수 사실은 남아야 하기 때문이다. 사유 없이 신고하면 응답 `data.reason`이 null이다.
      - `reason`은 신고 사유 카탈로그(`booking_report_reasons`) 테이블의 **활성 `code`** 문자열이다. **행으로 늘어날 수 있으니 클라이언트에 하드코딩하지 말고 `GET /api/v1/bookings/report-reasons`로 받아 쓴다.** 미정의·비활성 코드는 400 `INVALID_INPUT`이다.
      - **다건 허용** — 동일 신고자가 동일 예약을 여러 번 신고할 수 있다(409가 없다). 도배 방지 레이트리밋(429)은 미구현이다.
      - 응답에 신고자 식별자와 `detail` 원문은 노출하지 않는다. 상태(`status`) 필드도 없다(전이할 상태가 없는 불변 기록).
      - 단건 조회 엔드포인트가 없어 201이지만 `Location` 헤더를 주지 않는다.
      - **삭제·차단 상태와 무관하다** — 이미 삭제했거나 상대를 차단한 예약도 신고할 수 있다(증거 보존). 그래서 같은 예약이 `GET /api/v1/bookings/{bookingId}`에서는 404인데 여기서는 201일 수 있다.
      - 참여자가 아니거나 없는 예약이면 403이 아니라 404 `BOOKING_NOT_FOUND`다(존재 비노출).

      에러 코드 — 400 `INVALID_INPUT`·`MALFORMED_REQUEST` / 401 `UNAUTHENTICATED`·`TOKEN_EXPIRED` / 403 `AUTH_ONBOARDING_REQUIRED` / 404 `BOOKING_NOT_FOUND`
      """;

  private static final String[] REPORT_404 = {"BOOKING_NOT_FOUND"};

  // ── §7 예약 신고 사유 목록 — GET /api/v1/bookings/report-reasons ───────────
  private static final String REPORT_REASONS_SUMMARY = "예약 신고 사유 목록";

  private static final String REPORT_REASONS_DESCRIPTION =
      """
      예약 신고(`POST /api/v1/bookings/{bookingId}/report`)에 쓸 사유 선택지를 반환한다.

      인증: 필수. 정식(`ACTIVE`) 회원. 역할 분기가 없다.

      - `code`는 언어와 무관한 불변 식별자이고 `label`만 요청자의 표시 언어(`users.lang`, 미설정·미지원이면 `en` 폴백)로 서버가 번역한다. 파라미터로 언어를 고를 수 없다.
      - 지원 언어는 `en`·`ko`·`ja` 3종이다(임대인은 `ko` 고정). 반환되는 **사유 집합과 순서는 `en` 행이 정본**이고, 요청 언어에 라벨이 없는 사유만 `en` 라벨로 폴백한다 — 언어를 바꿔도 항목 수·순서는 같다.
      - **정렬은 카탈로그의 `display_order` 오름차순 고정**이며 클라이언트는 받은 순서를 그대로 노출한다.
      - 페이지네이션이 없다 — 활성 사유 전체를 한 번에 반환한다.
      - **사유는 DB 카탈로그 행이라 배포 없이 늘어난다.** 아래 `code` 목록은 현재 시드값 스냅샷일 뿐이니 클라이언트에 하드코딩하지 말고 이 엔드포인트 응답으로 선택지를 구성한다.

      에러 코드 — 401 `UNAUTHENTICATED`·`TOKEN_EXPIRED` / 403 `AUTH_ONBOARDING_REQUIRED`
      """;

  /**
   * 신고 사유 카탈로그({@code booking_report_reasons})의 현재 활성 code — enum이 아니라 <b>DB 행</b>이라 배포 없이 늘어난다
   * (V17 시드 6종). 그래서 {@code enumField}가 아니라 {@code codeField}로 값을 직접 나열하고, description에 하드코딩 금지 안내를
   * 단다.
   */
  private static final List<String> REPORT_REASON_CODES =
      List.of("SPAM", "ABUSE", "SEXUAL_CONTENT", "EXTERNAL_CONTACT", "FALSE_INFO", "ETC");

  private static final String REASON_CODE_DESCRIPTION =
      "신고 사유 코드 — 언어 무관 불변 식별자. `booking_report_reasons` 테이블의 활성 행이라"
          + " **배포 없이 늘어날 수 있으니 하드코딩하지 말고 `GET /api/v1/bookings/report-reasons`의 응답을 쓴다**";

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

  private static ParameterDescriptor[] deletePathParameters() {
    return new ParameterDescriptor[] {
      parameterWithName("bookingId").description("삭제할 예약 식별자 — 요청자가 참여한 예약이어야 한다")
    };
  }

  private static ParameterDescriptor[] blockPathParameters() {
    return new ParameterDescriptor[] {
      parameterWithName("bookingId").description("차단 대상 상대를 도출할 예약 식별자 — 요청자가 참여한 예약이어야 한다")
    };
  }

  private static ParameterDescriptor[] reportPathParameters() {
    return new ParameterDescriptor[] {
      parameterWithName("bookingId").description("신고 대상 예약 식별자 — 요청자가 참여한 예약이어야 한다")
    };
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
                resourceDetails()
                    .tag(ApiDocsTags.BOOKINGS)
                    .summary(DELETE_SUMMARY)
                    .description(DELETE_DESCRIPTION),
                pathParameters(deletePathParameters())));
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
        DELETE_SUMMARY,
        DELETE_DESCRIPTION,
        deletePathParameters(),
        DELETE_404);
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
                resourceDetails()
                    .tag(ApiDocsTags.BOOKINGS)
                    .summary(BLOCK_SUMMARY)
                    .description(BLOCK_DESCRIPTION),
                pathParameters(blockPathParameters())));
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
    // 이 스니펫만은 예약 "생성" 오퍼레이션에 병합된다 — 문구·path 파라미터·코드 배열을 공유 상수에서 가져온다.
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
        BookingDocsFields.CREATE_SUMMARY,
        BookingDocsFields.CREATE_DESCRIPTION,
        BookingDocsFields.createPathParameters(),
        BookingDocsFields.CREATE_403);
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
        // 신고자 식별자·신고 상세 원문은 응답에 노출하지 않는다(프라이버시).
        .andExpect(jsonPath("$.data.reporterId").doesNotExist())
        .andExpect(jsonPath("$.data.detail").doesNotExist())
        .andDo(
            document(
                "booking-report",
                resourceDetails()
                    .tag(ApiDocsTags.BOOKINGS)
                    .summary(REPORT_SUMMARY)
                    .description(REPORT_DESCRIPTION),
                pathParameters(reportPathParameters()),
                requestFields(reportRequestFields()),
                responseFields(reportResponseFields())));
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
        // 사유 없이 신고하면 `reason`은 키가 사라지는 것이 아니라 **null**이다(NON_NULL 미적용 DTO).
        .andExpect(jsonPath("$.data.reason").isEmpty())
        .andExpect(jsonPath("$.data.reportId").exists())
        // 신고자 식별자·신고 상세 원문은 여기서도 노출하지 않는다(booking-report와 동일 계약).
        .andExpect(jsonPath("$.data.reporterId").doesNotExist())
        .andExpect(jsonPath("$.data.detail").doesNotExist())
        .andDo(
            document(
                // 같은 (path, method, 201)이라 스키마는 booking-report와 병합된다 — 필드 헬퍼를 그대로 재사용해야
                // 하고, 이 스니펫은 `reason: null` 실물 예시를 examples 드롭다운에 추가하는 역할만 한다.
                "booking-report-no-reason",
                resourceDetails()
                    .tag(ApiDocsTags.BOOKINGS)
                    .summary(REPORT_SUMMARY)
                    .description(REPORT_DESCRIPTION),
                pathParameters(reportPathParameters()),
                requestFields(reportRequestFields()),
                responseFields(reportResponseFields())));
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
        REPORT_SUMMARY,
        REPORT_DESCRIPTION,
        reportPathParameters(),
        REPORT_404);
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
                resourceDetails()
                    .tag(ApiDocsTags.BOOKINGS)
                    .summary(REPORT_REASONS_SUMMARY)
                    .description(REPORT_REASONS_DESCRIPTION),
                responseFields(reportReasonsResponseFields())));
  }

  /**
   * 같은 오퍼레이션의 `en` 라벨 예시. 같은 {@code (path, method, 200)}이라 스키마는 {@code booking-report-reasons}와
   * 병합되고(필드 헬퍼 동일) examples 드롭다운에만 항목이 하나 더 생긴다 — 라벨이 표시 언어로 번역된다는 계약을 값으로 보여준다. 라벨 정본은 {@code
   * V17__create_booking_report_reasons.sql} 시드다.
   */
  @Test
  void getReportReasons_englishLabels() throws Exception {
    given(userAccountService.getLanguage(TENANT_ID)).willReturn("en");

    mockMvc
        .perform(
            get("/api/v1/bookings/report-reasons")
                .header(HttpHeaders.AUTHORIZATION, token(TENANT_ID)))
        .andExpect(status().isOk())
        // code·항목 수·순서는 언어와 무관하게 동일하고 label만 바뀐다.
        .andExpect(jsonPath("$.data.reasons.length()").value(REPORT_REASON_CODES.size()))
        .andExpect(jsonPath("$.data.reasons[0].code").value("SPAM"))
        .andExpect(jsonPath("$.data.reasons[0].label").value("Spam/Advertising"))
        .andExpect(jsonPath("$.data.reasons[5].code").value("ETC"))
        .andExpect(jsonPath("$.data.reasons[5].label").value("Other"))
        .andDo(
            document(
                "booking-report-reasons-en",
                resourceDetails()
                    .tag(ApiDocsTags.BOOKINGS)
                    .summary(REPORT_REASONS_SUMMARY)
                    .description(REPORT_REASONS_DESCRIPTION),
                responseFields(reportReasonsResponseFields())));
  }

  private static List<FieldDescriptor> reportRequestFields() {
    return List.of(
        optCodeField(
            "reason",
            REPORT_REASON_CODES,
            "신고 사유(선택) — " + REASON_CODE_DESCRIPTION + ". 생략·null이면 사유 없이 접수된다"),
        optField(
            "detail",
            JsonFieldType.STRING,
            "신고 상세(선택) — 자유 입력 최대 500자. 초과는 400 `INVALID_INPUT`. 응답에는 노출하지 않는다"));
  }

  private static List<FieldDescriptor> reportResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 성공 응답은 항상 true"),
        field("data.reportId", JsonFieldType.NUMBER, "접수된 신고 식별자"),
        field("data.bookingId", JsonFieldType.NUMBER, "신고 대상 예약 식별자"),
        optCodeField(
            "data.reason",
            REPORT_REASON_CODES,
            "접수된 신고 사유 — " + REASON_CODE_DESCRIPTION + ". 사유 없이 신고했으면 null"),
        field("data.createdAt", JsonFieldType.STRING, "신고 접수 일시(ISO-8601 UTC)"),
        errorNull());
  }

  private static List<FieldDescriptor> reportReasonsResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 성공 응답은 항상 true"),
        codeField("data.reasons[].code", REPORT_REASON_CODES, REASON_CODE_DESCRIPTION),
        field(
            "data.reasons[].label",
            JsonFieldType.STRING,
            "사유 표시명 — 요청자의 표시 언어로 서버가 번역한 문자열. 화면에 그대로 노출한다(클라이언트가 code로 다시 매핑하지 않는다)"),
        errorNull());
  }

  private void perform(
      MockHttpServletRequestBuilder request,
      ResultMatcher expectedStatus,
      String expectedCode,
      String identifier,
      String summary,
      String description,
      ParameterDescriptor[] pathParameters,
      String... errorCodes)
      throws Exception {
    mockMvc
        .perform(request)
        .andExpect(expectedStatus)
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value(expectedCode))
        .andDo(
            ApiDocsErrors.errorSnippet(
                identifier,
                ApiDocsTags.BOOKINGS,
                summary,
                description,
                pathParameters,
                errorCodes));
  }
}
