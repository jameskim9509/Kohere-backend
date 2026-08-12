package com.kohere.listing.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 요청의 코드값이 {@code listingCatalog}에 없을 때 던진다.
 *
 * <p>enum 역직렬화가 오타는 이미 걸러내므로, 이 예외는 앱이 아는 코드와 서버 카탈로그가 어긋난 상황을 뜻한다. 라벨 없는 코드를 저장하면 응답에서 코드 문자열이 그대로
 * 노출된다.
 */
public class ListingUnknownCatalogCodeException extends BusinessException {

  public ListingUnknownCatalogCodeException() {
    super(ErrorCode.LISTING_UNKNOWN_CATALOG_CODE);
  }
}
