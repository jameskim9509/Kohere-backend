package com.kohere.user.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/** 이미 {@code WITHDRAWN}된 사용자의 탈퇴 재요청. 전역 핸들러가 409 {@code USER_ALREADY_WITHDRAWN}으로 변환한다. */
public class UserAlreadyWithdrawnException extends BusinessException {

  public UserAlreadyWithdrawnException() {
    super(ErrorCode.USER_ALREADY_WITHDRAWN);
  }
}
