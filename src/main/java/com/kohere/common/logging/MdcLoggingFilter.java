package com.kohere.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 단위 MDC 수립 필터(ADR-0038 롤아웃 ①). {@code traceId}를 발급해 이 요청이 남기는 <b>모든</b> 로그 줄에 자동으로 붙인다 — 접근
 * 로그·보안 감사·5xx 스택이 흩어져 기록되므로, 이들을 하나의 요청으로 되묶는 조인 키가 필요하다.
 *
 * <p>{@link Ordered#HIGHEST_PRECEDENCE}로 보안 필터 체인({@code SecurityProperties.DEFAULT_FILTER_ORDER =
 * -100})보다 앞에 선다. 보안 계층이 401/403을 직접 write하는 경로(#181)에서도 {@code traceId}가 이미 서 있어야 하기 때문이다.
 *
 * <p>{@code userId}는 여기서 {@link LogFields#ANONYMOUS}로 깔아두고, 인증에 성공하면 {@code
 * JwtAuthenticationFilter}가 실제 값으로 덮는다 — 신원을 아는 시점이 인증 이후라 두 단계로 나뉜다.
 *
 * <p><b>{@code finally}의 {@code MDC.clear()}가 필수다.</b> MDC는 {@code ThreadLocal}이고 톰캣은 워커 스레드를
 * 재사용하므로, 지우지 않으면 다음 요청이 앞 요청의 {@code traceId}를 물고 간다. 현재 저장소에 {@code @Async}·{@code @Scheduled}가
 * 0건이라 스레드 경계를 넘는 전파는 고려하지 않는다 — 비동기 경로가 생기면 재검토 대상이다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcLoggingFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      MDC.put(LogFields.TRACE_ID, UUID.randomUUID().toString());
      MDC.put(LogFields.USER_ID, LogFields.ANONYMOUS);
      filterChain.doFilter(request, response);
    } finally {
      MDC.clear();
    }
  }
}
