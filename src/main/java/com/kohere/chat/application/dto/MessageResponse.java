package com.kohere.chat.application.dto;

import com.kohere.chat.domain.MessageType;
import java.time.Instant;

/**
 * 메시지 응답 DTO. 시스템·카드 메시지는 {@code senderId}가 null(서버 생성)이며, {@code content}는 {@code TEXT}에만 존재한다.
 * {@code mine}은 요청자가 보낸 메시지면 true.
 *
 * <p>docs/api/specs/04-booking-inquiry-chat.md §4. TODO: 카드 페이로드(card)는 카드 타입 도입 시 추가한다.
 */
public record MessageResponse(
    Long messageId,
    MessageType type,
    Long senderId,
    boolean mine,
    String content,
    boolean pinned,
    Instant sentAt) {}
