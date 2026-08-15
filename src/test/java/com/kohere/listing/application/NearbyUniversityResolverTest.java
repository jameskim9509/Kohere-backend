package com.kohere.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import com.kohere.listing.domain.LocalizedText;
import com.kohere.listing.domain.catalog.ListingCatalogCategory;
import com.kohere.listing.domain.catalog.ListingCatalogEntry;
import com.kohere.listing.domain.catalog.ListingCatalogRepository;
import com.kohere.listing.domain.nearby.NearbyPlace;
import com.kohere.listing.domain.nearby.NearbyPlaceSearchClient;
import com.kohere.listing.domain.nearby.NearbyPlaceSearchUpstreamException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 등록이 좌표로 {@code nearbyUniversityCodes}를 파생하는 규칙을 고정한다(ADR-0044).
 *
 * <p>이 값은 진단 추천이 매물을 찾는 <b>조인 키</b>라 틀리면 조용히 추천이 오염된다. 그래서 ① 반경 내 대학을 전부 담는지 ② 카탈로그가 모르는 대학을 버리는지 ③
 * 외부 실패를 흡수해 등록을 죽이지 않는지 세 가지를 못 박는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NearbyUniversityResolverTest {

  private static final double SINCHON_LAT = 37.5559918;
  private static final double SINCHON_LNG = 126.9368647;

  @Mock private NearbyPlaceSearchClient nearbyPlaceSearchClient;
  @Mock private ListingCatalogRepository listingCatalogRepository;

  private NearbyUniversityResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new NearbyUniversityResolver(nearbyPlaceSearchClient, listingCatalogRepository);
    given(listingCatalogRepository.findAll()).willReturn(catalog());
  }

  /**
   * 가장 가까운 하나가 아니라 <b>반경 내 전부</b>다.
   *
   * <p>필드가 복수형이고 진단 추천이 {@code $in}이라 담긴 수만큼 매칭 기회가 는다 — 신촌 매물이 연세대만 달면 이화여대를 고른 진단 결과에 걸리지 않는다.
   */
  @Test
  void 반경내대학을_전부담는다() {
    given(nearbyPlaceSearchClient.searchNearbyUniversities(any()))
        .willReturn(List.of(place("연세대학교", 780), place("이화여자대학교", 950), place("홍익대학교", 1800)));

    assertThat(resolver.resolve(SINCHON_LAT, SINCHON_LNG))
        .containsExactlyInAnyOrder("YONSEI", "EWHA", "HONGIK");
  }

  /** 캠퍼스 표기가 붙어도 카탈로그 label.ko contains 매칭으로 코드가 된다. */
  @Test
  void 캠퍼스표기가붙어도_코드로매칭한다() {
    given(nearbyPlaceSearchClient.searchNearbyUniversities(any()))
        .willReturn(List.of(place("서울대학교 관악캠퍼스", 500), place("한국외국어대학교 서울캠퍼스", 900)));

    assertThat(resolver.resolve(SINCHON_LAT, SINCHON_LNG)).containsExactlyInAnyOrder("SNU", "HUFS");
  }

  /** 카탈로그가 모르는 대학은 저장할 코드가 없으므로 버린다. */
  @Test
  void 카탈로그에없는대학은_버린다() {
    given(nearbyPlaceSearchClient.searchNearbyUniversities(any()))
        .willReturn(List.of(place("연세대학교", 780), place("명지대학교", 2400)));

    assertThat(resolver.resolve(SINCHON_LAT, SINCHON_LNG)).containsExactly("YONSEI");
  }

  /** 반경 내 대학이 없으면 빈 집합이다 — 정상 상태다. */
  @Test
  void 반경내대학이없으면_빈집합이다() {
    given(nearbyPlaceSearchClient.searchNearbyUniversities(any())).willReturn(List.of());

    assertThat(resolver.resolve(SINCHON_LAT, SINCHON_LNG)).isEmpty();
  }

  /**
   * <b>외부 실패를 밖으로 내보내지 않는다.</b>
   *
   * <p>주소 좌표는 없으면 매물이 성립하지 않는 필수값이라 실패가 곧 등록 실패였지만(ADR-0042 §2), 이 값은 빈 집합이 이미 유효한 상태다. 파생값 때문에 등록이
   * 죽으면 안 된다.
   */
  @Test
  void 외부장애를_빈집합으로흡수한다() {
    willThrow(new NearbyPlaceSearchUpstreamException(new IllegalStateException("kakao down")))
        .given(nearbyPlaceSearchClient)
        .searchNearbyUniversities(any());

    assertThat(resolver.resolve(SINCHON_LAT, SINCHON_LNG)).isEmpty();
  }

  /** 좌표가 WGS84 범위를 벗어나도 등록을 막지 않는다 — 파생만 비운다. */
  @Test
  void 좌표가범위를벗어나도_등록을막지않는다() {
    assertThat(resolver.resolve(999.0, 999.0)).isEmpty();
  }

  private static NearbyPlace place(String name, int distanceMeters) {
    return new NearbyPlace(name, "", "", 37.5, 126.9, distanceMeters);
  }

  /** 정본 카탈로그의 UNIVERSITY 항목 일부다 — label.ko가 매칭 기준이다. */
  private static List<ListingCatalogEntry> catalog() {
    return Set.of(
            entry("YONSEI", "연세대학교", "Yonsei Univ."),
            entry("EWHA", "이화여자대학교", "Ewha Womans Univ."),
            entry("HONGIK", "홍익대학교", "Hongik Univ."),
            entry("SNU", "서울대학교", "Seoul National Univ."),
            entry("HUFS", "한국외국어대학교", "Hankuk Univ. of Foreign Studies"))
        .stream()
        .toList();
  }

  private static ListingCatalogEntry entry(String code, String ko, String en) {
    return new ListingCatalogEntry(
        ListingCatalogCategory.UNIVERSITY, code, new LocalizedText(ko, en));
  }
}
