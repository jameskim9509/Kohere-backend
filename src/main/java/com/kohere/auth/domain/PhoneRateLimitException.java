package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/** 연락처 인증번호 재발송 간격 미달 또는 검증 시도 횟수 상한 초과. 전역 핸들러가 429 {@code TOO_MANY_REQUESTS}로 변환한다. */
public class PhoneRateLimitException extends BusinessException {

  public PhoneRateLimitException() {
    super(ErrorCode.TOO_MANY_REQUESTS);
  }
}
