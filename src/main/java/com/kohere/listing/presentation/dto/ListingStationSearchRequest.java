package com.kohere.listing.presentation.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 이름으로 인근 역을 찾는 API의 쿼리 파라미터를 담는다(ADR-0044).
 *
 * <p>프론트는 검색어와 (선택적으로) 매물 좌표만 전달하고, 카카오의 {@code category_group_code}·{@code size}·{@code sort} 같은
 * 제공자 전용 옵션은 서버 정책으로 고정한다.
 *
 * <p>좌표는 {@code Double}이다 — primitive로 두면 "보내지 않음"과 "0.0을 보냄"이 구분되지 않아, 적도·본초자오선 좌표를 보낸 요청과 좌표를 생략한
 * 요청이 같아진다.
 */
@Getter
@Setter
public class ListingStationSearchRequest {

  /** 임대인이 등록 폼 역 칸에 입력한 원본 검색어. 예: {@code 신촌}. */
  private String keyword;

  /** 매물 위도. 주소 검색이 준 값을 그대로 넘긴다. 생략하면 정확도순이고 거리 정보가 없다. */
  private Double lat;

  /** 매물 경도. {@code lat}과 항상 짝으로 보낸다. */
  private Double lng;
}
