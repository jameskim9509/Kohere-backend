package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 온보딩 제출 {@code email}이 미검증이거나 검증한 이메일과 불일치. 전역 핸들러가 422 {@code AUTH_EMAIL_NOT_VERIFIED}로 변환한다(이메일
 * 인증 선행 필요).
 */
public class EmailNotVerifiedException extends BusinessException {

  public EmailNotVerifiedException() {
    super(ErrorCode.AUTH_EMAIL_NOT_VERIFIED);
  }
}
