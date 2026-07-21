package com.kohere.auth.domain;

/**
 * 이메일 인증번호 <b>발급</b> 포트 — 인증번호 생성과 발송을 한 책임으로 묶는다. 둘을 분리하면 "발송만 건너뛰고 코드는 랜덤 생성"처럼 저장된 해시와 사용자가 받은
 * 코드가 어긋나는 조합이 성립하므로, 생성·발송을 한 구현이 함께 결정하게 한다.
 *
 * <p>동기 발송이며 발송 실패는 {@link EmailDispatchException}(502)으로 던진다 — 호출자({@code
 * EmailVerificationService})는 이 메서드가 정상 반환한 경우에만 챌린지를 저장한다(send-then-store).
 */
public interface EmailVerificationCodeIssuer {

  /**
   * 인증번호를 발급하고 필요하면 발송한다.
   *
   * @param userId 요청 주체(고정 인증번호 대상 판별에 쓴다)
   * @param email 인증 대상 이메일(발송처)
   * @return 저장할 챌린지의 원본 인증번호
   */
  String issue(long userId, String email);
}
