package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 인증번호 메일 발송 실패(provider 장애·타임아웃). 동기 발송 정책상 발송 실패 시 챌린지를 저장하지 않고 이 예외로 거절한다. 전역 핸들러가 502 {@code
 * UPSTREAM_ERROR}로 변환해 클라이언트가 재시도하도록 한다.
 */
public class EmailDispatchException extends BusinessException {

  public EmailDispatchException(Throwable cause) {
    super(ErrorCode.UPSTREAM_ERROR);
    initCause(cause);
  }
}
