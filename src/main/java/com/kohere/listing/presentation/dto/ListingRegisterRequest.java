package com.kohere.listing.presentation.dto;

import com.kohere.listing.domain.ArcRequirement;
import com.kohere.listing.domain.ConditionTag;
import com.kohere.listing.domain.ContractDifficulty;
import com.kohere.listing.domain.KitchenFacility;
import com.kohere.listing.domain.LaundryFacility;
import com.kohere.listing.domain.Listing;
import com.kohere.listing.domain.ListingType;
import com.kohere.listing.domain.LivingAmenity;
import com.kohere.listing.domain.Nationality;
import com.kohere.listing.domain.NearbyFacility;
import com.kohere.listing.domain.ProvidedSupply;
import com.kohere.listing.domain.SecurityFeature;
import com.kohere.listing.domain.SupportedLanguage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;

/**
 * 매물 등록 요청 DTO({@code POST /api/v2/listings}, 임대인 전용).
 *
 * <p>등록 폼 1칸에 대응하는 두 값은 문자열 {@code min~max}로 받아 서버가 두 필드로 나눈다 — {@code
 * building.usedFloorRange}({@code usedFloorMin}·{@code usedFloorMax})와 {@code ageRange}({@code
 * ageMin}·{@code ageMax}).
 *
 * <p>다국어 문구는 <b>한국어 한 값만</b> 받는다. 저장 계약({@code LocalizedText})이 두 언어를 모두 요구하므로 서버가 {@code en}에 같은
 * 값을 복사하고, 영어 번역은 관리자가 승인 심사에서 채운다.
 *
 * <p>사진 파일은 이 요청에 없다. {@code POST /api/v2/listings/images}로 <b>한 장씩 미리 올려</b> 받은 저장 키를 {@code
 * imageKeys}(1~5개)·{@code roomOffers[].roomImageKeys}(방마다 2~5개)로 참조한다. 장수와 소유권은 {@code
 * ListingImageKeySet}이 확인하고, 서버가 확정 위치로 복사한 뒤 그 URL을 응답에 채운다(ADR-0041).
 *
 * <p>주소도 미리 검색해 둔다 — {@code GET /api/v1/listings/addresses}가 준 후보의 도로명 주소와 좌표를 {@code
 * address.fullAddress}·{@code address.lat}·{@code address.lng}에 그대로 담는다. 서버는 그 좌표로 매물의 {@code
 * location}을 채우며 등록 시점에 지오코딩을 다시 하지 않는다(ADR-0042).
 *
 * <p>요청에 없는 값은 서버가 채운다 — {@code _id}·{@code roomOffers[].roomOfferId}·{@code
 * schemaVersion}(4)·{@code status}({@code PENDING})·{@code favoriteCount}(0)·{@code
 * createdAt}/{@code updatedAt}·{@code rentalType}({@code MONTHLY_RENT})·{@code
 * pricing.currency}({@code KRW})·{@code roomOffers[].status}({@code ACTIVE}). {@code
 * nearbyUniversityCodes}는 좌표 기반 파생이 후속이라 빈 상태로 저장한다.
 *
 * <p>docs/api/specs/03-listings-favorites.md · 시퀀스 US-3-6.
 */
public record ListingRegisterRequest(
    @NotBlank String title,
    @NotNull ListingType type,
    @NotNull @Valid ContactRequest contact,
    @NotBlank String businessRegistrationNumber,
    String blogUrl,
    @NotNull @Valid AddressRequest address,
    @NotNull @Valid BuildingRequest building,
    @NotNull Listing.GenderPolicy genderPolicy,
    @NotEmpty Set<SupportedLanguage> languagesSupported,
    @NotBlank String ageRange,
    @NotNull ArcRequirement arcRequired,
    @NotNull @Valid FacilitiesRequest facilities,
    @NotNull @Valid NearestTransitRequest nearestTransit,
    @NotEmpty Set<NearbyFacility> nearbyFacilities,
    @NotBlank String description,
    @NotBlank String extraNotes,
    @NotBlank String refundPolicy,
    @NotEmpty List<String> imageKeys,
    @NotEmpty @Valid List<RoomOfferRequest> roomOffers,
    @NotEmpty Set<Nationality> preferredNationalities,
    @NotEmpty Set<ContractDifficulty> contractDifficulties,
    String serviceFeedback) {

  /** 세입자에게 공개하는 매물 담당자 연락처다. */
  public record ContactRequest(
      @NotBlank String managerName, @NotBlank String phone, @NotBlank String sms) {}

  /**
   * 주소는 {@code GET /api/v1/listings/addresses}가 준 값을 그대로 되돌려 받는다(ADR-0042).
   *
   * <p>도로명 주소는 받은 값을 그대로 저장하고 행정구역은 서버가 파싱해 채운다. 좌표는 매물의 {@code location}이 되며, 검색 결과가 아닌 값을 임의로 만들어
   * 보내지 않는다 — 관리자 승인 심사가 주소와 좌표의 일치를 본다.
   */
  public record AddressRequest(
      @NotBlank String fullAddress,
      String detail,
      @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
      @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lng) {}

  /** 지점 운영층은 {@code usedFloorRange}({@code min~max})로 받아 두 필드로 나눈다. */
  public record BuildingRequest(
      @NotNull Listing.BuildingType type,
      @Min(1) int totalFloors,
      @NotBlank String usedFloorRange,
      boolean parkingAvailable,
      boolean elevatorAvailable) {}

  /** 시설은 전부 복수 선택 코드 집합이다. */
  public record FacilitiesRequest(
      @NotEmpty Set<Listing.HeatingSystem> heatingSystem,
      @NotEmpty Set<KitchenFacility> kitchen,
      @NotEmpty Set<LaundryFacility> laundry,
      @NotEmpty Set<LivingAmenity> livingAmenities,
      @NotEmpty Set<SecurityFeature> securityFeatures,
      @NotEmpty Set<Listing.CommonSpaceType> commonSpaces,
      @NotEmpty Set<ProvidedSupply> providedSupplies) {}

  /** 근처 지하철역 정보다. 현재 {@code type}의 허용값은 {@code SUBWAY} 하나다. */
  public record NearestTransitRequest(
      @NotNull Listing.TransitType type, @NotBlank String name, @Min(0) int walkMinutes) {}

  /** 개별 객실 타입이다. 사진은 미리 올려 둔 키로 참조한다. */
  public record RoomOfferRequest(
      @NotBlank String name,
      @NotNull @Valid ContractRequest contract,
      @NotNull @Valid PricingRequest pricing,
      @NotEmpty Set<ConditionTag> filterTags,
      @NotEmpty List<String> roomImageKeys) {}

  /** 이용 기간(개월)이다. {@code maxStayMonths >= minStayMonths}는 도메인이 재검증한다. */
  public record ContractRequest(@Min(1) int minStayMonths, @Min(1) int maxStayMonths) {}

  /** 통화는 서버가 {@code KRW}로 고정하므로 요청에 없다. */
  public record PricingRequest(
      @Min(0) int monthlyRent, @Min(0) int deposit, @Min(0) int maintenanceFee) {}
}
