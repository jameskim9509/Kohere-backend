package com.kohere.booking.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 예약 대상 매물·방 상품이 없거나 비공개(미게시/방 상품 비활성)인 경우. 전역 핸들러가 404 {@code LISTING_NOT_FOUND}로 변환한다. 매물 존재·공개
 * 판정은 {@code listing :: api} 공개 쿼리 결과(부재)로 이루어진다(cross-store 조인 금지 · ADR-0005).
 */
public class ListingUnavailableException extends BusinessException {

  public ListingUnavailableException() {
    super(ErrorCode.LISTING_NOT_FOUND);
  }
}
