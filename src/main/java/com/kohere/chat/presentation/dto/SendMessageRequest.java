package com.kohere.chat.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 텍스트 메시지 전송 요청 바디. 사용자는 {@code TEXT}만 보낼 수 있다(카드/시스템은 서버 전용). 공백만으로는 보낼 수 없으며 최대 1000자.
 *
 * <p>docs/api/specs/04-booking-inquiry-chat.md §5.
 */
public record SendMessageRequest(@NotBlank @Size(max = 1000) String content) {}
