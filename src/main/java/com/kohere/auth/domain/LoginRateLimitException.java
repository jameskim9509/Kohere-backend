package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 임대인 웹 로그인 시도 한도(IP·이메일) 초과. 전역 핸들러가 429 {@code TOO_MANY_REQUESTS}로 변환한다 — 새 에러 코드를 만들지 않는다(레이트리밋
 * 초과는 이미 공통 코드가 있고, 웹 로그인만 따로 코드를 두면 클라이언트가 429를 두 갈래로 분기해야 한다).
 *
 * <p>{@link PhoneRateLimitException}과 코드는 같지만 타입을 나눈다 — 저쪽은 "연락처 인증 발송·확인"의 한도라 이름·javadoc·발생 지점이
 * 전부 SMS에 묶여 있다. 재사용하면 로그인 실패 로그에 연락처 인증 예외가 찍혀 원인 추적이 어긋난다.
 */
public class LoginRateLimitException extends BusinessException {

  public LoginRateLimitException() {
    super(ErrorCode.TOO_MANY_REQUESTS);
  }
}
