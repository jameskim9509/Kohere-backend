package com.kohere.user.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 약관 동의(PENDING→TERMS_AGREED)를 마치지 않은 상태에서 온보딩 제출을 시도한 경우. 상태 소유자인 user가 판정한다. 전역 핸들러가 422 {@code
 * AUTH_TERMS_AGREEMENT_REQUIRED}로 변환한다(약관 동의 선행 필요).
 */
public class TermsAgreementRequiredException extends BusinessException {

  public TermsAgreementRequiredException() {
    super(ErrorCode.AUTH_TERMS_AGREEMENT_REQUIRED);
  }
}
