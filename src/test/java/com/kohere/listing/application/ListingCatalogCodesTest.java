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
            entry(ListingCatalogCategory.DISTRICT, "GWANAK_GU", "관악구", "Gwanak-gu"),
            entry(ListingCatalogCategory.DISTRICT, "MAPO_GU", "마포구", "Mapo-gu")));
  }

  private static ListingCatalogEntry entry(
      ListingCatalogCategory category, String code, String ko, String en) {
    return new ListingCatalogEntry(category, code, new LocalizedText(ko, en));
  }
}
