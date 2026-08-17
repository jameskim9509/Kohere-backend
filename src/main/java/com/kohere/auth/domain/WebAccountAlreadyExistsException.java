package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 인증된 휴대폰 번호로 찾은 계정에 이미 웹 자격증명이 붙어 있음. 전역 핸들러가 409 {@code AUTH_WEB_ACCOUNT_ALREADY_EXISTS}로 변환한다.
 * 붙일 자리가 찬 상태에서 남은 동작은 기존 자격증명 덮어쓰기뿐인데 그건 가입이 아니라 로그인 ID 교체라 오류로 끊는다(ADR-0047).
 */
public class WebAccountAlreadyExistsException extends BusinessException {

  public WebAccountAlreadyExistsException() {
    super(ErrorCode.AUTH_WEB_ACCOUNT_ALREADY_EXISTS);
  }
}
