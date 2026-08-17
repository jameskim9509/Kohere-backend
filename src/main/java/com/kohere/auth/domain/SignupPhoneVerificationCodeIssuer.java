package com.kohere.auth.domain;

/**
 * 가입용 연락처(SMS) 인증번호 <b>발급</b> 포트 — 인증번호 생성과 발송을 한 책임으로 묶는다({@link PhoneVerificationCodeIssuer}와 같은
 * 이유: 둘을 나누면 저장된 해시와 사용자가 받은 코드가 어긋나는 조합이 성립한다).
 *
 * <p><b>왜 {@link PhoneVerificationCodeIssuer}를 그대로 쓰지 않는가</b> — 그 포트의 시그니처는 {@code issue(long
 * userId, ...)}라 계정이 없는 가입 전 단계에서는 넘길 값이 없고(가짜 {@code 0}을 흘리면 읽는 사람이 거짓말을 해독해야 한다), local·dev에서 그
 * 포트의 {@code @Primary} 구현이 <b>앱스토어 심사용 고정 인증번호 우회</b>({@code FixedVerificationPolicy})라 주입하는 것만으로 웹
 * 가입 경로가 그 우회에 연결된다. 그 우회는 {@code userId} + Google 소셜 계정으로 판정하므로 <b>여기엔 적용될 수 없고 적용해서도 안 된다</b>(앱
 * 심사용 기능이라 웹 가입과 무관 — 스펙 §1-1). 그래서 번호만 받는 포트를 따로 두고, 실제로 공유해야 할 것(자릿수 정책 {@code
 * app.phone.code-length}, 발송 포트 {@link VerificationSmsSender})은 구현체가 그대로 공유한다.
 *
 * <p>동기 발송이며 발송 실패는 {@link SmsDispatchException}(502)으로 던진다 — 호출자({@code
 * SignupPhoneVerificationService})는 이 메서드가 정상 반환한 경우에만 챌린지를 저장한다(send-then-store).
 */
public interface SignupPhoneVerificationCodeIssuer {

  /**
   * 인증번호를 발급하고 발송한다.
   *
   * @param phoneNumber 정규화된 인증 대상 휴대폰 번호(발송처)
   * @return 저장할 챌린지의 원본 인증번호
   */
  String issue(String phoneNumber);
}
