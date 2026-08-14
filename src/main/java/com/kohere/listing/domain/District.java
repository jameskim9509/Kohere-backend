package com.kohere.listing.domain;

/**
 * 매물 주소의 기초 행정구역이다. 진단 {@code District}(5구 + {@code ETC})보다 넓은 집합이며, 진단이 {@code ETC}를 고르면 진단 선택지에
 * 없는 나머지 구를 매칭한다(여집합).
 */
public enum District {
  GWANAK_GU,
  DONGDAEMUN_GU,
  JONGNO_GU,
  GWANGJIN_GU,
  MAPO_GU,
  SEODAEMUN_GU,
  GURO_GU,
  YEONGDEUNGPO_GU,
  GEUMCHEON_GU
}
