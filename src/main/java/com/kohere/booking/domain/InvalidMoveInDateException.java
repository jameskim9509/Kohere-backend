package com.kohere.booking.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/** 입주 희망일이 과거이거나 매물의 입주 가능일 이전인 경우. 전역 핸들러가 422 {@code BOOKING_INVALID_MOVE_IN_DATE}로 변환한다. */
public class InvalidMoveInDateException extends BusinessException {

  public InvalidMoveInDateException() {
    super(ErrorCode.BOOKING_INVALID_MOVE_IN_DATE);
  }
}
