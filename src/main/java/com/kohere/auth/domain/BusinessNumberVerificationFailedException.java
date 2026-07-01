package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 사업자등록번호 검증 결과 미등록·휴업·폐업이거나 진위 실패. 전역 핸들러가 422 {@code AUTH_BUSINESS_NUMBER_VERIFICATION_FAILED}로
 * 변환한다(번호를 검증 완료로 표시하지 않음, ADR-0033).
 */
public class BusinessNumberVerificationFailedException extends BusinessException {

  public BusinessNumberVerificationFailedException() {
    super(ErrorCode.AUTH_BUSINESS_NUMBER_VERIFICATION_FAILED);
  }
}
