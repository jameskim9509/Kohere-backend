package com.kohere.common.exception;

/**
 * 입력값이 의미상 올바르지 않을 때(예: enum 문자열 매핑 실패). 전역 핸들러가 400 {@code INVALID_INPUT}으로 변환한다.
 *
 * <p>Bean Validation으로 잡히는 형식 위반은 핸들러가 {@code INVALID_INPUT}으로 직접 매핑하므로, 이 예외는 응용/도메인 계층에서 형식은
 * 통과했으나 값이 유효하지 않을 때 쓴다.
 */
public class InvalidInputException extends BusinessException {

  public InvalidInputException() {
    super(ErrorCode.INVALID_INPUT);
  }

  public InvalidInputException(String message) {
    super(ErrorCode.INVALID_INPUT, message);
  }
}
