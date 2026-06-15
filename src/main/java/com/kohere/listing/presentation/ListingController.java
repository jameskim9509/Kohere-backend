package com.kohere.listing.presentation;

import com.kohere.common.response.ApiResponse;
import com.kohere.common.response.PageResponse;
import com.kohere.listing.application.ListingService;
import com.kohere.listing.application.dto.FavoriteToggleResponse;
import com.kohere.listing.application.dto.ListingSummaryResponse;
import com.kohere.listing.domain.ListingSort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매물 탐색·찜 REST 컨트롤러. 입력 검증·DTO 변환만 담당하고 비즈니스 로직은 응용 계층에 위임한다 (docs/convention/code-style.md §3-3).
 * 응답은 공통 래퍼로 감싼다.
 *
 * <p>스펙: docs/api/specs/03-listings-favorites.md. TODO: 지도(GET /map)·키워드 검색(GET /search), 필터 쿼리
 * 파라미터 전체를 채운다.
 */
@RestController
@RequestMapping("/api/v1/listings")
@RequiredArgsConstructor
public class ListingController {

  private final ListingService listingService;

  @GetMapping
  public ApiResponse<PageResponse<ListingSummaryResponse>> getListings(
      @RequestParam(required = false) Integer minBudget,
      @RequestParam(required = false) Integer maxBudget,
      @RequestParam(defaultValue = "RECOMMENDED") ListingSort sort,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(listingService.getListings(page, size));
  }

  @GetMapping("/{listingId}")
  public ApiResponse<ListingSummaryResponse> getListing(@PathVariable Long listingId) {
    return ApiResponse.success(listingService.getListing(listingId));
  }

  @PostMapping("/{listingId}/favorite")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<FavoriteToggleResponse> addFavorite(@PathVariable Long listingId) {
    return ApiResponse.success(listingService.addFavorite(listingId));
  }

  @DeleteMapping("/{listingId}/favorite")
  public ApiResponse<FavoriteToggleResponse> removeFavorite(@PathVariable Long listingId) {
    return ApiResponse.success(listingService.removeFavorite(listingId));
  }
}
