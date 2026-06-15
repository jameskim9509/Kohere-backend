package com.kohere.chat.application.dto;

import com.kohere.chat.domain.ChatCategory;
import java.time.Instant;

/**
 * 채팅방 리스트 항목 DTO. 마지막 메시지 시각 내림차순으로 반환된다.
 *
 * <p>docs/api/specs/04-booking-inquiry-chat.md §3. TODO: 매물 요약(listing)·상대방(counterpart)·마지막 메시지
 * 미리보기 정보는 listing/user 모듈 공개 API·이벤트로 채운다.
 */
public record ChatRoomResponse(
    Long chatRoomId, ChatCategory category, int unreadCount, Instant lastMessageAt) {}
