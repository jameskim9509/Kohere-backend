package com.kohere.report.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/** 동일 신고자가 동일 대상을 이미 신고한 경우. 전역 핸들러가 409 {@code REPORT_ALREADY_EXISTS}로 변환한다. */
public class ReportAlreadyExistsException extends BusinessException {

  public ReportAlreadyExistsException() {
    super(ErrorCode.REPORT_ALREADY_EXISTS);
  }
}
