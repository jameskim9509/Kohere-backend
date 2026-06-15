package com.kohere.user.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/** 사용자가 없거나 탈퇴되어 조회 불가한 경우. 전역 핸들러가 404 {@code USER_NOT_FOUND}로 변환한다. */
public class UserNotFoundException extends BusinessException {

  public UserNotFoundException() {
    super(ErrorCode.USER_NOT_FOUND);
  }
}
