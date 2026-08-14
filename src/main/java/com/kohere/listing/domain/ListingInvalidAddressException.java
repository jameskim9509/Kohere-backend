package com.kohere.listing.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 도로명 주소에서 행정구역({@link City}·{@link District})을 뽑지 못했을 때 던진다.
 *
 * <p>좌표 계산 실패와는 무관하다 — 좌표가 정상이어도 {@code address.fullAddress}에서 행정구역을 뽑지 못하면 이 예외다. 주소 검색이 그런 후보를
 * {@code supported=false}로 미리 알린다(ADR-0042).
 */
public class ListingInvalidAddressException extends BusinessException {

  public ListingInvalidAddressException() {
    super(ErrorCode.LISTING_INVALID_ADDRESS);
  }
}
