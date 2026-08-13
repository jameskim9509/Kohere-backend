package com.kohere.listing.presentation.v1;

import com.kohere.common.response.ApiResponse;
import com.kohere.common.response.PageInfo;
import com.kohere.common.response.PageResponse;
import com.kohere.listing.application.dto.v1.FavoriteListingResponse;
import com.kohere.listing.application.dto.v1.RecentListingsResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 스코프(me) 매물 조회의 v1 경로다. 찜 목록·최근 본 매물 모두 항상 비어 있다.
 *
 * <p>{@link ListingV1Controller}와 같은 이유다 — v4 스키마 개편으로 v1 응답 계약을 채울 수 없어 빈 결과로 종료한다(ADR-0040).
 * 저장소·응용 서비스를 호출하지 않으므로 사용자의 실제 찜·조회 이력은 그대로 남아 있고, {@code /api/v2/users/me}에서 보인다.
 *
 * <p>여전히 회원 전용이다 — 비로그인 요청은 보안 필터가 {@code 401}로 막는다. 빈 목록을 준다고 인가까지 풀면 구버전 앱의 로그인 만료 처리가 조용히 깨진다.
 *
 * <p>스펙: docs/api/specs/03-listings-favorites.md.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@Deprecated(forRemoval = true)
public class MyListingV1Controller {

  /** 항상 빈 목록이다. */
  @GetMapping("/favorites")
  public ApiResponse<PageResponse<FavoriteListingResponse>> getMyFavorites(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(PageResponse.of(List.of(), new PageInfo(page, size, 0L, 0, false)));
  }

  /** 항상 빈 목록이다. */
  @GetMapping("/recent-listings")
  public ApiResponse<RecentListingsResponse> getRecentListings() {
    return ApiResponse.success(new RecentListingsResponse(List.of()));
  }
}
