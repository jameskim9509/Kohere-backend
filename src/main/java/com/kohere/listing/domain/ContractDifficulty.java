package com.kohere.listing.domain;

/**
 * 임대인이 외국인 임차인과 계약할 때 겪은 어려움이다. 등록 폼 설문 응답이라 세입자 응답에 나가지 않고, {@code listingCatalog}에도 카테고리를 두지 않는다.
 */
public enum ContractDifficulty {
  LANGUAGE,
  CULTURE,
  IDENTITY,
  PAYMENT,
  CONTRACT_FULFILLMENT,
  COMMUNICATION_CHANNEL,
  OTHER
}
