package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 소셜 {@code idToken}의 서명/{@code aud}/{@code iss}/{@code exp} 검증 실패. 전역 핸들러가 401 {@code
 * AUTH_INVALID_SOCIAL_TOKEN}으로 변환한다.
 */
public class InvalidSocialTokenException extends BusinessException {

  public InvalidSocialTokenException() {
    super(ErrorCode.AUTH_INVALID_SOCIAL_TOKEN);
  }
}
