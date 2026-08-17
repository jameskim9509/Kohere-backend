package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 웹 회원가입의 이메일을 이미 다른 사람이 웹 로그인 ID로 쓰고 있음. 전역 핸들러가 409 {@code AUTH_EMAIL_ALREADY_REGISTERED}로 변환한다.
 * 중복 검사 대상은 {@code local_accounts.email}뿐이며 {@code users.email}은 보지 않는다 — 소셜과 같은 이메일로 웹 가입하는 정상 경로를
 * 막지 않기 위해서다(ADR-0047).
 */
public class EmailAlreadyRegisteredException extends BusinessException {

  public EmailAlreadyRegisteredException() {
    super(ErrorCode.AUTH_EMAIL_ALREADY_REGISTERED);
  }
}
