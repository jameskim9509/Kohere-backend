package com.kohere.auth.infrastructure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Twilio Programmable SMS 설정(app.twilio, ADR-0034). {@code enabled=false}면 실 발송 어댑터 대신 로깅 폴백을
 * 쓴다(크리덴셜 없이 로컬/dev/test 동작). {@code authToken}은 1급 시크릿이라 SSM에서 주입하며({@code
 * /kohere-{env}/TWILIO_AUTH_TOKEN}, ADR-0023) 로그·{@code toString}에 노출하지 않는다.
 *
 * <p>TODO(확인 필요): Twilio Messages.json 정확한 필드·한국 발신번호(sender ID) 등록·국내외 번호 정책·단가는 미확정(ADR-0034 미결).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.twilio")
public class TwilioProperties {

  /** 실 발송 활성화(미설정 시 로깅 폴백). */
  private boolean enabled = false;

  /** Twilio Account SID. */
  private String accountSid;

  /** Twilio Auth Token — 1급 시크릿(SSM). */
  private String authToken;

  /** 발신 번호(Twilio 등록 번호). */
  private String fromNumber;

  /** Messages 엔드포인트 템플릿({@code %s}=AccountSid). */
  private String messagesUrlTemplate =
      "https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json";

  private long connectTimeoutMillis = 3000;
  private long readTimeoutMillis = 5000;
}
