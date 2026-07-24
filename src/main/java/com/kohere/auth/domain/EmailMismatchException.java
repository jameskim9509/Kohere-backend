package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 최초 로그인(신규 가입) 시 요청 {@code email}이 토큰의 {@code email} 클레임과 불일치(요청 값 위조 방어 — email은 provider 진본으로
 * 확정). 전역 핸들러가 422 {@code AUTH_EMAIL_MISMATCH}로 변환한다(#192).
 */
public class EmailMismatchException extends BusinessException {

  public EmailMismatchException() {
    super(ErrorCode.AUTH_EMAIL_MISMATCH);
  }
}
