package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 연락처 인증번호 재발송 간격 미달 또는 검증 시도 횟수 상한 초과. 전역 핸들러가 429 {@code TOO_MANY_REQUESTS}로 변환한다.
 *
 * <p>가입용(비로그인) 경로의 번호·IP 시간당 한도 초과({@link SignupSmsRateLimiter})도 같은 예외를 쓴다 — 세 방어의 상태는 서로 다른 곳에
 * 있지만 응답은 하나여야 한다. 어떤 한도에 걸렸는지 구분해 주면 그 자체가 남용자에게 "무엇을 바꾸면 통과하는지"를 알려 준다.
 */
public class PhoneRateLimitException extends BusinessException {

  public PhoneRateLimitException() {
    super(ErrorCode.TOO_MANY_REQUESTS);
  }
}
