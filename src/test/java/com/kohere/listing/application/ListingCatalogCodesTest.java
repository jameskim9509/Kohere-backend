package com.kohere.listing.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.kohere.listing.domain.City;
import com.kohere.listing.domain.District;
import com.kohere.listing.domain.LocalizedText;
import com.kohere.listing.domain.catalog.ListingCatalogCategory;
import com.kohere.listing.domain.catalog.ListingCatalogEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 등록 요청의 코드 대조와 도로명 주소에서의 행정구역 추출을 검증한다. */
class ListingCatalogCodesTest {

  /** 카탈로그에 있는 코드는 통과한다. */
  @Test
  void contains_카탈로그에_있는_코드를_허용한다() {
    ListingCatalogCodes catalog = catalog();

    assertThat(catalog.contains(ListingCatalogCategory.LISTING_TYPE, "SHARE_HOUSE")).isTrue();
  }

  /** 카탈로그에 없는 코드는 막는다 — 라벨이 없으면 응답에 코드 문자열이 그대로 나간다. */
  @Test
  void contains_카탈로그에_없는_코드를_거절한다() {
    ListingCatalogCodes catalog = catalog();

    assertThat(catalog.contains(ListingCatalogCategory.LISTING_TYPE, "GOSHIWON")).isFalse();
  }

  /** 카테고리가 다르면 같은 코드라도 별개로 본다. */
  @Test
  void contains_카테고리가_다르면_같은_코드도_거절한다() {
    ListingCatalogCodes catalog = catalog();

    assertThat(catalog.contains(ListingCatalogCategory.BUILDING_TYPE, "SHARE_HOUSE")).isFalse();
  }

  /** 도로명 주소에서 광역·기초 행정구역을 모두 뽑는다. */
  @Test
  void findCityAndDistrict_도로명_주소에서_행정구역을_뽑는다() {
    ListingCatalogCodes catalog = catalog();
    String address = "서울특별시 관악구 신림동 나로 56-15";

    assertThat(catalog.findCity(address)).contains(City.SEOUL);
    assertThat(catalog.findDistrict(address)).contains(District.GWANAK_GU);
  }

  /** 접두사가 아니라 포함 여부로 판정한다 — 등록 폼이 주소를 자유 입력으로 받기 때문이다. */
  @Test
  void findDistrict_주소_중간에_있어도_찾는다() {
    ListingCatalogCodes catalog = catalog();

    assertThat(catalog.findDistrict("대한민국 서울특별시 마포구 홍익로 10")).contains(District.MAPO_GU);
  }

  /** 행정구역을 못 찾으면 비어 있고, 서비스가 이를 400으로 바꾼다. */
  @Test
  void findDistrict_행정구역이_없으면_비어있다() {
    ListingCatalogCodes catalog = catalog();

    assertThat(catalog.findDistrict("어딘가 이상한 주소")).isEmpty();
    assertThat(catalog.findCity("어딘가 이상한 주소")).isEmpty();
  }

  /** 값이 비어 있으면 조회하지 않는다. */
  @Test
  void findCity_값이_비면_비어있다() {
    ListingCatalogCodes catalog = catalog();

    assertThat(catalog.findCity(null)).isEmpty();
    assertThat(catalog.findCity("  ")).isEmpty();
  }

  /**
   * 주소 검색(NCP Geocoding)이 주는 모양 그대로 행정구역을 뽑는다(ADR-0042).
   *
   * <p>등록은 검색 응답의 {@code roadAddress}를 손대지 않고 그대로 받는다. NCP는 도로명 뒤에 <b>건물명</b>을 덧붙여 주는데, 그 접미사가 파싱을
   * 방해하지 않아야 한다 — 시·도와 구·군은 문자열 앞쪽에 있다.
   */
  @Test
  void findCityAndDistrict_NCP가_준_건물명_붙은_도로명주소를_파싱한다() {
    ListingCatalogCodes catalog = catalog();
    String ncpRoadAddress = "서울특별시 서대문구 신촌로 12 코히어빌딩";

    assertThat(catalog.findCity(ncpRoadAddress)).contains(City.SEOUL);
    assertThat(catalog.findDistrict(ncpRoadAddress)).contains(District.SEODAEMUN_GU);
  }

  /**
   * 시·도만 카탈로그에 있는 지역은 구·군에서 걸린다 — 등록의 {@code 400 LISTING_INVALID_ADDRESS}가 나는 지점이다.
   *
   * <p>주소 검색이 이 후보를 {@code supported=false}로 내려보내는 근거이기도 하다. 카탈로그에 {@code GYEONGGI}는 있지만 그 시·군·구는
   * 없다.
   */
  @Test
  void findDistrict_시도만_아는_지역은_비어있다() {
    ListingCatalogCodes catalog = catalog();
    String ncpRoadAddress = "경기도 성남시 분당구 불정로 6 NAVER그린팩토리";

    assertThat(catalog.findCity(ncpRoadAddress)).contains(City.GYEONGGI);
    assertThat(catalog.findDistrict(ncpRoadAddress)).isEmpty();
  }

  /** 카탈로그에는 있으나 도메인 enum에 없는 코드는 매칭 대상에서 빠진다. */
  @Test
  void findDistrict_도메인_enum에_없는_코드는_무시한다() {
    ListingCatalogCodes catalog =
        ListingCatalogCodes.of(
            List.of(entry(ListingCatalogCategory.DISTRICT, "SEOCHO_GU", "서초구", "Seocho-gu")));

    assertThat(catalog.findDistrict("서울특별시 서초구 반포대로 1")).isEmpty();
  }

  private static ListingCatalogCodes catalog() {
    return ListingCatalogCodes.of(
        List.of(
            entry(ListingCatalogCategory.LISTING_TYPE, "SHARE_HOUSE", "쉐어하우스", "Share House"),
            entry(ListingCatalogCategory.CITY, "SEOUL", "서울특별시", "Seoul"),
            // 시·도만 있고 그 구·군은 없는 지역이다. 정본 카탈로그도 같은 상태라(DISTRICT는 서울 9개 구뿐)
            // 등록 가능 지역이 검색 결과보다 좁다는 사실을 이 항목이 재현한다.
            entry(ListingCatalogCategory.CITY, "GYEONGGI", "경기도", "Gyeonggi-do"),
            entry(ListingCatalogCategory.DISTRICT, "GWANAK_GU", "관악구", "Gwanak-gu"),
            entry(ListingCatalogCategory.DISTRICT, "SEODAEMUN_GU", "서대문구", "Seodaemun-gu"),
            entry(ListingCatalogCategory.DISTRICT, "MAPO_GU", "마포구", "Mapo-gu")));
  }

  private static ListingCatalogEntry entry(
      ListingCatalogCategory category, String code, String ko, String en) {
    return new ListingCatalogEntry(category, code, new LocalizedText(ko, en));
  }
}
