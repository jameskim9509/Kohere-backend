package com.kohere.auth.domain;

/**
 * 인증번호 메일 발송 아웃바운드 포트. 구현은 infrastructure(SMTP). <b>동기 발송</b>이며 발송 실패(provider 장애·타임아웃)는 {@link
 * EmailDispatchException}(502)으로 던진다 — 발송 성공 시에만 챌린지를 확정한다(send-then-store). domain-model
 * §1(EmailVerification 협력).
 */
public interface VerificationEmailSender {

  void send(String to, String code);
}
