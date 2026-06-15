package com.kohere.booking.domain;

/**
 * 예약 상태. 신청 직후 {@code REQUESTED}. 임대인 수락/거절·세입자 취소는 본 스펙 범위 밖(확장 시 정의).
 * docs/api/specs/04-booking-inquiry-chat.md (status).
 */
public enum BookingStatus {
  REQUESTED,
  ACCEPTED,
  REJECTED,
  CANCELED
}
