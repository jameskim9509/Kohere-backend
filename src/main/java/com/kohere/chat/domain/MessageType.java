package com.kohere.chat.domain;

/**
 * 메시지 타입. 사용자가 보낼 수 있는 타입은 {@code TEXT}뿐이며, 카드/시스템 메시지는 서버가 생성한다.
 * docs/api/specs/04-booking-inquiry-chat.md (type).
 */
public enum MessageType {
  TEXT,
  BOOKING_CARD,
  LISTING_CARD,
  SYSTEM
}
