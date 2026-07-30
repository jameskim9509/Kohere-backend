package com.kohere.common.logging;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * 응답을 만드는 쪽이 접근 로그에 값을 넘기는 통로. 현재는 {@code errorCode} 하나다.
 *
 * <p>{@link AccessLogInterceptor}는 응답 <b>상태</b>만 볼 수 있고 공통 래퍼의 {@code error.code}는 볼 수 없다. 4xx는 스택을
 * 남기지 않기로 했으므로(ADR-0038 범위 제외) 이 코드가 4xx를 분류하는 유일한 값이라, {@code GlobalExceptionHandler}가 요청 속성에 실어
 * 인터셉터로 전달한다.
 */
public final class AccessLogContext {

  static final String ERROR_CODE_ATTRIBUTE = "kohere.log.errorCode";

  private AccessLogContext() {}

  /**
   * 이 요청의 응답 에러 코드를 기록한다. 서블릿 요청 범위 밖(비 MVC 컨텍스트)에서는 조용히 무시한다.
   *
   * @param errorCode 공통 래퍼 {@code error.code}
   */
  public static void errorCode(String errorCode) {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes != null) {
      attributes.setAttribute(ERROR_CODE_ATTRIBUTE, errorCode, RequestAttributes.SCOPE_REQUEST);
    }
  }
}
