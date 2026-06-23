package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 약관 동의(PENDING→TERMS_AGREED)를 마치지 않은 상태에서 이메일 인증·온보딩을 시도한 경우. auth가 흐름 순서를 강제한다 — 약관 동의가 이메일
 * 인증·온보딩에 선행하므로, 미동의(PENDING)면 이메일 인증 안내보다 약관 동의 안내를 먼저 돌려준다. 전역 핸들러가 422 {@code
 * AUTH_TERMS_AGREEMENT_REQUIRED}로 변환한다.
 */
public class TermsAgreementRequiredException extends BusinessException {

  public TermsAgreementRequiredException() {
    super(ErrorCode.AUTH_TERMS_AGREEMENT_REQUIRED);
  }
}
