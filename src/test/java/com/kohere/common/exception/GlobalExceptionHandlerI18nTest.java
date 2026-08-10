package com.kohere.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.kohere.common.response.ApiResponse;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.ResponseEntity;

/**
 * 에러 메시지 i18n(리소스 번들) 단위 테스트. ErrorCode 코드 키로 요청 locale의 messages 번들에서 메시지를 해소하고, 키/언어가 없으면
 * 영어(messages.properties)로 폴백하는지 검증한다(ADR-0030, #52).
 */
class GlobalExceptionHandlerI18nTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler(messageSource());

  private static MessageSource messageSource() {
    ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
    ms.setBasename("messages");
    ms.setDefaultEncoding("UTF-8");
    ms.setFallbackToSystemLocale(false);
    return ms;
  }

  @AfterEach
  void resetLocale() {
    LocaleContextHolder.resetLocaleContext();
  }

  @Test
  @DisplayName("한국어 locale이면 ko 메시지로 번역한다")
  void koreanMessage() {
    LocaleContextHolder.setLocale(Locale.KOREAN);
    assertThat(message(ErrorCode.USER_NOT_FOUND)).isEqualTo("사용자를 찾을 수 없습니다.");
  }

  @Test
  @DisplayName("영어 locale이면 en 메시지로 번역한다")
  void englishMessage() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);
    assertThat(message(ErrorCode.USER_NOT_FOUND)).isEqualTo("User not found.");
  }

  @Test
  @DisplayName("번들에 없는 locale(ja)은 영어 기본 번들로 폴백한다")
  void fallsBackToEnglish() {
    LocaleContextHolder.setLocale(Locale.JAPANESE);
    assertThat(message(ErrorCode.DIAGNOSIS_NOT_FOUND)).isEqualTo("Diagnosis not found.");
  }

  @Test
  @DisplayName("errors[].reason도 요청 locale로 번역하고 인자를 채운다")
  void koreanReason() {
    LocaleContextHolder.setLocale(Locale.KOREAN);
    assertThat(reason(new InvalidInputException("size", "validation.range", 1, 100, 101)))
        .isEqualTo("1~100 사이여야 합니다. 받은 값: 101");
  }

  @Test
  @DisplayName("영어 locale이면 reason도 영어다")
  void englishReason() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);
    assertThat(reason(new InvalidInputException("size", "validation.range", 1, 100, 101)))
        .isEqualTo("Must be between 1 and 100. Received: 101");
  }

  /**
   * {@code reason}은 클라이언트가 보낸 값을 <b>그대로</b> 되비춰야 자기 입력과 대조할 수 있다 — {@code MessageFormat} 기본 숫자 포맷이
   * 붙이는 천 단위 구분자({@code 700000} → {@code 700,000})를 번들에서 껐다.
   */
  @Test
  @DisplayName("숫자 인자에 천 단위 구분자를 붙이지 않는다")
  void numbersKeepClientFormat() {
    LocaleContextHolder.setLocale(Locale.KOREAN);
    assertThat(
            reason(
                new InvalidInputException(
                    "minBudget", "validation.notGreaterThan", "maxBudget", 700000, 300000)))
        .isEqualTo("maxBudget보다 클 수 없습니다. 받은 값: 700000 > 300000");
  }

  /** 키를 두지 않은 호출부가 종전처럼 동작하는지 — 넘긴 문자열이 그대로 나간다. */
  @Test
  @DisplayName("번들에 없는 사유 코드는 넘긴 문자열을 그대로 쓴다")
  void unknownReasonCodeFallsBackToItself() {
    LocaleContextHolder.setLocale(Locale.KOREAN);
    assertThat(reason(new InvalidInputException("size", "아직 키로 바꾸지 않은 문장")))
        .isEqualTo("아직 키로 바꾸지 않은 문장");
  }

  @Test
  @DisplayName("필드를 특정하지 않으면 errors[]는 빈 배열이다")
  void noFieldMeansEmptyErrors() {
    LocaleContextHolder.setLocale(Locale.KOREAN);
    assertThat(handler.handleInvalidInput(new InvalidInputException("설명만 있는 경우")).getBody())
        .satisfies(body -> assertThat(body.error().errors()).isEmpty());
  }

  private String message(ErrorCode code) {
    ResponseEntity<ApiResponse<Void>> response = handler.handleBusiness(new TestException(code));
    return response.getBody().error().message();
  }

  private String reason(InvalidInputException e) {
    return handler.handleInvalidInput(e).getBody().error().errors().get(0).reason();
  }

  /** 테스트용 구체 비즈니스 예외(추상 {@link BusinessException}를 인스턴스화하기 위함). */
  private static final class TestException extends BusinessException {
    private TestException(ErrorCode errorCode) {
      super(errorCode);
    }
  }
}
