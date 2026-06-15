package com.kohere.listing.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/** 매물이 없거나 비공개/삭제된 경우. 전역 핸들러가 404 {@code LISTING_NOT_FOUND}로 변환한다. */
public class ListingNotFoundException extends BusinessException {

  public ListingNotFoundException() {
    super(ErrorCode.LISTING_NOT_FOUND);
  }
}
