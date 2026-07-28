package com.kohere.common.logging;

/**
 * 로그 필드 키 정본(ADR-0038 "공통 기반"). 문서·구현·CloudWatch Logs Insights 쿼리가 <b>같은 문자열</b>을 쓰게 하려고 상수로 고정한다 —
 * {@code actor} 같은 별칭을 쓰지 않는다.
 *
 * <p>{@link #TRACE_ID}·{@link #USER_ID}·{@link #ONBOARDING}은 MDC에 실려 해당 요청의 모든 로그 줄에 자동으로 붙는다. 나머지는
 * 라인별 {@code StructuredArguments.kv} 인자다.
 */
public final class LogFields {

  /** 요청 1건을 식별하는 UUID. {@link MdcLoggingFilter}가 넣고 요청 종료 시 지운다. */
  public static final String TRACE_ID = "traceId";

  /** 인증된 사용자 id, 또는 {@link #ANONYMOUS}. */
  public static final String USER_ID = "userId";

  /** 온보딩 완료 여부(토큰 스코프). */
  public static final String ONBOARDING = "onboarding";

  /**
   * 신원 없음. 미인증 요청과 {@code permitAll} 경로의 게스트가 같은 값을 갖는다(#181) — 둘의 구분은 {@link #PATH_PATTERN}이 공개
   * 경로인지로 사후 판별한다.
   */
  public static final String ANONYMOUS = "anonymous";

  public static final String METHOD = "method";

  /** 원경로가 아닌 URI 템플릿. 엔드포인트 단위 집계의 전제다. */
  public static final String PATH_PATTERN = "pathPattern";

  /** 템플릿에 채워진 실제 값. 이게 없으면 "무엇을" 바꿨는지가 로그에서 사라진다. */
  public static final String PATH_VARS = "pathVars";

  public static final String STATUS = "status";

  /** 구간 소요 시간(ms). 용도 4는 전용 라인 없이 이 필드로만 충족한다. */
  public static final String LATENCY_MS = "latencyMs";

  /** 공통 래퍼의 {@code error.code}. 4xx는 스택 대신 이 값으로만 관측한다. */
  public static final String ERROR_CODE = "errorCode";

  private LogFields() {}
}
