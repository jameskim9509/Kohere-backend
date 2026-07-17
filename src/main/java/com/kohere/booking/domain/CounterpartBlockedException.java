package com.kohere.booking.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 신규 예약 신청 시 요청자와 매물 소유자 사이에 차단 관계(어느 방향이든)가 있는 경우. 전역 핸들러가 403 {@code FORBIDDEN}(공통 코드)으로 변환한다 —
 * 별도 도메인 코드를 두지 않는다. 이 가드가 없으면 차단한 상대 매물에 신청 시 201은 나가지만 목록엔 영영 보이지 않는 블랙홀 예약이 생긴다.
 */
public class CounterpartBlockedException extends BusinessException {

  public CounterpartBlockedException() {
    super(ErrorCode.FORBIDDEN);
  }
}
