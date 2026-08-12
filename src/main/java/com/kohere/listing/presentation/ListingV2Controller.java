package com.kohere.listing.presentation;

import com.kohere.common.response.ApiResponse;
import com.kohere.common.security.AuthPrincipal;
import com.kohere.listing.application.ListingRegisterService;
import com.kohere.listing.application.dto.ListingDetailResponse;
import com.kohere.listing.presentation.dto.ListingRegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매물 v2 엔드포인트다. 현재는 임대인 등록만 있고 조회 계열은 아직 v1에 있다.
 *
 * <p>스펙: docs/api/specs/03-listings-favorites.md · 시퀀스 US-3-6.
 */
@RestController
@RequestMapping("/api/v2/listings")
@RequiredArgsConstructor
public class ListingV2Controller {

  private final ListingRegisterService listingRegisterService;

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
}
