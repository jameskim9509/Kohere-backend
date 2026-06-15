package com.kohere.community.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/** 게시글이 없거나 소프트 삭제된 경우. 전역 핸들러가 404 {@code POST_NOT_FOUND}로 변환한다. */
public class PostNotFoundException extends BusinessException {

  public PostNotFoundException() {
    super(ErrorCode.POST_NOT_FOUND);
  }
}
