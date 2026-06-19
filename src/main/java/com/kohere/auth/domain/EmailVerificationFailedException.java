package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 이메일 인증번호 불일치 또는 만료(미발송·만료·오입력 포함). 챌린지 부재 시에는 {@code attempts} 증가 없이 즉시 거절한다. 전역 핸들러가 422 {@code
 * AUTH_EMAIL_VERIFICATION_FAILED}로 변환한다.
 */
public class EmailVerificationFailedException extends BusinessException {

  public EmailVerificationFailedException() {
    super(ErrorCode.AUTH_EMAIL_VERIFICATION_FAILED);
  }
}
