package com.kohere.listing.domain;

/**
 * 임대인이 선호하는 입주자 국적이다. 등록 폼 설문 응답이라 세입자 응답에 나가지 않고, 노출 소비처가 없어 {@code listingCatalog}에도 카테고리를 두지
 * 않는다.
 */
public enum Nationality {
  JAPAN,
  USA,
  CHINA,
  SOUTHEAST_ASIA,
  EUROPE,
  OTHER
}
