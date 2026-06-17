package com.kohere.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kohere.common.exception.ErrorCode;
import com.kohere.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;

/** 보안 계층(EntryPoint/AccessDeniedHandler)이 공통 래퍼 에러 응답을 직접 직렬화하기 위한 헬퍼. */
final class SecurityErrorResponder {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private SecurityErrorResponder() {}

  static void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
    response.setStatus(errorCode.getHttpStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    OBJECT_MAPPER.writeValue(
        response.getWriter(),
        ApiResponse.error(errorCode.getCode(), errorCode.getDefaultMessage()));
  }
}
