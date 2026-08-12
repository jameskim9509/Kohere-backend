package com.kohere.listing.application;

import com.kohere.listing.application.dto.ListingDetailResponse;
import com.kohere.listing.domain.City;
import com.kohere.listing.domain.District;
import com.kohere.listing.domain.LandlordOnlyListingException;
import com.kohere.listing.domain.Listing;
import com.kohere.listing.domain.ListingInvalidAddressException;
import com.kohere.listing.domain.ListingRepository;
import com.kohere.listing.domain.ListingUnknownCatalogCodeException;
import com.kohere.listing.domain.LocalizedText;
import com.kohere.listing.domain.catalog.ListingCatalogCategory;
import com.kohere.listing.domain.catalog.ListingCatalogRepository;
import com.kohere.listing.presentation.dto.ListingRegisterRequest;
import com.kohere.user.api.UserAccountService;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 임대인이 올린 등록 요청을 검증해 매물 문서로 저장한다({@code POST /api/v2/listings}).
 *
 * <p>인가는 두 겹이다 — 보안 필터가 {@code hasRole("USER")}로 정식 회원만 통과시키고, 여기서 {@code userType}이 임대인인지 다시 확인한다.
 * 필터만으로는 세입자 토큰을 막을 수 없다.
 *
 * <p>등록 직후 상태는 {@code PENDING}이라 조회·검색·상세에 노출되지 않는다. 관리자 승인에서 {@code PUBLISHED}로 전이하며, 그 승인 심사가
 * 사업자등록번호 진위와 영어 번역을 함께 확인한다.
 *
 * <p>docs/api/specs/03-listings-favorites.md · 시퀀스 US-3-6.
 */
@Service
@RequiredArgsConstructor
public class ListingRegisterService {

  /** {@code user::api}가 문자열로 주는 임대인 구분값이다. */
  private static final String USER_TYPE_LANDLORD = "LANDLORD";

  private final ListingRepository listingRepository;
  private final ListingCatalogRepository listingCatalogRepository;
  private final ListingLocalizationService listingLocalizationService;
  private final UserAccountService userAccountService;

  /**
   * 등록 요청을 저장하고 생성된 매물을 상세 응답 구조로 돌려준다.
   *
   * <p>응답 언어는 다른 조회와 같이 임대인 계정의 표시 언어를 따른다. 다만 등록 직후 문서는 두 언어에 같은 한국어가 들어 있어 어느 언어를 골라도 결과가 같다.
   */
  public ListingDetailResponse register(long landlordId, ListingRegisterRequest request) {
    requireLandlord(landlordId);
    ListingCatalogCodes catalog = ListingCatalogCodes.of(listingCatalogRepository.findAll());
    Listing saved = listingRepository.save(toListing(landlordId, request, catalog));
    return ListingResponseMapper.toDetail(
        saved,
        false,
        listingLocalizationService.contextFor(userAccountService.getLanguage(landlordId)));
  }

  private void requireLandlord(long landlordId) {
    if (!USER_TYPE_LANDLORD.equals(userAccountService.getUserType(landlordId))) {
      throw new LandlordOnlyListingException();
    }
  }

  /**
   * 요청을 도메인 애그리거트로 조립한다.
   *
   * <p>서버가 채우는 값(상태·통화·시각 등)과 폼 1칸에서 나눈 값(운영층·연령대), 주소에서 뽑은 행정구역이 여기서 결정된다. 값 범위 불변식은 {@code
   * ListingValidator}가 저장 직전에 다시 본다.
   */
  private Listing toListing(
      long landlordId, ListingRegisterRequest request, ListingCatalogCodes catalog) {
    requireCatalogCodes(request, catalog);
    RangeInput usedFloor =
        RangeInput.parse("building.usedFloorRange", request.building().usedFloorRange());
    RangeInput age = RangeInput.parse("ageRange", request.ageRange());
    Instant now = Instant.now();

    return Listing.builder()
        .schemaVersion(4)
        .landlordId(landlordId)
        .contact(
            new Listing.Contact(
                request.contact().managerName(),
                request.contact().phone(),
                request.contact().sms()))
        .businessRegistrationNumber(request.businessRegistrationNumber())
        .blogUrl(request.blogUrl())
        .ageMin(age.min())
        .ageMax(age.max())
        .title(bilingual(request.title()))
        .type(request.type())
        .rentalType(Listing.RentalType.MONTHLY_RENT)
        .status(Listing.ListingStatus.PENDING)
        .genderPolicy(request.genderPolicy())
        .languagesSupported(request.languagesSupported())
        .favoriteCount(0)
        .imageUrls(request.imageUrls())
        .nearbyUniversityCodes(Set.of())
        .createdAt(now)
        .updatedAt(now)
        .address(toAddress(request, catalog))
        .building(
            new Listing.Building(
                request.building().type(),
                usedFloor.min(),
                usedFloor.max(),
                request.building().totalFloors(),
                request.building().parkingAvailable(),
                request.building().elevatorAvailable()))
        .description(bilingual(request.description()))
        .extraNotes(bilingual(request.extraNotes()))
        .facilities(
            new Listing.Facilities(
                request.facilities().heatingSystem(),
                request.facilities().kitchen(),
                request.facilities().laundry(),
                request.facilities().livingAmenities(),
                request.facilities().securityFeatures(),
                request.facilities().commonSpaces(),
                request.facilities().providedSupplies()))
        .nearestTransit(
            new Listing.NearestTransit(
                request.nearestTransit().type(),
                bilingual(request.nearestTransit().name()),
                request.nearestTransit().walkMinutes()))
        .nearbyFacilities(request.nearbyFacilities())
        .arcRequired(request.arcRequired())
        .refundPolicy(bilingual(request.refundPolicy()))
        .roomOffers(request.roomOffers().stream().map(ListingRegisterService::toRoomOffer).toList())
        .preferredNationalities(request.preferredNationalities())
        .contractDifficulties(request.contractDifficulties())
        .serviceFeedback(request.serviceFeedback())
        .build();
  }

  /**
   * 도로명 주소에서 행정구역을 뽑는다.
   *
   * <p>좌표({@code location})는 지오코딩이 없어 채우지 않는다. 좌표 없는 매물은 관리자 승인을 통과하지 못하므로 공개 매물은 항상 좌표를 갖는다.
   */
  private static Listing.Address toAddress(
      ListingRegisterRequest request, ListingCatalogCodes catalog) {
    String fullAddress = request.address().fullAddress();
    City city = catalog.findCity(fullAddress).orElseThrow(ListingInvalidAddressException::new);
    District district =
        catalog.findDistrict(fullAddress).orElseThrow(ListingInvalidAddressException::new);
    String detail = request.address().detail();
    return new Listing.Address(
        city,
        district,
        bilingual(fullAddress),
        detail == null || detail.isBlank() ? null : bilingual(detail));
  }

  private static Listing.RoomOffer toRoomOffer(ListingRegisterRequest.RoomOfferRequest request) {
    return new Listing.RoomOffer(
        null,
        bilingual(request.name()),
        Listing.RoomOfferStatus.ACTIVE,
        new Listing.Contract(
            request.contract().minStayMonths(), request.contract().maxStayMonths()),
        new Listing.Pricing(
            request.pricing().monthlyRent(),
            request.pricing().weeklyRent(),
            request.pricing().deposit(),
            request.pricing().maintenanceFee(),
            Listing.Currency.KRW),
        request.filterTags(),
        request.roomImageUrls());
  }

  /**
   * 한국어 한 값을 두 언어에 같이 넣는다.
   *
   * <p>저장 계약({@code LocalizedText})이 두 언어를 모두 요구하는데 등록 폼은 한국어 1칸만 받는다. 영어 번역은 관리자가 승인 심사에서 채우며, 그
   * 전까지 매물은 {@code PENDING}이라 세입자 조회에 노출되지 않는다.
   */
  private static LocalizedText bilingual(String korean) {
    return new LocalizedText(korean, korean);
  }

  /** 요청의 코드값이 전부 카탈로그에 있는지 확인한다. 라벨 없는 코드는 응답에서 코드 문자열로 새어 나간다. */
  private static void requireCatalogCodes(
      ListingRegisterRequest request, ListingCatalogCodes catalog) {
    requireCode(catalog, ListingCatalogCategory.LISTING_TYPE, request.type());
    requireCode(catalog, ListingCatalogCategory.RENTAL_TYPE, Listing.RentalType.MONTHLY_RENT);
    requireCode(catalog, ListingCatalogCategory.GENDER_POLICY, request.genderPolicy());
    requireCode(catalog, ListingCatalogCategory.BUILDING_TYPE, request.building().type());
    requireCode(catalog, ListingCatalogCategory.TRANSIT_TYPE, request.nearestTransit().type());
    requireCode(catalog, ListingCatalogCategory.ARC_REQUIREMENT, request.arcRequired());
    requireCodes(catalog, ListingCatalogCategory.SUPPORTED_LANGUAGE, request.languagesSupported());
    requireCodes(catalog, ListingCatalogCategory.NEARBY_FACILITY, request.nearbyFacilities());
    requireCodes(
        catalog, ListingCatalogCategory.HEATING_SYSTEM, request.facilities().heatingSystem());
    requireCodes(catalog, ListingCatalogCategory.KITCHEN, request.facilities().kitchen());
    requireCodes(catalog, ListingCatalogCategory.LAUNDRY, request.facilities().laundry());
    requireCodes(
        catalog, ListingCatalogCategory.LIVING_AMENITY, request.facilities().livingAmenities());
    requireCodes(
        catalog, ListingCatalogCategory.SECURITY_FEATURE, request.facilities().securityFeatures());
    requireCodes(catalog, ListingCatalogCategory.COMMON_SPACE, request.facilities().commonSpaces());
    requireCodes(
        catalog, ListingCatalogCategory.PROVIDED_SUPPLY, request.facilities().providedSupplies());
    for (ListingRegisterRequest.RoomOfferRequest roomOffer : request.roomOffers()) {
      requireCodes(catalog, ListingCatalogCategory.CONDITION_TAG, roomOffer.filterTags());
    }
  }

  private static void requireCodes(
      ListingCatalogCodes catalog, ListingCatalogCategory category, Set<? extends Enum<?>> codes) {
    codes.forEach(code -> requireCode(catalog, category, code));
  }

  private static void requireCode(
      ListingCatalogCodes catalog, ListingCatalogCategory category, Enum<?> code) {
    if (!catalog.contains(category, code.name())) {
      throw new ListingUnknownCatalogCodeException();
    }
  }
}
