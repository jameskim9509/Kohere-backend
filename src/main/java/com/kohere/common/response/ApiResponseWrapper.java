package com.kohere.common.response;

import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 성공 응답 공통 래퍼 자동 적용(ADR-0013). 컨트롤러는 도메인 응답 DTO만 반환하고, 본 advice가 {@link
 * ApiResponse#success(Object)}로 감싼다. 에러 응답은 {@link
 * com.kohere.common.exception.GlobalExceptionHandler}가 이미 래핑한다.
 *
 * <p>제외 규칙: (a) 이미 {@link ApiResponse}(전역 에러 핸들러 응답 포함)는 재래핑하지 않는다, (b) 본문 없는 응답(204 등), (c) {@code
 * String}/{@code byte[]}/{@link Resource} 등 비-JSON 본문. 적용 범위는 {@code com.kohere} 컨트롤러로 한정해 Actuator
 * 등 프레임워크 엔드포인트는 건드리지 않는다.
 */
@ControllerAdvice(basePackages = "com.kohere")
public class ApiResponseWrapper implements ResponseBodyAdvice<Object> {

  @Override
  public boolean supports(
      MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    // 이미 ApiResponse를 반환하는 핸들러(아직 미전환 컨트롤러·에러 핸들러 등)는 재래핑 대상이 아니다.
    return !ApiResponse.class.isAssignableFrom(returnType.getParameterType());
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter returnType,
      MediaType selectedContentType,
      Class<? extends HttpMessageConverter<?>> selectedConverterType,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    // 본문 없음(204 등)·이미 래핑됨·비-JSON 본문은 그대로 둔다.
    if (body == null
        || body instanceof ApiResponse<?>
        || body instanceof CharSequence
        || body instanceof byte[]
        || body instanceof Resource) {
      return body;
    }
    return ApiResponse.success(body);
  }
}
