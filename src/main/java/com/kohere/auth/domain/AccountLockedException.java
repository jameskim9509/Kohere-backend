package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 비밀번호 연속 실패 상한에 도달해 잠긴 계정의 웹 로그인 시도. 전역 핸들러가 423 {@code AUTH_ACCOUNT_LOCKED}로 변환한다. 잠금 판정이 자격증명
 * 대조보다 우선하며(비밀번호가 맞아도 잠금이 이긴다) 시간 경과 자동 해제는 없다(US-1-12).
 */
public class AccountLockedException extends BusinessException {

  public AccountLockedException() {
    super(ErrorCode.AUTH_ACCOUNT_LOCKED);
  }
}
