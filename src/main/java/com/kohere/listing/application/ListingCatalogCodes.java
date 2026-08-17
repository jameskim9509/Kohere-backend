package com.kohere.listing.application;

import com.kohere.listing.domain.catalog.ListingCatalogCategory;
import com.kohere.listing.domain.catalog.ListingCatalogEntry;
import java.util.Arrays;
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
 * 매칭은 주소를 공백으로 끊은 <b>토큰과의 완전 일치</b>다 — 부분 문자열로 보면 {@code 성북구} 주소가 부산 {@code 북구}에 걸린다.
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
   * <p>카탈로그의 한국어 라벨(예 {@code 서울특별시})과 같은 토큰이 주소에 있는지로 판정한다. 위치는 보지 않는다 — 도(道)는 {@code 경기도 수원시 장안구
   * …}처럼 한 단계 더 들어가고, 주소 앞에 {@code 대한민국} 같은 말이 붙기도 한다.
   */
  Optional<String> findCity(String fullAddress) {
    return findByLabel(ListingCatalogCategory.CITY, fullAddress);
  }

  /** 도로명 주소에서 기초 행정구역을 찾는다. */
  Optional<String> findDistrict(String fullAddress) {
    return findByLabel(ListingCatalogCategory.DISTRICT, fullAddress);
  }

  private Optional<String> findByLabel(ListingCatalogCategory category, String fullAddress) {
    if (fullAddress == null || fullAddress.isBlank()) {
      return Optional.empty();
    }
    Set<String> tokens = tokenize(fullAddress);
    return koLabelsByCategory.getOrDefault(category, Map.of()).entrySet().stream()
        .filter(entry -> tokens.contains(entry.getValue()))
        .map(Map.Entry::getKey)
        .findFirst();
  }

  /**
   * 주소를 공백으로 끊는다.
   *
   * <p>라벨과 <b>완전히 같은 토큰</b>만 매칭하기 위해서다. 부분 문자열로 보면 카탈로그가 서울 밖으로 넓어지는 순간 {@code "서울특별시 성북구 …"}가 부산
   * {@code 북구}에 걸리고, 어느 쪽이 이길지는 카탈로그 순서가 정하게 된다.
   */
  private static Set<String> tokenize(String fullAddress) {
    return Arrays.stream(fullAddress.trim().split("\s+")).collect(Collectors.toUnmodifiableSet());
  }
}
