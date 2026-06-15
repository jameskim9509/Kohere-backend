package com.kohere.chat.presentation.dto;

/**
 * 읽음 처리 요청 바디. {@code lastReadMessageId}는 선택이며, 생략 시 방의 가장 최신 메시지까지 읽음 처리한다. 해당 방의 메시지여야 한다.
 *
 * <p>docs/api/specs/04-booking-inquiry-chat.md §6.
 */
public record ReadRequest(Long lastReadMessageId) {}
