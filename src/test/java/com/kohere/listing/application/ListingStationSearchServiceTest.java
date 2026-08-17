package com.kohere.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

import com.kohere.common.exception.InvalidInputException;
import com.kohere.listing.application.dto.ListingStationSearchResponse;
import com.kohere.listing.domain.LandlordOnlyListingException;
import com.kohere.listing.domain.nearby.Coordinate;
import com.kohere.listing.domain.nearby.NearbyPlace;
import com.kohere.listing.domain.nearby.NearbyPlaceSearchClient;
import com.kohere.listing.presentation.dto.ListingNearbyStationRequest;
import com.kohere.listing.presentation.dto.ListingStationSearchRequest;
import com.kohere.user.api.UserAccountService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 역 검색 유스케이스의 입력 검증·인가·도보 시간 산정을 고정한다(ADR-0044).
 *
 * <p>외부 연동은 포트를 목으로 대체한다 — HTTP 계약은 {@code KakaoLocalPlaceClientTest}가 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ListingStationSearchServiceTest {

  private static final long LANDLORD_ID = 42L;
  private static final long TENANT_ID = 1L;

  @Mock private NearbyPlaceSearchClient nearbyPlaceSearchClient;
  @Mock private UserAccountService userAccountService;

  private ListingStationSearchService service;

  @BeforeEach
  void setUp() {
    service = new ListingStationSearchService(nearbyPlaceSearchClient, userAccountService);
    given(userAccountService.getUserType(LANDLORD_ID)).willReturn("LANDLORD");
    given(userAccountService.getUserType(TENANT_ID)).willReturn("TENANT");
  }

  // ── 인가 ──────────────────────────────────────────────────────────────────

  /** 등록 폼 전용 API라 세입자는 컨트롤러까지 도달해도 서비스가 거절한다. */
  @Test
  void 임대인이아니면_거절한다() {
    assertThatThrownBy(() -> service.searchByKeyword(TENANT_ID, keywordRequest("신촌", null, null)))
        .isInstanceOf(LandlordOnlyListingException.class);
    assertThatThrownBy(() -> service.searchNearby(TENANT_ID, nearbyRequest(37.5, 126.9)))
        .isInstanceOf(LandlordOnlyListingException.class);
  }

  // ── 키워드 검증 ───────────────────────────────────────────────────────────

  @Test
  void 키워드가비었거나너무길면_거절한다() {
    assertThatThrownBy(
            () -> service.searchByKeyword(LANDLORD_ID, keywordRequest("   ", null, null)))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("keyword");

    assertThatThrownBy(
            () -> service.searchByKeyword(LANDLORD_ID, keywordRequest("가".repeat(51), null, null)))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("50");
  }

  /** 외부를 부르기 전에 trim한다 — 잘못된 입력이 일일 호출량을 소모하지 않게 한다. */
  @Test
  void 키워드는_trim해서_외부에전달한다() {
    given(nearbyPlaceSearchClient.searchStationsByKeyword(eq("신촌"), isNull()))
        .willReturn(List.of());

    service.searchByKeyword(LANDLORD_ID, keywordRequest("  신촌  ", null, null));

    ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(nearbyPlaceSearchClient)
        .searchStationsByKeyword(keyword.capture(), isNull());
    assertThat(keyword.getValue()).isEqualTo("신촌");
  }

  // ── 좌표 검증 ─────────────────────────────────────────────────────────────

  /**
   * 좌표는 둘 다 오거나 둘 다 없어야 한다.
   *
   * <p>하나만 온 요청을 조용히 무시하면 프론트는 거리순으로 정렬됐다고 믿는데 실제로는 정확도순인 결과를 받는다 — 화면에서 드러나지 않는 불일치라 입력 오류로 되돌려
   * 준다.
   */
  @Test
  @DisplayName("키워드 검색에서 좌표를 하나만 보내면 거절한다")
  void 좌표를하나만보내면_거절한다() {
    assertThatThrownBy(
            () -> service.searchByKeyword(LANDLORD_ID, keywordRequest("신촌", 37.5559918, null)))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("lng");

    assertThatThrownBy(
            () -> service.searchByKeyword(LANDLORD_ID, keywordRequest("신촌", null, 126.9368647)))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("lat");
  }

  @Test
  void 좌표가WGS84범위를벗어나면_거절한다() {
    assertThatThrownBy(() -> service.searchNearby(LANDLORD_ID, nearbyRequest(91.0, 126.9)))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("lat");

    assertThatThrownBy(() -> service.searchNearby(LANDLORD_ID, nearbyRequest(37.5, 181.0)))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("lng");
  }

  /** 좌표 검색은 좌표가 곧 검색 조건이라 누락을 허용하지 않는다. */
  @Test
  void 좌표검색에서_좌표가없으면_거절한다() {
    assertThatThrownBy(() -> service.searchNearby(LANDLORD_ID, nearbyRequest(null, 126.9)))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("lat");
  }

  /** 좌표를 주면 그대로 포트에 전달된다 — 위·경도가 뒤집히지 않는지 함께 본다. */
  @Test
  void 좌표를주면_그대로외부에전달한다() {
    given(nearbyPlaceSearchClient.searchStationsByKeyword(eq("신촌"), any())).willReturn(List.of());

    service.searchByKeyword(LANDLORD_ID, keywordRequest("신촌", 37.5559918, 126.9368647));

    ArgumentCaptor<Coordinate> origin = ArgumentCaptor.forClass(Coordinate.class);
    org.mockito.Mockito.verify(nearbyPlaceSearchClient)
        .searchStationsByKeyword(eq("신촌"), origin.capture());
    assertThat(origin.getValue().lat()).isEqualTo(37.5559918);
    assertThat(origin.getValue().lng()).isEqualTo(126.9368647);
  }

  // ── 도보 시간 제안 ────────────────────────────────────────────────────────

  /**
   * {@code ceil(distance / 80)}이며 최소 1분이다.
   *
   * <p>0m라도 "도보 0분"으로 두지 않는다 — 화면에서 값이 빠진 것처럼 보인다.
   */
  @Test
  void 도보시간제안_경계값() {
    given(nearbyPlaceSearchClient.searchNearbyStations(any()))
        .willReturn(
            List.of(station(0), station(1), station(80), station(81), station(160), station(320)));

    List<ListingStationSearchResponse.Item> items =
        service.searchNearby(LANDLORD_ID, nearbyRequest(37.5, 126.9)).items();

    assertThat(items)
        .extracting(ListingStationSearchResponse.Item::suggestedWalkMinutes)
        .containsExactly(1, 1, 1, 2, 2, 4);
  }

  /** 거리를 모르면 제안하지 않는다 — 지어내지 않는다. */
  @Test
  void 거리가없으면_도보시간을제안하지않는다() {
    given(nearbyPlaceSearchClient.searchStationsByKeyword(any(), isNull()))
        .willReturn(List.of(station(null)));

    List<ListingStationSearchResponse.Item> items =
        service.searchByKeyword(LANDLORD_ID, keywordRequest("신촌", null, null)).items();

    assertThat(items).hasSize(1);
    assertThat(items.getFirst().distanceMeters()).isNull();
    assertThat(items.getFirst().suggestedWalkMinutes()).isNull();
  }

  /** 결과가 없으면 장애가 아니라 빈 목록이다. */
  @Test
  void 결과가없으면_빈목록이다() {
    given(nearbyPlaceSearchClient.searchNearbyStations(any())).willReturn(List.of());

    assertThat(service.searchNearby(LANDLORD_ID, nearbyRequest(37.5, 126.9)).items()).isEmpty();
  }

  private static NearbyPlace station(Integer distanceMeters) {
    return new NearbyPlace(
        "신촌역 2호선", "서울 서대문구 신촌로 90", "서울 서대문구 창천동 30-33", 37.555134, 126.936893, distanceMeters);
  }

  private static ListingStationSearchRequest keywordRequest(
      String keyword, Double lat, Double lng) {
    ListingStationSearchRequest request = new ListingStationSearchRequest();
    request.setKeyword(keyword);
    request.setLat(lat);
    request.setLng(lng);
    return request;
  }

  private static ListingNearbyStationRequest nearbyRequest(Double lat, Double lng) {
    ListingNearbyStationRequest request = new ListingNearbyStationRequest();
    request.setLat(lat);
    request.setLng(lng);
    return request;
  }
}
