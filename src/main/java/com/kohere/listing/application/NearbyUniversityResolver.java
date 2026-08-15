package com.kohere.listing.application;

import com.kohere.listing.domain.catalog.ListingCatalogRepository;
import com.kohere.listing.domain.nearby.Coordinate;
import com.kohere.listing.domain.nearby.NearbyPlace;
import com.kohere.listing.domain.nearby.NearbyPlaceSearchClient;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 매물 좌표에서 {@code nearbyUniversityCodes}를 파생한다(ADR-0044).
 *
 * <p>이 값은 임대인이 고르는 입력이 아니라 <b>진단 추천이 매물을 찾는 조인 키</b>다({@code ListingRepositoryImpl.recommend}의
 * {@code $in}). 그래서 요청으로 받지 않고 좌표에서 기계적으로 정한다 — 고르게 하면 "우리 매물 서울대 근처"라는 주장이 추천에 실린다.
 *
 * <p><b>실패를 밖으로 내보내지 않는다.</b> 주소 좌표는 없으면 매물이 성립하지 않는 필수값이라 실패가 곧 등록 실패였지만(ADR-0042 §2), 이 값은 빈 집합이
 * 이미 유효한 상태다. 그래서 외부 장애를 흡수하고 등록을 성공시키되 {@code WARN}으로 남긴다 — 누락은 관리자 승인 심사가 보정한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NearbyUniversityResolver {

  private final NearbyPlaceSearchClient nearbyPlaceSearchClient;
  private final ListingCatalogRepository listingCatalogRepository;

  /**
   * 좌표 주변에서 카탈로그가 아는 대학 코드를 <b>전부</b> 모은다.
   *
   * <p>가장 가까운 하나가 아니라 반경 내 전부인 이유는 필드가 복수형({@code Set<String>})이고 진단 추천이 {@code $in}이기 때문이다 — 담긴
   * 수만큼 매칭 기회가 는다. 신촌 매물이 연세대만 달면 이화여대를 고른 진단 결과에 걸리지 않는다.
   *
   * @param lat 매물 위도
   * @param lng 매물 경도
   * @return 거리순으로 정렬된 대학 코드 집합. 외부 실패·미발견·좌표 이상이면 빈 집합
   */
  public Set<String> resolve(double lat, double lng) {
    try {
      ListingCatalogCodes catalog = ListingCatalogCodes.of(listingCatalogRepository.findAll());
      Set<String> codes = new LinkedHashSet<>();
      for (NearbyPlace place :
          nearbyPlaceSearchClient.searchNearbyUniversities(new Coordinate(lat, lng))) {
        catalog.findUniversityCode(place.name()).ifPresent(codes::add);
      }
      return Set.copyOf(codes);
    } catch (RuntimeException e) {
      // 등록을 실패시키지 않는다. 어느 매물이 왜 비었는지는 심사자·운영이 이 로그로 판단한다(ADR-0038).
      log.warn("인근 대학 파생에 실패해 빈 집합으로 저장한다. lat={}, lng={}", lat, lng, e);
      return Set.of();
    }
  }
}
