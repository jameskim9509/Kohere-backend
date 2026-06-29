package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * Apple {@code /auth/token}·{@code /auth/revoke} 연동의 일시 장애(타임아웃·5xx·I/O). 전역 핸들러가 502 {@code
 * UPSTREAM_ERROR}로 변환해 클라이언트가 재시도하도록 한다(ADR-0031). 탈퇴 흐름의 revoke는 이 예외를 best-effort로 흡수한다.
 */
public class AppleUpstreamException extends BusinessException {

  public AppleUpstreamException(Throwable cause) {
    super(ErrorCode.UPSTREAM_ERROR);
    initCause(cause);
  }
}
