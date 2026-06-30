package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 임대인 온보딩 제출 {@code businessRegistrationNumber}가 미검증이거나 검증한 번호와 불일치. 전역 핸들러가 422 {@code
 * AUTH_BUSINESS_NUMBER_NOT_VERIFIED}로 변환한다(사업자번호 검증 선행 필요, ADR-0033).
 */
public class BusinessNumberNotVerifiedException extends BusinessException {

  public BusinessNumberNotVerifiedException() {
    super(ErrorCode.AUTH_BUSINESS_NUMBER_NOT_VERIFIED);
  }
}
