package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.SmsDispatchException;
import com.kohere.auth.domain.VerificationSmsSender;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.service.DefaultMessageService;

/**
 * {@link VerificationSmsSender} 어댑터 — 국내 SMS API SOLAPI SDK로 인증번호를 발송한다(ADR-0034). 동기 발송 — SOLAPI
 * SDK 발송 실패(잘못된 요청·미수신·빈 응답 등 런타임 예외)는 모두 {@link SmsDispatchException}(502)으로 매핑한다(발송 성공 시에만 챌린지
 * 확정, 이메일 발송과 대칭). 인증번호·자격증명은 로깅하지 않는다.
 *
 * <p>발신번호(from)는 SOLAPI 콘솔에 사전 등록된 번호여야 한다. 크리덴셜이 확정되면 {@link SolapiClientConfig}에서 {@code
 * app.solapi.enabled=true}로 활성화한다.
 */
public class SolapiVerificationSmsSender implements VerificationSmsSender {

  private final DefaultMessageService messageService;
  private final String from;

  public SolapiVerificationSmsSender(DefaultMessageService messageService, String from) {
    this.messageService = messageService;
    this.from = from;
  }

  @Override
  public void send(String toPhoneNumber, String code) {
    Message message = new Message();
    message.setFrom(digits(from));
    message.setTo(digits(toPhoneNumber));
    message.setText("[Kohere] 인증번호: " + code);
    try {
      messageService.send(message);
    } catch (Exception e) {
      // SOLAPI SDK 발송 실패(미수신·잘못된 요청 등)는 모두 502로 매핑(공식 예제의 catch(Exception) 패턴).
      throw new SmsDispatchException(e);
    }
  }

  /** 발송용 번호 정규화 — 숫자만(국내 형식, 예 01012345678). */
  private static String digits(String phone) {
    return phone == null ? null : phone.replaceAll("\\D", "");
  }
}
