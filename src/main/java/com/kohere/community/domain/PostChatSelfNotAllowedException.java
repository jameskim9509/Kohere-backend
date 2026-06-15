package com.kohere.community.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/** 본인 게시글에 동네친구 채팅을 시작하려는 경우. 전역 핸들러가 422 {@code POST_CHAT_SELF_NOT_ALLOWED}로 변환한다. */
public class PostChatSelfNotAllowedException extends BusinessException {

  public PostChatSelfNotAllowedException() {
    super(ErrorCode.POST_CHAT_SELF_NOT_ALLOWED);
  }
}
