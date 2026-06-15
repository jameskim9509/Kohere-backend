package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/** refresh 토큰 만료/위조/무효화/재사용 탐지. 전역 핸들러가 401 {@code AUTH_INVALID_REFRESH_TOKEN}으로 변환한다. */
public class InvalidRefreshTokenException extends BusinessException {

  public InvalidRefreshTokenException() {
    super(ErrorCode.AUTH_INVALID_REFRESH_TOKEN);
  }
}
