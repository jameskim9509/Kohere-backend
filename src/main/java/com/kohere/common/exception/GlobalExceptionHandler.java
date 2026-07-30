package com.kohere.common.exception;

import com.kohere.common.logging.AccessLogContext;
import com.kohere.common.response.ApiResponse;
import com.kohere.common.response.FieldErrorDetail;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 핸들러. 모든 예외를 공통 래퍼 에러 응답으로 일관되게 변환한다.
 *
 * <p>도메인/비즈니스 예외는 {@link BusinessException}으로, 입력 검증 실패는 {@code INVALID_INPUT}으로, Spring MVC 표준 예외는
 * 대응 공통 코드로 매핑한다. docs/api/error-response-guide.md §5.
 *
 * <p>응답 {@code message}는 {@link ErrorCode#getCode()}를 키로 리소스 번들({@code
 * messages[_<lang>].properties})에서 요청 locale({@code Accept-Language})로 번역한다 — 키/언어가 없으면 기본 번들(영어),
 * 그래도 없으면 코드의 기본 메시지로 폴백한다(ADR-0030). 동적 메시지({@code BusinessException}의 커스텀 메시지)는 키 부재 시 그대로 노출된다.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

  private final MessageSource messageSource;

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
    ErrorCode ec = e.getErrorCode();
    // 5xx(UPSTREAM_ERROR·INTERNAL_ERROR 등)는 서버/의존성 장애다 — cause 스택트레이스까지 남겨야
    // 삼켜지지 않는다(예: SMTP MailException). 4xx는 클라이언트 오류라 노이즈이므로 로깅하지 않는다.
    if (ec.getHttpStatus().is5xxServerError()) {
      log.error("Business exception [{}]: {}", ec.getCode(), e.getMessage(), e);
    }
    AccessLogContext.errorCode(ec.getCode());
    return ResponseEntity.status(ec.getHttpStatus())
        .body(ApiResponse.error(ec.getCode(), resolveMessage(ec, e.getMessage())));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
    List<FieldErrorDetail> details =
        e.getBindingResult().getFieldErrors().stream()
            .map(GlobalExceptionHandler::toFieldErrorDetail)
            .toList();
    ErrorCode ec = ErrorCode.INVALID_INPUT;
    AccessLogContext.errorCode(ec.getCode());
    return ResponseEntity.badRequest()
        .body(ApiResponse.error(ec.getCode(), resolveMessage(ec, ec.getDefaultMessage()), details));
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

  // 미매핑 경로(NoHandlerFoundException)·정적 리소스 미존재(NoResourceFoundException, Boot 3) 모두 404로.
  @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
  public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception e) {
    return errorResponse(ErrorCode.RESOURCE_NOT_FOUND);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
    log.error("Unhandled exception", e);
    return errorResponse(ErrorCode.INTERNAL_ERROR);
  }

  private ResponseEntity<ApiResponse<Void>> errorResponse(ErrorCode ec) {
    AccessLogContext.errorCode(ec.getCode());
    return ResponseEntity.status(ec.getHttpStatus())
        .body(ApiResponse.error(ec.getCode(), resolveMessage(ec, ec.getDefaultMessage())));
  }

  /** ErrorCode 코드 키로 요청 locale의 메시지를 해소한다(키/언어 부재 시 {@code fallback}). */
  private String resolveMessage(ErrorCode ec, String fallback) {
    return messageSource.getMessage(ec.getCode(), null, fallback, LocaleContextHolder.getLocale());
  }

  private static FieldErrorDetail toFieldErrorDetail(FieldError fe) {
    return new FieldErrorDetail(fe.getField(), fe.getDefaultMessage());
  }
}
