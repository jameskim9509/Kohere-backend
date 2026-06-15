package com.kohere.chat.application.dto;

/**
 * 읽음 처리 결과 DTO. 요청자의 마지막 읽은 메시지 위치와 갱신된 안읽음 수를 담는다.
 *
 * <p>docs/api/specs/04-booking-inquiry-chat.md §6.
 */
public record ReadResponse(Long chatRoomId, Long lastReadMessageId, int unreadCount) {}
