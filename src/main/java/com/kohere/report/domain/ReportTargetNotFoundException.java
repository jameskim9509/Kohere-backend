package com.kohere.report.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/** 신고 대상(targetType+targetId)이 존재하지 않는 경우. 전역 핸들러가 404 {@code REPORT_TARGET_NOT_FOUND}로 변환한다. */
public class ReportTargetNotFoundException extends BusinessException {

  public ReportTargetNotFoundException() {
    super(ErrorCode.REPORT_TARGET_NOT_FOUND);
  }
}
