package com.kohere.listing.application;

import com.kohere.listing.domain.City;
import com.kohere.listing.domain.District;
import com.kohere.listing.domain.catalog.ListingCatalogCategory;
import com.kohere.listing.domain.catalog.ListingCatalogEntry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 등록 요청의 코드값을 {@code listingCatalog}와 대조하고, 도로명 주소에서 행정구역을 뽑는다.
 *
 * <p>enum으로 역직렬화된 값이라 오타는 이미 걸러졌지만, <b>카탈로그에 라벨이 없는 코드</b>는 응답에서 코드 문자열이 그대로 노출된다({@code
 * ListingLocalizationContext}가 라벨 없는 코드를 코드값으로 폴백한다). 저장 시점에 막지 않으면 외국인 화면에 {@code SHARE_HOUSE} 같은
 * 값이 나가므로 등록 경로에서 차단한다.
 *
 * <p>행정구역도 같은 카탈로그를 쓴다. 별도 주소 사전을 두면 카탈로그와 갈라지므로, {@code CITY}·{@code DISTRICT}의 한국어 라벨을 그대로 매칭한다.
 */
final class ListingCatalogCodes {

  private final Map<ListingCatalogCategory, Set<String>> codesByCategory;
  private final Map<ListingCatalogCategory, Map<String, String>> koLabelsByCategory;

  private ListingCatalogCodes(List<ListingCatalogEntry> entries) {
    this.codesByCategory =
        entries.stream()
            .collect(
                Collectors.groupingBy(
                    ListingCatalogEntry::category,
                    Collectors.mapping(ListingCatalogEntry::code, Collectors.toUnmodifiableSet())));
    this.koLabelsByCategory = new LinkedHashMap<>();
    for (ListingCatalogEntry entry : entries) {
      koLabelsByCategory
          .computeIfAbsent(entry.category(), key -> new LinkedHashMap<>())
          .put(entry.code(), entry.label().ko());
    }
  }

  static ListingCatalogCodes of(List<ListingCatalogEntry> entries) {
    return new ListingCatalogCodes(entries);
  }

  /** 코드 하나가 해당 카테고리에 있는지 확인한다. */
  boolean contains(ListingCatalogCategory category, String code) {
    return codesByCategory.getOrDefault(category, Set.of()).contains(code);
  }

  /**
   * 도로명 주소에서 광역 행정구역을 찾는다.
   *
   * <p>카탈로그의 한국어 라벨(예 {@code 서울특별시})이 주소에 포함되는지로 판정한다. 등록 폼이 도로명 주소를 자유 입력으로 받으므로 접두사 일치만 요구하지 않는다.
   */
  Optional<City> findCity(String fullAddress) {
    return findByLabel(ListingCatalogCategory.CITY, fullAddress, City::valueOf);
  }

  /** 도로명 주소에서 기초 행정구역을 찾는다. */
  Optional<District> findDistrict(String fullAddress) {
    return findByLabel(ListingCatalogCategory.DISTRICT, fullAddress, District::valueOf);
  }

  /**
   * 외부에서 받은 장소 이름에서 대학 코드를 찾는다(ADR-0044).
   *
   * <p>{@code City}·{@code District}와 같은 {@code contains} 매칭이다 — 카카오는 {@code 연세대학교 신촌캠퍼스 제1공학관}처럼
   * 캠퍼스·건물이 붙은 이름을 주므로 접두사 일치만 요구하지 않는다. 카탈로그 14종은 서로의 부분 문자열이 아니라 모호성이 없다.
   *
   * <p>{@code City}·{@code District}와 달리 도메인 enum이 없어 카탈로그 코드 문자열을 그대로 돌려준다 — {@code
   * nearbyUniversityCodes}가 {@code Set<String>}이기 때문이다.
   *
   * @param placeName 제공자가 준 장소 이름
   * @return 카탈로그가 아는 대학이면 그 코드, 아니면 빈 값
   */
  Optional<String> findUniversityCode(String placeName) {
    if (placeName == null || placeName.isBlank()) {
      return Optional.empty();
    }
    return koLabelsByCategory
        .getOrDefault(ListingCatalogCategory.UNIVERSITY, Map.of())
        .entrySet()
        .stream()
        .filter(entry -> placeName.contains(entry.getValue()))
        .map(Map.Entry::getKey)
        .findFirst();
  }

  private <E extends Enum<E>> Optional<E> findByLabel(
      ListingCatalogCategory category,
      String fullAddress,
      java.util.function.Function<String, E> toEnum) {
    if (fullAddress == null || fullAddress.isBlank()) {
      return Optional.empty();
    }
    return koLabelsByCategory.getOrDefault(category, Map.of()).entrySet().stream()
        .filter(entry -> fullAddress.contains(entry.getValue()))
        .map(Map.Entry::getKey)
        .flatMap(code -> toEnumOrEmpty(code, toEnum).stream())
        .findFirst();
  }

  /** 카탈로그에는 있으나 도메인 enum에 없는 코드는 매칭 대상에서 조용히 제외한다. */
  private static <E extends Enum<E>> Optional<E> toEnumOrEmpty(
      String code, java.util.function.Function<String, E> toEnum) {
    try {
      return Optional.of(toEnum.apply(code));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
