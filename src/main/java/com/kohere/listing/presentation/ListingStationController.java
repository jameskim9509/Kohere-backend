package com.kohere.listing.presentation;

import com.kohere.common.security.AuthPrincipal;
import com.kohere.listing.application.ListingStationSearchService;
import com.kohere.listing.application.dto.ListingStationSearchResponse;
import com.kohere.listing.presentation.dto.ListingNearbyStationRequest;
import com.kohere.listing.presentation.dto.ListingStationSearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매물 등록 폼의 인근 역 칸을 채울 후보를 제공하는 REST 컨트롤러다(ADR-0044).
 *
 * <p>매물 데이터를 쓰지 않아 v4 개편의 영향을 받지 않으므로 장소 후보 검색·주소 검색과 같이 {@code /api/v1}에 둔다. 등록 폼 전용이라 인증 정책은 공개
 * 조회와 정반대로 <b>임대인 전용</b>이다 — {@code SecurityConfig}의 {@code hasRole("USER")} 매처를 공개 조회 매처보다 먼저 선언해야
 * 하며, 임대인 여부는 응용 서비스가 다시 확인한다.
 *
 * <p><b>{@code /stations}는 한 세그먼트라 {@code GET /api/v1/listings/*} {@code permitAll} 매처에 잡힌다</b> —
 * 매처 순서가 뒤집히면 인증 요구가 통째로 무시된다(주소 검색이 겪은 함정과 같다).
 *
 * <p>스펙: docs/api/specs/03-listings-favorites.md · 시퀀스 US-3-6 ⓪-b.
 */
@RestController
@RequestMapping("/api/v1/listings/stations")
@RequiredArgsConstructor
public class ListingStationController {

  private final ListingStationSearchService listingStationSearchService;

  /**
   * 역 이름과 관련된 지하철역 후보를 반환한다.
   *
   * <p>응답의 {@code name}을 매물 등록 요청의 {@code nearestTransit.name}에 그대로 담는다. 매물 좌표를 함께 보내면 거리순으로 정렬되고
   * {@code suggestedWalkMinutes}가 채워진다.
   *
   * @param principal 토큰에서 얻은 요청자. 임대인 여부는 서비스가 확인한다
   * @param request {@code keyword}와 선택적 {@code lat}·{@code lng}를 담은 요청 DTO
   * @return 역 이름·주소·좌표·거리·도보 시간 제안을 담은 후보 목록
   */
  @GetMapping
  public ListingStationSearchResponse searchStations(
      @AuthenticationPrincipal AuthPrincipal principal,
      @ModelAttribute ListingStationSearchRequest request) {
    return listingStationSearchService.searchByKeyword(principal.userId(), request);
  }

  /**
   * 매물 좌표 주변의 지하철역을 가까운 순으로 반환한다.
   *
   * <p>임대인이 아무것도 입력하지 않아도 후보를 보여주기 위한 경로다. 반경은 서버가 2km로 고정한다.
   *
   * @param principal 토큰에서 얻은 요청자
   * @param request 매물 좌표를 담은 요청 DTO
   * @return 반경 내 역 목록. 없으면 빈 목록
   */
  @GetMapping("/nearby")
  public ListingStationSearchResponse searchNearbyStations(
      @AuthenticationPrincipal AuthPrincipal principal,
      @ModelAttribute ListingNearbyStationRequest request) {
    return listingStationSearchService.searchNearby(principal.userId(), request);
  }
}
