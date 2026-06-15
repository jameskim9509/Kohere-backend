package com.kohere.gamification.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/** 퀴즈가 존재하나 오늘의 퀴즈가 아닌 경우(과거/미래 분 제출 불가). 전역 핸들러가 422 {@code QUIZ_NOT_TODAY}로 변환한다. */
public class QuizNotTodayException extends BusinessException {

  public QuizNotTodayException() {
    super(ErrorCode.QUIZ_NOT_TODAY);
  }
}
