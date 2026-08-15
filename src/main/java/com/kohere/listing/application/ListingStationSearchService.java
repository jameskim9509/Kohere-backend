package com.kohere.listing.application;

import com.kohere.common.exception.InvalidInputException;
import com.kohere.listing.application.dto.ListingStationSearchResponse;
import com.kohere.listing.domain.LandlordOnlyListingException;
import com.kohere.listing.domain.nearby.Coordinate;
import com.kohere.listing.domain.nearby.NearbyPlace;
import com.kohere.listing.domain.nearby.NearbyPlaceSearchClient;
import com.kohere.listing.presentation.dto.ListingNearbyStationRequest;
import com.kohere.listing.presentation.dto.ListingStationSearchRequest;
import com.kohere.user.api.UserAccountService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 등록 폼의 역 검색 입력을 검증하고 외부 장소 검색을 조율한다(ADR-0044).
 *
 * <p>이 유스케이스는 역 후보만 반환하며 매물을 조회하지도, 저장하지도 않는다. 고른 후보의 이름을 등록 요청에 담아 보내는 것은 프론트의 책임이다 — 서버는 등록 시점에 이
 * API 호출 여부를 확인하지 않는다.
 *
 * <p>인가는 주소 검색·등록·사진 업로드와 같은 두 겹이다 — 보안 필터가 {@code hasRole("USER")}로 정식 회원만 통과시키고, 여기서 {@code
 * userType}이 임대인인지 다시 확인한다. 등록 폼 전용 API를 공개로 두면 인증 없이 카카오 호출 쿼터를 소모하는 프록시가 된다.
 */
@Service
@RequiredArgsConstructor
public class ListingStationSearchService {

  /** 역 이름은 주소보다 짧아 장소 후보 검색과 같은 한도를 쓴다. */
  private static final int MAX_KEYWORD_LENGTH = 50;

  /** {@code user::api}가 문자열로 주는 임대인 구분값이다. */
  private static final String USER_TYPE_LANDLORD = "LANDLORD";

  /**
   * 도보 1분을 80m로 환산한다.
   *
   * <p>부동산 표시·광고에서 쓰는 관행값이라 임의의 상수를 새로 만들지 않는다. 카카오가 주는 거리는 <b>직선거리</b>라 실제 보행 경로보다 짧게 나오므로, 이 값은
   * 정답이 아니라 하한 제안이다 — 필드 이름의 {@code suggested}가 그 계약이다.
   */
  private static final int WALK_METERS_PER_MINUTE = 80;

  private final NearbyPlaceSearchClient nearbyPlaceSearchClient;
  private final UserAccountService userAccountService;

  /**
   * 역 이름으로 후보를 찾는다.
   *
   * @param landlordId 토큰에서 얻은 요청자 ID
   * @param request 검색어와 (선택적) 매물 좌표를 담은 요청 DTO
   * @return 제공자 순서를 유지한 역 후보 목록
   * @throws LandlordOnlyListingException 임대인이 아닌 사용자의 요청
   * @throws InvalidInputException 검색어가 누락·공백이거나 50자를 초과한 경우, 좌표가 한쪽만 오거나 범위를 벗어난 경우
   */
  public ListingStationSearchResponse searchByKeyword(
      long landlordId, ListingStationSearchRequest request) {
    requireLandlord(landlordId);
    String keyword = validateAndNormalizeKeyword(request == null ? null : request.getKeyword());
    Coordinate origin =
        optionalCoordinate(
            request == null ? null : request.getLat(), request == null ? null : request.getLng());

    return toResponse(nearbyPlaceSearchClient.searchStationsByKeyword(keyword, origin));
  }

  /**
   * 매물 좌표 주변의 역을 가까운 순으로 찾는다.
   *
   * @param landlordId 토큰에서 얻은 요청자 ID
   * @param request 매물 좌표를 담은 요청 DTO
   * @return 반경 내 역 목록. 없으면 빈 목록
   * @throws LandlordOnlyListingException 임대인이 아닌 사용자의 요청
   * @throws InvalidInputException 좌표가 누락되거나 WGS84 범위를 벗어난 경우
   */
  public ListingStationSearchResponse searchNearby(
      long landlordId, ListingNearbyStationRequest request) {
    requireLandlord(landlordId);
    Coordinate origin =
        requiredCoordinate(
            request == null ? null : request.getLat(), request == null ? null : request.getLng());

    return toResponse(nearbyPlaceSearchClient.searchNearbyStations(origin));
  }

  private void requireLandlord(long landlordId) {
    if (!USER_TYPE_LANDLORD.equals(userAccountService.getUserType(landlordId))) {
      throw new LandlordOnlyListingException();
    }
  }

  /**
   * 사용자 입력의 앞뒤 공백을 제거하고 API가 허용하는 1~50자 검색어인지 확인한다.
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

  /**
   * 선택 좌표를 읽는다 — <b>둘 다 있거나 둘 다 없어야 한다.</b>
   *
   * <p>하나만 온 요청을 조용히 무시하면 프론트는 거리순으로 정렬됐다고 믿는데 실제로는 정확도순인 결과를 받는다. 그 불일치가 화면에서 드러나지 않으므로 입력 오류로 되돌려
   * 준다.
   */
  private static Coordinate optionalCoordinate(Double lat, Double lng) {
    if (lat == null && lng == null) {
      return null;
    }
    if (lat == null) {
      throw new InvalidInputException("lat", "validation.required");
    }
    if (lng == null) {
      throw new InvalidInputException("lng", "validation.required");
    }
    return new Coordinate(lat, lng);
  }

  /** 좌표 검색은 좌표가 곧 검색 조건이라 누락을 허용하지 않는다. */
  private static Coordinate requiredCoordinate(Double lat, Double lng) {
    if (lat == null) {
      throw new InvalidInputException("lat", "validation.required");
    }
    if (lng == null) {
      throw new InvalidInputException("lng", "validation.required");
    }
    return new Coordinate(lat, lng);
  }

  /** 제공자 독립 도메인 값을 presentation 계약에 맞는 응답 항목으로 옮기고 도보 시간 제안을 덧붙인다. */
  private static ListingStationSearchResponse toResponse(List<NearbyPlace> places) {
    return new ListingStationSearchResponse(
        places.stream().map(ListingStationSearchService::toResponseItem).toList());
  }

  private static ListingStationSearchResponse.Item toResponseItem(NearbyPlace place) {
    return new ListingStationSearchResponse.Item(
        place.name(),
        place.roadAddress(),
        place.jibunAddress(),
        place.lat(),
        place.lng(),
        place.distanceMeters(),
        suggestedWalkMinutes(place.distanceMeters()));
  }

  /**
   * 직선거리를 도보 시간(분)으로 올림 환산한다. 거리를 모르면 제안하지 않는다.
   *
   * <p>0m라도 최소 1분으로 둔다 — "도보 0분"은 화면에서 값이 빠진 것처럼 보인다.
   */
  private static Integer suggestedWalkMinutes(Integer distanceMeters) {
    if (distanceMeters == null) {
      return null;
    }
    int minutes = (distanceMeters + WALK_METERS_PER_MINUTE - 1) / WALK_METERS_PER_MINUTE;
    return Math.max(1, minutes);
  }
}
