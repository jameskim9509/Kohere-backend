package com.kohere.chat.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/** 본인이 소유한 매물에 문의한 경우. 전역 핸들러가 422 {@code CHAT_SELF_INQUIRY_NOT_ALLOWED}로 변환한다. */
public class SelfInquiryNotAllowedException extends BusinessException {

  public SelfInquiryNotAllowedException() {
    super(ErrorCode.CHAT_SELF_INQUIRY_NOT_ALLOWED);
  }
}
