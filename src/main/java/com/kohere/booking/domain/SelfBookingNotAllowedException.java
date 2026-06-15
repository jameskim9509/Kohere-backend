package com.kohere.booking.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/** 본인이 소유한 매물에 신청을 시도한 경우. 전역 핸들러가 422 {@code BOOKING_SELF_NOT_ALLOWED}로 변환한다. */
public class SelfBookingNotAllowedException extends BusinessException {

  public SelfBookingNotAllowedException() {
    super(ErrorCode.BOOKING_SELF_NOT_ALLOWED);
  }
}
