package com.kohere.listing.domain;

/**
 * 외국인등록증(ARC) 요구 여부다. 진단 {@code ArcStatus}(2값)와 1:1로 대응하며, 진단이 {@code NO_ARC}이면 {@code
 * NOT_REQUIRED} 매물만 매칭한다. 카탈로그 카테고리는 {@code ARC_REQUIREMENT}다.
 */
public enum ArcRequirement {
  REQUIRED,
  NOT_REQUIRED
}
