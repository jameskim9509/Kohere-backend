package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.SmsDispatchException;
import com.kohere.auth.domain.VerificationSmsSender;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link VerificationSmsSender} 어댑터 — Twilio Programmable SMS({@code Messages.json})에 Basic 인증 form
 * POST로 인증번호를 발송한다(ADR-0034). 동기 발송 — 4xx/5xx/타임아웃/I/O 등 모든 발송 실패는 {@link
 * SmsDispatchException}(502)으로 매핑한다(발송 성공 시에만 챌린지 확정). 인증번호·Auth Token은 로깅하지 않는다.
 *
 * <p>크리덴셜/계약이 확정되면 {@link TwilioClientConfig}에서 {@code app.twilio.enabled=true}로 활성화한다. TODO(확인
 * 필요): Messages.json 정확한 필드·한국 발신번호 정책·단가(ADR-0034 미결).
 */
public class TwilioVerificationSmsSender implements VerificationSmsSender {

  private final TwilioProperties properties;
  private final RestClient restClient;

  public TwilioVerificationSmsSender(TwilioProperties properties, RestClient restClient) {
    this.properties = properties;
    this.restClient = restClient;
  }

  @Override
  public void send(String toPhoneNumber, String code) {
    String url = String.format(properties.getMessagesUrlTemplate(), properties.getAccountSid());
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("To", toPhoneNumber);
    form.add("From", properties.getFromNumber());
    form.add("Body", "[Kohere] 인증번호: " + code);
    try {
      restClient
          .post()
          .uri(url)
          .headers(h -> h.setBasicAuth(properties.getAccountSid(), properties.getAuthToken()))
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(form)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException e) {
      throw new SmsDispatchException(e);
    }
  }
}
