package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * provider별 필수 자격(Google {@code idToken} / Apple {@code authorizationCode}) 누락. provider별 필수 여부는
 * Bean Validation이 아니라 application 계층에서 판정한다(ADR-0031 #3). 전역 핸들러가 400 {@code
 * AUTH_MISSING_CREDENTIAL}로 변환한다.
 */
public class MissingCredentialException extends BusinessException {

  public MissingCredentialException() {
    super(ErrorCode.AUTH_MISSING_CREDENTIAL);
  }
}
