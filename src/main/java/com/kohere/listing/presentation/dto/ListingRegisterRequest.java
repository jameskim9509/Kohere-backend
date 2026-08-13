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
 * <p>사진은 이 JSON에 없다. 요청이 {@code multipart/form-data}이고 이 DTO는 그중 {@code request} part 하나에 대응한다 —
 * 파일은 {@code listingImages}·{@code roomImages{i}} part로 오고 서버가 저장한 URL을 채운다(ADR-0041).
 *
 * <p>요청에 없는 값은 서버가 채운다 — {@code _id}·{@code roomOffers[].roomOfferId}·{@code
 * schemaVersion}(4)·{@code status}({@code PENDING})·{@code favoriteCount}(0)·{@code
 * createdAt}/{@code updatedAt}·{@code rentalType}({@code MONTHLY_RENT})·{@code
 * pricing.currency}({@code KRW})·{@code roomOffers[].status}({@code ACTIVE}). {@code
 * location}·{@code nearbyUniversityCodes}는 지오코딩 미구현이라 각각 비어 있는 상태로 저장한다.
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
    @NotEmpty @Valid List<RoomOfferRequest> roomOffers,
    @NotEmpty Set<Nationality> preferredNationalities,
    @NotEmpty Set<ContractDifficulty> contractDifficulties,
    String serviceFeedback) {

  /** 세입자에게 공개하는 매물 담당자 연락처다. */
  public record ContactRequest(
      @NotBlank String managerName, @NotBlank String phone, @NotBlank String sms) {}

  /** 도로명 주소는 입력값을 그대로 저장하고, 행정구역은 서버가 파싱해 채운다. */
  public record AddressRequest(@NotBlank String fullAddress, String detail) {}

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

  /** 개별 객실 타입이다. 사진은 이 JSON이 아니라 {@code roomImages{i}} part로 온다. */
  public record RoomOfferRequest(
      @NotBlank String name,
      @NotNull @Valid ContractRequest contract,
      @NotNull @Valid PricingRequest pricing,
      @NotEmpty Set<ConditionTag> filterTags) {}

  /** 이용 기간(개월)이다. {@code maxStayMonths >= minStayMonths}는 도메인이 재검증한다. */
  public record ContractRequest(@Min(1) int minStayMonths, @Min(1) int maxStayMonths) {}

  /** 통화는 서버가 {@code KRW}로 고정하므로 요청에 없다. */
  public record PricingRequest(
      @Min(0) int monthlyRent, @Min(0) int deposit, @Min(0) int maintenanceFee) {}
}
