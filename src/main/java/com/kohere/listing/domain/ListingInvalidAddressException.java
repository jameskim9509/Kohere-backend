package com.kohere.listing.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 도로명 주소에서 행정구역({@link City}·{@link District})을 뽑지 못했을 때 던진다.
 *
 * <p>좌표 계산 실패와는 무관하다 — 지오코딩은 아직 없고, {@code location}은 비어 있어도 저장된다.
 */
public class ListingInvalidAddressException extends BusinessException {

  public ListingInvalidAddressException() {
    super(ErrorCode.LISTING_INVALID_ADDRESS);
  }
}
