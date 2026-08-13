package com.kohere.listing.presentation;

import com.kohere.common.response.ApiResponse;
import com.kohere.common.response.PageResponse;
import com.kohere.common.security.AuthPrincipal;
import com.kohere.listing.application.ListingRegisterService;
import com.kohere.listing.application.ListingService;
import com.kohere.listing.application.dto.FavoriteToggleResponse;
import com.kohere.listing.application.dto.FavoriteToggleResult;
import com.kohere.listing.application.dto.ListingDetailResponse;
import com.kohere.listing.application.dto.ListingKeywordSearchResponse;
import com.kohere.listing.application.dto.ListingMapResponse;
import com.kohere.listing.application.dto.ListingSummaryResponse;
import com.kohere.listing.presentation.dto.ListingKeywordSearchRequest;
import com.kohere.listing.presentation.dto.ListingMapRequest;
import com.kohere.listing.presentation.dto.ListingRegisterRequest;
import com.kohere.listing.presentation.dto.ListingSearchRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매물 탐색·찜·등록 REST 컨트롤러다. 입력 검증·DTO 변환만 담당하고 비즈니스 로직은 응용 계층에 위임한다 (docs/convention/code-style.md
 * §3-3). 도메인 DTO만 반환하고, 공통 래퍼는 {@link com.kohere.common.response.ApiResponseWrapper}가 자동
 * 적용한다(ADR-0013).
 *
 * <p>조회 계열은 v4 스키마 개편과 함께 v1에서 넘어왔다 — {@code /api/v1/listings}는 빈 결과만 준다(ADR-0040). 장소 후보 검색({@code
 * /listings/places})은 매물 스키마와 무관해 v1에 남는다.
 *
 * <p>스펙: docs/api/specs/03-listings-favorites.md · 시퀀스 US-3-1~US-3-6.
 */
@RestController
@RequestMapping("/api/v2/listings")
@RequiredArgsConstructor
public class ListingV2Controller {

  private final ListingService listingService;
  private final ListingRegisterService listingRegisterService;

  /** 조건·정렬로 매물 목록을 조회한다. 비회원도 볼 수 있고, 로그인 상태면 각 항목에 {@code favorited}가 채워진다. */
  @GetMapping
  public PageResponse<ListingSummaryResponse> getListings(
      @AuthenticationPrincipal AuthPrincipal principal,
      @ModelAttribute ListingSearchRequest request) {
    return listingService.getListings(userIdOrNull(principal), request);
  }

  /** 지도 화면의 마커를 조회한다. 목록과 달리 좌표만 내려주고 카드 정보는 상세에서 가져간다. */
  @GetMapping("/map")
  public ListingMapResponse getListingMap(@ModelAttribute ListingMapRequest request) {
    return listingService.getListingMap(request);
  }

  /** 키워드로 매물을 검색한다. 장소명이 일치하면 {@code matchedPlace}로 함께 내려준다. */
  @GetMapping("/search")
  public ListingKeywordSearchResponse searchListings(
      @AuthenticationPrincipal AuthPrincipal principal,
      @ModelAttribute ListingKeywordSearchRequest request) {
    return listingService.searchListings(userIdOrNull(principal), request);
  }

  /** 매물 상세를 조회한다. 로그인 상태면 최근 본 매물에 기록된다. */
  @GetMapping("/{listingId}")
  public ListingDetailResponse getListing(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String listingId) {
    return listingService.getListing(userIdOrNull(principal), listingId);
  }

  /** 매물을 찜한다. 이미 찜한 매물이면 {@code 200}, 새로 찜하면 {@code 201}이다. */
  @PostMapping("/{listingId}/favorite")
  public ResponseEntity<FavoriteToggleResponse> addFavorite(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String listingId) {
    FavoriteToggleResult result = listingService.addFavorite(principal.userId(), listingId);
    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(result.response());
  }

  /** 찜을 해제한다. 찜하지 않은 매물이어도 {@code 200}이다. */
  @DeleteMapping("/{listingId}/favorite")
  public FavoriteToggleResponse removeFavorite(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String listingId) {
    return listingService.removeFavorite(principal.userId(), listingId);
  }

  /**
   * 임대인이 매물을 등록한다.
   *
   * <p>{@code landlordId}는 요청 본문이 아니라 토큰에서 가져온다. 임대인 여부는 서비스가 {@code user::api}로 다시 확인해 임대인이 아니면
   * {@code 403 FORBIDDEN}이다 — 보안 필터는 정식 회원인지까지만 본다.
   *
   * <p>등록 직후 상태는 {@code PENDING}이라 조회·검색·상세에 노출되지 않는다.
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<ListingDetailResponse> register(
      @AuthenticationPrincipal AuthPrincipal principal,
      @Valid @RequestBody ListingRegisterRequest request) {
    return ApiResponse.success(listingRegisterService.register(principal.userId(), request));
  }

  /**
   * 공개 API에서 개인화에 사용할 수 있는 정식 사용자 ID만 반환한다.
   *
   * <p>비로그인 요청은 principal이 없고, 온보딩 토큰은 principal이 있어도 아직 ROLE_USER가 아니다. 두 경우 모두 {@code null}을 전달하면
   * 서비스가 영어 기본값을 사용하고 찜 조회·최근 본 기록 같은 사용자 전용 처리를 건너뛴다. 온보딩 완료 토큰만 실제 userId를 전달한다.
   */
  private static Long userIdOrNull(AuthPrincipal principal) {
    return principal == null || !principal.onboardingCompleted() ? null : principal.userId();
  }
}
