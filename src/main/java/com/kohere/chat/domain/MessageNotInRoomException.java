package com.kohere.chat.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/** 읽음 처리 시 지정한 메시지가 해당 방에 속하지 않은 경우. 전역 핸들러가 422 {@code CHAT_MESSAGE_NOT_IN_ROOM}로 변환한다. */
public class MessageNotInRoomException extends BusinessException {

  public MessageNotInRoomException() {
    super(ErrorCode.CHAT_MESSAGE_NOT_IN_ROOM);
  }
}
