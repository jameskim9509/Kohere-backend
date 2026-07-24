package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 최초 로그인(신규 가입) 시 토큰의 {@code email} 클레임·요청 {@code email} 어느 쪽에도 이메일이 없어 provider 진본 이메일을 확정할 수 없음.
 * 전역 핸들러가 422 {@code AUTH_EMAIL_REQUIRED}로 변환한다(#192).
 */
public class EmailRequiredException extends BusinessException {

  public EmailRequiredException() {
    super(ErrorCode.AUTH_EMAIL_REQUIRED);
  }
}
