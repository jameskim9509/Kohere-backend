package com.kohere.docs;

import static com.kohere.docs.ApiDocsFields.errorNull;
import static com.kohere.docs.ApiDocsFields.field;
import static com.kohere.docs.ApiDocsFields.optField;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;

import java.util.ArrayList;
import java.util.List;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.restdocs.request.ParameterDescriptor;

/**
 * {@code /api/v1} 매물 조회 오퍼레이션의 문서 자산이다.
 *
 * <p>이 경로들은 매물 데이터를 반환하지 않는다 — 목록 계열은 항상 빈 결과, 단건·찜 토글은 항상 {@code 404}다. 응답 본문에 매물 카드가 실리지 않으므로
 * 기술자도 껍데기(페이지 정보·에러 래퍼)만 있고, {@link ListingDocsFields}의 매물 문서 헬퍼를 재사용하지 않는다.
 *
 * <p>{@code v2} 오퍼레이션과 <b>상수를 공유하지 않는다.</b> summary·description은 오퍼레이션당 한 벌이어야 하는데(ADR-0017) v1과
 * v2는 응답이 정반대라 같은 문구를 쓸 수 없다. 기술자도 마찬가지로 {@code (path, method, status)}가 달라 충돌하지 않는다.
 *
 * <p>장소 후보 검색({@code GET /api/v1/listings/places})은 여기 없다 — 매물 스키마와 무관해 정상 동작하고 문서 자산도 {@link
 * ListingDocsFields}에 그대로 있다.
 */
public final class ListingV1DocsFields {

  private ListingV1DocsFields() {}

  /** 모든 v1 매물 조회 설명에 공통으로 붙는 안내다. 오퍼레이션마다 다시 쓰지 않도록 한 벌로 둔다. */
  private static final String MOVED_NOTICE = "이 경로는 매물 데이터를 반환하지 않는다. 매물 조회는 `/api/v2` 경로를 사용한다.";

  private static final String AUTH_OPTIONAL_HEADER =
      """
      **헤더**

      - `Authorization: Bearer <accessToken>` — 선택. 응답이 토큰 유무에 좌우되지 않는다.
      """;

  private static final String AUTH_REQUIRED_HEADER =
      """
      **헤더**

      - `Authorization: Bearer <accessToken>` — 필수. 온보딩을 완료한 정식 회원 토큰이어야 한다.
      """;

  // ── §1 매물 목록 — GET /api/v1/listings ────────────────────────────────────

  public static final String V1_LISTINGS_LIST_SUMMARY = "매물 목록 조회 (v1)";

  public static final String V1_LISTINGS_LIST_DESCRIPTION =
      MOVED_NOTICE
          + " 목록 조회는 `GET /api/v2/listings`다.\n\n"
          + AUTH_OPTIONAL_HEADER
          + """

      **응답 주의사항**

      - 어떤 조건을 보내도 `content`는 항상 빈 배열이고 `page.totalElements`는 0이다.
      - `page`·`size`만 읽어 그대로 돌려준다. 범위를 검사하지 않으므로 `size=500`을 보내면 `page.size`도 500이다.
      - 필터·정렬·지도 범위 파라미터는 **보내도 무시한다.** 값이 무엇이든 같은 응답이고, 미정의 필터 값이어도 오류가 아니다.

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 400 | `MALFORMED_REQUEST` | `page`·`size`가 숫자가 아님 |
      """;

  // ── §2 지도 마커 — GET /api/v1/listings/map ────────────────────────────────

  public static final String V1_LISTINGS_MAP_SUMMARY = "지도 마커 조회 (v1)";

  public static final String V1_LISTINGS_MAP_DESCRIPTION =
      MOVED_NOTICE
          + " 지도 마커 조회는 `GET /api/v2/listings/map`이다.\n\n"
          + AUTH_OPTIONAL_HEADER
          + """

      **응답 주의사항**

      - 어떤 지도 영역을 보내도 `markers`는 항상 빈 배열이고 `total`은 0이다.
      - 좌표·필터 파라미터는 **보내도 무시한다.** 일부만 보내거나 아예 없어도 오류가 아니다.

      **에러 코드**

      - 없다. 이 경로는 어떤 요청에도 `200`을 반환한다.
      """;

  // ── §3 키워드 검색 — GET /api/v1/listings/search ───────────────────────────

  public static final String V1_LISTINGS_SEARCH_SUMMARY = "키워드 매물 검색 (v1)";

  public static final String V1_LISTINGS_SEARCH_DESCRIPTION =
      MOVED_NOTICE
          + " 키워드 검색은 `GET /api/v2/listings/search`다.\n\n"
          + AUTH_OPTIONAL_HEADER
          + """

      **응답 주의사항**

      - 어떤 키워드를 보내도 `content`는 항상 빈 배열이고 `matchedPlace`는 항상 null이다.
      - 키워드를 검사하지 않는다. 비우거나 길이를 넘겨도 오류가 아니라 빈 결과다.

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 400 | `MALFORMED_REQUEST` | `page`·`size`가 숫자가 아님 |
      """;

  // ── §4 매물 상세 — GET /api/v1/listings/{listingId} ────────────────────────

  public static final String V1_LISTING_DETAIL_SUMMARY = "매물 상세 조회 (v1)";

  public static final String V1_LISTING_DETAIL_DESCRIPTION =
      MOVED_NOTICE
          + " 상세 조회는 `GET /api/v2/listings/{listingId}`다.\n\n"
          + AUTH_OPTIONAL_HEADER
          + """

      **응답 주의사항**

      - 어떤 `listingId`를 보내도 404다. 최근 본 매물에도 기록되지 않는다.

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 404 | `LISTING_NOT_FOUND` | 항상 |
      """;

  // ── §5 찜 등록 — POST /api/v1/listings/{listingId}/favorite ────────────────

  public static final String V1_FAVORITE_ADD_SUMMARY = "매물 찜 등록 (v1)";

  public static final String V1_FAVORITE_ADD_DESCRIPTION =
      MOVED_NOTICE
          + " 찜 등록은 `POST /api/v2/listings/{listingId}/favorite`다.\n\n"
          + AUTH_REQUIRED_HEADER
          + """

      **응답 주의사항**

      - 어떤 `listingId`를 보내도 404이며 찜은 저장되지 않는다. 기존에 저장된 찜은 그대로 남는다.

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 401 | `UNAUTHENTICATED` | 토큰이 없거나 위조 |
      | 401 | `TOKEN_EXPIRED` | 만료된 access token |
      | 403 | `AUTH_ONBOARDING_REQUIRED` | 온보딩 미완료 토큰 |
      | 404 | `LISTING_NOT_FOUND` | 인가를 통과한 모든 요청 |
      """;

  // ── §6 찜 해제 — DELETE /api/v1/listings/{listingId}/favorite ──────────────

  public static final String V1_FAVORITE_REMOVE_SUMMARY = "매물 찜 해제 (v1)";

  public static final String V1_FAVORITE_REMOVE_DESCRIPTION =
      MOVED_NOTICE
          + " 찜 해제는 `DELETE /api/v2/listings/{listingId}/favorite`다.\n\n"
          + AUTH_REQUIRED_HEADER
          + """

      **응답 주의사항**

      - 어떤 `listingId`를 보내도 404이며 저장된 찜은 지워지지 않는다.

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 401 | `UNAUTHENTICATED` | 토큰이 없거나 위조 |
      | 401 | `TOKEN_EXPIRED` | 만료된 access token |
      | 403 | `AUTH_ONBOARDING_REQUIRED` | 온보딩 미완료 토큰 |
      | 404 | `LISTING_NOT_FOUND` | 인가를 통과한 모든 요청 |
      """;

  // ── §7 내 찜 목록 — GET /api/v1/users/me/favorites ─────────────────────────

  public static final String V1_FAVORITES_LIST_SUMMARY = "내 찜 목록 조회 (v1)";

  public static final String V1_FAVORITES_LIST_DESCRIPTION =
      MOVED_NOTICE
          + " 내 찜 목록은 `GET /api/v2/users/me/favorites`다.\n\n"
          + AUTH_REQUIRED_HEADER
          + """

      **응답 주의사항**

      - `content`는 항상 빈 배열이다. 사용자가 저장한 찜 자체는 지워지지 않으며 v2 경로에서 그대로 보인다.

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 401 | `UNAUTHENTICATED` | 토큰이 없거나 위조 |
      | 401 | `TOKEN_EXPIRED` | 만료된 access token |
      | 403 | `AUTH_ONBOARDING_REQUIRED` | 온보딩 미완료 토큰 |
      """;

  // ── §8 최근 본 매물 — GET /api/v1/users/me/recent-listings ─────────────────

  public static final String V1_RECENT_LISTINGS_SUMMARY = "최근 본 매물 조회 (v1)";

  public static final String V1_RECENT_LISTINGS_DESCRIPTION =
      MOVED_NOTICE
          + " 최근 본 매물은 `GET /api/v2/users/me/recent-listings`다.\n\n"
          + AUTH_REQUIRED_HEADER
          + """

      **응답 주의사항**

      - `content`는 항상 빈 배열이다. 저장된 조회 이력 자체는 지워지지 않으며 v2 경로에서 그대로 보인다.

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 401 | `UNAUTHENTICATED` | 토큰이 없거나 위조 |
      | 401 | `TOKEN_EXPIRED` | 만료된 access token |
      | 403 | `AUTH_ONBOARDING_REQUIRED` | 온보딩 미완료 토큰 |
      """;

  // ── 쿼리 파라미터 ──────────────────────────────────────────────────────────

  /**
   * v1 목록·검색이 실제로 읽는 파라미터다. 둘 다 <b>선택</b>이고, 값을 검사하지 않고 응답 {@code page}에 그대로 반향한다.
   *
   * <p>{@link ListingDocsFields}의 v2 기술자를 재사용하지 않는다 — v2 것은 필터·정렬이 동작한다고 설명하는데 v1은 그 파라미터를 읽지도 않아,
   * 재사용하면 Swagger가 죽은 필터를 살아 있는 것처럼 문서화한다.
   */
  public static ParameterDescriptor[] pageQueryParameters() {
    return new ParameterDescriptor[] {
      parameterWithName("page")
          .optional()
          .description("0부터 시작하는 페이지 번호(기본 0). 검사 없이 응답 `page.number`로 그대로 돌아온다"),
      parameterWithName("size")
          .optional()
          .description("페이지 크기(기본 20). 범위를 검사하지 않고 응답 `page.size`로 그대로 돌아온다")
    };
  }

  /** v1 키워드 검색의 파라미터다. {@code keyword}는 읽지 않지만 구버전 앱이 보내므로 문서에는 남긴다. */
  public static ParameterDescriptor[] searchQueryParameters() {
    List<ParameterDescriptor> params = new ArrayList<>();
    params.add(
        parameterWithName("keyword").optional().description("검색어. **읽지 않는다** — 무엇을 보내도 결과가 없다"));
    for (ParameterDescriptor descriptor : pageQueryParameters()) {
      params.add(descriptor);
    }
    return params.toArray(new ParameterDescriptor[0]);
  }

  // ── 응답 필드 ──────────────────────────────────────────────────────────────

  /** 빈 페이지 응답 필드다. 목록·내 찜 목록이 함께 쓴다 — 둘 다 같은 껍데기다. */
  public static List<FieldDescriptor> emptyPageResponseFields() {
    List<FieldDescriptor> fields = new ArrayList<>();
    fields.add(field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"));
    fields.add(field("data.content", JsonFieldType.ARRAY, "항상 빈 배열"));
    fields.addAll(emptyPageFields());
    fields.add(errorNull());
    return List.copyOf(fields);
  }

  /** 빈 키워드 검색 응답 필드다. 일치 장소도 내려주지 않아 {@code matchedPlace}가 null이다. */
  public static List<FieldDescriptor> emptySearchResponseFields() {
    List<FieldDescriptor> fields = new ArrayList<>();
    fields.add(field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"));
    fields.add(optField("data.matchedPlace", JsonFieldType.OBJECT, "항상 null"));
    fields.add(field("data.content", JsonFieldType.ARRAY, "항상 빈 배열"));
    fields.addAll(emptyPageFields());
    fields.add(errorNull());
    return List.copyOf(fields);
  }

  /** 빈 지도 마커 응답 필드다. */
  public static List<FieldDescriptor> emptyMapResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field("data.markers", JsonFieldType.ARRAY, "항상 빈 배열"),
        field("data.total", JsonFieldType.NUMBER, "항상 0"),
        errorNull());
  }

  /** 빈 최근 본 매물 응답 필드다. 페이지네이션이 없어 목록과 껍데기가 다르다. */
  public static List<FieldDescriptor> emptyRecentListingsResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field("data.content", JsonFieldType.ARRAY, "항상 빈 배열"),
        errorNull());
  }

  private static List<FieldDescriptor> emptyPageFields() {
    return List.of(
        field("data.page.number", JsonFieldType.NUMBER, "요청한 페이지 번호를 그대로 돌려준다"),
        field("data.page.size", JsonFieldType.NUMBER, "요청한 페이지 크기를 그대로 돌려준다"),
        field("data.page.totalElements", JsonFieldType.NUMBER, "항상 0"),
        field("data.page.totalPages", JsonFieldType.NUMBER, "항상 0"),
        field("data.page.hasNext", JsonFieldType.BOOLEAN, "항상 false"));
  }
}
