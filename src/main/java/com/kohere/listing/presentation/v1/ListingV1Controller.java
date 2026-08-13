package com.kohere.listing.presentation.v1;

import com.kohere.common.response.PageInfo;
import com.kohere.common.response.PageResponse;
import com.kohere.listing.application.dto.FavoriteToggleResponse;
import com.kohere.listing.application.dto.ListingMapResponse;
import com.kohere.listing.application.dto.v1.ListingDetailResponse;
import com.kohere.listing.application.dto.v1.ListingKeywordSearchResponse;
import com.kohere.listing.application.dto.v1.ListingSummaryResponse;
import com.kohere.listing.domain.ListingNotFoundException;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매물 탐색·찜의 v1 경로다. 매물 데이터를 반환하지 않는다 — 목록 계열은 항상 빈 페이지, 단건·찜 토글은 항상 {@code 404 LISTING_NOT_FOUND}다.
 *
 * <p>v4 스키마 개편으로 v1 응답 계약을 더는 채울 수 없어, 구버전 앱이 잘못된 매물을 보여주는 대신 빈 화면을 보게 한다(ADR-0040). 매물 조회는 {@code
 * /api/v2/listings}에 있다.
 *
 * <p>저장소·응용 서비스를 <b>호출하지 않는다.</b> 의존을 남겨 두면 다음 스키마 개편 때 v1이 또 컴파일에 끌려온다.
 *
 * <p><b>필터 파라미터를 바인딩하지 않는다.</b> 페이지 번호·크기만 읽고 나머지는 이름조차 보지 않는다. {@code type}·{@code conditions}를
 * 도메인 enum으로 받으면 v4에서 삭제된 상수({@code ConditionTag.NO_ARC}, {@code ListingType.OTHER})를 구버전 앱이 보낼 때 빈
 * 목록이 아니라 {@code 400}이 나간다 — 개정 전 v1에서는 유효한 필터 값이었다. 그러면 "필터 값과 무관하게 같은 응답"이라는 종료 계약이 깨지고, 필터를 켠
 * 사용자만 "매물 없음" 대신 오류 화면을 보게 된다(ADR-0040). 응답 DTO를 v1 패키지에 동결한 것과 같은 이유로 요청 바인딩도 v1에서 잘라 낸다.
 *
 * <p>그래서 <b>검증도 하지 않는다.</b> 개정 전 v1의 입력 검증은 응용 계층({@code ListingService})에 있었고 그 계층을 부르지 않기 때문이다.
 * {@code size=101}·bbox 일부 누락·빈 키워드는 {@code 400}이 아니라 빈 결과 {@code 200}이다. 남는 {@code 400}은 {@code
 * page}·{@code size}가 숫자가 아닐 때의 타입 변환 실패({@code MALFORMED_REQUEST})뿐이다.
 *
 * <p>장소 후보 검색({@code /api/v1/listings/places})은 이 규칙에서 빠진다. 네이버 지역 검색 결과라 매물 스키마와 무관하고 {@link
 * com.kohere.listing.presentation.ListingPlaceController}에서 그대로 동작한다.
 *
 * <p>스펙: docs/api/specs/03-listings-favorites.md.
 */
@RestController
@RequestMapping("/api/v1/listings")
@Deprecated(forRemoval = true)
public class ListingV1Controller {

  /** 항상 비어 있는 목록이다. 요청한 page·size는 그대로 돌려줘 구버전 앱의 페이지네이션 계산이 깨지지 않게 한다. */
  private static PageInfo emptyPage(int page, int size) {
    return new PageInfo(page, size, 0L, 0, false);
  }

  /** 항상 빈 목록이다. 필터·정렬·지도 범위 파라미터는 보내도 무시한다. */
  @GetMapping
  public PageResponse<ListingSummaryResponse> getListings(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return PageResponse.of(List.of(), emptyPage(page, size));
  }

  /** 항상 마커가 없다. 지도 범위·필터 파라미터는 보내도 무시한다. */
  @GetMapping("/map")
  public ListingMapResponse getListingMap() {
    return new ListingMapResponse(List.of(), 0L);
  }

  /** 항상 빈 목록이다. 키워드는 보내도 무시하고, 일치한 장소({@code matchedPlace})도 내려주지 않는다. */
  @GetMapping("/search")
  public ListingKeywordSearchResponse searchListings(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return new ListingKeywordSearchResponse(null, List.of(), emptyPage(page, size));
  }

  /** 항상 {@code 404 LISTING_NOT_FOUND}다. */
  @GetMapping("/{listingId}")
  public ListingDetailResponse getListing(@PathVariable String listingId) {
    throw new ListingNotFoundException();
  }

  /** 항상 {@code 404 LISTING_NOT_FOUND}다. 찜할 매물이 없으므로 아무것도 저장하지 않는다. */
  @PostMapping("/{listingId}/favorite")
  public FavoriteToggleResponse addFavorite(@PathVariable String listingId) {
    throw new ListingNotFoundException();
  }

  /** 항상 {@code 404 LISTING_NOT_FOUND}다. */
  @DeleteMapping("/{listingId}/favorite")
  public FavoriteToggleResponse removeFavorite(@PathVariable String listingId) {
    throw new ListingNotFoundException();
  }
}
