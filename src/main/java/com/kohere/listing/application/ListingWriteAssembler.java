package com.kohere.listing.application;

import com.kohere.listing.domain.ConditionTag;
import com.kohere.listing.domain.Listing;
import com.kohere.listing.domain.ListingRequiredAgreementMissingException;
import com.kohere.listing.domain.ListingUnknownCatalogCodeException;
import com.kohere.listing.domain.LocalizedText;
import com.kohere.listing.domain.catalog.ListingCatalogCategory;
import com.kohere.listing.domain.catalog.ListingCatalogRepository;
import com.kohere.listing.domain.nearby.Coordinate;
import com.kohere.listing.domain.university.UniversityRepository;
import com.kohere.listing.presentation.dto.ListingRegisterRequest;
import com.kohere.listing.presentation.dto.ListingWriteRequest;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 등록과 수정이 <b>똑같이 하는 조립</b>을 한 곳에 모은다.
 *
 * <p>임대인이 수정하는 것은 등록 때 보낸 속성 그대로라, 카탈로그 대조 16종·범위 파싱·행정구역 판별·주변 대학 파생·다국어 복사가 두 경로에서 완전히 같다. 이 코드가
 * 두 벌로 갈라지면 등록 폼에 필드가 하나 늘 때 한쪽만 고쳐지고, 그 차이는 <b>수정할 때 값이 조용히 사라지는</b> 모양으로만 드러난다.
 *
 * <p><b>여기서 하지 않는 것</b>이 계약의 절반이다 — {@code status}·{@code rejectionReason}·{@code updatedAt}은 전이가
 * 정하고({@link Listing#afterEdit}), {@code id}·{@code landlordId}·{@code schemaVersion}·{@code
 * createdAt}·{@code favoriteCount}·{@code consents}는 등록이 만들거나 수정이 승계한다. 특히 {@code consents}의 저장 값을
 * 여기서 만들면 수정할 때마다 최초 동의 시각을 덮어써 증빙이 사라진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ListingWriteAssembler {

  /** 행정구역 카탈로그가 모르는 지역에 쓰는 코드다(ADR-0046). 관리자 심사가 확정한다. */
  private static final String UNMAPPED_REGION_CODE = "ETC";

  /** 매물 좌표에서 이 반경 안의 대학을 주변 대학으로 본다(ADR-0045). */
  private static final int NEARBY_UNIVERSITY_RADIUS_METERS = 2_000;

  private final ListingCatalogRepository listingCatalogRepository;
  private final UniversityRepository universityRepository;

  /** 카탈로그 원장을 통째로 읽어 대조용 코드 묶음을 만든다. 요청 한 건에 한 번만 부른다. */
  ListingCatalogCodes catalog() {
    return ListingCatalogCodes.of(listingCatalogRepository.findAll());
  }

  /**
   * 요청이 정하는 값을 빌더에 채운다. 등록은 빈 빌더로, 수정은 {@code toBuilder()}로 부른다.
   *
   * <p>동의 게이트가 <b>여기</b> 있는 이유는 등록·수정 어느 경로도 빠뜨리지 못하게 하기 위해서다. 저장되는 동의 값 자체는 각 서비스가 정한다.
   *
   * @param builder 채울 빌더
   * @param request 등록·수정이 공유하는 요청 필드
   * @param roomFilterTags 방마다의 조건 태그. 카탈로그 대조에만 쓴다
   * @param catalog 대조할 카탈로그 코드
   */
  Listing.ListingBuilder apply(
      Listing.ListingBuilder builder,
      ListingWriteRequest request,
      List<Set<ConditionTag>> roomFilterTags,
      ListingCatalogCodes catalog) {
    requireCatalogCodes(request, roomFilterTags, catalog);
    requireConsents(request.consents());

    RangeInput usedFloor =
        RangeInput.parse("building.usedFloorRange", request.building().usedFloorRange());
    RangeInput age = RangeInput.parse("ageRange", request.ageRange());
    Listing.GeoPoint location = toLocation(request);

    return builder
        .contact(new Listing.Contact(request.contact().managerName(), request.contact().phone()))
        .businessRegistrationNumber(request.businessRegistrationNumber())
        .blogUrl(request.blogUrl())
        .ageMin(age.min())
        .ageMax(age.max())
        .title(bilingual(request.title()))
        .type(request.type())
        .rentalType(Listing.RentalType.MONTHLY_RENT)
        .genderPolicy(request.genderPolicy())
        .languagesSupported(request.languagesSupported())
        .nearbyUniversityCodes(findNearbyUniversityCodes(location))
        .address(toAddress(request, catalog))
        .location(location)
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
        .preferredNationalities(request.preferredNationalities())
        .contractDifficulties(request.contractDifficulties())
        .serviceFeedback(request.serviceFeedback());
  }

  /**
   * 이용약관 동의 2종이 모두 {@code true}인지 확인한다.
   *
   * <p>이 게이트 덕분에 <b>저장된 매물은 예외 없이 동의를 마친 매물</b>이 된다 — 심사 단계가 동의 여부를 판단 기준으로 다시 쓰지 않는 근거다. 수정할 때도 다시
   * 받고 다시 확인한다.
   */
  private static void requireConsents(ListingRegisterRequest.ConsentsRequest consents) {
    if (!Boolean.TRUE.equals(consents.privacyPolicyAgreed())
        || !Boolean.TRUE.equals(consents.listingExposureAgreed())) {
      throw new ListingRequiredAgreementMissingException();
    }
  }

  /**
   * 도로명 주소에서 행정구역을 뽑는다.
   *
   * <p>좌표는 여기서 다루지 않는다 — 주소 검색이 준 값을 {@link #toLocation}이 그대로 옮긴다(ADR-0042).
   */
  private static Listing.Address toAddress(
      ListingWriteRequest request, ListingCatalogCodes catalog) {
    String fullAddress = request.address().fullAddress();
    String city = catalog.findCity(fullAddress).orElse(UNMAPPED_REGION_CODE);
    String district = catalog.findDistrict(fullAddress).orElse(UNMAPPED_REGION_CODE);
    String detail = request.address().detail();
    return new Listing.Address(
        city,
        district,
        bilingual(fullAddress),
        detail == null || detail.isBlank() ? null : bilingual(detail));
  }

  /**
   * 요청의 좌표를 매물 좌표로 옮긴다.
   *
   * <p>값은 주소 검색이 준 것을 클라이언트가 되돌려 보낸 것이다 — 저장 시점에 지오코딩을 다시 하면 요청마다 외부 왕복과 502 경로가 생긴다(ADR-0042 §2).
   * 좌표 위조는 심사가 흡수한다.
   *
   * <p>도메인·GeoJSON 순서가 {@code (longitude, latitude)}라 요청의 {@code lat}·{@code lng}와 자리가 뒤집힌다.
   */
  private static Listing.GeoPoint toLocation(ListingWriteRequest request) {
    return new Listing.GeoPoint(request.address().lng(), request.address().lat());
  }

  /**
   * 매물 좌표에서 반경 {@value #NEARBY_UNIVERSITY_RADIUS_METERS}m 안의 대학 코드를 찾는다(ADR-0045).
   *
   * <p>폼은 대학을 묻지 않는다 — 임대인이 고르게 하면 자기 매물을 띄우려고 먼 대학까지 넣는다. 대신 서버가 시드된 좌표 원장과 대조해 파생한다.
   *
   * <p><b>수정으로 주소가 바뀌면 다시 파생한다.</b> 대학가 밖으로 옮기면 집합이 비어 진단 추천에서 빠지는데, 등록과 같은 정책이라 저장을 막지 않는다.
   */
  private Set<String> findNearbyUniversityCodes(Listing.GeoPoint location) {
    Set<String> codes =
        universityRepository.findCodesWithin(
            new Coordinate(location.latitude(), location.longitude()),
            NEARBY_UNIVERSITY_RADIUS_METERS);
    if (codes.isEmpty()) {
      log.warn(
          "인근 대학을 찾지 못했다 — 대학가 밖이거나 universities 시드가 비어 있다. lat={}, lng={}",
          location.latitude(),
          location.longitude());
    }
    return codes;
  }

  /**
   * 한국어 한 값을 두 언어에 같이 넣는다.
   *
   * <p>저장 계약({@code LocalizedText})이 두 언어를 모두 요구하는데 폼은 한국어 1칸만 받는다. 영어 번역은 관리자가 심사에서 채우며, 그 전까지 매물은
   * 세입자 조회에 노출되지 않는다.
   */
  static LocalizedText bilingual(String korean) {
    return new LocalizedText(korean, korean);
  }

  /** 요청의 코드값이 전부 카탈로그에 있는지 확인한다. 라벨 없는 코드는 응답에서 코드 문자열로 새어 나간다. */
  private static void requireCatalogCodes(
      ListingWriteRequest request,
      List<Set<ConditionTag>> roomFilterTags,
      ListingCatalogCodes catalog) {
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
    for (Set<ConditionTag> filterTags : roomFilterTags) {
      requireCodes(catalog, ListingCatalogCategory.CONDITION_TAG, filterTags);
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
