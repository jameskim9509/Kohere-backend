package com.kohere.listing.presentation;

import com.kohere.common.security.AuthPrincipal;
import com.kohere.listing.application.ListingAddressSearchService;
import com.kohere.listing.application.dto.ListingAddressSearchResponse;
import com.kohere.listing.presentation.dto.ListingAddressSearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매물 등록 폼의 주소 칸을 채울 도로명 주소 후보를 제공하는 REST 컨트롤러다(ADR-0042).
 *
 * <p>매물 데이터를 쓰지 않아 v4 개편의 영향을 받지 않으므로 장소 후보 검색과 같이 {@code /api/v1}에 둔다. 다만 등록 폼 전용이라 인증 정책은 정반대로
 * <b>임대인 전용</b>이다 — {@code SecurityConfig}의 {@code hasRole("USER")} 매처를 공개 조회 매처보다 먼저 선언해야 하며, 임대인
 * 여부는 응용 서비스가 다시 확인한다.
 *
 * <p>스펙: docs/api/specs/03-listings-favorites.md · 시퀀스 US-3-6 ⓪.
 */
@RestController
@RequestMapping("/api/v1/listings/addresses")
@RequiredArgsConstructor
public class ListingAddressController {

  private final ListingAddressSearchService listingAddressSearchService;

  /**
   * 검색 키워드와 관련된 도로명 주소 후보를 최대 5개 반환한다.
   *
   * <p>응답의 {@code roadAddress}·{@code lat}·{@code lng}를 매물 등록 요청의 {@code address}에 그대로 담는다. 후보를 거르지
   * 않는다 — 등록이 카탈로그가 모르는 지역도 받고 행정구역을 {@code ETC}로 저장하기 때문이다.
   *
   * @param principal 토큰에서 얻은 요청자. 임대인 여부는 서비스가 확인한다
   * @param request {@code keyword} 쿼리 파라미터를 담은 요청 DTO
   * @return 도로명 주소·좌표를 담은 후보 목록
   */
  @GetMapping
  public ListingAddressSearchResponse searchAddresses(
      @AuthenticationPrincipal AuthPrincipal principal,
      @ModelAttribute ListingAddressSearchRequest request) {
    return listingAddressSearchService.search(principal.userId(), request);
  }
}
