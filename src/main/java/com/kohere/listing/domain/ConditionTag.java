package com.kohere.listing.domain;

/**
 * 방 상품 검색 필터 태그다. {@code roomOffers[].filterTags}에 저장한 값과 응답 태그가 1:1이며 서버가 파생하는 값은 없다.
 * docs/api/specs/03-listings-favorites.md (ConditionTag).
 */
public enum ConditionTag {
  MOVE_IN_NOW,
  FEMALE_ONLY,
  MEALS_INCLUDED,
  DOUBLE_ROOM,
  PRIVATE_BATH,
  ENGLISH_OK,
  ADDRESS_REGISTRATION,
  NO_MAINT_FEE
}
