package com.kohere.common.security;

import static com.kohere.common.logging.LogFields.ERROR_CODE;
import static com.kohere.common.logging.LogFields.STATUS;
import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kohere.common.exception.ErrorCode;
import com.kohere.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;

/**
 * 보안 계층이 공통 래퍼 에러 응답을 직접 직렬화하기 위한 헬퍼. 호출자는 셋이다 — {@link RestAuthenticationEntryPoint}(401 미인증),
 * {@link RestAccessDeniedHandler}(403 권한 부족), {@link JwtAuthenticationFilter}(만료 401. {@code
 * permitAll} 경로는 EntryPoint가 돌지 않아 필터가 직접 끊는다, #181).
 *
 * <p>클라이언트에 나가는 401/403이 전부 이 메서드를 거치므로 <b>보안 감사 로그도 여기 한 곳에서 남긴다</b>(ADR-0038 용도 3). 호출자마다 남기면 네
 * 번째 호출자가 생길 때 조용히 누락되지만, 여기 두면 로그 줄 수와 실제 거부 응답 수가 구조적으로 일치한다.
 */
@Slf4j
final class SecurityErrorResponder {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private SecurityErrorResponder() {}

  static void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
    logDenial(errorCode);
    response.setStatus(errorCode.getHttpStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    OBJECT_MAPPER.writeValue(
        response.getWriter(),
        ApiResponse.error(errorCode.getCode(), errorCode.getDefaultMessage()));
  }

  /**
   * 인증·인가 거부를 감사 로그로 남긴다(ADR-0038 용도 3). {@code traceId}·{@code userId}는 MDC라 자동으로 붙는다.
   *
   * <p>{@code TOKEN_EXPIRED}만 401 중 유일하게 INFO다 — 짧은 access + refresh 회전 구조에서 만료 401은 일상이라, WARN에 두면
   * 무차별 토큰 대입 같은 진짜 이상 신호가 그 안에 묻힌다.
   */
  private static void logDenial(ErrorCode errorCode) {
    if (errorCode == ErrorCode.TOKEN_EXPIRED) {
      log.info("AUTH_DENIED {} {}", kv(ERROR_CODE, errorCode.getCode()), kv(STATUS, 401));
    } else {
      log.warn(
          "AUTH_DENIED {} {}",
          kv(ERROR_CODE, errorCode.getCode()),
          kv(STATUS, errorCode.getHttpStatus().value()));
    }
  }
}
