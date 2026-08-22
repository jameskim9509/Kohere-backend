package com.kohere.listing.application;

import com.kohere.listing.application.ListingImageConfirmer.ConfirmedListingImages;
import com.kohere.listing.application.dto.ListingDetailResponse;
import com.kohere.listing.domain.LandlordOnlyListingException;
import com.kohere.listing.domain.Listing;
import com.kohere.listing.domain.ListingRepository;
import com.kohere.listing.domain.ListingRequiredAgreementMissingException;
import com.kohere.listing.domain.ListingUnknownCatalogCodeException;
import com.kohere.listing.domain.LocalizedText;
import com.kohere.listing.domain.catalog.ListingCatalogCategory;
import com.kohere.listing.domain.catalog.ListingCatalogRepository;
import com.kohere.listing.domain.image.ListingImageKeySet;
import com.kohere.listing.domain.nearby.Coordinate;
import com.kohere.listing.domain.university.UniversityRepository;
import com.kohere.listing.presentation.dto.ListingRegisterRequest;
import com.kohere.user.api.UserAccountService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
@Slf4j
public class ListingRegisterService {

  /** {@code user::api}가 문자열로 주는 임대인 구분값이다. */
  private static final String USER_TYPE_LANDLORD = "LANDLORD";

  /**
   * 인근 대학으로 볼 반경이다(ADR-0045).
   *
   * <p>도보~버스 한두 정거장 생활권이다. 더 넓히면 신촌·홍대처럼 대학이 밀집한 지역에서 실제로는 무관한 대학까지 붙고, 좁히면 대학가 매물이 빈 배열로 남아 진단
   * 추천에서 통째로 빠진다.
   */
  private static final int NEARBY_UNIVERSITY_RADIUS_METERS = 2_000;

  /**
   * 카탈로그가 모르는 지역에 쓰는 코드다.
   *
   * <p>등록을 막지 않는다 — 9개 구 목록은 데이터 무결성이 아니라 영업 범위 정책이고, 그 정책 게이트는 이미 뒤에 있다(등록은 {@code PENDING}이고 관리자
   * 승인이 반려한다). 심사에서 관리자가 실제 코드로 확정하고 카탈로그에 그 지역을 추가한다.
   *
   * <p>진단 지역의 {@code ETC}("그 외 지역")와 같은 값으로 만난다 — 코드로 못 잡았다는 건 곧 명시 목록 밖 지역이라는 뜻이라, 그 사용자에게 노출되는 것이
   * 맞다.
   */
  private static final String UNMAPPED_REGION_CODE = "ETC";

  private final ListingRepository listingRepository;
  private final ListingCatalogRepository listingCatalogRepository;
  private final UniversityRepository universityRepository;
  private final ListingImageConfirmer listingImageConfirmer;
  private final ListingLocalizationService listingLocalizationService;
  private final UserAccountService userAccountService;

  /**
   * 매물 등록 동의가 참조하는 약관 버전이다. 회원 약관 버전({@code app.terms.version})과 <b>독립적으로 개정</b>된다 — 매물 약관만 고쳐도 회원
   * 약관 버전은 그대로여야 한다.
   */
  private final String consentVersion;

  public ListingRegisterService(
      ListingRepository listingRepository,
      ListingCatalogRepository listingCatalogRepository,
      UniversityRepository universityRepository,
      ListingImageConfirmer listingImageConfirmer,
      ListingLocalizationService listingLocalizationService,
      UserAccountService userAccountService,
      @Value("${app.terms.listing-consent-version}") String consentVersion) {
    this.listingRepository = listingRepository;
    this.listingCatalogRepository = listingCatalogRepository;
    this.universityRepository = universityRepository;
    this.listingImageConfirmer = listingImageConfirmer;
    this.listingLocalizationService = listingLocalizationService;
    this.userAccountService = userAccountService;
    this.consentVersion = consentVersion;
  }

  /**
   * 등록 요청을 저장하고 생성된 매물을 상세 응답 구조로 돌려준다.
   *
   * <p>순서가 중요하다 — 입력 검증을 모두 끝낸 뒤에 사진을 확정 위치로 복사하고, 그다음 저장한다. 검증이 복사보다 앞서야 거절된 요청이 확정 위치에 흔적을 남기지
   * 않고, 저장이 실패하면 복사본을 지워 사진 없는 매물이 남지 않는다. 임시본은 어느 실패에서도 지우지 않아 사용자가 그대로 다시 제출할 수 있다(ADR-0041 §4).
   *
   * <p>응답 언어는 다른 조회와 같이 임대인 계정의 표시 언어를 따른다. 다만 등록 직후 문서는 두 언어에 같은 한국어가 들어 있어 어느 언어를 골라도 결과가 같다.
   */
  public ListingDetailResponse register(long landlordId, ListingRegisterRequest request) {
    requireLandlord(landlordId);
    // 사진 키 검사가 가장 앞이다 — 저장소를 부르기 전에 장수와 소유권이 끝나므로, 거절될 요청은 확정 위치에 흔적을 남기지 않는다.
    ListingImageKeySet keys =
        ListingImageKeySet.of(
            landlordId,
            request.imageKeys(),
            request.roomOffers().stream()
                .map(ListingRegisterRequest.RoomOfferRequest::roomImageKeys)
                .toList());
    ListingCatalogCodes catalog = ListingCatalogCodes.of(listingCatalogRepository.findAll());
    // 요청을 통째로 매물로 조립해 본다 — 카탈로그 대조·범위 파싱·주소 판별이 모두 여기서 끝난다.
    // 식별자와 사진 URL만 아직 비어 있다.
    Listing draft = toListing(landlordId, request, catalog);

    String listingId = listingRepository.nextIdentity();
    List<String> roomOfferIds =
        request.roomOffers().stream().map(unused -> listingRepository.nextIdentity()).toList();
    ConfirmedListingImages confirmed = listingImageConfirmer.confirm(listingId, roomOfferIds, keys);

    Listing saved;
    try {
      saved = listingRepository.save(withStoredImages(draft, listingId, roomOfferIds, confirmed));
    } catch (RuntimeException e) {
      listingImageConfirmer.rollback(confirmed);
      throw e;
    }
    // 저장이 끝나야 임시본을 치운다. 실패해도 만료 규칙이 대신 치우므로 재시도하지 않는다.
    listingImageConfirmer.discardPending(keys);
    return ListingResponseMapper.toDetail(
        saved,
        false,
        listingLocalizationService.contextFor(userAccountService.getLanguage(landlordId)));
  }

  /**
   * 조립해 둔 매물에 발급한 식별자와 업로드 결과를 채운다.
   *
   * <p>확정 키가 식별자를 포함해서 저장보다 먼저 발급했고, URL은 복사가 끝나야 알 수 있다. 두 값만 마지막에 얹는다.
   */
  private static Listing withStoredImages(
      Listing draft,
      String listingId,
      List<String> roomOfferIds,
      ConfirmedListingImages confirmed) {
    List<Listing.RoomOffer> roomOffers = new ArrayList<>();
    for (int i = 0; i < roomOfferIds.size(); i++) {
      Listing.RoomOffer roomOffer = draft.getRoomOffers().get(i);
      roomOffers.add(
          new Listing.RoomOffer(
              roomOfferIds.get(i),
              roomOffer.name(),
              roomOffer.status(),
              roomOffer.contract(),
              roomOffer.pricing(),
              roomOffer.filterTags(),
              confirmed.roomUrls().get(i)));
    }
    return draft.toBuilder()
        .id(listingId)
        .imageUrls(confirmed.coverUrls())
        .roomOffers(List.copyOf(roomOffers))
        .build();
  }

  /**
   * 이용약관 동의 2종이 모두 {@code true}인지 확인한다.
   *
   * <p>이 게이트 덕분에 <b>저장된 매물은 예외 없이 동의를 마친 매물</b>이 된다 — 심사 단계가 동의 여부를 판단 기준으로 다시 쓰지 않는 근거다.
   */
  private static void requireConsents(ListingRegisterRequest request) {
    var consents = request.consents();
    if (!Boolean.TRUE.equals(consents.privacyPolicyAgreed())
        || !Boolean.TRUE.equals(consents.listingExposureAgreed())) {
      throw new ListingRequiredAgreementMissingException();
    }
  }

  private void requireLandlord(long landlordId) {
    if (!USER_TYPE_LANDLORD.equals(userAccountService.getUserType(landlordId))) {
      throw new LandlordOnlyListingException();
    }
  }

  /**
   * 요청을 도메인 애그리거트로 조립한다.
   *
   * <p>서버가 채우는 값(상태·통화·시각 등)과 폼 1칸에서 나눈 값(운영층·연령대), 주소에서 뽑은 행정구역, 좌표에서 파생한 인근 대학이 여기서 결정된다. 값 범위
   * 불변식은 {@code ListingValidator}가 저장 직전에 다시 본다.
   *
   * <p>식별자와 사진 URL은 아직 비어 있다 — 저장 키가 식별자를 포함하고 URL은 업로드가 끝나야 정해지므로, 그 둘만 {@link #withStoredImages}가
   * 마지막에 얹는다.
   */
  private Listing toListing(
      long landlordId, ListingRegisterRequest request, ListingCatalogCodes catalog) {
    requireCatalogCodes(request, catalog);
    requireConsents(request);
    RangeInput usedFloor =
        RangeInput.parse("building.usedFloorRange", request.building().usedFloorRange());
    RangeInput age = RangeInput.parse("ageRange", request.ageRange());
    Instant now = Instant.now();
    Listing.GeoPoint location = toLocation(request);

    return Listing.builder()
        .schemaVersion(4)
        .landlordId(landlordId)
        .contact(new Listing.Contact(request.contact().managerName(), request.contact().phone()))
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
        .imageUrls(List.of())
        .nearbyUniversityCodes(findNearbyUniversityCodes(location))
        .createdAt(now)
        .updatedAt(now)
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
        .roomOffers(request.roomOffers().stream().map(ListingRegisterService::toRoomOffer).toList())
        .preferredNationalities(request.preferredNationalities())
        .contractDifficulties(request.contractDifficulties())
        .serviceFeedback(request.serviceFeedback())
        .consents(new Listing.Consents(true, true, consentVersion, now))
        .build();
  }

  /**
   * 도로명 주소에서 행정구역을 뽑는다.
   *
   * <p>좌표는 여기서 다루지 않는다 — 주소 검색이 준 값을 {@link #toLocation}이 그대로 옮긴다(ADR-0042).
   */
  private static Listing.Address toAddress(
      ListingRegisterRequest request, ListingCatalogCodes catalog) {
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
   * <p>값은 주소 검색({@code GET /api/v1/listings/addresses})이 준 것을 클라이언트가 되돌려 보낸 것이다 — 등록 시점에 지오코딩을 다시
   * 하면 등록마다 외부 왕복과 502 경로가 생긴다(ADR-0042 §2). 좌표 위조는 {@code PENDING} 상태와 관리자 승인 심사가 흡수한다.
   *
   * <p>도메인·GeoJSON 순서가 {@code (longitude, latitude)}라 요청의 {@code lat}·{@code lng}와 자리가 뒤집힌다. 값 범위는
   * 요청 검증과 {@code GeoPoint} 생성자가 이중으로 본다.
   */
  private static Listing.GeoPoint toLocation(ListingRegisterRequest request) {
    return new Listing.GeoPoint(request.address().lng(), request.address().lat());
  }

  /**
   * 매물 좌표에서 반경 {@value #NEARBY_UNIVERSITY_RADIUS_METERS}m 안의 대학 코드를 찾는다(ADR-0045).
   *
   * <p>등록 폼은 대학을 묻지 않는다 — 임대인이 고르게 하면 자기 매물을 띄우려고 먼 대학까지 넣는다. 대신 서버가 시드된 좌표 원장과 대조해 파생한다. 진단 추천은 이
   * 집합을 대학 그룹의 멤버 코드와 대조한다.
   *
   * <p>비어 있어도 등록을 막지 않는다. 대학가 밖 매물은 정상적으로 빈 집합이고, 원장이 비어 있어도(시드 전 신규 환경) 같은 결과라 둘을 구분할 수 없다. 등록을
   * 세우는 대신 경고를 남긴다 — 배포 절차의 시드 단계가 빠졌다는 유일한 신호다.
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

  private static Listing.RoomOffer toRoomOffer(ListingRegisterRequest.RoomOfferRequest request) {
    return new Listing.RoomOffer(
        null,
        bilingual(request.name()),
        Listing.RoomOfferStatus.ACTIVE,
        new Listing.Contract(
            request.contract().minStayMonths(), request.contract().maxStayMonths()),
        new Listing.Pricing(
            request.pricing().monthlyRent(),
            request.pricing().deposit(),
            request.pricing().maintenanceFee(),
            Listing.Currency.KRW),
        request.filterTags(),
        List.of());
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
