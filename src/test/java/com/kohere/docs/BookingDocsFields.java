package com.kohere.docs;

import static com.kohere.docs.ApiDocsFields.enumField;
import static com.kohere.docs.ApiDocsFields.errorNull;
import static com.kohere.docs.ApiDocsFields.field;
import static com.kohere.docs.ApiDocsFields.optCodeField;
import static com.kohere.docs.ApiDocsFields.optField;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;

import com.kohere.booking.domain.BookingStatus;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.restdocs.request.ParameterDescriptor;
import org.springframework.restdocs.snippet.Snippet;

/**
 * booking 도메인 문서 테스트가 공유하는 오퍼레이션 상수·필드 기술자(#151).
 *
 * <p><b>왜 별도 클래스인가</b> — 예약 생성({@code POST /api/v1/listings/{listingId}/bookings})·목록({@code GET
 * /api/v1/bookings})·상세({@code GET /api/v1/bookings/{bookingId}}) 세 오퍼레이션의 스니펫이 {@code
 * BookingDocsTest}와 {@code BookingManagementDocsTest} <b>두 파일에 걸쳐</b> 있다. 특히 차단 관계 403({@code
 * booking-create-blocked})은 관리 테스트에 있으면서 생성 오퍼레이션에 병합된다. 같은 {@code (path, method)} 모델들의
 * summary/description은 <b>첫 non-blank 하나</b>만 채택되고, 같은 {@code (path, method, status)}의 필드 기술자는
 * {@code (path, type)} 기준 dedup·last-wins라 승자가 파일 순회 순서에 좌우된다. 그래서 문구·필드·에러 코드 배열을 여기 한 벌만
 * 둔다({@link ApiDocsFields} 클래스 주석 참조).
 *
 * <p><b>역할 분기 필드의 합집합</b> — 목록·상세는 요청자 {@code userType}으로 응답 DTO가 갈리는데(세입자 {@code
 * BookingSummaryResponse}/{@code BookingDetailResponse} · 임대인 {@code
 * LandlordBookingSummaryResponse}/{@code LandlordBookingDetailResponse}) 생성기는 status별 스키마를 하나만 만든다.
 * 그래서 한 오퍼레이션·한 status의 기술자 목록은 두 분기의 <b>합집합</b>이고, 한쪽에만 있는 키는 {@code optional()}로 낮춘 뒤
 * description에 「null이 아니라 필드 자체가 없다」를 적는다. 느슨해진 계약은 테스트의 {@code doesNotExist()} 단정으로 되메운다(#151 규약
 * 13).
 */
public final class BookingDocsFields {

  private BookingDocsFields() {}

  // ── §1 예약 생성 — POST /api/v1/listings/{listingId}/bookings ──────────────

  public static final String CREATE_SUMMARY = "매물 예약 신청";

  public static final String CREATE_DESCRIPTION =
      """
      방 상품(roomOffer)에 타겟 입주일과 계약 개월수로 예약(신청)을 생성한다.

      인증: 필수. 정식(`ACTIVE`) **세입자**(`userType=TENANT`) 전용이다. 임대인 등 비세입자가 호출하면 403 `FORBIDDEN`이며, 온보딩 미완료는 403 `AUTH_ONBOARDING_REQUIRED`다.

      - 신청 직후 `status`는 `REQUESTED` 고정이다. MVP에서는 `REQUESTED`만 반환된다 — 수락·거절·취소 엔드포인트가 없어 `ACCEPTED`·`REJECTED`·`CANCELED`로 전이할 경로가 아직 없다.
      - 동일 세입자·동일 방 상품(`roomOfferId`)에 예약은 1건만 허용된다(DB UNIQUE). 재신청은 409 `BOOKING_ALREADY_EXISTS`.
      - 요청자와 매물 소유자 사이에 **어느 방향이든** 차단 관계가 있으면 403 `FORBIDDEN`이다(블랙홀 예약 방지 — 저장돼도 상대 목록에서 차단 필터에 걸려 영영 보이지 않기 때문).
      - `moveInDate`는 오늘 이후이고 방 상품의 입주 가능일 이후여야 한다. 위반은 422 `BOOKING_INVALID_MOVE_IN_DATE`, 날짜 형식 위반은 400 `MALFORMED_REQUEST`다.
      - 성공 응답에는 `Location: /api/v1/bookings/{bookingId}` 헤더가 붙는다. 응답 본문은 예약 코어 내역만 담고 매물 요약·가격·성명은 상세 조회에서 조인해 내려준다.

      에러 코드 — 400 `INVALID_INPUT`·`MALFORMED_REQUEST` / 401 `UNAUTHENTICATED`·`TOKEN_EXPIRED` / 403 `FORBIDDEN`·`AUTH_ONBOARDING_REQUIRED` / 404 `LISTING_NOT_FOUND` / 409 `BOOKING_ALREADY_EXISTS` / 422 `BOOKING_INVALID_MOVE_IN_DATE`
      """;

  public static final String[] CREATE_400 = {"INVALID_INPUT", "MALFORMED_REQUEST"};
  public static final String[] CREATE_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};
  public static final String[] CREATE_403 = {"FORBIDDEN", "AUTH_ONBOARDING_REQUIRED"};
  public static final String[] CREATE_404 = {"LISTING_NOT_FOUND"};
  public static final String[] CREATE_409 = {"BOOKING_ALREADY_EXISTS"};
  public static final String[] CREATE_422 = {"BOOKING_INVALID_MOVE_IN_DATE"};

  public static ParameterDescriptor[] createPathParameters() {
    return new ParameterDescriptor[] {
      parameterWithName("listingId").description("예약 대상 매물 ID(ObjectId 24자리 hex)")
    };
  }

  public static List<FieldDescriptor> createRequestFields() {
    return List.of(
        field("roomOfferId", JsonFieldType.STRING, "예약 대상 방 상품 ID(ObjectId 24자리 hex). 누락·공백은 400"),
        field(
            "moveInDate",
            JsonFieldType.STRING,
            "타겟 입주일(`YYYY-MM-DD`). 형식 위반은 400 `MALFORMED_REQUEST`, 과거·입주 가능일 이전은 422"),
        field(
            "contractPeriod",
            JsonFieldType.NUMBER,
            "계약 개월수(1 이상의 정수). 0·음수는 400. 총 금액 계산에 그대로 쓰인다"));
  }

  public static List<FieldDescriptor> createResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 성공 응답은 항상 true"),
        field("data.bookingId", JsonFieldType.NUMBER, "생성된 예약 식별자"),
        enumField("data.status", BookingStatus.class, BOOKING_STATUS_DESCRIPTION),
        field("data.listingId", JsonFieldType.STRING, "예약 대상 매물 ID"),
        field("data.roomOfferId", JsonFieldType.STRING, "예약 대상 방 상품 ID"),
        field("data.moveInDate", JsonFieldType.STRING, "타겟 입주일(`YYYY-MM-DD`)"),
        field("data.contractPeriod", JsonFieldType.NUMBER, "계약 개월수"),
        field("data.createdAt", JsonFieldType.STRING, "예약 접수 일시(ISO-8601 UTC)"),
        errorNull());
  }

  /**
   * 201의 {@code Location} 헤더. 저장소에서 유일한 {@code responseHeaders} 선언이라 Swagger의 Headers 표도 여기서만 나온다.
   */
  public static Snippet createResponseHeaders() {
    return responseHeaders(
        headerWithName(HttpHeaders.LOCATION)
            .description("생성된 예약의 조회 경로 — `/api/v1/bookings/{bookingId}`"));
  }

  // ── §2 예약 목록 — GET /api/v1/bookings ────────────────────────────────────

  public static final String LIST_SUMMARY = "예약 목록 조회";

  public static final String LIST_DESCRIPTION =
      """
      예약을 `createdAt` 내림차순 오프셋 페이지로 조회한다.

      인증: 필수. 정식(`ACTIVE`) 회원이면 세입자·임대인 모두 유효한 요청이라 역할 403은 없다. 요청자 `userType`으로 **반환 대상이 갈린다** — 세입자는 본인이 신청한 예약, 임대인은 본인 소유 매물에 신청된 예약(일시중지 매물 포함)이다.

      - **Schema 탭의 필드 목록은 세입자·임대인 두 분기의 합집합이며 실제 한 응답에 둘 다 나오지 않는다.** `(세입자만)`·`(임대인만)`이 붙은 필드는 다른 분기의 응답에서 **키 자체가 없다**(값이 null인 것이 아니다). 실제 형태는 Examples에서 역할을 골라 본다.
      - `status`는 MVP에서 `REQUESTED`만 반환된다(수락·거절·취소 엔드포인트 미구현).
      - 정렬은 `createdAt,desc` 고정이며 쿼리로 바꿀 수 없다. `size`는 최대 100으로 잘린다.
      - 요청자가 삭제한 예약과 요청자가 차단한 상대의 예약은 목록에서 빠진다. 삭제·차단 상태 자체는 응답 필드로 노출하지 않는다(사라지는 것으로만 관측된다).
      - 매물·방 상품이 삭제·비공개면 조인 대상이 사라져 `title`·`thumbnailUrl`·`roomOfferName`이 null이 된다(예약 코어 내역은 그대로 유지된다).
      - 결과가 없으면 `content: []` + `page.totalElements: 0`이다(에러 아님).

      에러 코드 — 400 `INVALID_INPUT` / 401 `UNAUTHENTICATED`·`TOKEN_EXPIRED` / 403 `AUTH_ONBOARDING_REQUIRED`
      """;

  public static ParameterDescriptor[] listQueryParameters() {
    return new ParameterDescriptor[] {
      parameterWithName("page").optional().description("0-base 페이지 번호(기본 0). 음수는 0으로 보정"),
      parameterWithName("size").optional().description("페이지 크기(기본 20, 최대 100). 초과분은 100으로 보정")
    };
  }

  /** 세입자·임대인 두 분기의 합집합. 한쪽에만 있는 키는 {@code optional()}이다. */
  public static List<FieldDescriptor> listResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 성공 응답은 항상 true"),
        field("data.content[].bookingId", JsonFieldType.NUMBER, "예약 식별자"),
        field("data.content[].listingId", JsonFieldType.STRING, "매물 ID"),
        optField("data.content[].title", JsonFieldType.STRING, TITLE_DESCRIPTION),
        optField("data.content[].thumbnailUrl", JsonFieldType.STRING, THUMBNAIL_DESCRIPTION),
        field("data.content[].roomOfferId", JsonFieldType.STRING, "방 상품 ID"),
        optField("data.content[].roomOfferName", JsonFieldType.STRING, ROOM_OFFER_NAME_DESCRIPTION),
        optField("data.content[].applicantName", JsonFieldType.STRING, APPLICANT_NAME_DESCRIPTION),
        field("data.content[].moveInDate", JsonFieldType.STRING, "타겟 입주일(`YYYY-MM-DD`)"),
        field("data.content[].contractPeriod", JsonFieldType.NUMBER, "계약 개월수"),
        enumField("data.content[].status", BookingStatus.class, BOOKING_STATUS_DESCRIPTION),
        field("data.content[].createdAt", JsonFieldType.STRING, "예약 접수 일시(ISO-8601 UTC)"),
        field("data.page.number", JsonFieldType.NUMBER, "현재 페이지 번호(0-base)"),
        field("data.page.size", JsonFieldType.NUMBER, "페이지 크기"),
        field("data.page.totalElements", JsonFieldType.NUMBER, "필터 적용 후 전체 건수"),
        field("data.page.totalPages", JsonFieldType.NUMBER, "전체 페이지 수"),
        field("data.page.hasNext", JsonFieldType.BOOLEAN, "다음 페이지 존재 여부"),
        errorNull());
  }

  // ── §3 예약 단건 상세 — GET /api/v1/bookings/{bookingId} ───────────────────

  public static final String DETAIL_SUMMARY = "예약 단건 상세 조회";

  public static final String DETAIL_DESCRIPTION =
      """
      예약 1건의 상세를 조회한다. 매물 정보·가격·성명은 스냅샷이 아니라 조회 시점에 실시간 조인한다(가격이 바뀌면 현재가 기준).

      인증: 필수. 정식(`ACTIVE`) 회원이면 세입자·임대인 모두 유효한 요청이라 역할 403은 없다. 요청자 `userType`으로 **응답 본문이 갈린다** — 세입자는 본인 예약 + 본인 성명(`tenantName`), 임대인은 본인 소유 매물에 신청된 예약 + 신청자 프로필(`applicant*`)이다.

      - **Schema 탭의 필드 목록은 세입자·임대인 두 분기의 합집합이며 실제 한 응답에 둘 다 나오지 않는다.** `tenantName`은 세입자 분기에만, `applicantId`·`applicantName`·`applicantGender`·`applicantCountry`·`applicantCountryName`·`applicantEmail`은 임대인 분기에만 있는 키로, 반대 분기에서는 null이 아니라 **필드 자체가 없다**.
      - `status`는 MVP에서 `REQUESTED`만 반환된다(수락·거절·취소 엔드포인트 미구현).
      - 총 금액 `totalAmount = deposit + monthlyRent × contractPeriod`(관리비 제외)이며 두 분기 모두 같은 정의다.
      - 매물·방 상품이 삭제·비공개면 조인 대상이 사라져 `title`·`thumbnailUrl`·`address`·`roomOfferName`이 null이 되고 `deposit`·`totalAmount`는 **0**이 된다(null이 아니다).
      - 신청자 PII(이메일·성별·국적)는 임대인에게 **마스킹 없이 평문**으로 내려간다. 신청자가 탈퇴하면 익명화로 값이 null이 될 수 있다.
      - 예약이 없거나 조회 권한 밖(세입자: 타인 예약 / 임대인: 내 소유 매물 신청 아님)이거나, 요청자가 이미 삭제했거나 상대를 차단한 예약이면 전부 404 `BOOKING_NOT_FOUND`로 통일한다(존재 비노출).

      에러 코드 — 401 `UNAUTHENTICATED`·`TOKEN_EXPIRED` / 403 `AUTH_ONBOARDING_REQUIRED` / 404 `BOOKING_NOT_FOUND`
      """;

  public static final String[] DETAIL_404 = {"BOOKING_NOT_FOUND"};

  public static ParameterDescriptor[] detailPathParameters() {
    return new ParameterDescriptor[] {
      parameterWithName("bookingId").description("예약 식별자 — 세입자는 본인 예약, 임대인은 본인 소유 매물에 신청된 예약")
    };
  }

  /** 세입자·임대인 두 분기의 합집합. 한쪽에만 있는 키는 {@code optional()}이다. */
  public static List<FieldDescriptor> detailResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 성공 응답은 항상 true"),
        field("data.bookingId", JsonFieldType.NUMBER, "예약 식별자"),
        enumField("data.status", BookingStatus.class, BOOKING_STATUS_DESCRIPTION),
        field("data.listingId", JsonFieldType.STRING, "매물 ID"),
        field("data.roomOfferId", JsonFieldType.STRING, "방 상품 ID"),
        optField("data.title", JsonFieldType.STRING, TITLE_DESCRIPTION),
        optField("data.thumbnailUrl", JsonFieldType.STRING, THUMBNAIL_DESCRIPTION),
        optField("data.address", JsonFieldType.STRING, "매물 주소 — 조회 시점 조인. 매물이 삭제·비공개면 null"),
        optField(
            "data.roomOfferName", JsonFieldType.STRING, "방 상품명 — 조회 시점 조인. 매물·방 상품이 삭제·비공개면 null"),
        field("data.createdAt", JsonFieldType.STRING, "예약 접수 일시(ISO-8601 UTC)"),
        field("data.moveInDate", JsonFieldType.STRING, "타겟 입주일(`YYYY-MM-DD`)"),
        field("data.contractPeriod", JsonFieldType.NUMBER, "계약 개월수"),
        optField(
            "data.tenantName",
            JsonFieldType.STRING,
            "예약자(요청자 본인) 성명(세입자만) — 값은 null이 되지 않는다(이름이 없으면 빈 문자열)"),
        optField("data.applicantId", JsonFieldType.NUMBER, "신청자(세입자) 식별자(임대인만)"),
        optField("data.applicantName", JsonFieldType.STRING, APPLICANT_NAME_DESCRIPTION),
        optCodeField(
            "data.applicantGender",
            List.of("MALE", "FEMALE"),
            "신청자 성별(임대인만) — 마스킹 없이 평문" + " 미수집이거나 탈퇴 익명화된 신청자는 null"),
        optField(
            "data.applicantCountry",
            JsonFieldType.STRING,
            "신청자 국적 ISO 3166-1 alpha-2 코드(임대인만) — 미수집·탈퇴 익명화 시 null"),
        optField(
            "data.applicantCountryName",
            JsonFieldType.STRING,
            "신청자 국적 표시명(임대인만) — 서버가 참조 테이블로 resolve, 국적 미수집·미등록 코드면 null"),
        optField(
            "data.applicantEmail",
            JsonFieldType.STRING,
            "신청자 이메일(임대인만) — 마스킹 없이 평문. 탈퇴 익명화 시 null"),
        field(
            "data.deposit",
            JsonFieldType.NUMBER,
            "보증금(KRW 정수) — 조회 시점 현재가. 매물·방 상품이 삭제·비공개면 null이 아니라 **0**"),
        field(
            "data.totalAmount",
            JsonFieldType.NUMBER,
            "총 금액 = 보증금 + 월세 × 계약 개월수(관리비 제외, 세입자·임대인 동일 정의)."
                + " 매물·방 상품이 삭제·비공개면 null이 아니라 **0**"),
        errorNull());
  }

  // ── 두 오퍼레이션 이상이 공유하는 필드 문구 ────────────────────────────────

  private static final String BOOKING_STATUS_DESCRIPTION =
      "예약 상태 — 신청 직후 `REQUESTED` 고정. MVP에서는 `REQUESTED`만 반환된다(수락·거절·취소 엔드포인트 미구현이라 나머지 값으로 전이할 경로가 없다)";

  private static final String TITLE_DESCRIPTION =
      "매물 제목 — 조회 시점에 listing 모듈로 조인한다. 매물이 삭제·비공개면 null";

  private static final String THUMBNAIL_DESCRIPTION = "매물 대표 이미지 URL — 조회 시점 조인. 매물이 삭제·비공개면 null";

  private static final String ROOM_OFFER_NAME_DESCRIPTION = "방 상품명(임대인만) — 매물·방 상품이 삭제·비공개면 null";

  private static final String APPLICANT_NAME_DESCRIPTION =
      "신청자(세입자) 성명(임대인만) — 값은 null이 되지 않는다(이름이 없으면 빈 문자열)";
}
