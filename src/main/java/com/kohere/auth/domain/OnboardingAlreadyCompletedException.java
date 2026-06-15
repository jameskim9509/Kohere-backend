package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 이미 온보딩 완료(ACTIVE)된 사용자가 온보딩을 재요청한 경우(동시 요청 포함). 전역 핸들러가 409 {@code
 * AUTH_ONBOARDING_ALREADY_COMPLETED}로 변환한다.
 */
public class OnboardingAlreadyCompletedException extends BusinessException {

  public OnboardingAlreadyCompletedException() {
    super(ErrorCode.AUTH_ONBOARDING_ALREADY_COMPLETED);
  }
}
