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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
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

  /**
   * 필드를 특정한 {@code InvalidInputException}은 Bean Validation 위반과 같은 모양으로 돌려준다 — {@code
   * error.message}가 일반 문구로 덮이는 구조라, 어느 필드가 왜 거절됐는지 전달할 수 있는 자리는 {@code errors[]}뿐이다(#151). 필드를 특정하지
   * 않았으면 빈 배열이라 {@link #handleBusiness}와 결과가 같다.
   */
  @ExceptionHandler(InvalidInputException.class)
  public ResponseEntity<ApiResponse<Void>> handleInvalidInput(InvalidInputException e) {
    ErrorCode ec = e.getErrorCode();
    AccessLogContext.errorCode(ec.getCode());
    List<FieldErrorDetail> details =
        e.getField() == null
            ? List.of()
            : List.of(new FieldErrorDetail(e.getField(), resolveReason(e)));
    return ResponseEntity.status(ec.getHttpStatus())
        .body(ApiResponse.error(ec.getCode(), resolveMessage(ec, ec.getDefaultMessage()), details));
  }

  /** 사유 번들 키를 요청 locale로 해소한다. 키가 없으면 넘어온 문자열을 그대로 쓴다 — 아직 키로 바꾸지 않은 호출부가 종전처럼 동작하게 하는 폴백이다. */
  private String resolveReason(InvalidInputException e) {
    return messageSource.getMessage(
        e.getReasonCode(), e.getReasonArgs(), e.getReasonCode(), LocaleContextHolder.getLocale());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
    List<FieldErrorDetail> details =
        e.getBindingResult().getFieldErrors().stream().map(this::toFieldErrorDetail).toList();
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

  /**
   * 요청 총량이 서블릿 상한을 넘었다.
   *
   * <p>multipart 해석은 핸들러를 찾기 전에 일어나므로 어느 엔드포인트인지 알 수 없다 — 도메인 코드 대신 공통 코드를 쓴다. 사진 한 장의 상한은 도메인이 따로
   * 보고 {@code LISTING_IMAGE_TOO_LARGE}로 안내한다(ADR-0041).
   */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiResponse<Void>> handlePayloadTooLarge(MaxUploadSizeExceededException e) {
    return errorResponse(ErrorCode.PAYLOAD_TOO_LARGE);
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

  /**
   * {@code errors[].reason}도 {@code message}와 같이 요청 locale로 번역한다(ADR-0030). {@link FieldError}는
   * {@code MessageSourceResolvable}이라 {@code NotBlank.<객체>.<필드>} → {@code NotBlank.<필드>} → {@code
   * NotBlank} 순으로 번들 키를 찾고, 없으면 제약의 기본 메시지로 폴백한다.
   *
   * <p>번들 키를 두지 않으면 Bean Validation의 영문 기본 문구(<i>must not be blank</i>)나 바인딩 실패의 내부 문구(<i>Failed to
   * convert property value of type…</i>)가 그대로 응답에 나간다 — 후자는 사용자에게 보여줄 수 없는 구현 세부다(#151).
   */
  private FieldErrorDetail toFieldErrorDetail(FieldError fe) {
    return new FieldErrorDetail(
        fe.getField(), messageSource.getMessage(fe, LocaleContextHolder.getLocale()));
  }
}
