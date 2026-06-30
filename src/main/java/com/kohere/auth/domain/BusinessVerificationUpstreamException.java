package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 사업자등록번호 외부 검증 서비스 장애·타임아웃·5xx. 동기 검증 정책상 외부 장애 시 검증 결과를 저장하지 않고 이 예외로 거절한다. 전역 핸들러가 502 {@code
 * UPSTREAM_ERROR}로 변환해 클라이언트가 재시도하도록 한다(ADR-0033).
 */
public class BusinessVerificationUpstreamException extends BusinessException {

  public BusinessVerificationUpstreamException(Throwable cause) {
    super(ErrorCode.UPSTREAM_ERROR);
    initCause(cause);
  }
}
