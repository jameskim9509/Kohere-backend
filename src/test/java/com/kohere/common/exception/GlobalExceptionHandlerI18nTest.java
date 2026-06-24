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

  private String message(ErrorCode code) {
    ResponseEntity<ApiResponse<Void>> response = handler.handleBusiness(new TestException(code));
    return response.getBody().error().message();
  }

  /** 테스트용 구체 비즈니스 예외(추상 {@link BusinessException}를 인스턴스화하기 위함). */
  private static final class TestException extends BusinessException {
    private TestException(ErrorCode errorCode) {
      super(errorCode);
    }
  }
}
