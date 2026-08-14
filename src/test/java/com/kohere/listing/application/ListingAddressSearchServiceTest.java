package com.kohere.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kohere.common.exception.InvalidInputException;
import com.kohere.listing.application.dto.ListingAddressSearchResponse;
import com.kohere.listing.domain.LandlordOnlyListingException;
import com.kohere.listing.domain.LocalizedText;
import com.kohere.listing.domain.address.AddressSearchClient;
import com.kohere.listing.domain.address.AddressSearchResult;
import com.kohere.listing.domain.catalog.ListingCatalogCategory;
import com.kohere.listing.domain.catalog.ListingCatalogEntry;
import com.kohere.listing.domain.catalog.ListingCatalogRepository;
import com.kohere.listing.presentation.dto.ListingAddressSearchRequest;
import com.kohere.user.api.UserAccountService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ListingAddressSearchService}의 인가·입력 검증과 등록 가능 여부 판정을 Spring 컨텍스트 없이 검증한다.
 *
 * <p>HTTP 변환은 인프라 테스트({@code NcpGeocodeClientTest})가 담당하므로 여기서는 임대인 재검사, 검색어 정규화, 잘못된 입력의 조기 거절,
 * {@code supported} 계산에 집중한다.
 */
@ExtendWith(MockitoExtension.class)
class ListingAddressSearchServiceTest {

  private static final long LANDLORD_ID = 42L;
  private static final long TENANT_ID = 1L;

  @Mock private AddressSearchClient addressSearchClient;
  @Mock private ListingCatalogRepository listingCatalogRepository;
  @Mock private UserAccountService userAccountService;

  private ListingAddressSearchService service;

  /** 각 테스트가 독립된 서비스 인스턴스와 Mockito 포트를 사용하도록 생성자 주입으로 조립한다. */
  @BeforeEach
  void setUp() {
    service =
        new ListingAddressSearchService(
            addressSearchClient, listingCatalogRepository, userAccountService);
    lenient().when(userAccountService.getUserType(LANDLORD_ID)).thenReturn("LANDLORD");
    lenient().when(userAccountService.getUserType(TENANT_ID)).thenReturn("TENANT");
  }

  /** 검색어 앞뒤 공백은 외부 호출 전에 제거하고, 주소 필드는 손실 없이 응답 DTO로 옮긴다. */
  @Test
  void search_유효한_검색어를_trim하고_주소_후보를_반환한다() {
    givenCatalog();
    when(addressSearchClient.search("신촌로 12")).thenReturn(List.of(seodaemun()));

    ListingAddressSearchResponse response = service.search(LANDLORD_ID, request("  신촌로 12  "));

    assertThat(response.items()).hasSize(1);
    ListingAddressSearchResponse.Item item = response.items().getFirst();
    assertThat(item.roadAddress()).isEqualTo("서울특별시 서대문구 신촌로 12");
    assertThat(item.jibunAddress()).isEqualTo("서울특별시 서대문구 창천동 1-1");
    assertThat(item.englishAddress()).isEqualTo("12, Sinchon-ro, Seodaemun-gu, Seoul");
    assertThat(item.lat()).isEqualTo(37.5559918);
    assertThat(item.lng()).isEqualTo(126.9368647);
    verify(addressSearchClient).search("신촌로 12");
  }

  /** 카탈로그에서 시·도와 구·군이 모두 잡히는 주소만 등록할 수 있다. */
  @Test
  void search_카탈로그가_아는_지역이면_supported가_true다() {
    givenCatalog();
    when(addressSearchClient.search("신촌로 12")).thenReturn(List.of(seodaemun()));

    assertThat(service.search(LANDLORD_ID, request("신촌로 12")).items().getFirst().supported())
        .isTrue();
  }

  /**
   * 구·군이 카탈로그에 없으면 등록에서 400이 되므로 검색 단계에서 알린다.
   *
   * <p>경기도는 {@code CITY}에 있지만 그 시·군·구는 {@code DISTRICT}에 없다 — 시·도만 잡히는 것으로는 등록할 수 없다.
   */
  @Test
  void search_구군이_카탈로그에_없으면_supported가_false다() {
    givenCatalog();
    when(addressSearchClient.search("불정로 6")).thenReturn(List.of(bundang()));

    assertThat(service.search(LANDLORD_ID, request("불정로 6")).items().getFirst().supported())
        .isFalse();
  }

  /** 외부가 정상적으로 빈 목록을 반환하면 실패로 바꾸지 않고 프론트가 빈 상태를 그릴 수 있게 유지한다. */
  @Test
  void search_검색_결과가_없으면_빈_items를_반환한다() {
    givenCatalog();
    when(addressSearchClient.search("없는주소")).thenReturn(List.of());

    assertThat(service.search(LANDLORD_ID, request("없는주소")).items()).isEmpty();
  }

  /** 임대인이 아니면 외부를 부르기 전에 403으로 거절한다 — 보안 필터는 정식 회원인지까지만 본다. */
  @Test
  void search_임대인이_아니면_외부_검색을_호출하지_않는다() {
    assertThatThrownBy(() -> service.search(TENANT_ID, request("신촌로 12")))
        .isInstanceOf(LandlordOnlyListingException.class);
    verifyNoInteractions(addressSearchClient, listingCatalogRepository);
  }

  /** 누락된 keyword는 외부 호출량을 소모하기 전에 400 입력 예외로 거절한다. */
  @Test
  void search_keyword가_누락되면_외부_검색을_호출하지_않는다() {
    assertThatThrownBy(() -> service.search(LANDLORD_ID, new ListingAddressSearchRequest()))
        .isInstanceOf(InvalidInputException.class);
    verifyNoInteractions(addressSearchClient, listingCatalogRepository);
  }

  /** 공백만 있는 keyword도 유효한 주소 검색어가 아니므로 외부 검색을 호출하지 않는다. */
  @Test
  void search_keyword가_공백이면_외부_검색을_호출하지_않는다() {
    assertThatThrownBy(() -> service.search(LANDLORD_ID, request("   ")))
        .isInstanceOf(InvalidInputException.class);
    verifyNoInteractions(addressSearchClient, listingCatalogRepository);
  }

  /** 주소 검색 정책인 최대 100자를 넘는 keyword는 외부 호출 전에 차단한다. */
  @Test
  void search_keyword가_100자를_초과하면_외부_검색을_호출하지_않는다() {
    assertThatThrownBy(() -> service.search(LANDLORD_ID, request("가".repeat(101))))
        .isInstanceOf(InvalidInputException.class);
    verifyNoInteractions(addressSearchClient, listingCatalogRepository);
  }

  /** 인가 검사는 요청자 ID로만 한다 — 다른 인자로 새어 나가지 않는지 확인한다. */
  @Test
  void search_요청자_ID로_임대인_여부를_확인한다() {
    givenCatalog();
    when(addressSearchClient.search("신촌로 12")).thenReturn(List.of(seodaemun()));

    service.search(LANDLORD_ID, request("신촌로 12"));

    verify(userAccountService).getUserType(LANDLORD_ID);
    verify(userAccountService, never()).getUserType(TENANT_ID);
  }

  /** 등록 경로와 같은 카탈로그를 쓴다 — 별도 사전을 두면 검색의 안내와 등록의 결과가 갈라진다. */
  private void givenCatalog() {
    when(listingCatalogRepository.findAll())
        .thenReturn(
            List.of(
                entry(ListingCatalogCategory.CITY, "SEOUL", "서울특별시", "Seoul"),
                entry(ListingCatalogCategory.CITY, "GYEONGGI", "경기도", "Gyeonggi-do"),
                entry(ListingCatalogCategory.DISTRICT, "SEODAEMUN_GU", "서대문구", "Seodaemun-gu")));
  }

  private static ListingCatalogEntry entry(
      ListingCatalogCategory category, String code, String ko, String en) {
    return new ListingCatalogEntry(category, code, new LocalizedText(ko, en));
  }

  /** 서울 서대문구 — 카탈로그가 시·도와 구·군을 모두 아는 등록 가능한 주소다. */
  private static AddressSearchResult seodaemun() {
    return new AddressSearchResult(
        "서울특별시 서대문구 신촌로 12",
        "서울특별시 서대문구 창천동 1-1",
        "12, Sinchon-ro, Seodaemun-gu, Seoul",
        37.5559918,
        126.9368647);
  }

  /** 경기도 성남시 분당구 — 시·도는 카탈로그에 있지만 구·군이 없어 등록할 수 없는 주소다. */
  private static AddressSearchResult bundang() {
    return new AddressSearchResult(
        "경기도 성남시 분당구 불정로 6 NAVER그린팩토리",
        "경기도 성남시 분당구 정자동 178-1",
        "6, Buljeong-ro, Bundang-gu, Seongnam-si, Gyeonggi-do",
        37.3595963,
        127.1054328);
  }

  /** 테스트 입력을 간결하게 만들되 실제 MVC 바인딩과 같은 setter 경로로 요청 DTO를 구성한다. */
  private static ListingAddressSearchRequest request(String keyword) {
    ListingAddressSearchRequest request = new ListingAddressSearchRequest();
    request.setKeyword(keyword);
    return request;
  }
}
