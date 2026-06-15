package com.kohere.report.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/** 본인이 작성한 콘텐츠를 신고한 경우(자기 신고 차단 정책 적용 시). 전역 핸들러가 422 {@code REPORT_SELF_TARGET}로 변환한다. */
public class ReportSelfTargetException extends BusinessException {

  public ReportSelfTargetException() {
    super(ErrorCode.REPORT_SELF_TARGET);
  }
}
