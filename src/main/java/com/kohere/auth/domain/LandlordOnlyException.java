package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 임대인 전용 리소스에 임대인이 아닌(userType≠LANDLORD) 사용자가 접근. 전역 핸들러가 403 {@code FORBIDDEN}으로 변환한다. 사업자등록번호 검증
 * API(POST /auth/business/verify)는 온보딩을 마친 임대인만 호출할 수 있다(ADR-0033).
 */
public class LandlordOnlyException extends BusinessException {

  public LandlordOnlyException() {
    super(ErrorCode.FORBIDDEN);
  }
}
