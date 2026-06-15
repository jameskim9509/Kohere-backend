package com.kohere.common.exception;

import com.kohere.common.response.ApiResponse;
import com.kohere.common.response.FieldErrorDetail;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 전역 예외 핸들러. 모든 예외를 공통 래퍼 에러 응답으로 일관되게 변환한다.
 *
 * <p>도메인/비즈니스 예외는 {@link BusinessException}으로, 입력 검증 실패는 {@code INVALID_INPUT}으로, Spring MVC 표준 예외는
 * 대응 공통 코드로 매핑한다. docs/api/error-response-guide.md §5.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
    ErrorCode ec = e.getErrorCode();
    return ResponseEntity.status(ec.getHttpStatus())
        .body(ApiResponse.error(ec.getCode(), e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
    List<FieldErrorDetail> details =
        e.getBindingResult().getFieldErrors().stream()
            .map(GlobalExceptionHandler::toFieldErrorDetail)
            .toList();
    return ResponseEntity.badRequest()
        .body(
            ApiResponse.error(
                ErrorCode.INVALID_INPUT.getCode(),
                ErrorCode.INVALID_INPUT.getDefaultMessage(),
                details));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ApiResponse<Void>> handleMalformed(Exception e) {
    return errorResponse(ErrorCode.MALFORMED_REQUEST);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
      HttpRequestMethodNotSupportedException e) {
    return errorResponse(ErrorCode.METHOD_NOT_ALLOWED);
  }

  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotFound(NoHandlerFoundException e) {
    return errorResponse(ErrorCode.RESOURCE_NOT_FOUND);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
    log.error("Unhandled exception", e);
    return errorResponse(ErrorCode.INTERNAL_ERROR);
  }

  private static ResponseEntity<ApiResponse<Void>> errorResponse(ErrorCode ec) {
    return ResponseEntity.status(ec.getHttpStatus())
        .body(ApiResponse.error(ec.getCode(), ec.getDefaultMessage()));
  }

  private static FieldErrorDetail toFieldErrorDetail(FieldError fe) {
    return new FieldErrorDetail(fe.getField(), fe.getDefaultMessage());
  }
}
