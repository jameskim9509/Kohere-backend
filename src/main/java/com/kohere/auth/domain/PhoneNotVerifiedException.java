package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 임대인 온보딩 제출 또는 프로필 연락처 변경 시 {@code phoneNumber}가 미검증이거나 검증한 번호와 불일치. 전역 핸들러가 422 {@code
 * AUTH_PHONE_NOT_VERIFIED}로 변환한다(연락처 SMS 인증 선행 필요, ADR-0034).
 */
public class PhoneNotVerifiedException extends BusinessException {

  public PhoneNotVerifiedException() {
    super(ErrorCode.AUTH_PHONE_NOT_VERIFIED);
  }
}
