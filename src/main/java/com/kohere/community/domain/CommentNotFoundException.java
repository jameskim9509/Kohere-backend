package com.kohere.community.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/** 댓글이 없거나 소프트 삭제된 경우. 전역 핸들러가 404 {@code COMMENT_NOT_FOUND}로 변환한다. */
public class CommentNotFoundException extends BusinessException {

  public CommentNotFoundException() {
    super(ErrorCode.COMMENT_NOT_FOUND);
  }
}
