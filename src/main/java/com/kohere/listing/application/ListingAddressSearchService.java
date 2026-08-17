package com.kohere.listing.application;

import com.kohere.common.exception.InvalidInputException;
import com.kohere.listing.application.dto.ListingAddressSearchResponse;
import com.kohere.listing.domain.LandlordOnlyListingException;
import com.kohere.listing.domain.address.AddressSearchClient;
import com.kohere.listing.domain.address.AddressSearchResult;
import com.kohere.listing.presentation.dto.ListingAddressSearchRequest;
import com.kohere.user.api.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 등록 폼의 주소 검색어를 검증하고 외부 지오코딩을 조율한다(ADR-0042).
 *
 * <p>이 유스케이스는 주소 후보만 반환하며 매물을 조회하지도, 저장하지도 않는다. 임대인이 고른 후보의 주소·좌표를 등록 요청에 담아 보내는 것은 프론트의 책임이다 — 서버는
 * 등록 시점에 이 API 호출 여부를 확인하지 않는다.
 *
 * <p>인가는 등록·사진 업로드와 같은 두 겹이다 — 보안 필터가 {@code hasRole("USER")}로 정식 회원만 통과시키고, 여기서 {@code userType}이
 * 임대인인지 다시 확인한다. 등록 폼 전용 API를 공개로 두면 인증 없이 외부 호출 쿼터를 소모하는 지오코딩 프록시가 된다.
 */
@Service
@RequiredArgsConstructor
public class ListingAddressSearchService {

  /** 지오코딩 질의는 주소 문자열 전체가 들어와 장소 키워드(50자)보다 여유를 둔다. */
  private static final int MAX_KEYWORD_LENGTH = 100;

  /** {@code user::api}가 문자열로 주는 임대인 구분값이다. */
  private static final String USER_TYPE_LANDLORD = "LANDLORD";

  private final AddressSearchClient addressSearchClient;
  private final UserAccountService userAccountService;

  /**
   * 검색어를 정규화한 뒤 외부 주소 검색 결과를 프론트 응답으로 변환한다.
   *
   * <p>후보를 거르지 않는다. 등록이 카탈로그가 모르는 지역도 받으므로(행정구역은 {@code ETC}로 저장하고 승인 심사가 확정한다) 검색이 미리 고를 수 없는 주소를
   * 표시할 이유가 없다.
   *
   * @param landlordId 토큰에서 얻은 요청자 ID
   * @param request 등록 폼 주소 칸의 원본 입력을 담은 요청 DTO
   * @return 외부 검색 순서를 유지한 도로명 주소 후보 최대 5개
   * @throws LandlordOnlyListingException 임대인이 아닌 사용자의 요청
   * @throws InvalidInputException 검색어가 누락·공백이거나 100자를 초과한 경우
   */
  public ListingAddressSearchResponse search(long landlordId, ListingAddressSearchRequest request) {
    requireLandlord(landlordId);
    String keyword = validateAndNormalizeKeyword(request == null ? null : request.getKeyword());

    return new ListingAddressSearchResponse(
        addressSearchClient.search(keyword).stream()
            .map(ListingAddressSearchService::toResponseItem)
            .toList());
  }

  private void requireLandlord(long landlordId) {
    if (!USER_TYPE_LANDLORD.equals(userAccountService.getUserType(landlordId))) {
      throw new LandlordOnlyListingException();
    }
  }

  /**
   * 사용자 입력의 앞뒤 공백을 제거하고 API가 허용하는 1~100자 검색어인지 확인한다.
   *
   * <p>외부를 호출하기 전에 검증해 잘못된 입력이 일일 외부 API 호출량을 소모하지 않게 한다.
   */
  private static String validateAndNormalizeKeyword(String keyword) {
    if (keyword == null || keyword.trim().isEmpty()) {
      throw new InvalidInputException("keyword", "validation.required");
    }
    String normalized = keyword.trim();
    if (normalized.length() > MAX_KEYWORD_LENGTH) {
      throw new InvalidInputException(
          "keyword", "validation.lengthRange", 1, MAX_KEYWORD_LENGTH, normalized.length());
    }
    return normalized;
  }

  /** 제공자 독립 도메인 값을 presentation 계약에 맞는 응답 항목으로 복사한다. */
  private static ListingAddressSearchResponse.Item toResponseItem(AddressSearchResult result) {
    return new ListingAddressSearchResponse.Item(
        result.roadAddress(),
        result.jibunAddress(),
        result.englishAddress(),
        result.lat(),
        result.lng());
  }
}
