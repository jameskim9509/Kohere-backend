package com.kohere.common.exception;

/**
 * 모든 비즈니스 예외의 최상위 추상 타입. {@link ErrorCode}를 보유하며, 전역 핸들러가 status·code로 변환한다.
 *
 * <p>각 모듈은 의미가 드러나는 이름의 구체 예외(예: {@code ListingNotFoundException})로 이 타입을 상속한다. 컨트롤러에서 try/catch로
 * 응답을 만들지 않는다. docs/api/error-response-guide.md §5, docs/convention/code-style.md §5.
 */
public abstract class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;

  protected BusinessException(ErrorCode errorCode) {
    super(errorCode.getDefaultMessage());
    this.errorCode = errorCode;
  }

  protected BusinessException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }
}
