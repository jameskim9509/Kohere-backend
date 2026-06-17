package com.kohere.common.security;

import com.kohere.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 미인증 요청(401) 진입점. 토큰 부재·위조는 {@code UNAUTHENTICATED}, 만료는 {@code TOKEN_EXPIRED}로 구분해 공통 래퍼로
 * 응답한다(ADR-0010 §5). 만료 여부는 {@link JwtAuthenticationFilter#EXPIRED_ATTRIBUTE}로 판단한다.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    boolean expired =
        Boolean.TRUE.equals(request.getAttribute(JwtAuthenticationFilter.EXPIRED_ATTRIBUTE));
    SecurityErrorResponder.write(
        response, expired ? ErrorCode.TOKEN_EXPIRED : ErrorCode.UNAUTHENTICATED);
  }
}
