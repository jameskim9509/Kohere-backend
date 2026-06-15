package com.kohere.chat.domain;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/**
 * 메시지 엔티티. 채팅방에 속하며, 시스템·카드 메시지는 {@code senderId}가 null(서버 생성)이다. {@code content}는 {@code TEXT}에만
 * 존재한다. docs/api/specs/04-booking-inquiry-chat.md.
 *
 * <p>TODO: 카드 메시지의 카드 페이로드(BOOKING_CARD/LISTING_CARD) 표현을 채운다.
 */
@Getter
@Builder
public class Message {

  private final Long id;
  private final Long roomId;
  private final Long senderId;
  private final MessageType type;
  private final String content;
  private final boolean pinned;
  private final Instant sentAt;
}
