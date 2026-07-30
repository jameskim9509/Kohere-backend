package com.kohere.common.logging;

import static net.logstash.logback.argument.StructuredArguments.kv;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 전 요청 접근 로그(ADR-0038 용도 1, 롤아웃 ③). 컨트롤러 매핑 65개 전부에 한 줄씩 남긴다 — 동작 46개와 미구현 껍데기 19개를 모두 포함하며, 껍데기
 * 호출은 500이라 용도 5의 스택과 같은 {@code traceId}로 묶인다.
 *
 * <p>이 한 줄이 세 용도에 동시에 기여한다 — 용도 1(무엇을 했나), 용도 4({@code latencyMs}로 지연 분해), 용도 5({@code
 * status}·{@code errorCode}로 실패 분류). 용도마다 라인을 따로 만들지 않는 이유다.
 *
 * <p>{@link LogFields#PATH_PATTERN}은 템플릿이라 엔드포인트 집계에 쓰이고, {@link LogFields#PATH_VARS}가 그 템플릿에 채워진
 * 실제 값을 담는다. 후자가 없으면 {@code DELETE /bookings/{bookingId}}에서 "예약 하나를 지웠다"까지만 남고 어느 예약인지가 사라져, 예약
 * 삭제·차단 해제·찜 토글을 별도 이벤트 없이 접근 로그로만 관측하겠다는 결정이 성립하지 않는다.
 *
 * <p>{@code traceId}·{@code userId}·{@code onboarding}은 MDC라 여기서 넘기지 않아도 붙는다.
 *
 * <p>보안 필터가 직접 끊은 401(#181)은 {@code DispatcherServlet}에 닿지 못해 이 인터셉터가 돌지 않는다 — 그 요청은 용도 3의 감사 로그가
 * 담당한다.
 */
@Slf4j
public class AccessLogInterceptor implements HandlerInterceptor {

  /** 초과 시 WARN으로 승격하는 요청 전체 지연(ms). dev 2주 실측 후 p95로 재조정한다. */
  static final long SLOW_REQUEST_THRESHOLD_MS = 1000L;

  private static final String START_NANOS_ATTRIBUTE = "kohere.log.startNanos";

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    request.setAttribute(START_NANOS_ATTRIBUTE, System.nanoTime());
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    long latencyMs = elapsedMillis(request);
    String message = "ACCESS {} {} {} {} {} {}";
    Object[] fields = {
      kv(LogFields.METHOD, request.getMethod()),
      kv(
          LogFields.PATH_PATTERN,
          attribute(request, HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)),
      kv(LogFields.PATH_VARS, pathVariables(request)),
      kv(LogFields.STATUS, response.getStatus()),
      kv(LogFields.LATENCY_MS, latencyMs),
      kv(LogFields.ERROR_CODE, request.getAttribute(AccessLogContext.ERROR_CODE_ATTRIBUTE))
    };
    if (latencyMs > SLOW_REQUEST_THRESHOLD_MS) {
      log.warn(message, fields);
    } else {
      log.info(message, fields);
    }
  }

  private static long elapsedMillis(HttpServletRequest request) {
    Object startNanos = request.getAttribute(START_NANOS_ATTRIBUTE);
    if (startNanos instanceof Long start) {
      return (System.nanoTime() - start) / 1_000_000L;
    }
    return -1L;
  }

  /** 템플릿 변수 맵. 매핑에 변수가 없으면 빈 맵이라 키 자체는 항상 존재한다. */
  @SuppressWarnings("unchecked")
  private static Map<String, String> pathVariables(HttpServletRequest request) {
    Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    return variables instanceof Map ? (Map<String, String>) variables : Map.of();
  }

  private static Object attribute(HttpServletRequest request, String name) {
    return request.getAttribute(name);
  }
}
