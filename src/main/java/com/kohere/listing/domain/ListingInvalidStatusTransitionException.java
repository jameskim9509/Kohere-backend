package com.kohere.listing.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 심사 대상이 아닌 매물에 승인·반려를 시도했을 때 던진다.
 *
 * <p>심사는 {@link Listing.ListingStatus#PENDING}에서만 시작한다. 이미 승인·반려된 매물의 재심사는 관리자가 직접 상태를 뒤집는 것이 아니라
 * <b>임대인이 고쳐 {@code PENDING}으로 되돌린 뒤</b> 다시 심사를 통과하는 경로다(수정 API는 후속).
 *
 * <p>클라이언트가 재시도로 해소할 수 있는 상태 충돌이므로 {@link ErrorCode#LISTING_INVALID_STATUS_TRANSITION}(409)이다.
 */
public class ListingInvalidStatusTransitionException extends BusinessException {

  public ListingInvalidStatusTransitionException() {
    super(ErrorCode.LISTING_INVALID_STATUS_TRANSITION);
  }
}
