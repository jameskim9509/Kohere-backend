package com.kohere.gamification.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/** 오늘 퀴즈를 이미 제출한 경우(하루 1회 초과/동시 중복 제출). 전역 핸들러가 409 {@code QUIZ_ALREADY_SUBMITTED}로 변환한다. */
public class QuizAlreadySubmittedException extends BusinessException {

  public QuizAlreadySubmittedException() {
    super(ErrorCode.QUIZ_ALREADY_SUBMITTED);
  }
}
