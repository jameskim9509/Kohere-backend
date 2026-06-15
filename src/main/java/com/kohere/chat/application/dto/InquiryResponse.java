package com.kohere.chat.application.dto;

import com.kohere.chat.domain.ChatCategory;

/**
 * 문의 결과 DTO. 매물 임대인과의 채팅방을 반환한다. {@code created}가 {@code true}면 신규 생성(201), {@code false}면 기존 방
 * 반환(200).
 *
 * <p>docs/api/specs/04-booking-inquiry-chat.md §2.
 */
public record InquiryResponse(Long chatRoomId, ChatCategory category, boolean created) {}
