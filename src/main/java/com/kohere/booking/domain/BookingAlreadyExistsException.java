package com.kohere.booking.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 동일 세입자–매물의 활성 예약({@code REQUESTED}/{@code ACCEPTED})이 이미 존재하는 경우(중복 신청). 전역 핸들러가 409 {@code
 * BOOKING_ALREADY_EXISTS}로 변환한다.
 */
public class BookingAlreadyExistsException extends BusinessException {

  public BookingAlreadyExistsException() {
    super(ErrorCode.BOOKING_ALREADY_EXISTS);
  }
}
