package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/** 필수 약관(이용약관/개인정보처리방침) 미동의. 전역 핸들러가 422 {@code AUTH_REQUIRED_AGREEMENT_MISSING}으로 변환한다. */
public class RequiredAgreementMissingException extends BusinessException {

  public RequiredAgreementMissingException() {
    super(ErrorCode.AUTH_REQUIRED_AGREEMENT_MISSING);
  }
}
