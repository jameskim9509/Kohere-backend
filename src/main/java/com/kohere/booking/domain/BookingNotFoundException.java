package com.kohere.booking.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/** 예약이 없거나 요청자 본인 예약이 아닌 경우. 전역 핸들러가 404 {@code BOOKING_NOT_FOUND}로 변환한다(존재 여부 미노출). */
public class BookingNotFoundException extends BusinessException {

  public BookingNotFoundException() {
    super(ErrorCode.BOOKING_NOT_FOUND);
  }
}
