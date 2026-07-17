package com.kohere.booking.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 동일 신고자가 동일 예약을 이미 신고한 경우. 전역 핸들러가 409 {@code BOOKING_REPORT_ALREADY_EXISTS}로 변환한다. 유니크 {@code
 * (reporter_id, booking_id)}로 1건만 허용한다.
 */
public class BookingReportAlreadyExistsException extends BusinessException {

  public BookingReportAlreadyExistsException() {
    super(ErrorCode.BOOKING_REPORT_ALREADY_EXISTS);
  }
}
