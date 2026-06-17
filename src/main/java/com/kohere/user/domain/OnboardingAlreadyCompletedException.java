package com.kohere.user.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 이미 온보딩을 완료(ACTIVE)한 사용자의 온보딩 재요청. 상태 소유자인 user가 판정한다. 전역 핸들러가 409 {@code
 * AUTH_ONBOARDING_ALREADY_COMPLETED}로 변환한다.
 */
public class OnboardingAlreadyCompletedException extends BusinessException {

  public OnboardingAlreadyCompletedException() {
    super(ErrorCode.AUTH_ONBOARDING_ALREADY_COMPLETED);
  }
}
