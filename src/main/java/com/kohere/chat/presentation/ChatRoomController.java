package com.kohere.chat.presentation;

import com.kohere.chat.application.ChatService;
import com.kohere.chat.application.dto.ChatRoomResponse;
import com.kohere.chat.application.dto.MessageResponse;
import com.kohere.chat.application.dto.ReadResponse;
import com.kohere.chat.presentation.dto.ReadRequest;
import com.kohere.chat.presentation.dto.SendMessageRequest;
import com.kohere.common.response.ApiResponse;
import com.kohere.common.response.CursorResponse;
import com.kohere.common.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 채팅방·메시지 REST 컨트롤러. 입력 검증·DTO 변환만 담당하고 비즈니스 로직은 응용 계층에 위임한다 (docs/convention/code-style.md §3-3).
 * 응답은 공통 래퍼로 감싼다.
 *
 * <p>스펙: docs/api/specs/04-booking-inquiry-chat.md §3~6. 리스트는 오프셋 페이지네이션, 메시지 조회는 커서 페이지네이션이다.
 *
 * <p>TODO: 본인이 참여하지 않은 방 접근 시 403 FORBIDDEN 처리는 응용 계층에서 인증 주체로 검증한다.
 */
@RestController
@RequestMapping("/api/v1/chat-rooms")
@RequiredArgsConstructor
public class ChatRoomController {

  private final ChatService chatService;

  @GetMapping
  public ApiResponse<PageResponse<ChatRoomResponse>> listRooms(
      @RequestParam(required = false) String category,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(chatService.listRooms(category, page, size));
  }

  @GetMapping("/{roomId}/messages")
  public ApiResponse<CursorResponse<MessageResponse>> getMessages(
      @PathVariable Long roomId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "30") int size) {
    return ApiResponse.success(chatService.getMessages(roomId, cursor, size));
  }

  @PostMapping("/{roomId}/messages")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<MessageResponse> sendMessage(
      @PathVariable Long roomId, @Valid @RequestBody SendMessageRequest request) {
    return ApiResponse.success(chatService.sendMessage(roomId, request));
  }

  @PostMapping("/{roomId}/read")
  public ApiResponse<ReadResponse> markRead(
      @PathVariable Long roomId, @RequestBody ReadRequest request) {
    return ApiResponse.success(chatService.markRead(roomId, request));
  }
}
