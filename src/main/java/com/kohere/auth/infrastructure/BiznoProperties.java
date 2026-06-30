package com.kohere.auth.infrastructure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 사업자등록번호 검증(비즈노 API) 설정(app.bizno, ADR-0033). {@code enabled=false}면 실 검증 어댑터 대신 스텁 폴백을 쓴다(크리덴셜 없이
 * 로컬/dev/test 동작). {@code apiKey}는 시크릿이라 SSM에서 주입한다({@code /kohere-{env}/BIZNO_API_KEY}, ADR-0023).
 *
 * <p>TODO(확인 필요): 비즈노 API 엔드포인트·요청/응답 계약·타임아웃/재시도·rate-limit 임계값 미확정(ADR-0033 미결).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.bizno")
public class BiznoProperties {

  /** 실 검증 활성화(미설정 시 스텁 폴백). */
  private boolean enabled = false;

  /** 비즈노 API 키 — 시크릿(SSM). */
  private String apiKey;

  /** 비즈노 API base URL(TODO: 정확한 엔드포인트 확정 필요). */
  private String baseUrl = "https://bizno.net/api";

  private long connectTimeoutMillis = 3000;
  private long readTimeoutMillis = 5000;
}
