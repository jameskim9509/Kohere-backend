package com.kohere.diagnosis.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/** 요청한 진단이 존재하지 않는 경우. 전역 핸들러가 404 {@code DIAGNOSIS_NOT_FOUND}로 변환한다. */
public class DiagnosisNotFoundException extends BusinessException {

  public DiagnosisNotFoundException() {
    super(ErrorCode.DIAGNOSIS_NOT_FOUND);
  }
}
