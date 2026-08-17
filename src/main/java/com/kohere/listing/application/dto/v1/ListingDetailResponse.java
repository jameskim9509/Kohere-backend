package com.kohere.listing.application.dto.v1;

import com.kohere.listing.application.dto.CodeLabelResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 매물 상세 화면 응답이다.
 *
 * <p>프론트는 {@link CodeLabelResponse#label()}을 화면에 표시하고 {@link CodeLabelResponse#code()}를 필터 요청이나 내부
 * 조건 비교에 사용한다. 매물명·주소·역명·방 이름·설명은 사용자 언어에 맞는 문자열 하나만 내려가므로 프론트에서 ko/en을 다시 선택할 필요가 없다.
 *
 * @param status 게시 상태는 일반 사용자 UI의 번역 대상이 아닌 서버 내부 상태이므로 기존 enum 값으로 유지한다.
 * @param conditions 상세 상단 조건 배지. 각 항목의 label을 표시하고 code는 필터 요청에 사용한다.
 */
public record ListingDetailResponse(
    String listingId,
    String title,
    CodeLabelResponse type,
    ListingStatus status,
    CodeLabelResponse rentalType,
    RefundPolicyResponse refundPolicy,
    Contract contract,
    CodeLabelResponse genderPolicy,
    GeoPoint location,
    AddressResponse address,
    NearestTransitResponse nearestTransit,
    Set<String> nearbyUniversityCodes,
    BuildingResponse building,
    PropertyPolicies propertyPolicies,
    FacilitiesResponse facilities,
    List<CodeLabelResponse> conditions,
    List<RoomOfferResponse> roomOffers,
    DescriptionsResponse descriptions,
    List<String> imageUrls,
    boolean favorited,
    int favoriteCount,
    Instant createdAt,
    Instant updatedAt) {

  /**
   * 아래 여섯은 개정 전 도메인 타입을 <b>이 패키지 안에 동결</b>한 사본이다.
   *
   * <p>{@code Inventory}·{@code PropertyPolicies}는 v4에서 삭제됐고 {@code Pricing}·{@code ListingStatus}는
   * 모양·값이 바뀌었다. 도메인을 다시 참조하면 다음 스키마 개정 때 v1 응답이 또 끌려가므로, 전부 사본으로 둬 v1 계약을 고정한다. 이 파일은 v1을 제거할 때까지
   * 수정하지 않는다.
   */
  public enum ListingStatus {
    DRAFT,
    PUBLISHED,
    PAUSED,
    DELETED
  }

  /** 개정 전 방 상품 상태다. */
  public enum RoomOfferStatus {
    ACTIVE,
    INACTIVE
  }

  /** 개정 전 매물 공통 계약기간이다. v4에서는 방 상품 하위로 내려갔다. */
  public record Contract(int minStayMonths, int maxStayMonths) {}

  /** 개정 전 운영 정책이다. v4에서 삭제되고 {@code arcRequired} 하나만 루트로 남았다. */
  public record PropertyPolicies(
      boolean arcRequired,
      boolean residentRegistrationAvailable,
      boolean studySuitable,
      boolean mealsProvided,
      boolean englishAvailable) {}

  /** 개정 전 가격이다. v4와 필드 구성이 같지만 v1 계약을 고정하려고 사본으로 둔다. */
  public record Pricing(int monthlyRent, int deposit, int maintenanceFee, String currency) {}

  /** 개정 전 방 재고다. v4에서 삭제됐다. */
  public record Inventory(
      int totalCount, int availableCount, java.time.LocalDate nextAvailableFrom) {}

  /** 프론트 지도 컴포넌트에서 바로 쓰는 위도·경도 값이다. */
  public record GeoPoint(double lat, double lng) {}

  /** 검색용 행정 코드는 유지하고, 화면 주소만 사용자 언어로 선택한 응답이다. */
  public record AddressResponse(String city, String district, String fullAddress, String detail) {}

  /** 가까운 교통수단의 code/label과 사용자 언어의 역명·주변 안내를 담는다. */
  public record NearestTransitResponse(
      CodeLabelResponse type, String name, int walkMinutes, String nearbyPlacesDescription) {}

  /** 건물 종류는 code/label로, 숫자와 boolean은 언어와 무관한 원래 값으로 내린다. */
  public record BuildingResponse(
      CodeLabelResponse type,
      int usedFloorMin,
      int usedFloorMax,
      int totalFloors,
      boolean parkingAvailable,
      boolean elevatorAvailable) {}

  /** 환불 정책 코드는 기존 의미를 유지하고 설명 문장만 사용자 언어로 선택한다. */
  public record RefundPolicyResponse(String code, String description) {}

  /** 시설 그룹별 공통 코드를 모두 code/label 형태로 내려주는 상세 응답이다. */
  public record FacilitiesResponse(
      List<CodeLabelResponse> heatingSystem,
      List<CodeLabelResponse> kitchen,
      List<CodeLabelResponse> laundry,
      List<CodeLabelResponse> livingAmenities,
      List<CodeLabelResponse> securityFeatures,
      List<CommonSpaceResponse> commonSpaces,
      List<CodeLabelResponse> providedSupplies) {}

  /** 공용공간 종류의 code/label과, 데이터가 있는 경우에만 사용하는 개수를 담는다. */
  public record CommonSpaceResponse(CodeLabelResponse type, Integer count) {}

  /**
   * 동일 가격·조건을 공유하는 실제 방 묶음 하나다.
   *
   * <p>프론트는 name을 그대로 표시하고 filterTags의 label을 배지에 표시한다. 필터 요청에는 같은 항목의 code를 사용한다.
   */
  public record RoomOfferResponse(
      String roomOfferId,
      String name,
      RoomOfferStatus status,
      Pricing pricing,
      Inventory inventory,
      List<CodeLabelResponse> filterTags,
      List<String> roomImageUrls) {}

  /** 사용자 언어로 선택된 매물 설명과 번역 범위에서 제외된 기존 추가 안내다. */
  public record DescriptionsResponse(String description, String extraNotes) {}
}
