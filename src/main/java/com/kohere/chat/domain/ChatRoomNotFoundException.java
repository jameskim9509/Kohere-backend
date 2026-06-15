package com.kohere.chat.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/** 채팅방이 존재하지 않는 경우. 전역 핸들러가 404 {@code CHAT_ROOM_NOT_FOUND}로 변환한다. */
public class ChatRoomNotFoundException extends BusinessException {

  public ChatRoomNotFoundException() {
    super(ErrorCode.CHAT_ROOM_NOT_FOUND);
  }
}
