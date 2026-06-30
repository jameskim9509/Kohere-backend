package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 연락처 인증번호 불일치 또는 만료(미발송 포함). 전역 핸들러가 422 {@code AUTH_PHONE_VERIFICATION_FAILED}로 변환한다(연락처를 검증 완료로
 * 표시하지 않음).
 */
public class PhoneVerificationFailedException extends BusinessException {

  public PhoneVerificationFailedException() {
    super(ErrorCode.AUTH_PHONE_VERIFICATION_FAILED);
  }
}
