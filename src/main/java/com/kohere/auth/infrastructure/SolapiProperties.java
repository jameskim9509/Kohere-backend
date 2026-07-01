package com.kohere.auth.infrastructure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SOLAPI(국내 SMS API) 발송 설정(app.solapi, ADR-0034). {@code enabled=false}면 실 발송 어댑터 대신 로깅 폴백을 쓴다(크리덴셜
 * 없이 로컬/dev/test 동작). {@code apiSecret}은 1급 시크릿이라 SSM에서 주입하며({@code
 * /kohere-{env}/SOLAPI_API_SECRET}, ADR-0023) 로그·{@code toString}에 노출하지 않는다. {@code from}은 SOLAPI
 * 콘솔에 사전 등록된 발신번호다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.solapi")
public class SolapiProperties {

  /** 실 발송 활성화(미설정 시 로깅 폴백). */
  private boolean enabled = false;

  /** SOLAPI API Key. */
  private String apiKey;

  /** SOLAPI API Secret — 1급 시크릿(SSM). */
  private String apiSecret;

  /** 사전 등록된 발신번호. */
  private String from;
}
