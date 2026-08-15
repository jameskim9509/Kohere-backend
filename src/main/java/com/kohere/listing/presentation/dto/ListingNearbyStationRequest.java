package com.kohere.listing.presentation.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 좌표로 인근 역 목록을 받는 API의 쿼리 파라미터를 담는다(ADR-0044).
 *
 * <p>반경·정렬·개수는 서버가 고정하므로 프론트는 매물 좌표만 보낸다. 좌표를 {@code Double}로 두는 이유는 이름 검색과 같다 — 누락을 {@code 0.0}과
 * 구분해 필수 검증이 성립해야 한다.
 */
@Getter
@Setter
public class ListingNearbyStationRequest {

  /** 매물 위도(WGS84). 필수. */
  private Double lat;

  /** 매물 경도(WGS84). 필수. */
  private Double lng;
}
