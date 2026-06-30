package com.kohere.auth.domain;

/**
 * 연락처 인증번호 SMS 발송 아웃바운드 포트. 인프라 어댑터(예: Twilio Programmable SMS)가 구현한다. 동기 발송 — provider 장애·타임아웃 시
 * {@link SmsDispatchException}(502)을 던진다(발송 성공 시에만 챌린지 확정, 이메일 발송과 대칭). 인증번호 생성·해시·검증은 서버가 보유하고
 * 어댑터는 발송만 담당한다(ADR-0034).
 */
public interface VerificationSmsSender {

  void send(String toPhoneNumber, String code);
}
