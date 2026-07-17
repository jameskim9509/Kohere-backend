package com.kohere.booking.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 동일 세입자가 동일 방 상품에 이미 신청한 경우. 전역 핸들러가 409 {@code BOOKING_ALREADY_EXISTS}로 변환한다. 유니크 {@code
 * (tenant_id, room_offer_id)}로 활성 예약 1건만 허용한다(상태 전이 미구현이라 전 예약이 활성이다).
 */
public class BookingAlreadyExistsException extends BusinessException {

  public BookingAlreadyExistsException() {
    super(ErrorCode.BOOKING_ALREADY_EXISTS);
  }
}
