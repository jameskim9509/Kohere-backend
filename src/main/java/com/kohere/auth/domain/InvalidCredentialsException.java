package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 웹 로그인의 이메일 미존재 또는 비밀번호 불일치. 전역 핸들러가 401 {@code AUTH_INVALID_CREDENTIALS}로 변환한다. 두 경우를 한 예외로 묶어 계정
 * 존재 여부를 노출하지 않는다(US-1-12).
 */
public class InvalidCredentialsException extends BusinessException {

  public InvalidCredentialsException() {
    super(ErrorCode.AUTH_INVALID_CREDENTIALS);
  }
}
