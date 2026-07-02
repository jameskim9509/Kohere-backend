package com.kohere.booking.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 예약은 세입자(TENANT) 전용 — 임대인 등 비세입자가 예약을 시도한 경우. 전역 핸들러가 403 {@code FORBIDDEN}으로 변환한다. 임대인은 매물 소유자로서
 * 예약 자체를 수행할 수 없어, 자기 매물 예약(본인 매물 차단) 상황은 성립하지 않는다.
 */
public class TenantOnlyException extends BusinessException {

  public TenantOnlyException() {
    super(ErrorCode.FORBIDDEN);
  }
}
